package com.rafaelmatheus.financialcontrol

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/** H-05 a H-08 — grupos e membros. */
class GrupoIntegracaoTest : SuporteDeIntegracao() {

    @Test
    fun `quem cria o grupo ja nasce membro dele`() {
        // Sem isto o grupo nasceria inacessivel ao proprio criador, por forca de
        // RN-G03. Nao e privilegio de criador — e alcancabilidade.
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val grupoId = criarGrupo(token, "Apartamento 42")

        mvc.perform(comToken(get("/grupos/$grupoId"), token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.membros.length()").value(1))
    }

    @Test
    fun `dois grupos podem ter o mesmo nome`() {
        // RN-G01: nao ha unicidade de nome.
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        criarGrupo(token, "Casa")
        criarGrupo(token, "Casa")

        mvc.perform(comToken(get("/grupos"), token))
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `usuario sem grupo recebe lista vazia, e isso e valido`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        mvc.perform(comToken(get("/grupos"), token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `qualquer membro renomeia — nao ha hierarquia`() {
        // RN-G02: quem cria nao tem privilegio sobre os demais.
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (idCarlos, tokenCarlos) = usuarioAutenticado("carlos@exemplo.com", "Carlos")

        val grupoId = criarGrupo(tokenAna, "Apartamento 42")
        adicionarMembro(tokenAna, grupoId, idCarlos)

        mvc.perform(
            comToken(put("/grupos/$grupoId"), tokenCarlos)
                .contentType(MediaType.APPLICATION_JSON).content("""{"nome":"Ap 42"}"""),
        ).andExpect(status().isOk).andExpect(jsonPath("$.nome").value("Ap 42"))
    }

    @Test
    fun `membro pode remover quem o adicionou`() {
        // RN-G02 levado ao limite: sem hierarquia significa sem hierarquia.
        val (idAna, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (idCarlos, tokenCarlos) = usuarioAutenticado("carlos@exemplo.com", "Carlos")

        val grupoId = criarGrupo(tokenAna, "Apartamento 42")
        adicionarMembro(tokenAna, grupoId, idCarlos)

        mvc.perform(comToken(delete("/grupos/$grupoId/membros/$idAna"), tokenCarlos))
            .andExpect(status().isNoContent)

        mvc.perform(comToken(get("/grupos/$grupoId"), tokenAna))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `adicionar usuario inexistente e erro de validacao`() {
        // E-07, RN-G04.
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val grupoId = criarGrupo(token, "Apartamento 42")

        mvc.perform(
            comToken(post("/grupos/$grupoId/membros"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"usuarioId":"${UUID.randomUUID()}"}"""),
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.codigo").value("USUARIO_NAO_ENCONTRADO"))
    }

    @Test
    fun `adicionar quem ja e membro ativo e recusado`() {
        // RN-G05.
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (idCarlos, _) = usuarioAutenticado("carlos@exemplo.com", "Carlos")

        val grupoId = criarGrupo(tokenAna, "Apartamento 42")
        adicionarMembro(tokenAna, grupoId, idCarlos)

        mvc.perform(
            comToken(post("/grupos/$grupoId/membros"), tokenAna)
                .contentType(MediaType.APPLICATION_JSON).content("""{"usuarioId":"$idCarlos"}"""),
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.codigo").value("JA_E_MEMBRO"))
    }

    @Test
    fun `nome de grupo vazio e recusado`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")

        mvc.perform(
            comToken(post("/grupos"), token)
                .contentType(MediaType.APPLICATION_JSON).content("""{"nome":"   "}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `nao-membro nao consegue adicionar ninguem`() {
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (idCarlos, tokenCarlos) = usuarioAutenticado("carlos@exemplo.com", "Carlos")

        val grupoId = criarGrupo(tokenAna, "Apartamento 42")

        mvc.perform(
            comToken(post("/grupos/$grupoId/membros"), tokenCarlos)
                .contentType(MediaType.APPLICATION_JSON).content("""{"usuarioId":"$idCarlos"}"""),
        ).andExpect(status().isNotFound)
    }

    private fun criarGrupo(token: String, nome: String): String {
        val resposta = mvc.perform(
            comToken(post("/grupos"), token)
                .contentType(MediaType.APPLICATION_JSON).content("""{"nome":"$nome"}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return json.readTree(resposta).get("id").asText()
    }

    private fun adicionarMembro(token: String, grupoId: String, usuarioId: String) {
        mvc.perform(
            comToken(post("/grupos/$grupoId/membros"), token)
                .contentType(MediaType.APPLICATION_JSON).content("""{"usuarioId":"$usuarioId"}"""),
        ).andExpect(status().isOk)
    }
}
