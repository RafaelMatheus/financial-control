package com.rafaelmatheus.financialcontrol.common.seguranca

import com.fasterxml.jackson.databind.ObjectMapper
import com.rafaelmatheus.financialcontrol.common.web.CHAVE_CORRELACAO
import com.rafaelmatheus.financialcontrol.common.web.CodigoErro
import com.rafaelmatheus.financialcontrol.common.web.RespostaErro
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Le o token e popula o `SecurityContext`. Nao decide autorizacao — quem decide
 * e a cadeia de filtros configurada abaixo.
 */
@Component
class FiltroJwt(private val emissor: EmissorDeToken) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val cabecalho = request.getHeader("Authorization")
        if (cabecalho != null && cabecalho.startsWith(PREFIXO_BEARER)) {
            val usuarioId: UUID? = emissor.usuarioDoToken(cabecalho.removePrefix(PREFIXO_BEARER))
            if (usuarioId != null) {
                // Sem authorities: o sistema nao tem papeis. A autorizacao e por
                // visibilidade de dado, nao por perfil de usuario.
                val autenticacao = UsernamePasswordAuthenticationToken(usuarioId, null, emptyList())
                org.springframework.security.core.context.SecurityContextHolder
                    .getContext().authentication = autenticacao
            }
        }
        filterChain.doFilter(request, response)
    }

    private companion object {
        const val PREFIXO_BEARER = "Bearer "
    }
}

@Configuration
@EnableConfigurationProperties(PropriedadesAuth::class)
class ConfiguracaoSeguranca(private val filtroJwt: FiltroJwt, private val jackson: ObjectMapper) {

    @Bean
    fun codificadorDeSenha(propriedades: PropriedadesAuth): PasswordEncoder =
        BCryptPasswordEncoder(propriedades.bcryptForca)

    @Bean
    fun cadeiaDeFiltros(http: HttpSecurity): SecurityFilterChain {
        http
            // Sem estado de sessao: e o ponto de D-02.
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // CSRF nao se aplica: nao ha cookie de sessao para o navegador enviar
            // sozinho. O token vai num cabecalho que so o proprio front poe.
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .headers { cabecalhos -> cabecalhos.frameOptions { it.disable() } }
            .authorizeHttpRequests { rotas ->
                rotas
                    // Cadastro e login precisam ser publicos por definicao.
                    .requestMatchers("/usuarios").permitAll()
                    .requestMatchers("/auth/**").permitAll()
                    // O healthcheck do container e o do nginx batem aqui SEM
                    // credencial. Exigir token derruba o deploy — verificado.
                    .requestMatchers("/health", "/actuator/health", "/actuator/health/**").permitAll()
                    // /swagger-ui.html estava de fora e dava 401; /swagger-ui/** estava
                    // dentro e dava 500, porque a UI vinha desligada. Agora as tres
                    // rotas sao coerentes, e quem decide se existem e o perfil.
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling { trata ->
                // Sem isto o Spring devolve um 403 em HTML, fora do formato de
                // erro da aplicacao (RNF-09).
                trata.authenticationEntryPoint { _, resposta, _ -> responder401(resposta) }
                trata.accessDeniedHandler { _, resposta, _ -> responder401(resposta) }
            }
        return http.build()
    }

    private fun responder401(resposta: HttpServletResponse) {
        val codigo = CodigoErro.NAO_AUTENTICADO
        resposta.status = codigo.status.value()
        resposta.contentType = MediaType.APPLICATION_JSON_VALUE
        resposta.characterEncoding = Charsets.UTF_8.name()
        jackson.writeValue(
            resposta.outputStream,
            RespostaErro(
                codigo = codigo.name,
                mensagem = codigo.mensagem,
                correlacao = MDC.get(CHAVE_CORRELACAO),
            ),
        )
    }
}
