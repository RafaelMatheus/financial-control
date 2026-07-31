package com.rafaelmatheus.financialcontrol.common.dominio

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Valor monetario com aritmetica decimal exata (RNF-01).
 *
 * Escala sempre 2 e arredondamento HALF_UP (D-43) — o arredondamento de fatura e
 * extrato bancario brasileiro, previsivel para quem confere a conta na mao.
 *
 * Negativos sao permitidos: rendimento negativo de investimento e caso real
 * (H-55), e rejeita-lo aqui empurraria o problema para quem consome.
 *
 * `Double` e `Float` nao aparecem em lugar nenhum desta classe, nem devem
 * aparecer em qualquer caminho que toque valor monetario.
 */
@JvmInline
value class Dinheiro private constructor(val valor: BigDecimal) : Comparable<Dinheiro> {

    operator fun plus(outro: Dinheiro): Dinheiro = Dinheiro(valor.add(outro.valor))

    operator fun minus(outro: Dinheiro): Dinheiro = Dinheiro(valor.subtract(outro.valor))

    operator fun unaryMinus(): Dinheiro = Dinheiro(valor.negate())

    override fun compareTo(other: Dinheiro): Int = valor.compareTo(other.valor)

    fun ehZero(): Boolean = valor.signum() == 0

    fun ehNegativo(): Boolean = valor.signum() < 0

    /**
     * Divide em [n] partes cuja soma e **exatamente** este valor, com as partes
     * diferindo entre si em no maximo um centavo.
     *
     * Trabalha em centavos inteiros, nao em decimal fracionario: a soma volta a
     * ser exata por construcao, e nao por sorte de arredondamento.
     *
     * Os centavos que sobram vao **um por parte, nas ultimas**. Nao "todos na
     * ultima": isso concentraria ate n-1 centavos num unico lugar. O caso que
     * expos o erro foi R$ 10.000.000.000,00 em 6 partes, onde sobram 4 centavos —
     * encontrado pelo property-based testing na primeira execucao, depois de o
     * exemplo de 100,00 em 3 ter passado por coincidencia de o residuo ali ser
     * de exatamente um centavo.
     *
     * Para o parcelamento isso importa de verdade: R$ 1,19 em 120 parcelas deve
     * dar centavos espalhados, nao 119 parcelas de zero e uma de R$ 1,19.
     *
     * Alvo de property-based testing (RNF-07) — ver DinheiroPropriedadesTest.
     */
    fun dividirEm(n: Int): List<Dinheiro> {
        require(n >= 1) { "Numero de partes deve ser pelo menos 1, recebido: $n" }

        // A escala e sempre 2, entao o valor sem escala ja e a quantia em centavos.
        val centavos = valor.unscaledValue()
        val divisor = BigInteger.valueOf(n.toLong())
        val base = centavos / divisor
        val resto = centavos.rem(divisor) // mesmo sinal do dividendo

        // Em valor negativo o resto e negativo, e o ajuste tem de ser de -1 centavo:
        // -100,00 em 3 deve dar -33,33 -33,33 -33,34, e nao -33,32.
        val ajuste = BigInteger.valueOf(resto.signum().toLong())
        val quantasAjustadas = resto.abs().toInt()

        return List(n) { indice ->
            val ehUmaDasUltimas = indice >= n - quantasAjustadas
            val parte = if (ehUmaDasUltimas) base + ajuste else base
            Dinheiro(BigDecimal(parte, 2))
        }
    }

    override fun toString(): String = valor.toPlainString()

    companion object {
        val ZERO: Dinheiro = Dinheiro(BigDecimal.ZERO.setScale(2))

        fun de(valor: BigDecimal): Dinheiro = Dinheiro(valor.setScale(2, RoundingMode.HALF_UP))

        fun de(valor: String): Dinheiro = de(BigDecimal(valor))

        fun de(valor: Long): Dinheiro = de(BigDecimal.valueOf(valor))

        /** Soma uma colecao sem passar por zero intermediario perdido. */
        fun soma(valores: Iterable<Dinheiro>): Dinheiro =
            valores.fold(ZERO) { acumulado, atual -> acumulado + atual }
    }
}
