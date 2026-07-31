package com.rafaelmatheus.financialcontrol.common.dominio

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/**
 * Property-based testing de `Competencia` (PBT-02, PBT-03).
 *
 * O gerador cobre janeiro e dezembro com frequencia suficiente para exercitar a
 * virada de ano, que e onde a aritmetica de ano-mes costuma errar.
 */
class CompetenciaPropriedadesTest : StringSpec({

    val competenciaArb: Arb<Competencia> = arbitrary {
        val ano = Arb.int(1970..2100).bind()
        val mes = Arb.int(1..12).bind()
        Competencia.de(ano, mes)
    }

    "proxima seguida de anterior devolve a original" {
        checkAll(competenciaArb) { c ->
            c.proxima().anterior() shouldBe c
        }
    }

    "anterior seguida de proxima devolve a original" {
        checkAll(competenciaArb) { c ->
            c.anterior().proxima() shouldBe c
        }
    }

    "proxima e sempre maior que a atual" {
        checkAll(competenciaArb) { c ->
            (c.proxima() > c) shouldBe true
        }
    }

    "avancar doze meses avanca exatamente um ano e mantem o mes" {
        checkAll(competenciaArb) { c ->
            val depois = c.mais(12)
            depois.ano shouldBe c.ano + 1
            depois.mes shouldBe c.mes
        }
    }

    "texto e valor fazem round-trip" {
        checkAll(competenciaArb) { c ->
            Competencia.de(c.toString()) shouldBe c
        }
    }

    "ordem por comparacao coincide com ordem cronologica" {
        checkAll(competenciaArb, Arb.int(1..240)) { c, meses ->
            (c.mais(meses.toLong()) > c) shouldBe true
        }
    }

    // ------------------------------------------------------- casos de borda

    "dezembro avanca para janeiro do ano seguinte" {
        Competencia.de(2026, 12).proxima() shouldBe Competencia.de(2027, 1)
    }

    "janeiro retrocede para dezembro do ano anterior" {
        Competencia.de(2026, 1).anterior() shouldBe Competencia.de(2025, 12)
    }

    "mes fora da faixa e rejeitado" {
        runCatching { Competencia.de(2026, 0) }.isFailure shouldBe true
        runCatching { Competencia.de(2026, 13) }.isFailure shouldBe true
    }

    "formato textual e AAAA-MM com mes de dois digitos" {
        Competencia.de(2026, 7).toString() shouldBe "2026-07"
    }
})
