package com.rafaelmatheus.financialcontrol.common.seguranca

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * O relogio e injetado para que o teste de expiracao nao durma 15 minutos.
 * Teste que depende de tempo real e teste que fica lento ou intermitente.
 */
class RegistroDeTentativasTest : StringSpec({

    val propriedades = PropriedadesAuth(
        jwtSecret = "irrelevante-para-este-teste-mas-precisa-existir",
        maxTentativas = 5,
        bloqueioMinutos = 15,
    )

    class RelogioAjustavel(var agora: Instant) : Clock() {
        override fun instant() = agora
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
        fun avancar(duracao: Duration) { agora = agora.plus(duracao) }
    }

    "conta nova nao esta bloqueada" {
        val registro = RegistroDeTentativas(propriedades, Clock.systemUTC())
        registro.estaBloqueado("alguem@exemplo.com") shouldBe false
    }

    "quatro falhas ainda nao bloqueiam" {
        val registro = RegistroDeTentativas(propriedades, Clock.systemUTC())
        repeat(4) { registro.registrarFalha("alguem@exemplo.com") }
        registro.estaBloqueado("alguem@exemplo.com") shouldBe false
    }

    "a quinta falha bloqueia" {
        val registro = RegistroDeTentativas(propriedades, Clock.systemUTC())
        repeat(5) { registro.registrarFalha("alguem@exemplo.com") }
        registro.estaBloqueado("alguem@exemplo.com") shouldBe true
    }

    "o bloqueio expira depois da janela" {
        val relogio = RelogioAjustavel(Instant.parse("2026-07-31T12:00:00Z"))
        val registro = RegistroDeTentativas(propriedades, relogio)

        repeat(5) { registro.registrarFalha("alguem@exemplo.com") }
        registro.estaBloqueado("alguem@exemplo.com") shouldBe true

        relogio.avancar(Duration.ofMinutes(14))
        registro.estaBloqueado("alguem@exemplo.com") shouldBe true

        relogio.avancar(Duration.ofMinutes(2))
        registro.estaBloqueado("alguem@exemplo.com") shouldBe false
    }

    "login bem-sucedido zera o contador" {
        val registro = RegistroDeTentativas(propriedades, Clock.systemUTC())
        repeat(4) { registro.registrarFalha("alguem@exemplo.com") }
        registro.registrarSucesso("alguem@exemplo.com")
        repeat(4) { registro.registrarFalha("alguem@exemplo.com") }
        registro.estaBloqueado("alguem@exemplo.com") shouldBe false
    }

    "o bloqueio e por conta, nao global" {
        val registro = RegistroDeTentativas(propriedades, Clock.systemUTC())
        repeat(5) { registro.registrarFalha("vitima@exemplo.com") }
        registro.estaBloqueado("vitima@exemplo.com") shouldBe true
        registro.estaBloqueado("outra@exemplo.com") shouldBe false
    }

    "limparVencidos descarta registros fora da janela" {
        // Sem esta limpeza o mapa cresceria com todo e-mail ja tentado uma vez,
        // que num sistema exposto e a maioria deles.
        val relogio = RelogioAjustavel(Instant.parse("2026-07-31T12:00:00Z"))
        val registro = RegistroDeTentativas(propriedades, relogio)

        repeat(5) { registro.registrarFalha("antiga@exemplo.com") }
        relogio.avancar(Duration.ofMinutes(20))
        registro.limparVencidos()

        relogio.avancar(Duration.ofMinutes(-20)) // volta no tempo: se o registro
        registro.estaBloqueado("antiga@exemplo.com") shouldBe false // sobrevivesse, bloquearia
    }
})
