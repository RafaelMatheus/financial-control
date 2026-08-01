package com.rafaelmatheus.financialcontrol

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * As invariantes que **so** o banco garante.
 *
 * Cada uma tem uma verificacao na aplicacao, que existe para dar boa mensagem.
 * Duas requisicoes simultaneas passam pelas duas verificacoes: quem realmente
 * barra e a restricao no banco. Este arquivo prova que ela existe e funciona.
 *
 * Sem `@Transactional` na classe, de proposito: com rollback automatico nao
 * haveria commit concorrente, e o teste passaria sem exercitar nada.
 */
class ConcorrenciaTest : SuporteDeIntegracao() {

    @Test
    fun `cadastros simultaneos com o mesmo email — apenas um vence`() {
        // RN-U01, uk_usuario_email.
        val corpo = """{"email":"disputa@exemplo.com","senha":"senha-de-teste","nome":"Ana"}"""

        val resultados = emParalelo(8) {
            mvc.perform(post("/usuarios").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andReturn().response.status
        }

        val criados = resultados.count { it == 201 }
        val conflitos = resultados.count { it == 409 }

        assert(criados == 1) { "Exatamente um cadastro deveria vencer, foram $criados: $resultados" }
        assert(criados + conflitos == resultados.size) {
            "Todo resultado deveria ser 201 ou 409, veio: $resultados"
        }
    }

    @Test
    fun `adicoes simultaneas do mesmo membro — apenas uma vence`() {
        // RN-G05, uk_membro_grupo_ativo — o indice unico PARCIAL.
        //
        // Este e o teste que justifica rodar contra PostgreSQL real: o indice
        // parcial nao e expressavel em JPA, `ddl-auto: validate` nao o verifica,
        // e num banco em memoria ele nao existiria. O teste passaria por nao ter
        // nada para barrar.
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (idCarlos, _) = usuarioAutenticado("carlos@exemplo.com", "Carlos")

        val grupoId = json.readTree(
            mvc.perform(
                comToken(post("/grupos"), tokenAna)
                    .contentType(MediaType.APPLICATION_JSON).content("""{"nome":"Apartamento 42"}"""),
            ).andExpect(status().isCreated).andReturn().response.contentAsString,
        ).get("id").asText()

        val resultados = emParalelo(8) {
            mvc.perform(
                comToken(post("/grupos/$grupoId/membros"), tokenAna)
                    .contentType(MediaType.APPLICATION_JSON).content("""{"usuarioId":"$idCarlos"}"""),
            ).andReturn().response.status
        }

        val sucessos = resultados.count { it == 200 }
        assert(sucessos == 1) {
            "Exatamente uma adicao deveria vencer, foram $sucessos: $resultados"
        }
    }

    @Test
    fun `categorias simultaneas com o mesmo nome no mesmo grupo — apenas uma vence`() {
        // RN-C02, uk_categoria_grupo — o segundo indice unico PARCIAL do projeto.
        //
        // Mesma justificativa do anterior: e PostgreSQL puro, invisivel ao
        // `ddl-auto: validate`, e num banco em memoria nao existiria.
        //
        // O cenario e o que D-54 veio resolver: Ana e Rafael criando "Mercado"
        // no mesmo grupo ao mesmo tempo. Sem o indice, o grupo ficaria com duas
        // categorias de mesmo nome e o total por categoria mostraria duas linhas.
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (idRafael, tokenRafael) = usuarioAutenticado("rafael@exemplo.com", "Rafael")

        val grupoId = criarGrupoDe(tokenAna, "Apartamento 42")
        adicionarMembroAoGrupo(tokenAna, grupoId, idRafael)

        val tokens = listOf(tokenAna, tokenRafael)
        val corpo = """{"nome":"Mercado","escopo":"GRUPO","grupoId":"$grupoId"}"""

        val resultados = emParaleloIndexado(8) { indice ->
            mvc.perform(
                comToken(post("/categorias"), tokens[indice % 2])
                    .contentType(MediaType.APPLICATION_JSON).content(corpo),
            ).andReturn().response.status
        }

        val criadas = resultados.count { it == 201 }
        assert(criadas == 1) {
            "Exatamente uma categoria deveria vencer, foram $criadas: $resultados"
        }
    }

    @Test
    fun `listagens simultaneas de usuario novo nao duplicam as categorias iniciais`() {
        // RN-C08 e o ramo de corrida do fluxo: o front carregando duas telas ao
        // mesmo tempo faz duas requisicoes chegarem com a lista vazia. O indice
        // unico absorve, e o perdedor rele.
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")

        val resultados = emParalelo(6) {
            mvc.perform(
                comToken(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/categorias"),
                    token,
                ),
            ).andReturn().response.status
        }

        assert(resultados.all { it == 200 }) { "Toda listagem deveria responder 200: $resultados" }

        val total = json.readTree(
            mvc.perform(
                comToken(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/categorias"),
                    token,
                ),
            ).andReturn().response.contentAsString,
        ).size()

        assert(total == 10) { "Deveriam existir exatamente 10 categorias iniciais, existem $total" }
    }

    private fun emParaleloIndexado(quantidade: Int, acao: (Int) -> Int): List<Int> {
        val executor = Executors.newFixedThreadPool(quantidade)
        return try {
            val tarefas = List(quantidade) { indice ->
                Callable { runCatching { acao(indice) }.getOrDefault(-1) }
            }
            executor.invokeAll(tarefas, 30, TimeUnit.SECONDS).map { it.get() }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun emParalelo(quantidade: Int, acao: () -> Int): List<Int> {
        val executor = Executors.newFixedThreadPool(quantidade)
        return try {
            val tarefas = List(quantidade) { Callable { runCatching(acao).getOrDefault(-1) } }
            executor.invokeAll(tarefas, 30, TimeUnit.SECONDS).map { it.get() }
        } finally {
            executor.shutdownNow()
        }
    }
}
