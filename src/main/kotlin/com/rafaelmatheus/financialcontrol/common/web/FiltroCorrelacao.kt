package com.rafaelmatheus.financialcontrol.common.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Poe um id de correlacao no MDC, para que as linhas de log de uma requisicao
 * possam ser reunidas (D-53).
 *
 * Primeiro filtro da cadeia: se ele rodasse depois do filtro de autenticacao, as
 * falhas de autenticacao — justamente as que mais interessa investigar —
 * ficariam sem id.
 */
@Component
@Order(FILTRO_CORRELACAO_ORDEM)
class FiltroCorrelacao : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        // Aceita um id vindo do cliente para poder atravessar o front-end, mas
        // gera o proprio quando nao vem — nunca fica sem.
        val correlacao = request.getHeader(CABECALHO_CORRELACAO)
            ?.takeIf { it.isNotBlank() && it.length <= 64 }
            ?: UUID.randomUUID().toString()

        MDC.put(CHAVE_CORRELACAO, correlacao)
        response.setHeader(CABECALHO_CORRELACAO, correlacao)

        try {
            filterChain.doFilter(request, response)
        } finally {
            // Obrigatorio: o pool reaproveita a thread, e sem a limpeza o id
            // vazaria para a proxima requisicao atendida por ela.
            MDC.remove(CHAVE_CORRELACAO)
        }
    }
}

const val CABECALHO_CORRELACAO = "X-Correlacao-Id"
const val CHAVE_CORRELACAO = "correlacao"
const val FILTRO_CORRELACAO_ORDEM = Int.MIN_VALUE + 10
