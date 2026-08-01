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
    @Autowired private lateinit var tentativas: com.rafaelmatheus.financialcontrol.common.seguranca.RegistroDeTentativas

    /**
     * Limpa as tabelas entre testes. TRUNCATE ... CASCADE em vez de @Transactional
     * com rollback: o teste de concorrencia precisa de commits de verdade, e um
     * rollback automatico esconderia justamente o que ele verifica.
     */
    /**
     * O RegistroDeTentativas e singleton com estado EM MEMORIA — TRUNCATE nao o
     * alcanca. Sem esta limpeza, o teste de bloqueio deixa a conta travada por 15
     * minutos e derruba todo teste seguinte que use o mesmo e-mail.
     *
     * E a mesma propriedade que faria o bloqueio quebrar com duas instancias
     * (nfr-design-patterns.md §4): o unico componente com estado da unidade.
     */
    @BeforeEach
    fun limparBanco() {
        tentativas.limparTudo()
        dataSource.connection.use { conexao ->
            conexao.createStatement().use {
                it.execute("TRUNCATE TABLE gasto, categoria, membro_grupo, grupo, usuario CASCADE")
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

    // --- U2 Lancamentos ---

    protected fun criarGrupoDe(token: String, nome: String): String {
        val resposta = mvc.perform(
            comToken(post("/grupos"), token).contentType(MediaType.APPLICATION_JSON)
                .content("""{"nome":"$nome"}"""),
        ).andReturn().response.contentAsString
        return json.readTree(resposta).get("id").asText()
    }

    protected fun adicionarMembroAoGrupo(token: String, grupoId: String, usuarioId: String) {
        mvc.perform(
            comToken(post("/grupos/$grupoId/membros"), token).contentType(MediaType.APPLICATION_JSON)
                .content("""{"usuarioId":"$usuarioId"}"""),
        ).andReturn()
    }

    protected fun criarCategoria(
        token: String,
        nome: String,
        escopo: String = "PESSOAL",
        grupoId: String? = null,
    ): String {
        val grupo = if (grupoId == null) "null" else "\"$grupoId\""
        val resposta = mvc.perform(
            comToken(post("/categorias"), token).contentType(MediaType.APPLICATION_JSON)
                .content("""{"nome":"$nome","escopo":"$escopo","grupoId":$grupo}"""),
        ).andReturn().response.contentAsString
        return json.readTree(resposta).get("id").asText()
    }

    protected fun lancarGasto(
        token: String,
        categoriaId: String,
        valor: String,
        data: String = "2026-08-10",
        escopo: String = "PESSOAL",
        grupoId: String? = null,
        descricao: String = "Compra",
    ): ResultActions {
        val grupo = if (grupoId == null) "null" else "\"$grupoId\""
        return mvc.perform(
            comToken(post("/gastos"), token).contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"descricao":"$descricao","valor":$valor,"data":"$data",
                       "categoriaId":"$categoriaId","escopo":"$escopo","grupoId":$grupo}""",
                ),
        )
    }

    protected fun idDe(acao: ResultActions): String =
        json.readTree(acao.andReturn().response.contentAsString).get("id").asText()

    protected fun consultarGastos(
        token: String,
        de: String = "2026-08-01",
        ate: String = "2026-08-31",
        extra: String = "",
    ): ResultActions = mvc.perform(comToken(get("/gastos?de=$de&ate=$ate$extra"), token))

    protected fun totais(
        token: String,
        de: String = "2026-08-01",
        ate: String = "2026-08-31",
        extra: String = "",
    ): ResultActions = mvc.perform(comToken(get("/gastos/totais?de=$de&ate=$ate$extra"), token))
}
