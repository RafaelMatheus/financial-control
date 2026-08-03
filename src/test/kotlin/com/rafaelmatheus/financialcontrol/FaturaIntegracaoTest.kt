package com.rafaelmatheus.financialcontrol

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * H-20 a H-26, H-27 a H-32 — o ciclo da fatura e o parcelamento, ponta a ponta.
 *
 * Cartao padrao do arquivo: fecha dia 28, vence dia 5. A janela da competencia
 * de agosto e `[28/06, 28/07)`; ela fecha em 28/07 e vence em 05/08.
 */
class FaturaIntegracaoTest : SuporteDeIntegracao() {

    @Test
    fun `gasto no cartao cai na fatura da competencia certa — H-20`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val cartao = criarCartao(token)
        val categoria = criarCategoria(token, "Mercado")

        // 27/07 < 28 -> agosto.
        lancarGasto(token, categoria, "100.00", data = "2026-07-27", cartaoId = cartao)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.competencia").value("2026-08"))

        // 28/07 >= 28 -> setembro. O corte e EXCLUSIVO (E-03).
        lancarGasto(token, categoria, "50.00", data = "2026-07-28", cartaoId = cartao)
            .andExpect(jsonPath("$.competencia").value("2026-09"))
    }

    @Test
    fun `fatura aberta acumula, e a fechada nao — H-22`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val cartao = criarCartao(token)
        val categoria = criarCategoria(token, "Mercado")

        lancarGasto(token, categoria, "389.90", data = "2026-07-20", cartaoId = cartao)
        lancarGasto(token, categoria, "150.00", data = "2026-07-21", cartaoId = cartao)

        mvc.perform(comToken(get("/cartoes/$cartao/faturas/2026-08"), token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.valorTotal").value("539.90"))
            .andExpect(jsonPath("$.status").value("ABERTA"))
            .andExpect(jsonPath("$.dataVencimento").value("2026-08-05"))
            .andExpect(jsonPath("$.lancamentos.length()").value(2))

        // Depois do fechamento, o valor vai para setembro e agosto nao muda.
        lancarGasto(token, categoria, "70.00", data = "2026-07-30", cartaoId = cartao)

        mvc.perform(comToken(get("/cartoes/$cartao/faturas/2026-08"), token))
            .andExpect(jsonPath("$.valorTotal").value("539.90"))
        mvc.perform(comToken(get("/cartoes/$cartao/faturas/2026-09"), token))
            .andExpect(jsonPath("$.valorTotal").value("70.00"))
    }

    @Test
    fun `o valor total nao e guardado — excluir um gasto recalcula sozinho, D-75`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val cartao = criarCartao(token)
        val categoria = criarCategoria(token, "Mercado")

        val gasto = idDe(
            lancarGasto(token, categoria, "100.00", data = "2026-07-20", cartaoId = cartao),
        )
        lancarGasto(token, categoria, "50.00", data = "2026-07-21", cartaoId = cartao)

        mvc.perform(comToken(get("/cartoes/$cartao/faturas/2026-08"), token))
            .andExpect(jsonPath("$.valorTotal").value("150.00"))

        mvc.perform(comToken(delete("/gastos/$gasto"), token)).andExpect(status().isNoContent)

        // Nenhum caminho de escrita precisou lembrar de recalcular: o total e
        // SUM na leitura. E o ponto de D-75.
        mvc.perform(comToken(get("/cartoes/$cartao/faturas/2026-08"), token))
            .andExpect(jsonPath("$.valorTotal").value("50.00"))
    }

    @Test
    fun `compra parcelada gera N parcelas com competencias sucessivas — H-27`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val cartao = criarCartao(token)
        val categoria = criarCategoria(token, "Eletronicos")

        // D-67: a entrada e o VALOR TOTAL. 12x de 100,00 = 1.200,00.
        mvc.perform(
            comToken(post("/compras"), token).contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"descricao":"Notebook","valorTotal":1200.00,"numeroParcelas":12,
                       "dataCompra":"2026-07-30","cartaoId":"$cartao",
                       "categoriaId":"$categoria","escopo":"PESSOAL","grupoId":null}""",
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.parcelas.length()").value(12))
            // 30/07 >= 28 -> a primeira cai em setembro.
            .andExpect(jsonPath("$.parcelas[0].competencia").value("2026-09"))
            .andExpect(jsonPath("$.parcelas[0].posicao").value("1/12"))
            .andExpect(jsonPath("$.parcelas[11].competencia").value("2027-08"))
            .andExpect(jsonPath("$.parcelas[11].valor").value("100.00"))
    }

    @Test
    fun `residuo vai todo para a ultima parcela — H-28, D-68`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val cartao = criarCartao(token)
        val categoria = criarCategoria(token, "Mercado")

        val resposta = mvc.perform(
            comToken(post("/compras"), token).contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"descricao":"Compra","valorTotal":100.00,"numeroParcelas":7,
                       "dataCompra":"2026-07-01","cartaoId":"$cartao",
                       "categoriaId":"$categoria","escopo":"PESSOAL","grupoId":null}""",
                ),
        ).andExpect(status().isCreated).andReturn().response.contentAsString

        val valores = json.readTree(resposta).get("parcelas").map { it.get("valor").asText() }
        check(valores.dropLast(1).all { it == "14.28" }) { "primeiras seis deveriam ser 14,28: $valores" }
        check(valores.last() == "14.32") { "a ultima deveria absorver o residuo: $valores" }
    }

    @Test
    fun `a parcela entra na fatura da sua competencia`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val cartao = criarCartao(token)
        val categoria = criarCategoria(token, "Eletronicos")

        mvc.perform(
            comToken(post("/compras"), token).contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"descricao":"Notebook","valorTotal":1200.00,"numeroParcelas":12,
                       "dataCompra":"2026-07-30","cartaoId":"$cartao",
                       "categoriaId":"$categoria","escopo":"PESSOAL","grupoId":null}""",
                ),
        ).andExpect(status().isCreated)

        mvc.perform(comToken(get("/cartoes/$cartao/faturas/2026-09"), token))
            .andExpect(jsonPath("$.valorTotal").value("100.00"))
            .andExpect(jsonPath("$.lancamentos[0].posicao").value("1/12"))
    }

    @Test
    fun `editar a compra descarta e regenera todas as parcelas — H-30`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val cartao = criarCartao(token)
        val categoria = criarCategoria(token, "Eletronicos")

        val compra = idDe(
            mvc.perform(
                comToken(post("/compras"), token).contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"descricao":"Notebook","valorTotal":1200.00,"numeroParcelas":12,
                           "dataCompra":"2026-07-30","cartaoId":"$cartao",
                           "categoriaId":"$categoria","escopo":"PESSOAL","grupoId":null}""",
                    ),
            ),
        )

        mvc.perform(
            comToken(put("/compras/$compra"), token).contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"descricao":"Notebook","valorTotal":1200.00,"numeroParcelas":10,
                       "dataCompra":"2026-07-30","cartaoId":"$cartao",
                       "categoriaId":"$categoria","escopo":"PESSOAL","grupoId":null}""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.parcelas.length()").value(10))
            .andExpect(jsonPath("$.parcelas[0].valor").value("120.00"))

        // As 12 anteriores sumiram: a fatura de 2027-08, que tinha a 12a, esvaziou.
        mvc.perform(comToken(get("/cartoes/$cartao/faturas/2027-08"), token))
            .andExpect(jsonPath("$.valorTotal").value("0.00"))
    }

    @Test
    fun `excluir a compra leva as parcelas junto — H-31, RF-34`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val cartao = criarCartao(token)
        val categoria = criarCategoria(token, "Eletronicos")

        val compra = idDe(
            mvc.perform(
                comToken(post("/compras"), token).contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"descricao":"Notebook","valorTotal":600.00,"numeroParcelas":6,
                           "dataCompra":"2026-07-01","cartaoId":"$cartao",
                           "categoriaId":"$categoria","escopo":"PESSOAL","grupoId":null}""",
                    ),
            ),
        )

        mvc.perform(comToken(delete("/compras/$compra"), token)).andExpect(status().isNoContent)

        mvc.perform(comToken(get("/cartoes/$cartao/faturas/2026-08"), token))
            .andExpect(jsonPath("$.valorTotal").value("0.00"))
    }

    @Test
    fun `numero de parcelas menor que 1 e recusado`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val cartao = criarCartao(token)
        val categoria = criarCategoria(token, "Mercado")

        mvc.perform(
            comToken(post("/compras"), token).contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"descricao":"X","valorTotal":100.00,"numeroParcelas":0,
                       "dataCompra":"2026-07-01","cartaoId":"$cartao",
                       "categoriaId":"$categoria","escopo":"PESSOAL","grupoId":null}""",
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.codigo").value("NUMERO_PARCELAS_INVALIDO"))
    }

    @Test
    fun `cartao encerrado nao recebe lancamento — RN-K04`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val cartao = criarCartao(token)
        val categoria = criarCategoria(token, "Mercado")

        mvc.perform(comToken(delete("/cartoes/$cartao"), token)).andExpect(status().isNoContent)

        lancarGasto(token, categoria, "10.00", data = "2026-07-20", cartaoId = cartao)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.codigo").value("CARTAO_ENCERRADO"))
    }

    @Test
    fun `fatura de cartao de outro usuario responde 404`() {
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (_, tokenCarlos) = usuarioAutenticado("carlos@exemplo.com", "Carlos")
        val cartao = criarCartao(tokenAna)

        // E-08: a visibilidade da fatura E a do cartao.
        mvc.perform(comToken(get("/cartoes/$cartao/faturas/2026-08"), tokenCarlos))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `cartao que fecha dia 31 em fevereiro — E-04, D-69`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val cartao = criarCartao(token, "Inter", diaFechamento = 31, diaVencimento = 10)
        val categoria = criarCategoria(token, "Mercado")

        // Em fevereiro de 2026 o dia efetivo e 28. 27/02 < 28 -> marco.
        lancarGasto(token, categoria, "10.00", data = "2026-02-27", cartaoId = cartao)
            .andExpect(jsonPath("$.competencia").value("2026-03"))
        // 28/02 >= 28 -> abril.
        lancarGasto(token, categoria, "20.00", data = "2026-02-28", cartaoId = cartao)
            .andExpect(jsonPath("$.competencia").value("2026-04"))
    }
}
