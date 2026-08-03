package com.rafaelmatheus.financialcontrol.orcamento.adaptador.persistencia

import com.rafaelmatheus.financialcontrol.common.dominio.BaseDoRealizado
import com.rafaelmatheus.financialcontrol.common.dominio.Competencia
import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.common.seguranca.ContextoUsuario
import com.rafaelmatheus.financialcontrol.orcamento.dominio.Orcamento
import com.rafaelmatheus.financialcontrol.orcamento.dominio.OrcamentoDuplicado
import com.rafaelmatheus.financialcontrol.orcamento.dominio.OrcamentoRepositorio
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "orcamento")
class OrcamentoJpa(
    @Id @Column(name = "id", nullable = false) var id: UUID,
    @Column(name = "categoria_id", nullable = false) var categoriaId: UUID,
    @Column(name = "competencia", nullable = false, length = 7) var competencia: String,
    @Column(name = "valor_teto", nullable = false, precision = 15, scale = 2) var valorTeto: BigDecimal,
    @Enumerated(EnumType.STRING)
    @Column(name = "base", nullable = false, length = 20) var base: BaseDoRealizado,
    @Column(name = "dono_id", nullable = false) var donoId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "escopo", nullable = false, length = 20) var escopo: Escopo,
    @Column(name = "grupo_id") var grupoId: UUID?,
    @Column(name = "criado_em", nullable = false) var criadoEm: Instant,
)

private const val VISIVEL =
    "(o.donoId = :usuario or (o.escopo = com.rafaelmatheus.financialcontrol.common.dominio.Escopo.GRUPO and o.grupoId in :grupos))"

interface OrcamentoSpringData : JpaRepository<OrcamentoJpa, UUID> {

    @Query("select o from OrcamentoJpa o where o.id = :id and $VISIVEL")
    fun buscarVisivel(
        @Param("id") id: UUID,
        @Param("usuario") usuario: UUID,
        @Param("grupos") grupos: Collection<UUID>,
    ): OrcamentoJpa?

    @Query("select o from OrcamentoJpa o where $VISIVEL")
    fun listarVisiveis(
        @Param("usuario") usuario: UUID,
        @Param("grupos") grupos: Collection<UUID>,
    ): List<OrcamentoJpa>

    @Query("select o from OrcamentoJpa o where $VISIVEL and o.competencia = :competencia")
    fun listarDaCompetencia(
        @Param("usuario") usuario: UUID,
        @Param("grupos") grupos: Collection<UUID>,
        @Param("competencia") competencia: String,
    ): List<OrcamentoJpa>

    fun existsByCategoriaIdAndCompetenciaAndDonoIdAndEscopoAndGrupoId(
        categoriaId: UUID,
        competencia: String,
        donoId: UUID,
        escopo: Escopo,
        grupoId: UUID?,
    ): Boolean
}

@Repository
class OrcamentoRepositorioAdaptador(
    private val jpa: OrcamentoSpringData,
    private val contexto: ContextoUsuario,
) : OrcamentoRepositorio {

    override fun buscarVisivel(id: UUID): Orcamento? {
        val c = contexto.criterio()
        return jpa.buscarVisivel(id, c.usuarioAtual, c.gruposDoUsuario.ouNulo())?.paraDominio()
    }

    override fun listarVisiveis(): List<Orcamento> {
        val c = contexto.criterio()
        return jpa.listarVisiveis(c.usuarioAtual, c.gruposDoUsuario.ouNulo()).map { it.paraDominio() }
    }

    override fun listarDaCompetencia(competencia: Competencia): List<Orcamento> {
        val c = contexto.criterio()
        return jpa.listarDaCompetencia(c.usuarioAtual, c.gruposDoUsuario.ouNulo(), competencia.toString())
            .map { it.paraDominio() }
    }

    override fun existeParaCategoria(
        categoria: UUID,
        competencia: Competencia,
        dono: UUID,
        escopo: Escopo,
        grupo: UUID?,
    ): Boolean = jpa.existsByCategoriaIdAndCompetenciaAndDonoIdAndEscopoAndGrupoId(
        categoria, competencia.toString(), dono, escopo, grupo,
    )

    override fun salvar(orcamento: Orcamento): Orcamento =
        try {
            // saveAndFlush: a violacao da unicidade precisa aparecer aqui, e nao
            // no commit (licao de cd310cb).
            jpa.saveAndFlush(orcamento.paraJpa()).paraDominio()
        } catch (_: DataIntegrityViolationException) {
            throw OrcamentoDuplicado()
        }

    override fun excluir(id: UUID) = jpa.deleteById(id)
}

private fun Set<UUID>.ouNulo(): Collection<UUID> = ifEmpty { setOf(UUID(0L, 0L)) }

private fun Orcamento.paraJpa() = OrcamentoJpa(
    id, categoria, competencia.toString(), valorTeto.valor, base, dono, escopo, grupo, criadoEm,
)

private fun OrcamentoJpa.paraDominio() = Orcamento(
    id, categoriaId, Competencia.de(competencia), Dinheiro.de(valorTeto), base,
    donoId, escopo, grupoId, criadoEm,
)
