package com.rafaelmatheus.financialcontrol.common.dominio

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import java.math.BigDecimal

/**
 * Property-based testing de `Dinheiro` (RNF-07, PBT-02, PBT-03, PBT-07, PBT-08).
 *
 * O gerador cobre deliberadamente (PBT-07):
 *  - zero, que e o caso de borda da divisao
 *  - negativos, porque rendimento negativo e caso real (H-55)
 *  - valores com residuo de divisao, como 0,01 em 3 partes
 *  - magnitudes altas o bastante para denunciar ponto flutuante caso alguem
 *    troque o tipo por engano
 *
 * A semente e registrada pelo Kotest na falha, o que torna o caso reproduzivel
 * (PBT-08). O `ci-app.yml` ja publica a semente.
 */
class DinheiroPropriedadesTest : StringSpec({

    // Centavos como Long, convertido para escala 2. Cobre a faixa inteira de
    // sinal e magnitudes ate a casa dos trilhoes de centavos.
    val dinheiroArb: Arb<Dinheiro> = arbitrary {
        val centavos = Arb.long(-1_000_000_000_000L..1_000_000_000_000L).bind()
        Dinheiro.de(BigDecimal.valueOf(centavos, 2))
    }

    val partesArb: Arb<Int> = Arb.int(1..120) // ate 120 parcelas cobre com folga o real

    // ---------------------------------------------------------------- PBT-03

    "a soma das partes de dividirEm e exatamente o valor original" {
        checkAll(dinheiroArb, partesArb) { valor, n ->
            Dinheiro.soma(valor.dividirEm(n)) shouldBe valor
        }
    }

    // SUBSTITUIU a propriedade "as partes diferem entre si em no maximo um
    // centavo", que era verdadeira ate D-68 e passou a ser FALSA.
    //
    // A regra mudou por decisao do usuario na Functional Design de U3: o residuo
    // inteiro vai para a ultima parte (RF-31, E-01), em vez de um centavo por
    // parte nas ultimas. Em 100,00 dividido em 7, as partes passam a diferir em
    // quatro centavos — e isso agora e o comportamento correto.
    //
    // A propriedade NAO foi removida: foi trocada por outra que prende o mesmo
    // que ela prendia. Sozinha, a de soma exata admitiria [V, 0, 0, ..., 0];
    // esta exige que so a ultima parte seja diferente.
    "as primeiras n-1 partes de dividirEm sao iguais entre si" {
        checkAll(dinheiroArb, partesArb) { valor, n ->
            val partes = valor.dividirEm(n)
            if (n > 1) {
                partes.dropLast(1).distinct().size shouldBe 1
            }
        }
    }

    "nenhuma parte de dividirEm tem sinal oposto ao do valor" {
        // Complementa a anterior: garante que a ultima parte nao vira negativa
        // ao absorver o residuo de um valor positivo.
        checkAll(dinheiroArb, partesArb) { valor, n ->
            val partes = valor.dividirEm(n)
            if (!valor.ehZero()) {
                partes.none { it.ehNegativo() != valor.ehNegativo() && !it.ehZero() } shouldBe true
            }
        }
    }

    "dividirEm devolve exatamente o numero de partes pedido" {
        checkAll(dinheiroArb, partesArb) { valor, n ->
            valor.dividirEm(n).size shouldBe n
        }
    }

    "soma e comutativa" {
        checkAll(dinheiroArb, dinheiroArb) { a, b ->
            (a + b) shouldBe (b + a)
        }
    }

    "soma e associativa" {
        checkAll(dinheiroArb, dinheiroArb, dinheiroArb) { a, b, c ->
            ((a + b) + c) shouldBe (a + (b + c))
        }
    }

    "subtrair e somar o oposto" {
        checkAll(dinheiroArb, dinheiroArb) { a, b ->
            (a - b) shouldBe (a + (-b))
        }
    }

    "somar e subtrair o mesmo valor devolve o original" {
        checkAll(dinheiroArb, dinheiroArb) { a, b ->
            ((a + b) - b) shouldBe a
        }
    }

    "zero e elemento neutro da soma" {
        checkAll(dinheiroArb) { a ->
            (a + Dinheiro.ZERO) shouldBe a
        }
    }

    // ---------------------------------------------------------------- PBT-02

    "texto e valor fazem round-trip" {
        checkAll(dinheiroArb) { a ->
            Dinheiro.de(a.toString()) shouldBe a
        }
    }

    // ------------------------------------------------------- casos de borda

    "dividir zero em qualquer numero de partes da tudo zero" {
        checkAll(partesArb) { n ->
            Dinheiro.ZERO.dividirEm(n).all { it.ehZero() } shouldBe true
        }
    }

    "um centavo dividido em tres concentra o residuo na ultima parte" {
        val partes = Dinheiro.de("0.01").dividirEm(3)
        partes.map { it.toString() } shouldBe listOf("0.00", "0.00", "0.01")
        Dinheiro.soma(partes) shouldBe Dinheiro.de("0.01")
    }

    "cem reais em tres devolve 33,33 33,33 33,34" {
        // O exemplo canonico do design. Ele da o MESMO resultado nas duas regras
        // de residuo — foi exatamente isso que O-28 registrou, e e por isso que
        // ele nao serve para distinguir uma da outra. Fica aqui porque continua
        // sendo verdade, nao porque prova a regra.
        val partes = Dinheiro.de("100.00").dividirEm(3)
        partes.map { it.toString() } shouldBe listOf("33.33", "33.33", "33.34")
    }

    "cem reais em sete concentra os quatro centavos na ultima" {
        // ESTE distingue as duas regras, e por isso ele existe.
        //   ultima absorve : 14,28 x6  +  14,32   <- D-68, o comportamento atual
        //   um por parte   : 14,28 x3  +  14,29 x4
        val partes = Dinheiro.de("100.00").dividirEm(7)
        partes.map { it.toString() } shouldBe
            listOf("14.28", "14.28", "14.28", "14.28", "14.28", "14.28", "14.32")
        Dinheiro.soma(partes) shouldBe Dinheiro.de("100.00")
    }

    "um real e dezenove em cento e vinte da 119 zeros e uma parcela cheia" {
        // O caso extremo de D-68, registrado porque foi apresentado ao usuario e
        // confirmado. Nao e defeito: e a consequencia da regra escolhida.
        val partes = Dinheiro.de("1.19").dividirEm(120)
        partes.dropLast(1).all { it.ehZero() } shouldBe true
        partes.last() shouldBe Dinheiro.de("1.19")
        Dinheiro.soma(partes) shouldBe Dinheiro.de("1.19")
    }

    "mil e duzentos em doze nao tem residuo" {
        // O caso comum do parcelamento: divisao exata, nenhuma parte diferente.
        val partes = Dinheiro.de("1200.00").dividirEm(12)
        partes.distinct().size shouldBe 1
        partes.first() shouldBe Dinheiro.de("100.00")
    }

    "valor negativo dividido tambem soma exato" {
        val partes = Dinheiro.de("-100.00").dividirEm(3)
        Dinheiro.soma(partes) shouldBe Dinheiro.de("-100.00")
    }

    "arredondamento na construcao e HALF_UP" {
        Dinheiro.de("0.005").toString() shouldBe "0.01"
        Dinheiro.de("0.015").toString() shouldBe "0.02"
        Dinheiro.de("-0.005").toString() shouldBe "-0.01"
    }
})
