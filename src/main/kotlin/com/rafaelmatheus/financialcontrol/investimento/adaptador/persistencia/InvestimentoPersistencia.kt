package com.rafaelmatheus.financialcontrol.investimento.adaptador.persistencia

import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.common.seguranca.ContextoUsuario
import com.rafaelmatheus.financialcontrol.investimento.dominio.Aporte
import com.rafaelmatheus.financialcontrol.investimento.dominio.ObjetivoInvestimento
import com.rafaelmatheus.financialcontrol.investimento.dominio.ObjetivoRepositorio
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "objetivo_investimento")
class ObjetivoJpa(
    @Id @Column(name = "id", nullable = false) var id: UUID,
    @Column(name = "nome", nullable = false, length = 120) var nome: String,
    @Column(name = "meta", precision = 15, scale = 2) var meta: BigDecimal?,
    @Column(name = "prazo_alvo") var prazoAlvo: LocalDate?,
    /** PERSISTIDO: e fato declarado pelo usuario, nao soma de nada (D-82). */
    @Column(name = "saldo_atual", nullable = false, precision = 15, scale = 2) var saldoAtual: BigDecimal,
    @Column(name = "dono_id", nullable = false) var donoId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "escopo", nullable = false, length = 20) var escopo: Escopo,
    @Column(name = "grupo_id") var grupoId: UUID?,
    @Column(name = "criado_em", nullable = false) var criadoEm: Instant,
)

@Entity
@Table(name = "aporte")
class AporteJpa(
    @Id @Column(name = "id", nullable = false) var id: UUID,
    @Column(name = "objetivo_id", nullable = false) var objetivoId: UUID,
    @Column(name = "valor", nullable = false, precision = 15, scale = 2) var valor: BigDecimal,
    @Column(name = "data", nullable = false) var data: LocalDate,
    @Column(name = "dono_id", nullable = false) var donoId: UUID,
    @Column(name = "criado_em", nullable = false) var criadoEm: Instant,
)

private const val VISIVEL =
    "(o.donoId = :usuario or (o.escopo = com.rafaelmatheus.financialcontrol.common.dominio.Escopo.GRUPO and o.grupoId in :grupos))"

interface ObjetivoSpringData : JpaRepository<ObjetivoJpa, UUID> {

    @Query("select o from ObjetivoJpa o where o.id = :id and $VISIVEL")
    fun buscarVisivel(
        @Param("id") id: UUID,
        @Param("usuario") usuario: UUID,
        @Param("grupos") grupos: Collection<UUID>,
    ): ObjetivoJpa?

    @Query("select o from ObjetivoJpa o where $VISIVEL order by o.nome")
    fun listarVisiveis(
        @Param("usuario") usuario: UUID,
        @Param("grupos") grupos: Collection<UUID>,
    ): List<ObjetivoJpa>
}

interface AporteSpringData : JpaRepository<AporteJpa, UUID> {

    /** D-82: `SUM` na leitura. `totalAportado` nao e coluna. */
    @Query("select coalesce(sum(a.valor), 0) from AporteJpa a where a.objetivoId = :objetivo")
    fun somarDoObjetivo(@Param("objetivo") objetivo: UUID): BigDecimal

    /** RF-76, D-18: o aporte conta como gasto no balanco do consultante. */
    @Query(
        "select coalesce(sum(a.valor), 0) from AporteJpa a " +
            "where a.donoId = :dono and a.data between :de and :ate",
    )
    fun somarDoDono(
        @Param("dono") dono: UUID,
        @Param("de") de: LocalDate,
        @Param("ate") ate: LocalDate,
    ): BigDecimal

    fun deleteByObjetivoId(objetivoId: UUID)
}

@Repository
class ObjetivoRepositorioAdaptador(
    private val objetivos: ObjetivoSpringData,
    private val aportes: AporteSpringData,
    private val contexto: ContextoUsuario,
) : ObjetivoRepositorio {

    override fun buscarVisivel(id: UUID): ObjetivoInvestimento? {
        val c = contexto.criterio()
        return objetivos.buscarVisivel(id, c.usuarioAtual, c.gruposDoUsuario.ouNulo())?.paraDominio()
    }

    override fun listarVisiveis(): List<ObjetivoInvestimento> {
        val c = contexto.criterio()
        return objetivos.listarVisiveis(c.usuarioAtual, c.gruposDoUsuario.ouNulo())
            .map { it.paraDominio() }
    }

    override fun salvar(objetivo: ObjetivoInvestimento): ObjetivoInvestimento =
        objetivos.saveAndFlush(objetivo.paraJpa()).paraDominio()

    override fun excluir(id: UUID) {
        // Os aportes saem por CASCADE; o delete explicito mantem o contexto de
        // persistencia coerente na mesma transacao.
        aportes.deleteByObjetivoId(id)
        objetivos.deleteById(id)
    }

    override fun totalAportado(objetivoId: UUID): Dinheiro =
        Dinheiro.de(aportes.somarDoObjetivo(objetivoId))

    override fun salvarAporte(aporte: Aporte): Aporte =
        aportes.saveAndFlush(aporte.paraJpa()).paraDominio()

    override fun buscarAporte(id: UUID): Aporte? =
        aportes.findById(id).orElse(null)?.paraDominio()

    override fun excluirAporte(id: UUID) = aportes.deleteById(id)

    override fun somarAportesDoConsultante(de: LocalDate, ate: LocalDate): Dinheiro =
        Dinheiro.de(aportes.somarDoDono(contexto.usuarioAtual(), de, ate))
}

private fun Set<UUID>.ouNulo(): Collection<UUID> = ifEmpty { setOf(UUID(0L, 0L)) }

private fun ObjetivoInvestimento.paraJpa() = ObjetivoJpa(
    id, nome, meta?.valor, prazoAlvo, saldoAtual.valor, dono, escopo, grupo, criadoEm,
)

private fun ObjetivoJpa.paraDominio() = ObjetivoInvestimento(
    id, nome, meta?.let { Dinheiro.de(it) }, prazoAlvo, Dinheiro.de(saldoAtual),
    donoId, escopo, grupoId, criadoEm,
)

private fun Aporte.paraJpa() = AporteJpa(id, objetivo, valor.valor, data, dono, criadoEm)

private fun AporteJpa.paraDominio() =
    Aporte(id, objetivoId, Dinheiro.de(valor), data, donoId, criadoEm)
