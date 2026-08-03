package com.rafaelmatheus.financialcontrol.receita.adaptador.persistencia

import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.seguranca.ContextoUsuario
import com.rafaelmatheus.financialcontrol.receita.dominio.Receita
import com.rafaelmatheus.financialcontrol.receita.dominio.ReceitaRepositorio
import jakarta.persistence.Column
import jakarta.persistence.Entity
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
@Table(name = "receita")
class ReceitaJpa(
    @Id @Column(name = "id", nullable = false) var id: UUID,
    @Column(name = "descricao", nullable = false, length = 200) var descricao: String,
    @Column(name = "valor", nullable = false, precision = 15, scale = 2) var valor: BigDecimal,
    @Column(name = "data", nullable = false) var data: LocalDate,
    @Column(name = "dono_id", nullable = false) var donoId: UUID,
    @Column(name = "criado_em", nullable = false) var criadoEm: Instant,
)

interface ReceitaSpringData : JpaRepository<ReceitaJpa, UUID> {

    fun findByIdAndDonoId(id: UUID, donoId: UUID): ReceitaJpa?

    fun findByDonoIdOrderByDataDesc(donoId: UUID): List<ReceitaJpa>

    fun findByDonoIdAndDataBetweenOrderByData(
        donoId: UUID,
        de: LocalDate,
        ate: LocalDate,
    ): List<ReceitaJpa>

    @Query(
        "select coalesce(sum(r.valor), 0) from ReceitaJpa r " +
            "where r.donoId = :dono and r.data between :de and :ate",
    )
    fun somar(
        @Param("dono") dono: UUID,
        @Param("de") de: LocalDate,
        @Param("ate") ate: LocalDate,
    ): BigDecimal
}

/**
 * Adaptador de `receita`.
 *
 * **O predicado de RN-V01 aqui e so a primeira metade**: `dono == usuarioAtual`.
 * Nao ha a segunda, porque receita nao tem escopo (P-05, RN-RC02).
 *
 * E o primeiro caso do sistema em que essa metade roda sozinha — em U1, U2 e U3,
 * toda entidade com dono tambem tinha escopo, e o OU sempre estava completo. Um
 * defeito que so aparecesse com o lado direito ausente teria passado despercebido
 * por tres unidades.
 */
@Repository
class ReceitaRepositorioAdaptador(
    private val jpa: ReceitaSpringData,
    private val contexto: ContextoUsuario,
) : ReceitaRepositorio {

    override fun buscarVisivel(id: UUID): Receita? =
        jpa.findByIdAndDonoId(id, contexto.usuarioAtual())?.paraDominio()

    override fun listarVisiveis(): List<Receita> =
        jpa.findByDonoIdOrderByDataDesc(contexto.usuarioAtual()).map { it.paraDominio() }

    override fun listarPorPeriodo(de: LocalDate, ate: LocalDate): List<Receita> =
        jpa.findByDonoIdAndDataBetweenOrderByData(contexto.usuarioAtual(), de, ate)
            .map { it.paraDominio() }

    override fun somarPeriodo(de: LocalDate, ate: LocalDate): Dinheiro =
        Dinheiro.de(jpa.somar(contexto.usuarioAtual(), de, ate))

    override fun salvar(receita: Receita): Receita = jpa.saveAndFlush(receita.paraJpa()).paraDominio()

    override fun excluir(id: UUID) = jpa.deleteById(id)
}

private fun Receita.paraJpa() = ReceitaJpa(id, descricao, valor.valor, data, dono, criadoEm)

private fun ReceitaJpa.paraDominio() =
    Receita(id, descricao, Dinheiro.de(valor), data, donoId, criadoEm)
