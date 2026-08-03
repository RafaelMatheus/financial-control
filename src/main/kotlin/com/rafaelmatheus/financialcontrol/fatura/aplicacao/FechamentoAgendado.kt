package com.rafaelmatheus.financialcontrol.fatura.aplicacao

import com.rafaelmatheus.financialcontrol.cartao.dominio.Cartao
import com.rafaelmatheus.financialcontrol.cartao.dominio.CartoesParaFechamento
import com.rafaelmatheus.financialcontrol.common.agendamento.TravaDeExecucao
import com.rafaelmatheus.financialcontrol.common.dominio.CalculadoraDeCompetencia
import com.rafaelmatheus.financialcontrol.common.web.CHAVE_CORRELACAO
import com.rafaelmatheus.financialcontrol.conta.dominio.ContaAPagar
import com.rafaelmatheus.financialcontrol.conta.dominio.ContaRepositorio
import com.rafaelmatheus.financialcontrol.conta.dominio.TipoConta
import com.rafaelmatheus.financialcontrol.fatura.dominio.Fatura
import com.rafaelmatheus.financialcontrol.fatura.dominio.FaturaRepositorio
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * O fechamento diario de faturas (D-71, RN-F05, RN-A05).
 *
 * **O primeiro componente agendado do sistema.** A NFR Design de U1 listava
 * "fila / job / agendador" na tabela do que deliberadamente nao existe; D-71 o
 * trouxe, e o inventario do que se perde esta em `logical-components.md` de U3.
 *
 * ## O modo de falha que ele introduz, e como e tratado
 *
 * Se o job nao rodar — instancia parada, excecao, deploy no horario —, a fatura
 * nao fecha e a conta a pagar nao nasce. **Ninguem recebe erro**: o vencimento
 * simplesmente nao aparece na lista. E uma falha silenciosa, e nenhuma unidade
 * anterior tinha uma.
 *
 * O tratamento e o que torna a decisao viavel: **ele nao fecha "as faturas de
 * hoje"**. Fecha todas as faturas cuja janela ja terminou e que ainda estao
 * abertas. Rodando depois de tres dias parado, recupera os tres.
 *
 * | Propriedade | Consequencia |
 * |---|---|
 * | Idempotente | Rodar duas vezes nao gera duas contas: a fatura fechada ja tem `dataFechamento` |
 * | Recuperavel | Nao depende de ter rodado ontem |
 * | Sem estado proprio | Nada a persistir sobre "ultima execucao" — e o que permite ser recuperavel |
 *
 * A alternativa recusada — fechar sob demanda, na consulta — **nao tinha este
 * modo de falha**, porque o fechamento acontecia no caminho de quem consulta.
 * O custo de D-71 e este, e ele esta pago pela idempotencia.
 */
@Component
class FechamentoAgendado(
    private val cartoes: CartoesParaFechamento,
    private val faturas: FaturaRepositorio,
    private val contas: ContaRepositorio,
    private val trava: TravaDeExecucao,
    private val relogio: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Uma vez por dia, de madrugada. O horario exato importa pouco: como o job e
     * recuperavel, atrasar nao perde nada.
     */
    @Scheduled(cron = "\${app.fechamento.cron:0 15 3 * * *}")
    fun executar() {
        // O job nao nasce de uma requisicao, entao nao herda id de correlacao
        // (D-53). Gera o proprio, para que as linhas de log de uma execucao
        // possam ser lidas juntas.
        MDC.put(CHAVE_CORRELACAO, "fechamento-${UUID.randomUUID()}")
        try {
            val executou = trava.comTrava(TravaDeExecucao.FECHAMENTO_DE_FATURAS) {
                fecharTudoQueJaVenceu()
            }
            if (!executou) {
                log.info("Fechamento ja em execucao em outra instancia; nada a fazer")
            }
        } finally {
            MDC.remove(CHAVE_CORRELACAO)
        }
    }

    /** Visivel para teste: avanca o relogio em vez de esperar o cron. */
    @Transactional
    fun fecharTudoQueJaVenceu() {
        val hoje = LocalDate.now(relogio)
        var fechadas = 0

        cartoes.listarAtivos().forEach { cartao ->
            faturas.listarAbertasParaFechamento(cartao.id)
                .filter { janelaTerminou(it, cartao, hoje) }
                .forEach { fatura ->
                    fechar(fatura, cartao)
                    fechadas++
                }
        }
        if (fechadas > 0) log.info("Faturas fechadas nesta execucao: {}", fechadas)
    }

    /**
     * A janela da competencia C, num cartao que fecha no dia F, e
     * `[F do mes C-2, F do mes C-1)`. Ela terminou quando **hoje ja alcancou** o
     * fechamento de C.
     */
    private fun janelaTerminou(fatura: Fatura, cartao: Cartao, hoje: LocalDate): Boolean {
        val fechamento = CalculadoraDeCompetencia
            .dataDeFechamento(fatura.competencia, cartao.diaFechamento)
        return !hoje.isBefore(fechamento)
    }

    /**
     * RN-A05, RF-59, H-45. O valor da conta e o **do momento do fechamento**.
     *
     * A partir daqui os dois numeros podem legitimamente divergir: a fatura
     * reflete a soma atual (D-75), a conta reflete o que foi cobrado. Se um
     * lancamento antigo for corrigido, a fatura muda e a conta **nao** — e e
     * assim que deve ser, senao corrigir um gasto de marco mudaria o valor de
     * uma conta paga em abril.
     */
    private fun fechar(fatura: Fatura, cartao: Cartao) {
        val total = faturas.somarLancamentos(cartao.id, fatura.competencia)
        if (total.ehZero()) {
            // Fatura sem lancamento nao vira conta a pagar: cobrar zero nao e
            // vencimento, e poluiria a visao consolidada todo mes.
            faturas.salvar(fatura.copy(dataFechamento = fatura.dataVencimento))
            return
        }

        val conta = contas.salvarSemContexto(
            ContaAPagar.nova(
                descricao = "Fatura ${cartao.apelido} ${fatura.competencia}",
                valor = total,
                dataVencimento = fatura.dataVencimento,
                tipo = TipoConta.FATURA_CARTAO,
                // SEM categoria, e de proposito. Uma fatura MISTURA categorias:
                // escolher uma delas seria inventar dado, e criar uma categoria
                // de sistema chamada "Fatura" seria criar registro que o usuario
                // nao pediu. O modelo aceita categoria nula exatamente aqui, e o
                // CHECK do banco garante que so aqui.
                categoria = null,
                dono = cartao.dono,
                escopo = cartao.escopo,
                grupo = cartao.grupo,
                criadoEm = relogio.instant(),
                origemFatura = fatura.id,
            ),
        )

        val fechamento = CalculadoraDeCompetencia
            .dataDeFechamento(fatura.competencia, cartao.diaFechamento)
        faturas.salvar(fatura.fechada(fechamento, conta.id))
    }

}
