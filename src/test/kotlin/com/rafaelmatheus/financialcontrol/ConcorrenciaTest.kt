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
