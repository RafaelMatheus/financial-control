package com.rafaelmatheus.financialcontrol.grupo.dominio

import java.time.Instant
import java.util.UUID

/**
 * Colecao nomeada de pessoas que compartilham visibilidade (RF-06 a RF-10).
 *
 * **Sem `criadorId`, de proposito.** RF-06 e H-05 sao explicitos: quem cria nao
 * recebe privilegio algum sobre os demais. Guardar o criador criaria um campo que
 * so serviria para, mais tarde, alguem decidir usa-lo como autoridade —
 * reintroduzindo por acidente a hierarquia que o requisito nega.
 *
 * O nome **nao** e unico: duas casas podem se chamar "Apartamento 42".
 */
data class Grupo(
    val id: UUID,
    val nome: String,
    val criadoEm: Instant,
) {
    init {
        require(nome.isNotBlank()) { "Nome do grupo nao pode ser vazio" }
    }

    fun comNome(novoNome: String): Grupo {
        require(novoNome.isNotBlank()) { "Nome do grupo nao pode ser vazio" }
        return copy(nome = novoNome.trim())
    }

    companion object {
        fun novo(nome: String, criadoEm: Instant) =
            Grupo(id = UUID.randomUUID(), nome = nome.trim(), criadoEm = criadoEm)
    }
}

/**
 * Associacao entre usuario e grupo, com historico.
 *
 * `saiuEm == null` significa **ativa**. Reentrada cria uma **linha nova** (D-45):
 * a tabela e um historico de participacoes, nao um estado atual. Uma associacao
 * encerrada nunca e reaberta.
 */
data class MembroGrupo(
    val id: UUID,
    val grupoId: UUID,
    val usuarioId: UUID,
    val entrouEm: Instant,
    val saiuEm: Instant? = null,
) {
    init {
        require(saiuEm == null || !saiuEm.isBefore(entrouEm)) {
            "Saida nao pode ser anterior a entrada"
        }
    }

    fun estaAtiva(): Boolean = saiuEm == null

    fun encerrar(quando: Instant): MembroGrupo {
        check(estaAtiva()) { "Associacao ja encerrada" }
        return copy(saiuEm = quando)
    }

    companion object {
        fun nova(grupoId: UUID, usuarioId: UUID, entrouEm: Instant) = MembroGrupo(
            id = UUID.randomUUID(),
            grupoId = grupoId,
            usuarioId = usuarioId,
            entrouEm = entrouEm,
        )
    }
}

/** Grupo com seus membros ativos, para a consulta de detalhe. */
data class GrupoComMembros(val grupo: Grupo, val membrosAtivos: List<MembroGrupo>)

interface GrupoRepositorio {
    fun salvar(grupo: Grupo): Grupo
    fun buscarPorId(id: UUID): Grupo?

    fun salvarMembro(membro: MembroGrupo): MembroGrupo
    fun buscarAssociacaoAtiva(grupoId: UUID, usuarioId: UUID): MembroGrupo?
    fun membrosAtivosDe(grupoId: UUID): List<MembroGrupo>
    fun gruposAtivosDe(usuarioId: UUID): Set<UUID>
    fun listarGruposDe(usuarioId: UUID): List<Grupo>
}

/** Violacao da restricao de associacao ativa unica, vinda do banco (RN-G05). */
class AssociacaoAtivaDuplicada : RuntimeException("Usuario ja e membro ativo do grupo")
