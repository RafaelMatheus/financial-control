package com.rafaelmatheus.financialcontrol.investimento.dominio

import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * As propriedades de investimento (RF-70 a RF-74).
 *
 * A mais interessante e a do aporte mensal: e a **segunda divisao monetaria do
 * sistema**, e tem direcao **oposta** a do parcelamento.
 */
class InvestimentoPropriedadesTest : StringSpec({

    val valores = Arb.long(1L, 100_000_000L).map { Dinheiro.de(BigDecimal.valueOf(it, 2)) }
    val hoje = LocalDate.of(2026, 8, 1)

    fun objetivo(meta: Dinheiro?, saldo: Dinheiro, prazo: LocalDate?) = ObjetivoInvestimento(
        id = UUID.randomUUID(),
        nome = "Objetivo",
        meta = meta,
        prazoAlvo = prazo,
        saldoAtual = saldo,
        dono = UUID.randomUUID(),
        escopo = Escopo.PESSOAL,
        grupo = null,
        criadoEm = Instant.EPOCH,
    )

    // ------------------------------------------------------- RF-72, RN-I06

    "rendimento e sempre saldo menos aportado, inclusive negativo" {
        checkAll(valores, valores) { saldo, aportado ->
            val p = PosicaoDoObjetivo(objetivo(null, saldo, null), aportado)
            p.rendimento shouldBe (saldo - aportado)
        }
    }

    "rendimento negativo e aceito, nao rejeitado — E-14, P-08" {
        // Prejuizo e resgate nao registrado sao casos reais. E a razao de
        // Dinheiro aceitar negativos desde U1, decidido sem consumidor a vista.
        val p = PosicaoDoObjetivo(objetivo(null, Dinheiro.de("950.00"), null), Dinheiro.de("1000.00"))
        p.rendimento shouldBe Dinheiro.de("-50.00")
    }

    "aportar e excluir sao inversas — D-80 e D-83" {
        checkAll(valores, valores) { saldoInicial, aporte ->
            val inicial = objetivo(null, saldoInicial, null)
            val depoisDeAportar = inicial.comAporte(aporte)
            val depoisDeExcluir = depoisDeAportar.semAporte(aporte)

            depoisDeExcluir.saldoAtual shouldBe saldoInicial
        }
    }

    "aportar nao move o rendimento — D-80" {
        // O saldo e o aportado sobem juntos. Sem D-80 o rendimento nasceria
        // negativo e ficaria errado ate o usuario corrigir a mao.
        checkAll(valores, valores) { aportadoAntes, novo ->
            val antes = objetivo(null, aportadoAntes, null)
            val rendimentoAntes = PosicaoDoObjetivo(antes, aportadoAntes).rendimento

            val depois = antes.comAporte(novo)
            val rendimentoDepois = PosicaoDoObjetivo(depois, aportadoAntes + novo).rendimento

            rendimentoDepois shouldBe rendimentoAntes
        }
    }

    // ---------------------------------------------- RF-74, RN-I08 — o alvo 4

    /**
     * **A propriedade mais interessante da unidade.**
     *
     * Esta divisao tem direcao **oposta** a do parcelamento:
     *
     * - no parcelamento, `soma(parcelas) == total` — a soma tem que ser **exata**;
     * - aqui, `aporteMensal x meses >= falta` — a soma tem que ser **suficiente**.
     *
     * Arredondar para baixo faria o usuario chegar ao prazo faltando centavos, o
     * que derrota o proposito do numero.
     */
    "aporte mensal vezes meses e SEMPRE suficiente para cobrir o que falta" {
        checkAll(valores, valores, Arb.int(1..120)) { meta, saldo, mesesAFrente ->
            val prazo = hoje.plusMonths(mesesAFrente.toLong())
            val mensal = CalculadoraDeAporte.mensalNecessario(meta, saldo, prazo, hoje)

            if (saldo < meta && mensal != null && !mensal.ehZero()) {
                val falta = (meta - saldo).valor
                val coberto = mensal.valor.multiply(BigDecimal.valueOf(mesesAFrente.toLong()))
                (coberto >= falta) shouldBe true
            }
        }
    }

    "meta ja atingida devolve zero, nao um valor negativo" {
        checkAll(valores) { meta ->
            val saldo = meta + Dinheiro.de("1.00")
            val mensal = CalculadoraDeAporte.mensalNecessario(meta, saldo, hoje.plusMonths(6), hoje)
            mensal shouldBe Dinheiro.ZERO
        }
    }

    "sem meta ou sem prazo, nao ha aporte mensal a calcular" {
        CalculadoraDeAporte.mensalNecessario(null, Dinheiro.ZERO, hoje.plusMonths(6), hoje) shouldBe null
        CalculadoraDeAporte.mensalNecessario(Dinheiro.de("100.00"), Dinheiro.ZERO, null, hoje) shouldBe null
    }

    "prazo vencido nao calcula aporte — o objetivo e sinalizado como atrasado (E-15)" {
        val mensal = CalculadoraDeAporte.mensalNecessario(
            Dinheiro.de("1000.00"), Dinheiro.de("100.00"), hoje.minusMonths(2), hoje,
        )
        mensal shouldBe null
    }

    "o exemplo de H-57" {
        // Faltam 6.000,00 em 12 meses -> 500,00 por mes.
        val mensal = CalculadoraDeAporte.mensalNecessario(
            Dinheiro.de("6000.00"), Dinheiro.ZERO, hoje.plusMonths(12), hoje,
        )
        mensal shouldBe Dinheiro.de("500.00")
    }

    "arredondamento para cima quando nao divide exato" {
        // Faltam 100,00 em 3 meses: 33,333... -> 33,34, e 3 x 33,34 = 100,02 >= 100,00.
        val mensal = CalculadoraDeAporte.mensalNecessario(
            Dinheiro.de("100.00"), Dinheiro.ZERO, hoje.plusMonths(3), hoje,
        )
        mensal shouldBe Dinheiro.de("33.34")
    }

    // --------------------------------------------------------- RN-I07

    "sem meta, progresso e falta sao ausentes — nao zero" {
        val p = PosicaoDoObjetivo(objetivo(null, Dinheiro.de("500.00"), null), Dinheiro.de("500.00"))
        p.progresso shouldBe null
        p.falta shouldBe null
    }

    "com meta, falta nunca e negativo" {
        checkAll(valores, valores) { meta, saldo ->
            val p = PosicaoDoObjetivo(objetivo(meta, saldo, null), Dinheiro.ZERO)
            (p.falta!!.ehNegativo()) shouldBe false
        }
    }
})
