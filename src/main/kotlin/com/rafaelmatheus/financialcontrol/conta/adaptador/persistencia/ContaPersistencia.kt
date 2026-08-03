package com.rafaelmatheus.financialcontrol.conta.adaptador.persistencia

import com.rafaelmatheus.financialcontrol.common.dominio.Competencia
import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.common.seguranca.ContextoUsuario
import com.rafaelmatheus.financialcontrol.conta.dominio.ContaAPagar
import com.rafaelmatheus.financialcontrol.conta.dominio.ContaRecorrente
import com.rafaelmatheus.financialcontrol.conta.dominio.ContaRepositorio
import com.rafaelmatheus.financialcontrol.conta.dominio.Frequencia
import com.rafaelmatheus.financialcontrol.conta.dominio.OcorrenciaJaMaterializada
import com.rafaelmatheus.financialcontrol.conta.dominio.RecorrenteRepositorio
import com.rafaelmatheus.financialcontrol.conta.dominio.StatusConta
import com.rafaelmatheus.financialcontrol.conta.dominio.TipoConta
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
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "conta_a_pagar")
class ContaJpa(
    @Id @Column(name = "id", nullable = false) var id: UUID,
    @Column(name = "descricao", nullable = false, length = 200) var descricao: String,
    @Column(name = "valor", nullable = false, precision = 15, scale = 2) var valor: BigDecimal,
    @Column(name = "data_vencimento", nullable = false) var dataVencimento: LocalDate,
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30) var tipo: TipoConta,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20) var status: StatusConta,
    @Column(name = "data_pagamento") var dataPagamento: LocalDate?,
    @Column(name = "categoria_id") var categoriaId: UUID?,
    @Column(name = "dono_id", nullable = false) var donoId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "escopo", nullable = false, length = 20) var escopo: Escopo,
    @Column(name = "grupo_id") var grupoId: UUID?,
    @Column(name = "origem_fatura_id") var origemFaturaId: UUID?,
    @Column(name = "origem_recorrente_id") var origemRecorrenteId: UUID?,
    @Column(name = "competencia_recorrencia", length = 7) var competenciaRecorrencia: String?,
    @Column(name = "criado_em", nullable = false) var criadoEm: Instant,
)

@Entity
@Table(name = "conta_recorrente")
class RecorrenteJpa(
    @Id @Column(name = "id", nullable = false) var id: UUID,
    @Column(name = "descricao", nullable = false, length = 200) var descricao: String,
    @Column(name = "valor_base", nullable = false, precision = 15, scale = 2) var valorBase: BigDecimal,
    @Column(name = "dia_vencimento", nullable = false) var diaVencimento: Int,
    @Enumerated(EnumType.STRING)
    @Column(name = "frequencia", nullable = false, length = 20) var frequencia: Frequencia,
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30) var tipo: TipoConta,
    @Column(name = "categoria_id", nullable = false) var categoriaId: UUID,
    @Column(name = "dono_id", nullable = false) var donoId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "escopo", nullable = false, length = 20) var escopo: Escopo,
    @Column(name = "grupo_id") var grupoId: UUID?,
    @Column(name = "inicio_em", nullable = false, length = 7) var inicioEm: String,
    @Column(name = "encerrada_em", length = 7) var encerradaEm: String?,
    @Column(name = "criado_em", nullable = false) var criadoEm: Instant,
)

private const val VISIVEL_CONTA =
    "(c.donoId = :usuario or (c.escopo = com.rafaelmatheus.financialcontrol.common.dominio.Escopo.GRUPO and c.grupoId in :grupos))"

interface ContaSpringData : JpaRepository<ContaJpa, UUID> {

    @Query("select c from ContaJpa c where c.id = :id and $VISIVEL_CONTA")
    fun buscarVisivel(
        @Param("id") id: UUID,
        @Param("usuario") usuario: UUID,
        @Param("grupos") grupos: Collection<UUID>,
    ): ContaJpa?

    @Query("select c from ContaJpa c where $VISIVEL_CONTA order by c.dataVencimento")
    fun listarVisiveis(
        @Param("usuario") usuario: UUID,
        @Param("grupos") grupos: Collection<UUID>,
    ): List<ContaJpa>

    @Query(
        """
        select c from ContaJpa c
        where $VISIVEL_CONTA and c.dataVencimento between :de and :ate
        order by c.dataVencimento
        """,
    )
    fun listarPorVencimento(
        @Param("usuario") usuario: UUID,
        @Param("grupos") grupos: Collection<UUID>,
        @Param("de") de: LocalDate,
        @Param("ate") ate: LocalDate,
    ): List<ContaJpa>

    fun findByOrigemRecorrenteIdAndCompetenciaRecorrencia(
        recorrenteId: UUID,
        competencia: String,
    ): ContaJpa?
}

private const val VISIVEL_REC =
    "(r.donoId = :usuario or (r.escopo = com.rafaelmatheus.financialcontrol.common.dominio.Escopo.GRUPO and r.grupoId in :grupos))"

interface RecorrenteSpringData : JpaRepository<RecorrenteJpa, UUID> {

    @Query("select r from RecorrenteJpa r where r.id = :id and $VISIVEL_REC")
    fun buscarVisivel(
        @Param("id") id: UUID,
        @Param("usuario") usuario: UUID,
        @Param("grupos") grupos: Collection<UUID>,
    ): RecorrenteJpa?

    @Query("select r from RecorrenteJpa r where $VISIVEL_REC order by r.descricao")
    fun listarVisiveis(
        @Param("usuario") usuario: UUID,
        @Param("grupos") grupos: Collection<UUID>,
    ): List<RecorrenteJpa>
}

@Repository
class ContaRepositorioAdaptador(
    private val jpa: ContaSpringData,
    private val contexto: ContextoUsuario,
) : ContaRepositorio {

    override fun buscarVisivel(id: UUID): ContaAPagar? {
        val c = contexto.criterio()
        return jpa.buscarVisivel(id, c.usuarioAtual, c.gruposDoUsuario.ouNulo())?.paraDominio()
    }

    override fun listarVisiveis(): List<ContaAPagar> {
        val c = contexto.criterio()
        return jpa.listarVisiveis(c.usuarioAtual, c.gruposDoUsuario.ouNulo()).map { it.paraDominio() }
    }

    override fun listarPorVencimento(de: LocalDate, ate: LocalDate): List<ContaAPagar> {
        val c = contexto.criterio()
        return jpa.listarPorVencimento(c.usuarioAtual, c.gruposDoUsuario.ouNulo(), de, ate)
            .map { it.paraDominio() }
    }

    override fun ocorrenciasMaterializadas(de: LocalDate, ate: LocalDate): List<ContaAPagar> =
        listarPorVencimento(de, ate).filter { it.origemRecorrente != null }

    override fun buscarOcorrencia(recorrenteId: UUID, competencia: Competencia): ContaAPagar? =
        jpa.findByOrigemRecorrenteIdAndCompetenciaRecorrencia(recorrenteId, competencia.toString())
            ?.paraDominio()

    override fun salvar(conta: ContaAPagar): ContaAPagar =
        try {
            // saveAndFlush: a violacao do indice unico parcial de ocorrencia
            // precisa aparecer aqui, e nao no commit (licao de cd310cb).
            jpa.saveAndFlush(conta.paraJpa()).paraDominio()
        } catch (_: DataIntegrityViolationException) {
            throw OcorrenciaJaMaterializada()
        }

    /** ⚠️ Sem contexto de usuario: uso exclusivo do job (D-71). */
    override fun salvarSemContexto(conta: ContaAPagar): ContaAPagar =
        jpa.saveAndFlush(conta.paraJpa()).paraDominio()

    override fun excluir(id: UUID) = jpa.deleteById(id)
}

@Repository
class RecorrenteRepositorioAdaptador(
    private val jpa: RecorrenteSpringData,
    private val contexto: ContextoUsuario,
) : RecorrenteRepositorio {

    override fun buscarVisivel(id: UUID): ContaRecorrente? {
        val c = contexto.criterio()
        return jpa.buscarVisivel(id, c.usuarioAtual, c.gruposDoUsuario.ouNulo())?.paraDominio()
    }

    override fun listarVisiveis(): List<ContaRecorrente> {
        val c = contexto.criterio()
        return jpa.listarVisiveis(c.usuarioAtual, c.gruposDoUsuario.ouNulo()).map { it.paraDominio() }
    }

    override fun salvar(recorrente: ContaRecorrente): ContaRecorrente =
        jpa.saveAndFlush(recorrente.paraJpa()).paraDominio()
}

private fun Set<UUID>.ouNulo(): Collection<UUID> = ifEmpty { setOf(UUID(0L, 0L)) }

private fun ContaAPagar.paraJpa() = ContaJpa(
    id = id,
    descricao = descricao,
    valor = valor.valor,
    dataVencimento = dataVencimento,
    tipo = tipo,
    status = status,
    dataPagamento = dataPagamento,
    categoriaId = categoria,
    donoId = dono,
    escopo = escopo,
    grupoId = grupo,
    origemFaturaId = origemFatura,
    origemRecorrenteId = origemRecorrente,
    competenciaRecorrencia = competenciaRecorrencia?.toString(),
    criadoEm = criadoEm,
)

private fun ContaJpa.paraDominio() = ContaAPagar(
    id = id,
    descricao = descricao,
    valor = Dinheiro.de(valor),
    dataVencimento = dataVencimento,
    tipo = tipo,
    status = status,
    dataPagamento = dataPagamento,
    categoria = categoriaId,
    dono = donoId,
    escopo = escopo,
    grupo = grupoId,
    origemFatura = origemFaturaId,
    origemRecorrente = origemRecorrenteId,
    competenciaRecorrencia = competenciaRecorrencia?.let { Competencia.de(it) },
    criadoEm = criadoEm,
)

private fun ContaRecorrente.paraJpa() = RecorrenteJpa(
    id = id,
    descricao = descricao,
    valorBase = valorBase.valor,
    diaVencimento = diaVencimento,
    frequencia = frequencia,
    tipo = tipo,
    categoriaId = categoria,
    donoId = dono,
    escopo = escopo,
    grupoId = grupo,
    inicioEm = inicioEm.toString(),
    encerradaEm = encerradaEm?.toString(),
    criadoEm = criadoEm,
)

private fun RecorrenteJpa.paraDominio() = ContaRecorrente(
    id = id,
    descricao = descricao,
    valorBase = Dinheiro.de(valorBase),
    diaVencimento = diaVencimento,
    frequencia = frequencia,
    tipo = tipo,
    categoria = categoriaId,
    dono = donoId,
    escopo = escopo,
    grupo = grupoId,
    inicioEm = Competencia.de(inicioEm),
    encerradaEm = encerradaEm?.let { Competencia.de(it) },
    criadoEm = criadoEm,
)
