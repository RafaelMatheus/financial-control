package com.rafaelmatheus.financialcontrol

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.transaction.annotation.Transactional
import javax.sql.DataSource

/**
 * Base dos testes de integracao.
 *
 * Roda contra PostgreSQL real via Testcontainers (RNF-06, NFR-U1-12) — e nao
 * contra H2. O motivo nao e purismo: o indice unico PARCIAL de `membro_grupo` e
 * PostgreSQL puro, e num banco em memoria a invariante que ele protege
 * simplesmente nao existiria. O teste passaria pelo motivo errado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
abstract class SuporteDeIntegracao {

    @Autowired protected lateinit var mvc: MockMvc
    @Autowired protected lateinit var json: ObjectMapper
    @Autowired private lateinit var dataSource: DataSource

    /**
     * Limpa as tabelas entre testes. TRUNCATE ... CASCADE em vez de @Transactional
     * com rollback: o teste de concorrencia precisa de commits de verdade, e um
     * rollback automatico esconderia justamente o que ele verifica.
     */
    @BeforeEach
    fun limparBanco() {
        dataSource.connection.use { conexao ->
            conexao.createStatement().use {
                it.execute("TRUNCATE TABLE membro_grupo, grupo, usuario CASCADE")
            }
        }
    }

    protected fun cadastrar(email: String, nome: String, senha: String = "senha-de-teste"): String {
        val resposta = mvc.perform(
            post("/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","senha":"$senha","nome":"$nome"}"""),
        ).andReturn().response.contentAsString
        return json.readTree(resposta).get("id").asText()
    }

    protected fun autenticar(email: String, senha: String = "senha-de-teste"): String {
        val resposta = mvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","senha":"$senha"}"""),
        ).andReturn().response.contentAsString
        return json.readTree(resposta).get("token").asText()
    }

    /** Cadastra, autentica e devolve o par (id, token). */
    protected fun usuarioAutenticado(email: String, nome: String): Pair<String, String> =
        cadastrar(email, nome) to autenticar(email)

    protected fun comToken(acao: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, token: String) =
        acao.header("Authorization", "Bearer $token")
}
