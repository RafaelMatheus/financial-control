package com.rafaelmatheus.financialcontrol

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** H-01, H-02, H-04 — cadastro, autenticacao e perfil. */
class UsuarioIntegracaoTest : SuporteDeIntegracao() {

    @Test
    fun `cadastro devolve o perfil sem jamais expor a credencial`() {
        val resposta = mvc.perform(
            post("/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"ana@exemplo.com","senha":"senha-de-teste","nome":"Ana"}"""),
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.email").value("ana@exemplo.com"))
            .andReturn().response.contentAsString

        // RN-U03: nem a senha, nem o hash, em nenhum campo da resposta.
        assert(!resposta.contains("senha", ignoreCase = true)) {
            "A resposta de cadastro nao pode conter nada parecido com senha: $resposta"
        }
    }

    @Test
    fun `email e normalizado — maiusculas e espacos nao criam conta nova`() {
        // RN-U01, D-46.
        cadastrar("ana@exemplo.com", "Ana")

        mvc.perform(
            post("/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"  Ana@Exemplo.COM  ","senha":"outra-senha","nome":"Outra"}"""),
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.codigo").value("EMAIL_JA_CADASTRADO"))
    }

    @Test
    fun `login com a forma nao normalizada do email funciona`() {
        cadastrar("ana@exemplo.com", "Ana")

        mvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"ANA@exemplo.com","senha":"senha-de-teste"}"""),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isNotEmpty)
    }

    @Test
    fun `email em formato invalido e recusado`() {
        mvc.perform(
            post("/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"nao-e-email","senha":"senha-de-teste","nome":"Ana"}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `senha errada e email inexistente devolvem exatamente a mesma resposta`() {
        // RN-U04: a resposta nao pode revelar se o e-mail existe.
        cadastrar("ana@exemplo.com", "Ana")

        val comEmailReal = mvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"ana@exemplo.com","senha":"senha-errada"}"""),
        ).andExpect(status().isUnauthorized).andReturn().response.contentAsString

        val comEmailInexistente = mvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"ninguem@exemplo.com","senha":"senha-errada"}"""),
        ).andExpect(status().isUnauthorized).andReturn().response.contentAsString

        val semCorrelacao = { corpo: String -> json.readTree(corpo).let { "${it["codigo"]}${it["mensagem"]}" } }
        assert(semCorrelacao(comEmailReal) == semCorrelacao(comEmailInexistente)) {
            "As respostas precisam ser indistinguiveis: $comEmailReal vs $comEmailInexistente"
        }
    }

    @Test
    fun `conta bloqueia apos cinco falhas e responde como senha errada`() {
        // NFR-U1-03. Dizer "conta bloqueada" confirmaria a existencia da conta
        // justamente para quem tenta adivinhar a senha dela.
        cadastrar("ana@exemplo.com", "Ana")

        repeat(5) {
            mvc.perform(
                post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"ana@exemplo.com","senha":"errada"}"""),
            ).andExpect(status().isUnauthorized)
        }

        // Agora bloqueada: mesmo com a senha CERTA, recusa — e com o mesmo codigo.
        mvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"ana@exemplo.com","senha":"senha-de-teste"}"""),
        ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"))
    }

    @Test
    fun `perfil so e alcancado com token e so devolve o proprio`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")

        mvc.perform(get("/usuarios/eu")).andExpect(status().isUnauthorized)

        mvc.perform(comToken(get("/usuarios/eu"), token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nome").value("Ana"))
    }

    @Test
    fun `atualizar perfil muda apenas o nome`() {
        // RN-U07.
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")

        mvc.perform(
            comToken(put("/usuarios/eu"), token)
                .contentType(MediaType.APPLICATION_JSON).content("""{"nome":"Ana Maria"}"""),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.nome").value("Ana Maria"))
            .andExpect(jsonPath("$.email").value("ana@exemplo.com"))
    }

    @Test
    fun `nome vazio no perfil e recusado`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")

        mvc.perform(
            comToken(put("/usuarios/eu"), token)
                .contentType(MediaType.APPLICATION_JSON).content("""{"nome":"   "}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `token invalido nao autentica`() {
        mvc.perform(get("/usuarios/eu").header("Authorization", "Bearer isto.nao.e.um.token"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `health responde sem autenticacao`() {
        // Se isto quebrar, o healthcheck do container e do nginx quebra junto —
        // e com ele o deploy.
        mvc.perform(get("/actuator/health")).andExpect(status().isOk)
    }
}
