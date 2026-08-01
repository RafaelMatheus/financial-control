package com.rafaelmatheus.financialcontrol

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** H-09, H-13 a H-17 — RN-L01 a RN-L10 e RN-T02 a RN-T07. */
class GastoIntegracaoTest : SuporteDeIntegracao() {

    @Test
    fun `valor zero e valor negativo sao recusados`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val categoria = criarCategoria(token, "Mercado")

        lancarGasto(token, categoria, "0.00")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.codigo").value("VALOR_INVALIDO"))

        lancarGasto(token, categoria, "-10.00")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.codigo").value("VALOR_INVALIDO"))
    }

    @Test
    fun `categoria inexistente e recusada com 404`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")

        lancarGasto(token, java.util.UUID.randomUUID().toString(), "10.00")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.codigo").value("CATEGORIA_NAO_ENCONTRADA"))
    }

    @Test
    fun `escopo GRUPO de quem nao pertence a grupo algum e recusado — E-09`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val categoria = criarCategoria(token, "Mercado")

        lancarGasto(
            token, categoria, "10.00",
            escopo = "GRUPO", grupoId = java.util.UUID.randomUUID().toString(),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `gasto PESSOAL pode usar categoria de GRUPO — D-60`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val grupo = criarGrupoDe(token, "Apartamento 42")
        val categoriaDoGrupo = criarCategoria(token, "Mercado", "GRUPO", grupo)

        // O escopo da categoria governa quem a ve e quem a edita, nao a que
        // lancamento ela se aplica.
        lancarGasto(token, categoriaDoGrupo, "89.00", escopo = "PESSOAL")
            .andExpect(status().isCreated)
    }

    @Test
    fun `data futura e aceita — D-61`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val categoria = criarCategoria(token, "Mercado")

        lancarGasto(token, categoria, "10.00", data = "2099-12-31")
            .andExpect(status().isCreated)
    }

    @Test
    fun `outro membro edita, e o dono continua sendo quem lancou`() {
        val (idAna, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (idRafael, tokenRafael) = usuarioAutenticado("rafael@exemplo.com", "Rafael")

        val grupo = criarGrupoDe(tokenAna, "Apartamento 42")
        adicionarMembroAoGrupo(tokenAna, grupo, idRafael)
        val categoria = criarCategoria(tokenAna, "Contas", "GRUPO", grupo)
        val gasto = idDe(
            lancarGasto(tokenAna, categoria, "400.00", escopo = "GRUPO", grupoId = grupo),
        )

        // RN-L05: a invariante mais importante da unidade. Se `dono` mudasse
        // aqui, `totalPessoal` deixaria de significar o que RF-97 diz — e o erro
        // seria silencioso, porque a soma continuaria fechando.
        mvc.perform(
            comToken(put("/gastos/$gasto"), tokenRafael).contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"descricao":"Conta de luz corrigida","valor":420.00,"data":"2026-08-10",
                       "categoriaId":"$categoria","escopo":"GRUPO","grupoId":"$grupo"}""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.donoId").value(idAna))
            .andExpect(jsonPath("$.valor").value("420.00"))
    }

    @Test
    fun `trocar o escopo de PESSOAL para GRUPO expoe retroativamente — D-58`() {
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (idRafael, tokenRafael) = usuarioAutenticado("rafael@exemplo.com", "Rafael")

        val grupo = criarGrupoDe(tokenAna, "Apartamento 42")
        adicionarMembroAoGrupo(tokenAna, grupo, idRafael)
        val categoria = criarCategoria(tokenAna, "Mercado")
        val gasto = idDe(lancarGasto(tokenAna, categoria, "89.00"))

        consultarGastos(tokenRafael).andExpect(jsonPath("$.itens.length()").value(0))

        mvc.perform(
            comToken(put("/gastos/$gasto"), tokenAna).contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"descricao":"Compra","valor":89.00,"data":"2026-08-10",
                       "categoriaId":"$categoria","escopo":"GRUPO","grupoId":"$grupo"}""",
                ),
        ).andExpect(status().isOk)

        // Consequencia aceita e documentada: o que era privado passa a ser visto.
        consultarGastos(tokenRafael).andExpect(jsonPath("$.itens.length()").value(1))
    }

    @Test
    fun `os dois totais do cenario de H-17`() {
        val (idAna, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (idRafael, tokenRafael) = usuarioAutenticado("rafael@exemplo.com", "Rafael")

        val grupo = criarGrupoDe(tokenRafael, "Apartamento 42")
        adicionarMembroAoGrupo(tokenRafael, grupo, idAna)

        val doGrupo = criarCategoria(tokenRafael, "Contas", "GRUPO", grupo)
        val pessoal = criarCategoria(tokenRafael, "Mercado")

        lancarGasto(tokenAna, doGrupo, "400.00", escopo = "GRUPO", grupoId = grupo)
        lancarGasto(tokenRafael, doGrupo, "300.00", escopo = "GRUPO", grupoId = grupo)
        lancarGasto(tokenRafael, pessoal, "89.00")

        // Rafael: 300 (dele, no grupo) + 89 (pessoal) = 389. Grupo: 700.
        totais(tokenRafael, extra = "&grupoId=$grupo")
            .andExpect(jsonPath("$.totalPessoal").value("389.00"))
            .andExpect(jsonPath("$.totalGrupo").value("700.00"))

        // Ana: 400. O total do grupo e o MESMO valor que o Rafael ve.
        totais(tokenAna, extra = "&grupoId=$grupo")
            .andExpect(jsonPath("$.totalPessoal").value("400.00"))
            .andExpect(jsonPath("$.totalGrupo").value("700.00"))

        require(idRafael.isNotBlank())
    }

    @Test
    fun `com dois grupos e sem filtro, a consulta exige o grupo — RN-T05`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        criarGrupoDe(token, "Apartamento 42")
        criarGrupoDe(token, "Viagem")

        totais(token)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.codigo").value("GRUPO_OBRIGATORIO"))
    }

    @Test
    fun `com um grupo so, o filtro e dispensavel`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val grupo = criarGrupoDe(token, "Apartamento 42")
        val categoria = criarCategoria(token, "Contas", "GRUPO", grupo)
        lancarGasto(token, categoria, "150.00", escopo = "GRUPO", grupoId = grupo)

        totais(token)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalGrupo").value("150.00"))
    }

    @Test
    fun `a resposta de totais nao traz a soma das duas grandezas — RN-T04`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val grupo = criarGrupoDe(token, "Apartamento 42")
        val categoria = criarCategoria(token, "Contas", "GRUPO", grupo)
        lancarGasto(token, categoria, "150.00", escopo = "GRUPO", grupoId = grupo)

        val corpo = totais(token).andReturn().response.contentAsString
        val campos = json.readTree(corpo).fieldNames().asSequence().toList()

        // A ausencia e a regra: a soma das duas nao tem significado.
        check(campos.none { it.lowercase().contains("geral") }) {
            "A resposta de totais nao pode ter campo de total geral: $campos"
        }
        check(json.readTree(corpo).get("totalPessoal").asText() == "0.00")
        check(json.readTree(corpo).get("totalGrupo").asText() == "150.00")
    }

    @Test
    fun `a listagem mistura gastos pessoais e do grupo, como H-16 pede`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val grupo = criarGrupoDe(token, "Apartamento 42")
        val doGrupo = criarCategoria(token, "Contas", "GRUPO", grupo)
        val pessoal = criarCategoria(token, "Mercado")

        lancarGasto(token, doGrupo, "150.00", escopo = "GRUPO", grupoId = grupo)
        lancarGasto(token, pessoal, "89.00")

        consultarGastos(token, extra = "&grupoId=$grupo")
            .andExpect(jsonPath("$.itens.length()").value(2))
    }

    @Test
    fun `paginacao respeita o teto de 100`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")

        // O teto existe para que `tamanho=1000000` nao vire caminho de exaustao
        // de memoria. E a unica defesa de recurso desta unidade, e e barata.
        consultarGastos(token, extra = "&tamanho=1000000")
            .andExpect(status().isBadRequest)
    }
}
