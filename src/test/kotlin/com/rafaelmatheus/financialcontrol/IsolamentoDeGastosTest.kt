package com.rafaelmatheus.financialcontrol

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * **O teste mais importante de U2** (RN-T01, RN-L07, D-62, H-03).
 *
 * U2 e a primeira unidade a *implementar* `RepositorioComVisibilidade` — em U1 a
 * porta nasceu sem implementador. Se o predicado estiver errado aqui, o erro se
 * propaga para as seis entidades de U3 e para U4 sem ser notado.
 *
 * O que este arquivo verifica nao e que o visivel aparece: e que **o invisivel
 * nao aparece**. Sao lados diferentes de uma bicondicional, e o segundo e o que
 * testes escritos a mao costumam esquecer.
 */
class IsolamentoDeGastosTest : SuporteDeIntegracao() {

    @Test
    fun `gasto PESSOAL e invisivel ate para membro do mesmo grupo`() {
        val (idAna, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (_, tokenRafael) = usuarioAutenticado("rafael@exemplo.com", "Rafael")

        val grupo = criarGrupoDe(tokenAna, "Apartamento 42")
        adicionarMembroAoGrupo(tokenAna, grupo, usuarioIdDe(tokenRafael))

        val categoria = criarCategoria(tokenAna, "Mercado")
        val gasto = idDe(lancarGasto(tokenAna, categoria, "89.00"))

        // Rafael e membro do mesmo grupo, e mesmo assim nao ve: PESSOAL e
        // impenetravel (RN-L07, RN-V03).
        consultarGastos(tokenRafael).andExpect(jsonPath("$.itens.length()").value(0))

        mvc.perform(comToken(delete("/gastos/$gasto"), tokenRafael))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.codigo").value("LANCAMENTO_NAO_ENCONTRADO"))

        // E a Ana continua vendo o proprio.
        consultarGastos(tokenAna).andExpect(jsonPath("$.itens.length()").value(1))
        require(idAna.isNotBlank())
    }

    @Test
    fun `gasto de GRUPO e visivel a todos os membros, com o dono identificado`() {
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (idRafael, tokenRafael) = usuarioAutenticado("rafael@exemplo.com", "Rafael")

        val grupo = criarGrupoDe(tokenAna, "Apartamento 42")
        adicionarMembroAoGrupo(tokenAna, grupo, idRafael)

        val categoria = criarCategoria(tokenAna, "Contas", "GRUPO", grupo)
        lancarGasto(tokenAna, categoria, "400.00", escopo = "GRUPO", grupoId = grupo)
            .andExpect(status().isCreated)

        // H-14: qualquer membro consulta e ve de quem e o lancamento.
        consultarGastos(tokenRafael)
            .andExpect(jsonPath("$.itens.length()").value(1))
            .andExpect(jsonPath("$.itens[0].donoNome").value("Ana"))
    }

    @Test
    fun `quem nao e membro nao ve nada do grupo`() {
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (_, tokenCarlos) = usuarioAutenticado("carlos@exemplo.com", "Carlos")

        val grupo = criarGrupoDe(tokenAna, "Apartamento 42")
        val categoria = criarCategoria(tokenAna, "Contas", "GRUPO", grupo)
        val gasto = idDe(
            lancarGasto(tokenAna, categoria, "400.00", escopo = "GRUPO", grupoId = grupo),
        )

        consultarGastos(tokenCarlos).andExpect(jsonPath("$.itens.length()").value(0))

        // 404 e nao 403: 403 confirmaria que o lancamento existe (RN-L06).
        mvc.perform(
            comToken(put("/gastos/$gasto"), tokenCarlos).contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"descricao":"X","valor":1.00,"data":"2026-08-10",
                       "categoriaId":"$categoria","escopo":"PESSOAL","grupoId":null}""",
                ),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `ex-membro perde acesso, mas os gastos dele permanecem para os demais`() {
        val (idAna, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (_, tokenRafael) = usuarioAutenticado("rafael@exemplo.com", "Rafael")

        val grupo = criarGrupoDe(tokenRafael, "Apartamento 42")
        adicionarMembroAoGrupo(tokenRafael, grupo, idAna)

        val categoria = criarCategoria(tokenRafael, "Contas", "GRUPO", grupo)
        lancarGasto(tokenAna, categoria, "400.00", escopo = "GRUPO", grupoId = grupo)
            .andExpect(status().isCreated)

        // Ana sai. D-44: corte TOTAL — deixa de ver o grupo inteiro.
        mvc.perform(comToken(delete("/grupos/$grupo/membros/eu"), tokenAna))
            .andExpect(status().isNoContent)

        // O gasto e dela, entao ela continua vendo como DONA — o que sai e a
        // visibilidade que vinha do grupo, nao a que vem de ser dono (RN-V01).
        consultarGastos(tokenAna).andExpect(jsonPath("$.itens.length()").value(1))

        // D-62: para quem ficou, o gasto do ex-membro permanece. O historico da
        // casa fica integro — aquele dinheiro foi gasto pela casa.
        totais(tokenRafael, extra = "&grupoId=$grupo")
            .andExpect(jsonPath("$.totalGrupo").value("400.00"))
            .andExpect(jsonPath("$.totalPessoal").value("0.00"))
    }

    @Test
    fun `membro novo enxerga todo o historico do grupo`() {
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (idCarlos, tokenCarlos) = usuarioAutenticado("carlos@exemplo.com", "Carlos")

        val grupo = criarGrupoDe(tokenAna, "Apartamento 42")
        val categoria = criarCategoria(tokenAna, "Contas", "GRUPO", grupo)
        lancarGasto(tokenAna, categoria, "400.00", escopo = "GRUPO", grupoId = grupo)

        // Antes de entrar, nada.
        consultarGastos(tokenCarlos).andExpect(jsonPath("$.itens.length()").value(0))

        adicionarMembroAoGrupo(tokenAna, grupo, idCarlos)

        // Depois de entrar, tudo — inclusive o que e anterior a entrada (D-13, E-10).
        consultarGastos(tokenCarlos).andExpect(jsonPath("$.itens.length()").value(1))
    }

    private fun usuarioIdDe(token: String): String {
        val resposta = mvc.perform(
            comToken(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/usuarios/eu"),
                token,
            ),
        ).andReturn().response.contentAsString
        return json.readTree(resposta).get("id").asText()
    }
}
