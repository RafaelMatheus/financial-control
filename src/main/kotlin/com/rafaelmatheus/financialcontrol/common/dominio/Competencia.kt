package com.rafaelmatheus.financialcontrol.common.dominio

import java.time.YearMonth

/**
 * Ano-mes de referencia. Identifica a fatura de cartao e o periodo de orcamento.
 *
 * Sem consumidor em U1 — nasce aqui porque pertence a `common`, e porque fecha-la
 * junto com o gerador de teste agora e mais barato que descobrir a virada de ano
 * errada no meio de U3.
 */
@JvmInline
value class Competencia(val valor: YearMonth) : Comparable<Competencia> {

    val ano: Int get() = valor.year
    val mes: Int get() = valor.monthValue

    fun proxima(): Competencia = Competencia(valor.plusMonths(1))

    fun anterior(): Competencia = Competencia(valor.minusMonths(1))

    fun mais(meses: Long): Competencia = Competencia(valor.plusMonths(meses))

    override fun compareTo(other: Competencia): Int = valor.compareTo(other.valor)

    /** Formato ISO `AAAA-MM`, que e o que a API expoe e o que ordena lexicograficamente. */
    override fun toString(): String = valor.toString()

    companion object {
        fun de(ano: Int, mes: Int): Competencia {
            require(mes in 1..12) { "Mes deve estar entre 1 e 12, recebido: $mes" }
            return Competencia(YearMonth.of(ano, mes))
        }

        fun de(texto: String): Competencia = Competencia(YearMonth.parse(texto))
    }
}
