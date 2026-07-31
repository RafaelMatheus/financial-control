package com.rafaelmatheus.financialcontrol.common.seguranca

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@ConfigurationProperties(prefix = "app.auth")
data class PropriedadesAuth(
    /**
     * Sem default: um default aqui viraria o segredo de producao no dia em que a
     * variavel faltasse. Vem do Parameter Store.
     */
    val jwtSecret: String,
    val jwtValidadeHoras: Long = 24,
    val bcryptForca: Int = 12,
    val maxTentativas: Int = 5,
    val bloqueioMinutos: Long = 15,
)

/**
 * Emite e valida o token de sessao (D-02, D-50).
 *
 * JWT stateless: o servidor nao guarda estado de sessao. A consequencia aceita e
 * que **nao ha revogacao antes do vencimento** — logout apaga o token no cliente,
 * e um token vazado vale ate 24 horas.
 *
 * A unica revogacao disponivel e **girar o segredo**, o que invalida todos os
 * tokens de uma vez. Procedimento de emergencia, nao operacao de rotina.
 */
@Component
class EmissorDeToken(propriedades: PropriedadesAuth) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val chave: SecretKey = Keys.hmacShaKeyFor(propriedades.jwtSecret.toByteArray())
    private val validadeSegundos = propriedades.jwtValidadeHoras * 3600

    fun emitir(usuarioId: UUID): String {
        val agora = Instant.now()
        return Jwts.builder()
            .subject(usuarioId.toString())
            .issuedAt(Date.from(agora))
            .expiration(Date.from(agora.plusSeconds(validadeSegundos)))
            .signWith(chave)
            .compact()
    }

    /**
     * Devolve o usuario do token, ou `null` se o token for invalido por qualquer
     * motivo — assinatura, expiracao, formato.
     *
     * Nao distingue os motivos de proposito: para quem chama, "invalido" e
     * "invalido". E o token **nunca** e registrado em log (NFR-U1-05).
     */
    fun usuarioDoToken(token: String): UUID? =
        runCatching {
            val claims = Jwts.parser().verifyWith(chave).build()
                .parseSignedClaims(token).payload
            UUID.fromString(claims.subject)
        }.getOrElse {
            log.debug("Token recusado: {}", it.javaClass.simpleName)
            null
        }

    fun validadeEmSegundos(): Long = validadeSegundos
}
