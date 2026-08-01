package com.rafaelmatheus.financialcontrol

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * H-03 — isolamento de dados entre usuarios (RF-03, RF-04, RNF-05, NFR-U1-04).
 *
 * **O teste mais importante de U1.** A unidade existe para que um usuario nao
 * alcance dado de outro; se este arquivo passar por acidente, o resto da suite
 * nao tem valor.
 *
 * Cobre os tres casos do gherkin de H-03, mais o corte total do ex-membro (D-44)
 * e a visibilidade retroativa de quem entra (D-13, E-10).
 */
class IsolamentoDeDadosTest : SuporteDeIntegracao() {

    @Test
    fun `quem nao e membro recebe 404 e nao 403`() {
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (_, tokenCarlos) = usuarioAutenticado("carlos@exemplo.com", "Carlos")

        val grupoId = criarGrupo(tokenAna, "Apartamento 42")

        // 403 confirmaria que o grupo existe, permitindo descobrir identificadores
        // validos por tentativa. Para quem nao e membro, o grupo nao existe.
        mvc.perform(comToken(get("/grupos/$grupoId"), tokenCarlos))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.codigo").value("GRUPO_NAO_ENCONTRADO"))
    }

    @Test
    fun `requisicao sem token e recusada`() {
        mvc.perform(get("/grupos"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"))
    }

    @Test
    fun `token de um usuario nao alcanca o perfil de outro`() {
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        usuarioAutenticado("carlos@exemplo.com", "Carlos")

        // Nao ha rota que aceite identificador de usuario — a regra esta na
        // assinatura (RN-U06). O perfil devolvido e sempre o do token.
        mvc.perform(comToken(get("/usuarios/eu"), tokenAna))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("ana@exemplo.com"))
    }

    @Test
    fun `listagem devolve apenas os grupos de que sou membro`() {
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (_, tokenCarlos) = usuarioAutenticado("carlos@exemplo.com", "Carlos")

        criarGrupo(tokenAna, "Apartamento 42")
        criarGrupo(tokenCarlos, "Republica")

        mvc.perform(comToken(get("/grupos"), tokenAna))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].nome").value("Apartamento 42"))
    }

    @Test
    fun `membro adicionado passa a enxergar o grupo`() {
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (idCarlos, tokenCarlos) = usuarioAutenticado("carlos@exemplo.com", "Carlos")

        val grupoId = criarGrupo(tokenAna, "Apartamento 42")
        adicionarMembro(tokenAna, grupoId, idCarlos)

        mvc.perform(comToken(get("/grupos/$grupoId"), tokenCarlos))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nome").value("Apartamento 42"))
    }

    @Test
    fun `ex-membro perde a visibilidade do grupo — corte total`() {
        // D-44: a visibilidade acompanha a associacao atual. Ao sair, perde-se o
        // acesso inclusive ao que se via antes.
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (idCarlos, tokenCarlos) = usuarioAutenticado("carlos@exemplo.com", "Carlos")

        val grupoId = criarGrupo(tokenAna, "Apartamento 42")
        adicionarMembro(tokenAna, grupoId, idCarlos)

        mvc.perform(comToken(get("/grupos/$grupoId"), tokenCarlos)).andExpect(status().isOk)

        mvc.perform(comToken(delete("/grupos/$grupoId/membros/eu"), tokenCarlos))
            .andExpect(status().isNoContent)

        // Mesma resposta de quem nunca foi membro: 404, nao 403.
        mvc.perform(comToken(get("/grupos/$grupoId"), tokenCarlos))
            .andExpect(status().isNotFound)

        mvc.perform(comToken(get("/grupos"), tokenCarlos))
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `quem sai pode ser readicionado e volta a enxergar`() {
        // D-45: reentrada cria linha nova; a associacao encerrada nao e reaberta.
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (idCarlos, tokenCarlos) = usuarioAutenticado("carlos@exemplo.com", "Carlos")

        val grupoId = criarGrupo(tokenAna, "Apartamento 42")
        adicionarMembro(tokenAna, grupoId, idCarlos)
        mvc.perform(comToken(delete("/grupos/$grupoId/membros/eu"), tokenCarlos))
            .andExpect(status().isNoContent)

        adicionarMembro(tokenAna, grupoId, idCarlos)

        mvc.perform(comToken(get("/grupos/$grupoId"), tokenCarlos))
            .andExpect(status().isOk)
    }

    @Test
    fun `grupo sem membros continua existindo e ninguem o enxerga`() {
        // RN-G08 e a consequencia registrada em business-rules.md §5: o grupo
        // fica permanentemente inacessivel. Nao e defeito — e a combinacao de
        // D-47 com "so membro opera" — mas precisa estar verificado.
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val grupoId = criarGrupo(tokenAna, "Apartamento 42")

        mvc.perform(comToken(delete("/grupos/$grupoId/membros/eu"), tokenAna))
            .andExpect(status().isNoContent)

        mvc.perform(comToken(get("/grupos/$grupoId"), tokenAna))
            .andExpect(status().isNotFound)
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
