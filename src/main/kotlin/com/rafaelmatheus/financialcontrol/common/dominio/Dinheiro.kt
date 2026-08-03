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
     * Adicionado por U2 (RN-L01): gasto exige valor estritamente positivo.
     *
     * Existe para que a invariante seja lida como afirmacao e nao como negacao
     * dupla — `!ehZero() && !ehNegativo()` diz a mesma coisa e ninguem le certo
     * na primeira passada.
     */
    fun ehPositivo(): Boolean = valor.signum() > 0

    /**
     * Divide em [n] partes cuja soma e **exatamente** este valor, com a **ultima
     * parte absorvendo todo o residuo** (RF-31, E-01, RN-P03, D-68).
     *
     * Trabalha em centavos inteiros, nao em decimal fracionario: a soma volta a
     * ser exata por construcao, e nao por sorte de arredondamento.
     *
     * ```
     * 100,00 em 3  ->  33,33  33,33  33,34
     * 100,00 em 7  ->  14,28 x6      14,32
     * 1,19  em 120 ->  0,00 x119     1,19
     * ```
     *
     * ## Historico desta funcao, porque ele importa para quem for altera-la
     *
     * A implementacao anterior distribuia o residuo **um centavo por parte, nas
     * ultimas**, o que mantinha a diferenca entre partes em no maximo um centavo.
     * Ela foi adotada em U1 porque o property-based testing mostrou, no primeiro
     * caso gerado, que o exemplo canonico de 100,00 em 3 **ilustrava a regra
     * errada com o resultado certo** — o residuo ali e de exatamente um centavo,
     * e as duas regras coincidem (research-log 3.36, O-28).
     *
     * A regra atual foi **escolhida pelo usuario** na Functional Design de U3
     * (D-68), apresentada com os tres exemplos acima e confirmada numa segunda
     * rodada. Ela cumpre RF-31 e E-01 ao pe da letra.
     *
     * O-28 continua valendo como observacao: o exemplo de 100,00 em 3 e
     * insuficiente para distinguir as duas regras. O que mudou foi a regra
     * escolhida, nao o fato de o exemplo nao servir para escolher.
     *
     * **Consequencia para quem testa**: a propriedade "partes diferem no maximo
     * 0,01" e FALSA aqui. A propriedade que vale e "as primeiras n-1 sao iguais
     * entre si". A de soma exata vale nas duas, e e a que RF-32 exige.
     *
     * Alvo de property-based testing (RNF-07) — ver DinheiroPropriedadesTest.
     */
    fun dividirEm(n: Int): List<Dinheiro> {
        require(n >= 1) { "Numero de partes deve ser pelo menos 1, recebido: $n" }

        // A escala e sempre 2, entao o valor sem escala ja e a quantia em centavos.
        val centavos = valor.unscaledValue()
        val divisor = BigInteger.valueOf(n.toLong())

        // Divisao truncada em direcao a zero, que e o comportamento de BigInteger.
        // Em valor negativo isso faz a base ser maior (menos negativa) que o
        // quociente exato, e a ultima parte absorve a diferenca com o sinal certo:
        // -100,00 em 3 da -33,33 -33,33 -33,34.
        val base = centavos / divisor
        val ultima = centavos - base * BigInteger.valueOf((n - 1).toLong())

        return List(n) { indice ->
            val parte = if (indice == n - 1) ultima else base
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
