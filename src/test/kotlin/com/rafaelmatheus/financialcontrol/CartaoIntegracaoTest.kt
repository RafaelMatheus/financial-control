package com.rafaelmatheus.financialcontrol

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** H-18, H-19 — RN-K01 a RN-K04. */
class CartaoIntegracaoTest : SuporteDeIntegracao() {

    @Test
    fun `cadastra cartao pessoal`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")

        mvc.perform(
            comToken(post("/cartoes"), token).contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"apelido":"Nubank","diaFechamento":28,"diaVencimento":5,
                       "escopo":"PESSOAL","grupoId":null}""",
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.apelido").value("Nubank"))
            .andExpect(jsonPath("$.ativo").value(true))
    }

    @Test
    fun `aceita cartao que fecha dia 31 — RN-K01, D-69`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")

        // Recusar dias acima de 28 eliminaria o caso de borda ao custo de
        // recusar cartoes reais. A queda para o ultimo dia do mes acontece na
        // CalculadoraDeCompetencia, nao no cadastro.
        mvc.perform(
            comToken(post("/cartoes"), token).contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"apelido":"Inter","diaFechamento":31,"diaVencimento":10,
                       "escopo":"PESSOAL","grupoId":null}""",
                ),
        ).andExpect(status().isCreated)
    }

    @Test
    fun `recusa dia fora do intervalo`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")

        mvc.perform(
            comToken(post("/cartoes"), token).contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"apelido":"X","diaFechamento":32,"diaVencimento":5,
                       "escopo":"PESSOAL","grupoId":null}""",
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.codigo").value("DIA_INVALIDO"))
    }

    @Test
    fun `cartao de grupo e visivel a todos os membros — H-19, E-08`() {
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (idRafael, tokenRafael) = usuarioAutenticado("rafael@exemplo.com", "Rafael")
        val (_, tokenCarlos) = usuarioAutenticado("carlos@exemplo.com", "Carlos")

        val grupo = criarGrupoDe(tokenAna, "Apartamento 42")
        adicionarMembroAoGrupo(tokenAna, grupo, idRafael)

        criarCartao(tokenAna, "Nubank da casa", escopo = "GRUPO", grupoId = grupo)

        mvc.perform(comToken(get("/cartoes"), tokenRafael))
            .andExpect(jsonPath("$.length()").value(1))

        // Quem nao e membro nao ve — E-08.
        mvc.perform(comToken(get("/cartoes"), tokenCarlos))
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `escopo GRUPO de quem nao pertence ao grupo e recusado`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")

        mvc.perform(
            comToken(post("/cartoes"), token).contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"apelido":"X","diaFechamento":28,"diaVencimento":5,
                       "escopo":"GRUPO","grupoId":"${java.util.UUID.randomUUID()}"}""",
                ),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `cartao de outro usuario responde 404 e nao 403`() {
        val (_, tokenAna) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val (_, tokenCarlos) = usuarioAutenticado("carlos@exemplo.com", "Carlos")
        val cartao = criarCartao(tokenAna)

        mvc.perform(comToken(get("/cartoes/$cartao"), tokenCarlos))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.codigo").value("CARTAO_NAO_ENCONTRADO"))
    }

    @Test
    fun `encerrar nao apaga o cartao — RN-K04`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val cartao = criarCartao(token)

        mvc.perform(comToken(delete("/cartoes/$cartao"), token))
            .andExpect(status().isNoContent)

        // O historico permanece consultavel; o que muda e que nao recebe mais
        // lancamentos.
        mvc.perform(comToken(get("/cartoes/$cartao"), token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ativo").value(false))
    }

    @Test
    fun `encerrar duas vezes e idempotente`() {
        val (_, token) = usuarioAutenticado("ana@exemplo.com", "Ana")
        val cartao = criarCartao(token)

        mvc.perform(comToken(delete("/cartoes/$cartao"), token)).andExpect(status().isNoContent)
        mvc.perform(comToken(delete("/cartoes/$cartao"), token)).andExpect(status().isNoContent)
    }
}
