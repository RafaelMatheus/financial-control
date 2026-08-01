package com.rafaelmatheus.financialcontrol.grupo.adaptador.persistencia

import com.rafaelmatheus.financialcontrol.common.seguranca.ConsultaDeGrupos
import com.rafaelmatheus.financialcontrol.grupo.dominio.AssociacaoAtivaDuplicada
import com.rafaelmatheus.financialcontrol.grupo.dominio.Grupo
import com.rafaelmatheus.financialcontrol.grupo.dominio.GrupoRepositorio
import com.rafaelmatheus.financialcontrol.grupo.dominio.MembroGrupo
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "grupo")
class GrupoJpa(
    @Id @Column(name = "id", nullable = false) var id: UUID,
    @Column(name = "nome", nullable = false, length = 120) var nome: String,
    @Column(name = "criado_em", nullable = false) var criadoEm: Instant,
)

@Entity
@Table(name = "membro_grupo")
class MembroGrupoJpa(
    @Id @Column(name = "id", nullable = false) var id: UUID,
    @Column(name = "grupo_id", nullable = false) var grupoId: UUID,
    @Column(name = "usuario_id", nullable = false) var usuarioId: UUID,
    @Column(name = "entrou_em", nullable = false) var entrouEm: Instant,
    @Column(name = "saiu_em") var saiuEm: Instant?,
)

interface GrupoSpringData : JpaRepository<GrupoJpa, UUID>

interface MembroGrupoSpringData : JpaRepository<MembroGrupoJpa, UUID> {

    fun findByGrupoIdAndUsuarioIdAndSaiuEmIsNull(grupoId: UUID, usuarioId: UUID): MembroGrupoJpa?

    fun findByGrupoIdAndSaiuEmIsNull(grupoId: UUID): List<MembroGrupoJpa>

    fun findByUsuarioIdAndSaiuEmIsNull(usuarioId: UUID): List<MembroGrupoJpa>

    @Query(
        """
        select g from GrupoJpa g
        where g.id in (
            select m.grupoId from MembroGrupoJpa m
            where m.usuarioId = :usuarioId and m.saiuEm is null
        )
        order by g.nome
        """,
    )
    fun gruposDoUsuario(@Param("usuarioId") usuarioId: UUID): List<GrupoJpa>
}

@Repository
class GrupoRepositorioAdaptador(
    private val grupos: GrupoSpringData,
    private val membros: MembroGrupoSpringData,
) : GrupoRepositorio, ConsultaDeGrupos {

    override fun salvar(grupo: Grupo): Grupo = grupos.save(grupo.paraJpa()).paraDominio()

    override fun buscarPorId(id: UUID): Grupo? = grupos.findById(id).orElse(null)?.paraDominio()

    override fun salvarMembro(membro: MembroGrupo): MembroGrupo =
        try {
            membros.save(membro.paraJpa()).paraDominio()
        } catch (_: DataIntegrityViolationException) {
            // Indice unico parcial em (grupo_id, usuario_id) where saiu_em is null.
            // E a unica coisa que barra duas requisicoes simultaneas de adicionar.
            throw AssociacaoAtivaDuplicada()
        }

    override fun buscarAssociacaoAtiva(grupoId: UUID, usuarioId: UUID): MembroGrupo? =
        membros.findByGrupoIdAndUsuarioIdAndSaiuEmIsNull(grupoId, usuarioId)?.paraDominio()

    override fun membrosAtivosDe(grupoId: UUID): List<MembroGrupo> =
        membros.findByGrupoIdAndSaiuEmIsNull(grupoId).map { it.paraDominio() }

    /**
     * Implementa tambem a porta [ConsultaDeGrupos], consumida por
     * `ContextoUsuario`. E assim que `common` descobre os grupos sem importar
     * `grupo` — a seta de dependencia continua apontando para dentro.
     *
     * Apenas associacoes ativas: e onde D-44 (corte total ao sair) vive.
     */
    override fun gruposAtivosDe(usuarioId: UUID): Set<UUID> =
        membros.findByUsuarioIdAndSaiuEmIsNull(usuarioId).map { it.grupoId }.toSet()

    override fun listarGruposDe(usuarioId: UUID): List<Grupo> =
        membros.gruposDoUsuario(usuarioId).map { it.paraDominio() }
}

private fun Grupo.paraJpa() = GrupoJpa(id = id, nome = nome, criadoEm = criadoEm)

private fun GrupoJpa.paraDominio() = Grupo(id = id, nome = nome, criadoEm = criadoEm)

private fun MembroGrupo.paraJpa() = MembroGrupoJpa(
    id = id, grupoId = grupoId, usuarioId = usuarioId, entrouEm = entrouEm, saiuEm = saiuEm,
)

private fun MembroGrupoJpa.paraDominio() = MembroGrupo(
    id = id, grupoId = grupoId, usuarioId = usuarioId, entrouEm = entrouEm, saiuEm = saiuEm,
)
