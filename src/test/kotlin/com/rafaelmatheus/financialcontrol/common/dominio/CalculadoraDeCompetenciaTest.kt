package com.rafaelmatheus.financialcontrol.common.dominio

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.localDate
import io.kotest.property.checkAll
import java.time.LocalDate
import java.time.YearMonth

/**
 * Property-based testing do nucleo aritmetico de U3 (RN-F01, RN-K03).
 *
 * A funcao e pura, entao as propriedades sao baratas de gerar — e sao o unico
 * lugar onde a virada de ano, o fevereiro bissexto e o cartao que fecha dia 31
 * sao exercitados de verdade.
 */
class CalculadoraDeCompetenciaTest : StringSpec({

    val diasArb = Arb.int(1..31)
    val datasArb = Arb.localDate(LocalDate.of(2020, 1, 1), LocalDate.of(2035, 12, 31))

    // ------------------------------------------------ RN-K03 / D-69 / E-04, E-11

    "diaEfetivo produz data valida para todo dia de 1 a 31 e todo mes" {
        checkAll(diasArb, Arb.int(2020..2035), Arb.int(1..12)) { dia, ano, mes ->
            val anoMes = YearMonth.of(ano, mes)
            val efetivo = CalculadoraDeCompetencia.diaEfetivo(dia, anoMes)

            (efetivo in 1..anoMes.lengthOfMonth()) shouldBe true
            // Nunca inventa dia: so reduz quando o mes e mais curto.
            (efetivo <= dia) shouldBe true
        }
    }

    "diaEfetivo so reduz o dia quando o mes e mais curto que ele" {
        checkAll(diasArb, Arb.int(2020..2035), Arb.int(1..12)) { dia, ano, mes ->
            val anoMes = YearMonth.of(ano, mes)
            val efetivo = CalculadoraDeCompetencia.diaEfetivo(dia, anoMes)
            if (dia <= anoMes.lengthOfMonth()) efetivo shouldBe dia
        }
    }

    "fevereiro bissexto e nao-bissexto" {
        CalculadoraDeCompetencia.diaEfetivo(31, YearMonth.of(2024, 2)) shouldBe 29
        CalculadoraDeCompetencia.diaEfetivo(31, YearMonth.of(2026, 2)) shouldBe 28
        CalculadoraDeCompetencia.diaEfetivo(30, YearMonth.of(2026, 4)) shouldBe 30
        CalculadoraDeCompetencia.diaEfetivo(31, YearMonth.of(2026, 4)) shouldBe 30
    }

    // --------------------------------------------------------- RN-F01 / RF-25

    /**
     * **A propriedade que protege contra o engano de RF-61.**
     *
     * Uma implementacao que usasse `diaVencimento` no lugar de `diaFechamento`
     * passaria em muitos exemplos e falharia aqui: dentro do mesmo mes, uma data
     * maior nunca pode cair numa competencia menor.
     */
    "competencia e monotonica: data maior nunca cai em competencia menor" {
        checkAll(datasArb, datasArb, diasArb) { a, b, fechamento ->
            val (menor, maior) = if (a <= b) a to b else b to a
            val cMenor = CalculadoraDeCompetencia.competenciaDe(menor, fechamento)
            val cMaior = CalculadoraDeCompetencia.competenciaDe(maior, fechamento)
            (cMaior >= cMenor) shouldBe true
        }
    }

    "competencia e sempre posterior ao mes da compra" {
        checkAll(datasArb, diasArb) { data, fechamento ->
            val competencia = CalculadoraDeCompetencia.competenciaDe(data, fechamento)
            (competencia.valor > YearMonth.from(data)) shouldBe true
        }
    }

    "competencia esta sempre a um ou dois meses do mes da compra" {
        checkAll(datasArb, diasArb) { data, fechamento ->
            val mesDaCompra = YearMonth.from(data)
            val competencia = CalculadoraDeCompetencia.competenciaDe(data, fechamento)
            val distancia = java.time.temporal.ChronoUnit.MONTHS
                .between(mesDaCompra, competencia.valor)
            (distancia in 1..2) shouldBe true
        }
    }

    "a data de fechamento da competencia e sempre anterior ao vencimento dela" {
        // Nao e tautologia: cartao que fecha dia 28 e vence dia 5 fecha em 28/07
        // e vence em 05/08 — meses diferentes. A ordem tem que valer mesmo assim.
        checkAll(diasArb, diasArb, Arb.int(2020..2035), Arb.int(1..12)) { f, v, ano, mes ->
            val competencia = Competencia.de(ano, mes)
            val fecha = CalculadoraDeCompetencia.dataDeFechamento(competencia, f)
            val vence = CalculadoraDeCompetencia.dataDeVencimento(competencia, v)
            (fecha < vence) shouldBe true
        }
    }

    // ------------------------------------------------- os cenarios de H-20

    "cartao que fecha dia 28: os tres cenarios de H-20" {
        val julho27 = LocalDate.of(2026, 7, 27)
        val julho28 = LocalDate.of(2026, 7, 28)
        val julho30 = LocalDate.of(2026, 7, 30)

        CalculadoraDeCompetencia.competenciaDe(julho27, 28) shouldBe Competencia.de(2026, 8)
        // O corte e EXCLUSIVO: o dia do fechamento ja pertence ao ciclo seguinte.
        CalculadoraDeCompetencia.competenciaDe(julho28, 28) shouldBe Competencia.de(2026, 9)
        CalculadoraDeCompetencia.competenciaDe(julho30, 28) shouldBe Competencia.de(2026, 9)
    }

    "o cenario de H-45: fecha 28/07, vence 05/08" {
        val agosto = Competencia.de(2026, 8)
        CalculadoraDeCompetencia.dataDeFechamento(agosto, 28) shouldBe LocalDate.of(2026, 7, 28)
        CalculadoraDeCompetencia.dataDeVencimento(agosto, 5) shouldBe LocalDate.of(2026, 8, 5)
    }

    "cartao que fecha dia 31 em fevereiro" {
        // 27/02 < 28 (o dia efetivo) -> marco. 28/02 >= 28 -> abril.
        CalculadoraDeCompetencia.competenciaDe(LocalDate.of(2026, 2, 27), 31) shouldBe
            Competencia.de(2026, 3)
        CalculadoraDeCompetencia.competenciaDe(LocalDate.of(2026, 2, 28), 31) shouldBe
            Competencia.de(2026, 4)
    }

    "virada de ano" {
        CalculadoraDeCompetencia.competenciaDe(LocalDate.of(2026, 12, 30), 28) shouldBe
            Competencia.de(2027, 2)
        CalculadoraDeCompetencia.competenciaDe(LocalDate.of(2026, 12, 1), 28) shouldBe
            Competencia.de(2027, 1)
    }
})
