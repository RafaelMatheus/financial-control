package com.rafaelmatheus.financialcontrol.common.seguranca

import com.rafaelmatheus.financialcontrol.common.persistencia.CriterioVisibilidade
import com.rafaelmatheus.financialcontrol.common.web.CodigoErro
import com.rafaelmatheus.financialcontrol.common.web.ErroDeNegocio
import org.springframework.context.annotation.Scope
import org.springframework.context.annotation.ScopedProxyMode
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

/**
 * Porta para descobrir de quais grupos um usuario e membro **ativo**.
 *
 * Vive em `common` e e implementada pelo adaptador de `grupo`. Sem ela, `common`
 * importaria `grupo`, e a seta de dependencia da arquitetura hexagonal
 * apontaria para fora.
 */
interface ConsultaDeGrupos {
    fun gruposAtivosDe(usuarioId: UUID): Set<UUID>
}

/**
 * Fonte **unica** do usuario autenticado e dos seus grupos (RF-03, RNF-05).
 *
 * Escopo de requisicao, com os grupos resolvidos uma vez e reaproveitados: sem
 * isso, cada consulta de uma requisicao dispararia a mesma busca de associacoes,
 * e ela roda em toda requisicao autenticada.
 */
@Component
@Scope(WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
class ContextoUsuario(private val consultaDeGrupos: ConsultaDeGrupos) {

    private var gruposMemorizados: Set<UUID>? = null

    /** Falha se nao houver autenticacao — RN-U05. */
    fun usuarioAtual(): UUID {
        val autenticacao = SecurityContextHolder.getContext().authentication
            ?: throw ErroDeNegocio(CodigoErro.NAO_AUTENTICADO)

        val principal = autenticacao.principal
        if (principal !is UUID) throw ErroDeNegocio(CodigoErro.NAO_AUTENTICADO)
        return principal
    }

    /**
     * Grupos com associacao **ativa**. Grupo de que o usuario saiu nao entra —
     * e aqui que D-44 (corte total) se materializa.
     */
    fun gruposDoUsuario(): Set<UUID> =
        gruposMemorizados ?: consultaDeGrupos.gruposAtivosDe(usuarioAtual()).also {
            gruposMemorizados = it
        }

    fun criterio(): CriterioVisibilidade =
        CriterioVisibilidade(usuarioAtual = usuarioAtual(), gruposDoUsuario = gruposDoUsuario())

    fun estaAutenticado(): Boolean =
        SecurityContextHolder.getContext().authentication?.principal is UUID
}
