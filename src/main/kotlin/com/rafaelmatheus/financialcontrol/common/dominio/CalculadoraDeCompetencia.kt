package com.rafaelmatheus.financialcontrol.common.dominio

import java.time.LocalDate
import java.time.YearMonth

/**
 * O nucleo aritmetico de U3, isolado como **funcao pura** (RN-F01, RN-K03).
 *
 * Nao conhece banco, nem Spring, nem entidade. Recebe data e dias do ciclo,
 * devolve competencia. E o que permite testar as sete propriedades de
 * `business-rules.md` sem subir nada.
 */
object CalculadoraDeCompetencia {

    /**
     * Dia que **de fato existe** naquele mes (RN-K03, D-69).
     *
     * Cartao que fecha dia 31 fecha dia 28 em fevereiro comum, 29 em bissexto,
     * 30 em abril. A alternativa era recusar dias acima de 28 no cadastro, o que
     * eliminaria o caso de borda ao custo de recusar cartoes que existem.
     *
     * A mesma funcao serve a tres lugares: fechamento de cartao, vencimento de
     * cartao e vencimento de conta recorrente (E-04 e E-11). Escreve-la tres
     * vezes seria criar tres oportunidades de divergirem.
     */
    fun diaEfetivo(dia: Int, anoMes: YearMonth): Int {
        require(dia in 1..31) { "Dia precisa estar entre 1 e 31, recebido: $dia" }
        return minOf(dia, anoMes.lengthOfMonth())
    }

    fun dataEfetiva(dia: Int, anoMes: YearMonth): LocalDate =
        anoMes.atDay(diaEfetivo(dia, anoMes))

    /**
     * Em qual fatura uma compra cai (RF-25, RF-61, E-03).
     *
     * ```
     * fechamento = diaEfetivo(cartao.diaFechamento, mes da compra)
     * dia < fechamento  ->  competencia = mes + 1
     * dia >= fechamento ->  competencia = mes + 2
     * ```
     *
     * **O corte e exclusivo**: uma compra no exato dia do fechamento pertence a
     * fatura seguinte, porque o fechamento ocorre no inicio do dia (E-03).
     *
     * Conferido contra os tres cenarios de H-20, cartao que fecha dia 28:
     * 27/07 -> agosto; 28/07 -> setembro; 30/07 -> setembro.
     *
     * ## O erro que esta funcao existe para prevenir
     *
     * Usar `diaVencimento` no lugar de `diaFechamento`. RF-61 e explicito, e o
     * engano e natural porque o vencimento e a data que o usuario ve no
     * aplicativo do banco. Uma implementacao trocada daria o resultado certo em
     * muitos cartoes e erraria em todos os que vencem no mes seguinte ao
     * fechamento — e a propriedade de monotonicidade e o que a pega.
     */
    fun competenciaDe(dataCompra: LocalDate, diaFechamento: Int): Competencia {
        val mesDaCompra = YearMonth.from(dataCompra)
        val fechamento = diaEfetivo(diaFechamento, mesDaCompra)
        val meses = if (dataCompra.dayOfMonth < fechamento) 1L else 2L
        return Competencia(mesDaCompra.plusMonths(meses))
    }

    /**
     * Quando a fatura de uma competencia **fecha**.
     *
     * A janela da competencia C, num cartao que fecha no dia F, e
     * `[ F do mes C-2 , F do mes C-1 )` — fechada a esquerda, aberta a direita.
     * O parentese da direita **e** o corte exclusivo de E-03.
     */
    fun dataDeFechamento(competencia: Competencia, diaFechamento: Int): LocalDate {
        val mesAnterior = competencia.valor.minusMonths(1)
        return dataEfetiva(diaFechamento, mesAnterior)
    }

    /** Quando a fatura de uma competencia **vence** (RF-23, RN-A05). */
    fun dataDeVencimento(competencia: Competencia, diaVencimento: Int): LocalDate =
        dataEfetiva(diaVencimento, competencia.valor)
}
