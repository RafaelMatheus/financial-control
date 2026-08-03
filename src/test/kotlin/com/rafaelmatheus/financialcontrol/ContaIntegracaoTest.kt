package com.rafaelmatheus.financialcontrol

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** H-42 a H-51 — RN-A01 a RN-A09 e RN-R01 a RN-R06. */
class ContaIntegracaoTest : SuporteDeIntegracao() {

    private fun criarConta(
        token: String,
        categoria: String,
        descricao: String,
        valor: String,
        vencimento: String,
        tipo: String = "BOLETO",
    ) = mvc.perform(
        comToken(post("/contas"), token).contentType(MediaType.APPLICATION_JSON)
            .content(
                """{"descricao":"$descricao","valor":$valor,"dataVencimento":"$vencimento",
                   "tipo":"$tipo","categoriaId":"$categoria","escopo":"PESSOAL","grupoId":null}""",
            ),
    )

    @Test
    fun `a visao de vencimentos reune os quatro tipos, ordenada — H-43`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val categoria = criarCategoria(token, "Casa")

        criarConta(token, categoria, "Internet", "120.00", "2026-08-20", "FATURA_SERVICO")
        criarConta(token, categoria, "Pix do aluguel", "1500.00", "2026-08-05", "PIX")
        criarConta(token, categoria, "Boleto do condominio", "450.00", "2026-08-10", "BOLETO")

        mvc.perform(comToken(get("/contas/vencimentos?de=2026-08-01&ate=2026-08-31"), token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.itens.length()").value(3))
            // Ordenada por vencimento.
            .andExpect(jsonPath("$.itens[0].descricao").value("Pix do aluguel"))
            .andExpect(jsonPath("$.itens[2].descricao").value("Internet"))
            .andExpect(jsonPath("$.total").value("2070.00"))
    }

    @Test
    fun `marcar paga registra a data — H-44`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val categoria = criarCategoria(token, "Casa")
        val conta = idDe(criarConta(token, categoria, "Boleto", "100.00", "2026-08-10"))

        mvc.perform(
            comToken(post("/contas/$conta/pagamento"), token).contentType(MediaType.APPLICATION_JSON)
                .content("""{"dataPagamento":"2026-08-09","valorAjustado":null}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PAGA"))
            .andExpect(jsonPath("$.dataPagamento").value("2026-08-09"))

        // RF-94: desmarcar volta para EM_ABERTO e limpa a data.
        mvc.perform(comToken(delete("/contas/$conta/pagamento"), token))
            .andExpect(jsonPath("$.status").value("EM_ABERTO"))
            .andExpect(jsonPath("$.dataPagamento").doesNotExist())
    }

    @Test
    fun `ocorrencia recorrente aparece na consulta sem existir como linha — D-72`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val categoria = criarCategoria(token, "Casa")

        mvc.perform(
            comToken(post("/recorrentes"), token).contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"descricao":"Aluguel","valorBase":1500.00,"diaVencimento":5,
                       "tipo":"BOLETO","categoriaId":"$categoria","escopo":"PESSOAL",
                       "grupoId":null,"inicioEm":"2026-01"}""",
                ),
        ).andExpect(status().isCreated)

        // H-47: ao consultar agosto, a ocorrencia esta la — projetada, sem linha.
        mvc.perform(comToken(get("/contas/vencimentos?de=2026-08-01&ate=2026-08-31"), token))
            .andExpect(jsonPath("$.itens.length()").value(1))
            .andExpect(jsonPath("$.itens[0].descricao").value("Aluguel"))
            .andExpect(jsonPath("$.itens[0].dataVencimento").value("2026-08-05"))
            // O id nulo denuncia a projecao a quem precisar agir sobre ela.
            .andExpect(jsonPath("$.itens[0].id").doesNotExist())
            .andExpect(jsonPath("$.itens[0].competencia").value("2026-08"))
    }

    @Test
    fun `ajustar o valor da ocorrencia nao muda o valor base — H-48, RN-R03`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val categoria = criarCategoria(token, "Casa")

        val recorrente = idDe(
            mvc.perform(
                comToken(post("/recorrentes"), token).contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"descricao":"Energia","valorBase":180.00,"diaVencimento":15,
                           "tipo":"FATURA_SERVICO","categoriaId":"$categoria","escopo":"PESSOAL",
                           "grupoId":null,"inicioEm":"2026-01"}""",
                    ),
            ),
        )

        // Materializa agosto e paga com o valor real da conta.
        val ocorrencia = idDe(
            mvc.perform(comToken(post("/contas/recorrentes/$recorrente/ocorrencias/2026-08"), token))
                .andExpect(status().isOk),
        )

        mvc.perform(
            comToken(post("/contas/$ocorrencia/pagamento"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"dataPagamento":"2026-08-14","valorAjustado":213.40}"""),
        )
            .andExpect(jsonPath("$.valor").value("213.40"))

        // O valor BASE permanece — e setembro continua projetando 180,00.
        mvc.perform(comToken(get("/recorrentes"), token))
            .andExpect(jsonPath("$[0].valorBase").value("180.00"))

        mvc.perform(comToken(get("/contas/vencimentos?de=2026-09-01&ate=2026-09-30"), token))
            .andExpect(jsonPath("$.itens[0].valor").value("180.00"))
    }

    @Test
    fun `materializar duas vezes e idempotente`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val categoria = criarCategoria(token, "Casa")
        val recorrente = idDe(
            mvc.perform(
                comToken(post("/recorrentes"), token).contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"descricao":"Aluguel","valorBase":1500.00,"diaVencimento":5,
                           "tipo":"BOLETO","categoriaId":"$categoria","escopo":"PESSOAL",
                           "grupoId":null,"inicioEm":"2026-01"}""",
                    ),
            ),
        )

        val primeira = idDe(
            mvc.perform(comToken(post("/contas/recorrentes/$recorrente/ocorrencias/2026-08"), token)),
        )
        val segunda = idDe(
            mvc.perform(comToken(post("/contas/recorrentes/$recorrente/ocorrencias/2026-08"), token)),
        )
        check(primeira == segunda) { "materializar duas vezes deveria devolver a mesma linha" }

        // E a consulta continua mostrando UMA ocorrencia, nao duas.
        mvc.perform(comToken(get("/contas/vencimentos?de=2026-08-01&ate=2026-08-31"), token))
            .andExpect(jsonPath("$.itens.length()").value(1))
    }

    @Test
    fun `encerrar a recorrente para a geracao e preserva o historico — H-51`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val categoria = criarCategoria(token, "Casa")
        val recorrente = idDe(
            mvc.perform(
                comToken(post("/recorrentes"), token).contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"descricao":"Academia","valorBase":99.00,"diaVencimento":10,
                           "tipo":"BOLETO","categoriaId":"$categoria","escopo":"PESSOAL",
                           "grupoId":null,"inicioEm":"2026-01"}""",
                    ),
            ),
        )

        mvc.perform(comToken(post("/contas/recorrentes/$recorrente/ocorrencias/2026-08"), token))

        mvc.perform(comToken(delete("/recorrentes/$recorrente?aPartirDe=2026-08"), token))
            .andExpect(jsonPath("$.ativa").value(false))

        // Agosto permanece — a ocorrencia ja materializada nao e apagada.
        mvc.perform(comToken(get("/contas/vencimentos?de=2026-08-01&ate=2026-08-31"), token))
            .andExpect(jsonPath("$.itens.length()").value(1))

        // Setembro nao e mais gerado.
        mvc.perform(comToken(get("/contas/vencimentos?de=2026-09-01&ate=2026-09-30"), token))
            .andExpect(jsonPath("$.itens.length()").value(0))
    }

    @Test
    fun `contas vencidas sao as com vencimento passado e ainda em aberto — H-50`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val categoria = criarCategoria(token, "Casa")

        criarConta(token, categoria, "Atrasada", "80.00", "2020-01-10")
        val paga = idDe(criarConta(token, categoria, "Antiga paga", "50.00", "2020-01-05"))
        mvc.perform(
            comToken(post("/contas/$paga/pagamento"), token).contentType(MediaType.APPLICATION_JSON)
                .content("""{"dataPagamento":"2020-01-05","valorAjustado":null}"""),
        )

        mvc.perform(comToken(get("/contas/vencidas"), token))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].descricao").value("Atrasada"))
    }

    @Test
    fun `conta de outro usuario responde 404`() {
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (_, tokenCarlos) = usuarioAutenticado("carlos@exemplo.com", "Carlos")
        val categoria = criarCategoria(tokenAna, "Casa")
        val conta = idDe(criarConta(tokenAna, categoria, "Boleto", "100.00", "2026-08-10"))

        mvc.perform(comToken(delete("/contas/$conta"), tokenCarlos))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.codigo").value("CONTA_NAO_ENCONTRADA"))
    }
}
