package com.rafaelmatheus.financialcontrol.compra.adaptador.persistencia

import com.rafaelmatheus.financialcontrol.common.dominio.Competencia
import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.common.seguranca.ContextoUsuario
import com.rafaelmatheus.financialcontrol.compra.dominio.Compra
import com.rafaelmatheus.financialcontrol.compra.dominio.CompraComParcelas
import com.rafaelmatheus.financialcontrol.compra.dominio.CompraRepositorio
import com.rafaelmatheus.financialcontrol.compra.dominio.Parcela
import com.rafaelmatheus.financialcontrol.fatura.dominio.ProtecaoFatura
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
@Table(name = "compra")
class CompraJpa(
    @Id @Column(name = "id", nullable = false) var id: UUID,
    @Column(name = "descricao", nullable = false, length = 200) var descricao: String,
    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2) var valorTotal: BigDecimal,
    @Column(name = "numero_parcelas", nullable = false) var numeroParcelas: Int,
    @Column(name = "data_compra", nullable = false) var dataCompra: LocalDate,
    @Column(name = "cartao_id", nullable = false) var cartaoId: UUID,
    @Column(name = "categoria_id", nullable = false) var categoriaId: UUID,
    @Column(name = "dono_id", nullable = false) var donoId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "escopo", nullable = false, length = 20) var escopo: Escopo,
    @Column(name = "grupo_id") var grupoId: UUID?,
    @Column(name = "criado_em", nullable = false) var criadoEm: Instant,
)

@Entity
@Table(name = "parcela")
class ParcelaJpa(
    @Id @Column(name = "id", nullable = false) var id: UUID,
    @Column(name = "compra_id", nullable = false) var compraId: UUID,
    @Column(name = "numero", nullable = false) var numero: Int,
    @Column(name = "valor", nullable = false, precision = 15, scale = 2) var valor: BigDecimal,
    @Column(name = "competencia", nullable = false, length = 7) var competencia: String,
)

interface CompraSpringData : JpaRepository<CompraJpa, UUID> {

    @Query(
        """
        select c from CompraJpa c
        where c.id = :id
          and (c.donoId = :usuario
               or (c.escopo = com.rafaelmatheus.financialcontrol.common.dominio.Escopo.GRUPO
                   and c.grupoId in :grupos))
        """,
    )
    fun buscarVisivel(
        @Param("id") id: UUID,
        @Param("usuario") usuario: UUID,
        @Param("grupos") grupos: Collection<UUID>,
    ): CompraJpa?

    @Query(
        """
        select c from CompraJpa c
        where c.donoId = :usuario
           or (c.escopo = com.rafaelmatheus.financialcontrol.common.dominio.Escopo.GRUPO
               and c.grupoId in :grupos)
        order by c.dataCompra desc
        """,
    )
    fun listarVisiveis(
        @Param("usuario") usuario: UUID,
        @Param("grupos") grupos: Collection<UUID>,
    ): List<CompraJpa>
}

interface ParcelaSpringData : JpaRepository<ParcelaJpa, UUID> {
    fun findByCompraId(compraId: UUID): List<ParcelaJpa>
    fun deleteByCompraId(compraId: UUID)
}

@Repository
class CompraRepositorioAdaptador(
    private val compras: CompraSpringData,
    private val parcelas: ParcelaSpringData,
    private val contexto: ContextoUsuario,
    private val protecao: ProtecaoFatura,
) : CompraRepositorio {

    override fun buscarVisivel(id: UUID): Compra? {
        val c = contexto.criterio()
        return compras.buscarVisivel(id, c.usuarioAtual, c.gruposDoUsuario.ouNulo())?.paraDominio()
    }

    override fun listarVisiveis(): List<Compra> {
        val c = contexto.criterio()
        return compras.listarVisiveis(c.usuarioAtual, c.gruposDoUsuario.ouNulo())
            .map { it.paraDominio() }
    }

    /**
     * 🔒 **A guarda de D-73.**
     *
     * A verificacao acontece aqui, no adaptador, alem de no servico. No servico
     * ela existe para dar mensagem acionavel; aqui, para **impedir a gravacao**.
     * Esquecer no servico produz mensagem ruim; esquecer aqui nao e possivel,
     * porque toda gravacao de compra passa por este metodo.
     *
     * A construcao de [CompraComParcelas] verifica a invariante de RF-32 —
     * `soma(parcelas) == valorTotal` — antes de qualquer coisa ir ao banco.
     */
    override fun salvar(compra: Compra, parcelas: List<Parcela>): CompraComParcelas {
        val resultado = CompraComParcelas(compra, parcelas)

        protecao.exigirAlteracaoPermitida(compra.cartao, parcelas.map { it.competencia }.toSet())

        compras.saveAndFlush(compra.paraJpa())
        // Edicao e sempre da compra inteira (RN-P06): descarta e regenera.
        this.parcelas.deleteByCompraId(compra.id)
        this.parcelas.flush()
        this.parcelas.saveAllAndFlush(parcelas.map { it.paraJpa() })

        return resultado
    }

    override fun buscarComParcelas(id: UUID): CompraComParcelas? {
        val compra = buscarVisivel(id) ?: return null
        return CompraComParcelas(compra, parcelas.findByCompraId(id).map { it.paraDominio() })
    }

    override fun excluir(id: UUID) {
        // As parcelas saem por CASCADE (RF-34); o delete explicito mantem o
        // contexto de persistencia coerente na mesma transacao.
        parcelas.deleteByCompraId(id)
        compras.deleteById(id)
    }

    override fun competenciasDe(compraId: UUID): Set<Competencia> =
        parcelas.findByCompraId(compraId).map { Competencia.de(it.competencia) }.toSet()
}

private fun Set<UUID>.ouNulo(): Collection<UUID> = ifEmpty { setOf(UUID(0L, 0L)) }

private fun Compra.paraJpa() = CompraJpa(
    id = id,
    descricao = descricao,
    valorTotal = valorTotal.valor,
    numeroParcelas = numeroParcelas,
    dataCompra = dataCompra,
    cartaoId = cartao,
    categoriaId = categoria,
    donoId = dono,
    escopo = escopo,
    grupoId = grupo,
    criadoEm = criadoEm,
)

private fun CompraJpa.paraDominio() = Compra(
    id = id,
    descricao = descricao,
    valorTotal = Dinheiro.de(valorTotal),
    numeroParcelas = numeroParcelas,
    dataCompra = dataCompra,
    cartao = cartaoId,
    categoria = categoriaId,
    dono = donoId,
    escopo = escopo,
    grupo = grupoId,
    criadoEm = criadoEm,
)

private fun Parcela.paraJpa() = ParcelaJpa(
    id = id,
    compraId = compra,
    numero = numero,
    valor = valor.valor,
    competencia = competencia.toString(),
)

private fun ParcelaJpa.paraDominio() = Parcela(
    id = id,
    compra = compraId,
    numero = numero,
    valor = Dinheiro.de(valor),
    competencia = Competencia.de(competencia),
)
