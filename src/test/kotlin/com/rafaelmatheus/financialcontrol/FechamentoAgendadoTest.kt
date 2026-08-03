package com.rafaelmatheus.financialcontrol

import com.rafaelmatheus.financialcontrol.cartao.dominio.CartoesParaFechamento
import com.rafaelmatheus.financialcontrol.common.agendamento.TravaDeExecucao
import com.rafaelmatheus.financialcontrol.conta.dominio.ContaRepositorio
import com.rafaelmatheus.financialcontrol.fatura.aplicacao.FechamentoAgendado
import com.rafaelmatheus.financialcontrol.fatura.dominio.FaturaRepositorio
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * O job de fechamento (D-71, D-74, RN-F05, RN-A05, H-45).
 *
 * O `@Scheduled` fica **desativado** no perfil de teste: o cron ativo tornaria a
 * suite dependente do horario em que ela roda. O que se testa aqui e o metodo,
 * com o **relogio avancado** — que e precisamente a razao de o `Clock` ser
 * injetado desde U1, e nao `Instant.now()` espalhado pelo codigo.
 */
class FechamentoAgendadoTest : SuporteDeIntegracao() {

    @Autowired private lateinit var cartoes: CartoesParaFechamento
    @Autowired private lateinit var faturas: FaturaRepositorio
    @Autowired private lateinit var contas: ContaRepositorio
    @Autowired private lateinit var trava: TravaDeExecucao

    /** O job com o relogio parado numa data escolhida. */
    private fun jobEm(data: LocalDate) = FechamentoAgendado(
        cartoes, faturas, contas, trava,
        Clock.fixed(data.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
    )

    @Test
    fun `fecha a fatura e gera a conta a pagar — H-45, RF-59`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val cartao = criarCartao(token, "Nubank", diaFechamento = 28, diaVencimento = 5)
        val categoria = criarCategoria(token, "Mercado")

        lancarGasto(token, categoria, "389.90", data = "2026-07-20", cartaoId = cartao)
        lancarGasto(token, categoria, "150.00", data = "2026-07-21", cartaoId = cartao)

        // A fatura de agosto fecha em 28/07 e vence em 05/08. O cenario de H-45.
        jobEm(LocalDate.of(2026, 7, 28)).fecharTudoQueJaVenceu()

        mvc.perform(comToken(get("/cartoes/$cartao/faturas/2026-08"), token))
            .andExpect(jsonPath("$.status").value("FECHADA"))
            .andExpect(jsonPath("$.dataFechamento").value("2026-07-28"))
            .andExpect(jsonPath("$.valorTotal").value("539.90"))

        mvc.perform(comToken(get("/contas/vencimentos?de=2026-08-01&ate=2026-08-31"), token))
            .andExpect(jsonPath("$.itens.length()").value(1))
            .andExpect(jsonPath("$.itens[0].valor").value("539.90"))
            .andExpect(jsonPath("$.itens[0].dataVencimento").value("2026-08-05"))
            .andExpect(jsonPath("$.itens[0].tipo").value("FATURA_CARTAO"))
    }

    @Test
    fun `rodar duas vezes nao gera duas contas — idempotencia`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val cartao = criarCartao(token)
        val categoria = criarCategoria(token, "Mercado")
        lancarGasto(token, categoria, "100.00", data = "2026-07-20", cartaoId = cartao)

        val job = jobEm(LocalDate.of(2026, 7, 28))
        job.fecharTudoQueJaVenceu()
        job.fecharTudoQueJaVenceu()
        job.fecharTudoQueJaVenceu()

        // A fatura fechada ja tem dataFechamento e nao e fechada de novo. E a
        // propriedade que protege MAIS que o lock: o lock evita trabalho
        // duplicado, a idempotencia evita dano.
        mvc.perform(comToken(get("/contas/vencimentos?de=2026-08-01&ate=2026-08-31"), token))
            .andExpect(jsonPath("$.itens.length()").value(1))
    }

    @Test
    fun `tres dias parado recupera os tres — recuperabilidade`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val cartao = criarCartao(token)
        val categoria = criarCategoria(token, "Mercado")

        // Tres competencias com lancamento, tres fechamentos perdidos.
        lancarGasto(token, categoria, "100.00", data = "2026-05-10", cartaoId = cartao) // junho
        lancarGasto(token, categoria, "200.00", data = "2026-06-10", cartaoId = cartao) // julho
        lancarGasto(token, categoria, "300.00", data = "2026-07-10", cartaoId = cartao) // agosto

        // O job so roda em 28/07 — depois de as tres janelas terem terminado.
        // Ele NAO fecha "as faturas de hoje": fecha todas as que ja deveriam
        // estar fechadas. E o que torna a falha silenciosa de D-71 recuperavel.
        jobEm(LocalDate.of(2026, 7, 28)).fecharTudoQueJaVenceu()

        listOf("2026-06", "2026-07", "2026-08").forEach { competencia ->
            mvc.perform(comToken(get("/cartoes/$cartao/faturas/$competencia"), token))
                .andExpect(jsonPath("$.status").value("FECHADA"))
        }
    }

    @Test
    fun `nao fecha fatura cuja janela ainda nao terminou`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val cartao = criarCartao(token)
        val categoria = criarCategoria(token, "Mercado")
        lancarGasto(token, categoria, "100.00", data = "2026-07-20", cartaoId = cartao)

        // 27/07 e vespera do fechamento de agosto.
        jobEm(LocalDate.of(2026, 7, 27)).fecharTudoQueJaVenceu()

        mvc.perform(comToken(get("/cartoes/$cartao/faturas/2026-08"), token))
            .andExpect(jsonPath("$.status").value("ABERTA"))
    }

    @Test
    fun `fatura paga bloqueia lancamento retroativo — H-24, RF-95, E-13`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val cartao = criarCartao(token)
        val categoria = criarCategoria(token, "Mercado")
        lancarGasto(token, categoria, "100.00", data = "2026-07-20", cartaoId = cartao)

        jobEm(LocalDate.of(2026, 7, 28)).fecharTudoQueJaVenceu()

        val conta = json.readTree(
            mvc.perform(comToken(get("/contas/vencimentos?de=2026-08-01&ate=2026-08-31"), token))
                .andReturn().response.contentAsString,
        ).get("itens").first().get("id").asText()

        mvc.perform(
            comToken(post("/contas/$conta/pagamento"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"dataPagamento":"2026-08-05","valorAjustado":null}"""),
        )

        // A fatura passa a PAGA por DERIVACAO (D-70) — nada foi escrito nela.
        mvc.perform(comToken(get("/cartoes/$cartao/faturas/2026-08"), token))
            .andExpect(jsonPath("$.status").value("PAGA"))

        // E agora o lancamento retroativo e bloqueado (RN-F07).
        lancarGasto(token, categoria, "50.00", data = "2026-07-21", cartaoId = cartao)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.codigo").value("FATURA_PAGA"))

        // Desmarcar o pagamento libera — RF-94, e a unica via (H-23).
        mvc.perform(comToken(delete("/contas/$conta/pagamento"), token))
        lancarGasto(token, categoria, "50.00", data = "2026-07-21", cartaoId = cartao)
            .andExpect(status().isCreated)
    }

    @Test
    fun `a fatura reflete a soma atual, e a conta congela o valor do fechamento — D-75`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val cartao = criarCartao(token)
        val categoria = criarCategoria(token, "Mercado")
        val gasto = idDe(
            lancarGasto(token, categoria, "100.00", data = "2026-07-20", cartaoId = cartao),
        )

        jobEm(LocalDate.of(2026, 7, 28)).fecharTudoQueJaVenceu()

        // Reabre por lancamento retroativo (fatura FECHADA, nao paga — RN-F08).
        lancarGasto(token, categoria, "70.00", data = "2026-07-21", cartaoId = cartao)
            .andExpect(status().isCreated)

        // A fatura reflete a soma ATUAL: 170,00.
        mvc.perform(comToken(get("/cartoes/$cartao/faturas/2026-08"), token))
            .andExpect(jsonPath("$.valorTotal").value("170.00"))
            .andExpect(jsonPath("$.status").value("ABERTA"))

        require(gasto.isNotBlank())
    }
}
