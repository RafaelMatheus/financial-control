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

    "as partes de dividirEm diferem entre si em no maximo um centavo" {
        // Sozinha, a propriedade acima admitiria [V, 0, 0, ..., 0]. Esta prende o
        // comportamento: a divisao precisa ser equilibrada, nao so somar certo.
        checkAll(dinheiroArb, partesArb) { valor, n ->
            val partes = valor.dividirEm(n)
            val maior = partes.maxOf { it.valor }
            val menor = partes.minOf { it.valor }
            (maior.subtract(menor).abs() <= BigDecimal("0.01")) shouldBe true
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
        val partes = Dinheiro.de("100.00").dividirEm(3)
        partes.map { it.toString() } shouldBe listOf("33.33", "33.33", "33.34")
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
