package com.rafaelmatheus.financialcontrol.common.web

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Declara o esquema de seguranca da especificacao (D-06, RNF-08).
 *
 * Sem isto, o springdoc gera a especificacao sem `securitySchemes`, e o Swagger
 * UI nao mostra o botao **Authorize** — ele nao infere autenticacao a partir da
 * cadeia de filtros do Spring Security, so a partir do que a especificacao
 * declara.
 *
 * O resultado pratico era enganoso: as rotas protegidas apareciam na UI como se
 * fossem publicas, e so devolviam 401 na hora de executar.
 */
@Configuration
class ConfiguracaoOpenApi {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("financial-control")
                .version("v1")
                .description(
                    "Controle financeiro pessoal e de grupos. " +
                        "Autenticacao por JWT: use POST /auth/login e cole o token em Authorize.",
                ),
        )
        .components(
            Components().addSecuritySchemes(
                ESQUEMA_BEARER,
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Token devolvido por POST /auth/login. Cole apenas o token, sem o prefixo Bearer."),
            ),
        )
        // Requisito global: vale para toda rota. As publicas (cadastro, login,
        // health) funcionam sem token de qualquer forma — o excesso aqui custa
        // um cadeado a mais na UI, e a falta custaria o botao Authorize sumir.
        .addSecurityItem(SecurityRequirement().addList(ESQUEMA_BEARER))

    private companion object {
        const val ESQUEMA_BEARER = "bearerAuth"
    }
}
