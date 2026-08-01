package com.rafaelmatheus.financialcontrol

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** H-33, H-34, H-35 — RN-C01 a RN-C08. */
class CategoriaIntegracaoTest : SuporteDeIntegracao() {

    @Test
    fun `primeira listagem cria o conjunto inicial`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")

        mvc.perform(comToken(get("/categorias"), token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(10))
            .andExpect(jsonPath("$[?(@.nome == 'Outros')]").isNotEmpty)
    }

    @Test
    fun `apagar todas faz as iniciais ressurgirem — consequencia aceita de D-56`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")

        val ids = json.readTree(
            mvc.perform(comToken(get("/categorias"), token)).andReturn().response.contentAsString,
        ).map { it.get("id").asText() }

        ids.forEach { mvc.perform(comToken(delete("/categorias/$it"), token)) }

        // Nao e defeito: e o preco de nao guardar estado de "ja recebeu as
        // iniciais". Esta escrito em RN-C08 e testado para nao virar surpresa.
        mvc.perform(comToken(get("/categorias"), token))
            .andExpect(jsonPath("$.length()").value(10))
    }

    @Test
    fun `nome duplicado no mesmo escopo pessoal e recusado`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        criarCategoria(token, "Farmacia")

        criarComResposta(token, "Farmacia")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.codigo").value("CATEGORIA_DUPLICADA"))
    }

    @Test
    fun `nome normalizado — Mercado e mercado com espacos sao a mesma categoria`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        criarCategoria(token, "Farmacia")

        criarComResposta(token, "  farmacia  ")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.codigo").value("CATEGORIA_DUPLICADA"))
    }

    @Test
    fun `dois usuarios podem ter cada um a sua categoria pessoal com o mesmo nome`() {
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (_, tokenRafael) = usuarioAutenticado("rafael@exemplo.com", "Rafael")

        criarCategoria(tokenAna, "Farmacia")
        // RN-C02: nas PESSOAIS a unicidade e por dono.
        criarComResposta(tokenRafael, "Farmacia").andExpect(status().isCreated)
    }

    @Test
    fun `no mesmo grupo o nome e unico, de quem quer que seja o dono`() {
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (idRafael, tokenRafael) = usuarioAutenticado("rafael@exemplo.com", "Rafael")

        val grupo = criarGrupoDe(tokenAna, "Apartamento 42")
        adicionarMembroAoGrupo(tokenAna, grupo, idRafael)

        criarCategoria(tokenAna, "Mercado", "GRUPO", grupo)

        // E a razao de existir de D-54: sem isto o total por categoria do grupo
        // mostraria duas linhas "Mercado" com UUIDs diferentes.
        criarComResposta(tokenRafael, "Mercado", "GRUPO", grupo)
            .andExpect(status().isConflict)
    }

    @Test
    fun `qualquer membro renomeia categoria do grupo, e o dono nao muda`() {
        val (idAna, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (idRafael, tokenRafael) = usuarioAutenticado("rafael@exemplo.com", "Rafael")

        val grupo = criarGrupoDe(tokenAna, "Apartamento 42")
        adicionarMembroAoGrupo(tokenAna, grupo, idRafael)
        val categoria = criarCategoria(tokenAna, "Mercado", "GRUPO", grupo)

        mvc.perform(
            comToken(put("/categorias/$categoria"), tokenRafael)
                .contentType(MediaType.APPLICATION_JSON).content("""{"nome":"Supermercado"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nome").value("Supermercado"))
            .andExpect(jsonPath("$.donoId").value(idAna))
    }

    @Test
    fun `escopo GRUPO sem grupo e recusado`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")

        criarComResposta(token, "Contas", "GRUPO", null)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.codigo").value("GRUPO_INVALIDO"))
    }

    @Test
    fun `exclusao de categoria em uso e bloqueada e informa a contagem`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val categoria = criarCategoria(token, "Mercado")
        lancarGasto(token, categoria, "10.00")
        lancarGasto(token, categoria, "20.00")

        // A contagem faz parte da regra (RN-C05): e o numero com que o usuario
        // decide se realoca ou desiste. H-34 pede exatamente isso.
        mvc.perform(comToken(delete("/categorias/$categoria"), token))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.codigo").value("CATEGORIA_EM_USO"))
            .andExpect(jsonPath("$.detalhes[0].campo").value("lancamentosVinculados"))
            .andExpect(jsonPath("$.detalhes[0].problema").value("2"))
    }

    @Test
    fun `realocacao alcanca gasto de outro dono e libera a exclusao`() {
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (idRafael, tokenRafael) = usuarioAutenticado("rafael@exemplo.com", "Rafael")

        val grupo = criarGrupoDe(tokenAna, "Apartamento 42")
        adicionarMembroAoGrupo(tokenAna, grupo, idRafael)

        val origem = criarCategoria(tokenAna, "Contas", "GRUPO", grupo)
        val destino = criarCategoria(tokenAna, "Casa", "GRUPO", grupo)

        // O gasto e do Rafael; quem exclui a categoria e a Ana (RN-C06, D-59).
        lancarGasto(tokenRafael, origem, "400.00", escopo = "GRUPO", grupoId = grupo)

        mvc.perform(comToken(delete("/categorias/$origem?realocarPara=$destino"), tokenAna))
            .andExpect(status().isNoContent)

        consultarGastos(tokenRafael, extra = "&grupoId=$grupo")
            .andExpect(jsonPath("$.itens[0].categoriaId").value(destino))
    }

    @Test
    fun `realocar para a propria categoria e recusado`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val categoria = criarCategoria(token, "Mercado")
        lancarGasto(token, categoria, "10.00")

        mvc.perform(comToken(delete("/categorias/$categoria?realocarPara=$categoria"), token))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.codigo").value("REALOCACAO_INVALIDA"))
    }

    @Test
    fun `categoria de outro usuario responde 404 e nao 403`() {
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (_, tokenCarlos) = usuarioAutenticado("carlos@exemplo.com", "Carlos")
        val categoria = criarCategoria(tokenAna, "Mercado")

        mvc.perform(comToken(delete("/categorias/$categoria"), tokenCarlos))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.codigo").value("CATEGORIA_NAO_ENCONTRADA"))
    }

    private fun criarComResposta(
        token: String,
        nome: String,
        escopo: String = "PESSOAL",
        grupoId: String? = null,
    ) = mvc.perform(
        comToken(post("/categorias"), token).contentType(MediaType.APPLICATION_JSON)
            .content(
                """{"nome":"$nome","escopo":"$escopo",
                   "grupoId":${if (grupoId == null) "null" else "\"$grupoId\""}}""",
            ),
    )
}
