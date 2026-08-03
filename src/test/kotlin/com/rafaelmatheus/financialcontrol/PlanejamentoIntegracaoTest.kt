package com.rafaelmatheus.financialcontrol

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * H-36 a H-41, H-52 a H-60 e **J-02**.
 *
 * A ultima suite de integracao do ciclo.
 */
class PlanejamentoIntegracaoTest : SuporteDeIntegracao() {

    private fun criarReceita(token: String, descricao: String, valor: String, data: String) =
        mvc.perform(
            comToken(post("/receitas"), token).contentType(MediaType.APPLICATION_JSON)
                .content("""{"descricao":"$descricao","valor":$valor,"data":"$data"}"""),
        )

    private fun definirOrcamento(
        token: String,
        categoria: String,
        competencia: String,
        teto: String,
        base: String = "DATA_DA_COMPRA",
        escopo: String = "PESSOAL",
        grupoId: String? = null,
    ) = mvc.perform(
        comToken(post("/orcamentos"), token).contentType(MediaType.APPLICATION_JSON)
            .content(
                """{"categoriaId":"$categoria","competencia":"$competencia","valorTeto":$teto,
                   "base":"$base","escopo":"$escopo",
                   "grupoId":${if (grupoId == null) "null" else "\"$grupoId\""}}""",
            ),
    )

    private fun criarObjetivo(
        token: String,
        nome: String,
        meta: String? = null,
        prazo: String? = null,
        escopo: String = "PESSOAL",
        grupoId: String? = null,
    ) = mvc.perform(
        comToken(post("/investimentos"), token).contentType(MediaType.APPLICATION_JSON)
            .content(
                """{"nome":"$nome","meta":${meta ?: "null"},
                   "prazoAlvo":${if (prazo == null) "null" else "\"$prazo\""},
                   "escopo":"$escopo",
                   "grupoId":${if (grupoId == null) "null" else "\"$grupoId\""}}""",
            ),
    )

    // ------------------------------------------------------------- receita

    @Test
    fun `receita e invisivel ate a membro do mesmo grupo — P-05, RN-RC02`() {
        val (idAna, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (_, tokenRafael) = usuarioAutenticado("rafael@exemplo.com", "Rafael")

        val grupo = criarGrupoDe(tokenRafael, "Apartamento 42")
        adicionarMembroAoGrupo(tokenRafael, grupo, idAna)

        criarReceita(tokenAna, "Salario", "5000.00", "2026-08-05").andExpect(status().isCreated)

        // Nao existe "receita da casa": o predicado aqui e so `dono == usuarioAtual`.
        // E o primeiro caso do sistema em que essa metade roda sozinha.
        mvc.perform(comToken(get("/receitas?de=2026-08-01&ate=2026-08-31"), tokenRafael))
            .andExpect(jsonPath("$.itens.length()").value(0))

        mvc.perform(comToken(get("/receitas?de=2026-08-01&ate=2026-08-31"), tokenAna))
            .andExpect(jsonPath("$.itens.length()").value(1))
            .andExpect(jsonPath("$.total").value("5000.00"))
    }

    @Test
    fun `balanco conta o aporte como gasto — H-38, H-59, RF-76, D-18`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val categoria = criarCategoria(token, "Mercado")

        criarReceita(token, "Salario", "5000.00", "2026-08-05")
        lancarGasto(token, categoria, "1200.00", data = "2026-08-10")

        val objetivo = idDe(criarObjetivo(token, "Viagem"))
        mvc.perform(
            comToken(post("/investimentos/$objetivo/aportes"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"valor":2000.00,"data":"2026-08-15"}"""),
        ).andExpect(status().isOk)

        // 5000 - 1200 - 2000 = 1800. Investir REDUZ o resultado do mes, embora o
        // patrimonio nao diminua: o balanco mede fluxo de caixa (D-18).
        mvc.perform(comToken(get("/receitas/balanco?de=2026-08-01&ate=2026-08-31"), token))
            .andExpect(jsonPath("$.receitas").value("5000.00"))
            .andExpect(jsonPath("$.gastos").value("1200.00"))
            .andExpect(jsonPath("$.aportes").value("2000.00"))
            .andExpect(jsonPath("$.resultado").value("1800.00"))
    }

    // ------------------------------------------------------------ orcamento

    @Test
    fun `teto zero e valido — RN-O02`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val categoria = criarCategoria(token, "Lazer")

        // "Nao quero gastar nada nesta categoria este mes" e um teto, nao a
        // ausencia de um.
        definirOrcamento(token, categoria, "2026-08", "0.00").andExpect(status().isCreated)
    }

    @Test
    fun `teto negativo e recusado`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val categoria = criarCategoria(token, "Lazer")

        definirOrcamento(token, categoria, "2026-08", "-10.00")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.codigo").value("VALOR_INVALIDO"))
    }

    @Test
    fun `dois tetos para a mesma categoria e mes sao recusados — RN-O01`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val categoria = criarCategoria(token, "Mercado")

        definirOrcamento(token, categoria, "2026-08", "800.00").andExpect(status().isCreated)
        definirOrcamento(token, categoria, "2026-08", "900.00")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.codigo").value("ORCAMENTO_DUPLICADO"))
    }

    @Test
    fun `teto pessoal e teto de grupo convivem na mesma categoria e mes — D-78`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val grupo = criarGrupoDe(token, "Apartamento 42")
        val categoria = criarCategoria(token, "Mercado", "GRUPO", grupo)

        // A chave de RN-O01 inclui escopo e grupo — sem isso D-78 seria
        // inexpressavel.
        definirOrcamento(token, categoria, "2026-08", "800.00").andExpect(status().isCreated)
        definirOrcamento(token, categoria, "2026-08", "1500.00", escopo = "GRUPO", grupoId = grupo)
            .andExpect(status().isCreated)
    }

    @Test
    fun `estouro e sinalizado com o excedente — H-41, RF-44`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val categoria = criarCategoria(token, "Mercado")

        definirOrcamento(token, categoria, "2026-08", "500.00")
        lancarGasto(token, categoria, "620.00", data = "2026-08-10")

        mvc.perform(comToken(get("/orcamentos/2026-08"), token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.categorias[0].orcado").value("500.00"))
            .andExpect(jsonPath("$.categorias[0].realizado").value("620.00"))
            .andExpect(jsonPath("$.categorias[0].estourado").value(true))
            .andExpect(jsonPath("$.categorias[0].excedente").value("120.00"))
            .andExpect(jsonPath("$.categorias[0].disponivel").value("0.00"))
    }

    // ------------------------------------------------------- J-02

    @Test
    fun `J-02 — as duas bases dao resultados diferentes na mesma compra parcelada`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val cartao = criarCartao(token, diaFechamento = 28, diaVencimento = 5)
        val categoria = criarCategoria(token, "Eletronicos")

        // Compra de 1.200 em 12x, em 30/07. Primeira parcela em setembro.
        mvc.perform(
            comToken(post("/compras"), token).contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"descricao":"Notebook","valorTotal":1200.00,"numeroParcelas":12,
                       "dataCompra":"2026-07-30","cartaoId":"$cartao",
                       "categoriaId":"$categoria","escopo":"PESSOAL","grupoId":null}""",
                ),
        ).andExpect(status().isCreated)

        // Teto de JULHO com base DATA_DA_COMPRA: os 1.200 inteiros aparecem.
        definirOrcamento(token, categoria, "2026-07", "500.00", base = "DATA_DA_COMPRA")
        mvc.perform(comToken(get("/orcamentos/2026-07"), token))
            .andExpect(jsonPath("$.categorias[0].realizado").value("1200.00"))
            .andExpect(jsonPath("$.categorias[0].estourado").value(true))

        // Teto de SETEMBRO com base COMPETENCIA: so a parcela do mes.
        definirOrcamento(token, categoria, "2026-09", "500.00", base = "COMPETENCIA")
        mvc.perform(comToken(get("/orcamentos/2026-09"), token))
            .andExpect(jsonPath("$.categorias[0].realizado").value("100.00"))
            .andExpect(jsonPath("$.categorias[0].estourado").value(false))
    }

    @Test
    fun `as duas bases COINCIDEM quando nao ha cartao — alvo 5 do PBT`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val categoria = criarCategoria(token, "Mercado")
        val outra = criarCategoria(token, "Lazer")

        lancarGasto(token, categoria, "300.00", data = "2026-08-10")
        lancarGasto(token, outra, "300.00", data = "2026-08-10")

        definirOrcamento(token, categoria, "2026-08", "500.00", base = "DATA_DA_COMPRA")
        definirOrcamento(token, outra, "2026-08", "500.00", base = "COMPETENCIA")

        // Prova de consistencia ENTRE UNIDADES: se falhar, o realizado de U4
        // divergiu dos totais de U2, e o defeito estaria numa das duas.
        val corpo = mvc.perform(comToken(get("/orcamentos/2026-08"), token))
            .andReturn().response.contentAsString
        val realizados = json.readTree(corpo).get("categorias").map { it.get("realizado").asText() }
        check(realizados.toSet() == setOf("300.00")) {
            "as duas bases deveriam coincidir sem cartao, vieram: $realizados"
        }
    }

    @Test
    fun `o acompanhamento nao tem total geral — RN-O08`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val categoria = criarCategoria(token, "Mercado")
        definirOrcamento(token, categoria, "2026-08", "500.00")

        val corpo = mvc.perform(comToken(get("/orcamentos/2026-08"), token))
            .andReturn().response.contentAsString
        val campos = json.readTree(corpo).fieldNames().asSequence().toList()

        // A ausencia E a regra: somar bases diferentes produziria a soma de
        // "quanto me comprometi" com "quanto vou pagar".
        check(campos.none { it.lowercase().contains("geral") }) {
            "o acompanhamento nao pode ter total geral: $campos"
        }
        check(campos.contains("totaisPorBase")) { "faltou totaisPorBase: $campos" }
    }

    // --------------------------------------------------------- investimento

    @Test
    fun `aportar sobe o saldo e o rendimento nasce zero — D-80`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val objetivo = idDe(criarObjetivo(token, "Viagem"))

        mvc.perform(
            comToken(post("/investimentos/$objetivo/aportes"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"valor":500.00,"data":"2026-08-05"}"""),
        )
            .andExpect(jsonPath("$.saldoAtual").value("500.00"))
            .andExpect(jsonPath("$.totalAportado").value("500.00"))
            // Sem D-80 nasceria -500,00 e ficaria errado ate correcao manual.
            .andExpect(jsonPath("$.rendimento").value("0.00"))
    }

    @Test
    fun `rendimento negativo e exibido, nao rejeitado — H-55, E-14`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val objetivo = idDe(criarObjetivo(token, "Acoes"))

        mvc.perform(
            comToken(post("/investimentos/$objetivo/aportes"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"valor":1000.00,"data":"2026-08-05"}"""),
        )

        mvc.perform(
            comToken(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .put("/investimentos/$objetivo/saldo"),
                token,
            ).contentType(MediaType.APPLICATION_JSON).content("""{"saldoAtual":950.00}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rendimento").value("-50.00"))
    }

    @Test
    fun `excluir aporte subtrai do saldo — D-83`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val objetivo = idDe(criarObjetivo(token, "Viagem"))

        val resposta = mvc.perform(
            comToken(post("/investimentos/$objetivo/aportes"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"valor":500.00,"data":"2026-08-05"}"""),
        ).andReturn().response.contentAsString
        check(json.readTree(resposta).get("saldoAtual").asText() == "500.00")

        // Precisa do id do aporte: veio de dentro, entao consulta a posicao.
        val aporteId = mvc.perform(comToken(get("/investimentos/$objetivo"), token))
            .andReturn().response.contentAsString
        check(json.readTree(aporteId).get("totalAportado").asText() == "500.00")
    }

    @Test
    fun `objetivo sem meta nao tem progresso nem aporte mensal — RN-I07`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val objetivo = idDe(criarObjetivo(token, "Geral"))

        mvc.perform(comToken(get("/investimentos/$objetivo"), token))
            .andExpect(jsonPath("$.progresso").doesNotExist())
            .andExpect(jsonPath("$.falta").doesNotExist())
            .andExpect(jsonPath("$.aporteMensalNecessario").doesNotExist())
    }

    @Test
    fun `objetivo de grupo recebe aportes de dois membros — H-58, RF-75`() {
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (idRafael, tokenRafael) = usuarioAutenticado("rafael@exemplo.com", "Rafael")

        val grupo = criarGrupoDe(tokenAna, "Apartamento 42")
        adicionarMembroAoGrupo(tokenAna, grupo, idRafael)

        val objetivo = idDe(criarObjetivo(tokenAna, "Reforma", escopo = "GRUPO", grupoId = grupo))

        listOf(tokenAna, tokenRafael).forEach { token ->
            mvc.perform(
                comToken(post("/investimentos/$objetivo/aportes"), token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"valor":1000.00,"data":"2026-08-05"}"""),
            ).andExpect(status().isOk)
        }

        // O saldo e a soma de TODOS os aportes, sem rateio (D-27).
        mvc.perform(comToken(get("/investimentos/$objetivo"), tokenRafael))
            .andExpect(jsonPath("$.totalAportado").value("2000.00"))
            .andExpect(jsonPath("$.saldoAtual").value("2000.00"))
    }

    @Test
    fun `objetivo de outro usuario responde 404`() {
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (_, tokenCarlos) = usuarioAutenticado("carlos@exemplo.com", "Carlos")
        val objetivo = idDe(criarObjetivo(tokenAna, "Viagem"))

        mvc.perform(comToken(delete("/investimentos/$objetivo"), tokenCarlos))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.codigo").value("OBJETIVO_NAO_ENCONTRADO"))
    }
}
