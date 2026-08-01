package com.rafaelmatheus.financialcontrol.common.seguranca

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.UUID

class EmissorDeTokenTest : StringSpec({

    val segredo = "segredo-de-teste-com-tamanho-suficiente-para-hmac-sha256-ok"
    val emissor = EmissorDeToken(PropriedadesAuth(jwtSecret = segredo))

    "token emitido devolve o mesmo usuario ao ser lido" {
        val usuario = UUID.randomUUID()
        emissor.usuarioDoToken(emissor.emitir(usuario)) shouldBe usuario
    }

    "token assinado com outro segredo e recusado" {
        // O caso que importa de verdade: quem forja um token nao tem a chave.
        val outroEmissor = EmissorDeToken(
            PropriedadesAuth(jwtSecret = "outro-segredo-completamente-diferente-e-longo-o-bastante"),
        )
        val forjado = outroEmissor.emitir(UUID.randomUUID())
        emissor.usuarioDoToken(forjado) shouldBe null
    }

    "token expirado e recusado" {
        val expirador = EmissorDeToken(PropriedadesAuth(jwtSecret = segredo, jwtValidadeHoras = 0))
        val token = expirador.emitir(UUID.randomUUID())
        Thread.sleep(1100) // a expiracao do JWT tem resolucao de segundos
        expirador.usuarioDoToken(token) shouldBe null
    }

    "lixo no lugar do token nao explode, so devolve nulo" {
        emissor.usuarioDoToken("isto-nao-e-um-jwt") shouldBe null
        emissor.usuarioDoToken("") shouldBe null
        emissor.usuarioDoToken("a.b.c") shouldBe null
    }

    "token adulterado e recusado" {
        val token = emissor.emitir(UUID.randomUUID())
        val adulterado = token.dropLast(1) + if (token.last() == 'A') 'B' else 'A'
        emissor.usuarioDoToken(adulterado) shouldBe null
    }

    "tokens de usuarios diferentes sao diferentes" {
        emissor.emitir(UUID.randomUUID()) shouldNotBe emissor.emitir(UUID.randomUUID())
    }

    "validade configurada e respeitada" {
        EmissorDeToken(PropriedadesAuth(jwtSecret = segredo, jwtValidadeHoras = 24))
            .validadeEmSegundos() shouldBe 86_400
    }
})
