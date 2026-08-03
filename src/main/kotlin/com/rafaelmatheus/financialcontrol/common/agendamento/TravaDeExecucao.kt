package com.rafaelmatheus.financialcontrol.common.agendamento

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Exclusao mutua entre instancias, por **advisory lock do PostgreSQL** (D-74).
 *
 * ## Por que existe
 *
 * D-71 trouxe o primeiro componente agendado do sistema. Com duas instancias,
 * ambas fechariam as mesmas faturas ao mesmo tempo — e esse seria o **segundo**
 * item da lista do que quebra com escala horizontal, ao lado do
 * `RegistroDeTentativas` de U1.
 *
 * O lock impede que ele entre na lista. E a primeira vez no projeto que essa
 * lista **nao cresce**, e o motivo e simples: resolver no minuto em que o
 * problema e criado custa uma chamada de funcao; resolver no dia em que a
 * segunda instancia sobe custa diagnosticar fatura duplicada em producao.
 *
 * ## Por que o banco, e nao um lock em memoria
 *
 * Lock em memoria e por processo, e o problema e **entre** processos. O banco ja
 * e o ponto de serializacao de todo o resto do sistema — usa-lo aqui nao
 * acrescenta dependencia nenhuma.
 *
 * ## O que acontece se o lock falhar
 *
 * Nada grave, e isso e por design. O fechamento e **idempotente**: a fatura ja
 * fechada tem `dataFechamento`, e nao e fechada de novo. O lock evita trabalho
 * duplicado e corrida; a idempotencia evita **dano**. As duas juntas fazem o
 * pior caso ser trabalho desperdicado, nunca conta a pagar em dobro.
 */
@Component
class TravaDeExecucao(private val jdbc: JdbcTemplate) {

    /**
     * Executa [acao] se conseguir a trava; devolve `false` sem executar se outra
     * instancia ja estiver dentro.
     *
     * `pg_try_advisory_lock` nao bloqueia esperando — devolve na hora. E o que
     * queremos: se outra instancia esta fechando as faturas, esta nao tem o que
     * fazer, e ficar esperando so ocuparia uma conexao.
     *
     * A trava e de **sessao**, entao ela cai junto com a conexao se o processo
     * morrer no meio — nao ha estado orfao a limpar.
     */
    fun comTrava(chave: Long, acao: () -> Unit): Boolean {
        val obteve = jdbc.queryForObject("select pg_try_advisory_lock(?)", Boolean::class.java, chave)
            ?: false
        if (!obteve) return false

        return try {
            acao()
            true
        } finally {
            // finally, e nao ao fim do try: uma excecao dentro da acao nao pode
            // deixar a trava presa ate a conexao ser devolvida ao pool.
            jdbc.queryForObject("select pg_advisory_unlock(?)", Boolean::class.java, chave)
        }
    }

    companion object {
        /** Chave do fechamento de faturas. Arbitraria, mas fixa e documentada. */
        const val FECHAMENTO_DE_FATURAS = 20_260_803L
    }
}
