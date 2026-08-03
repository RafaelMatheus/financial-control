package com.rafaelmatheus.financialcontrol.cartao.adaptador.persistencia

import com.rafaelmatheus.financialcontrol.cartao.dominio.Cartao
import com.rafaelmatheus.financialcontrol.cartao.dominio.CartaoRepositorio
import com.rafaelmatheus.financialcontrol.cartao.dominio.CartoesParaFechamento
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.common.seguranca.ContextoUsuario
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
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "cartao")
class CartaoJpa(
    @Id @Column(name = "id", nullable = false) var id: UUID,
    @Column(name = "apelido", nullable = false, length = 80) var apelido: String,
    @Column(name = "dia_fechamento", nullable = false) var diaFechamento: Int,
    @Column(name = "dia_vencimento", nullable = false) var diaVencimento: Int,
    @Column(name = "dono_id", nullable = false) var donoId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "escopo", nullable = false, length = 20) var escopo: Escopo,
    @Column(name = "grupo_id") var grupoId: UUID?,
    @Column(name = "encerrado_em") var encerradoEm: Instant?,
    @Column(name = "criado_em", nullable = false) var criadoEm: Instant,
)

interface CartaoSpringData : JpaRepository<CartaoJpa, UUID> {

    @Query(
        """
        select c from CartaoJpa c
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
    ): CartaoJpa?

    @Query(
        """
        select c from CartaoJpa c
        where c.donoId = :usuario
           or (c.escopo = com.rafaelmatheus.financialcontrol.common.dominio.Escopo.GRUPO
               and c.grupoId in :grupos)
        order by c.apelido
        """,
    )
    fun listarVisiveis(
        @Param("usuario") usuario: UUID,
        @Param("grupos") grupos: Collection<UUID>,
    ): List<CartaoJpa>

    fun findByEncerradoEmIsNull(): List<CartaoJpa>
}

@Repository
class CartaoRepositorioAdaptador(
    private val jpa: CartaoSpringData,
    private val contexto: ContextoUsuario,
) : CartaoRepositorio, CartoesParaFechamento {

    override fun buscarVisivel(id: UUID): Cartao? {
        val c = contexto.criterio()
        return jpa.buscarVisivel(id, c.usuarioAtual, c.gruposDoUsuario.ouNulo())?.paraDominio()
    }

    override fun listarVisiveis(): List<Cartao> {
        val c = contexto.criterio()
        return jpa.listarVisiveis(c.usuarioAtual, c.gruposDoUsuario.ouNulo()).map { it.paraDominio() }
    }

    override fun salvar(cartao: Cartao): Cartao = jpa.saveAndFlush(cartao.paraJpa()).paraDominio()

    /**
     * ⚠️ Implementa [CartoesParaFechamento] — **sem filtro de visibilidade**.
     *
     * O job de fechamento (D-71) roda sem usuario autenticado: nao ha requisicao,
     * logo nao ha criterio. Chamar `contexto.criterio()` aqui lancaria
     * `NAO_AUTENTICADO`.
     *
     * A excecao esta isolada numa interface propria justamente para que ela seja
     * visivel na revisao de quem a injetar.
     */
    override fun listarAtivos(): List<Cartao> =
        jpa.findByEncerradoEmIsNull().map { it.paraDominio() }
}

private fun Set<UUID>.ouNulo(): Collection<UUID> = ifEmpty { setOf(UUID(0L, 0L)) }

private fun Cartao.paraJpa() = CartaoJpa(
    id = id,
    apelido = apelido,
    diaFechamento = diaFechamento,
    diaVencimento = diaVencimento,
    donoId = dono,
    escopo = escopo,
    grupoId = grupo,
    encerradoEm = encerradoEm,
    criadoEm = criadoEm,
)

private fun CartaoJpa.paraDominio() = Cartao(
    id = id,
    apelido = apelido,
    diaFechamento = diaFechamento,
    diaVencimento = diaVencimento,
    dono = donoId,
    escopo = escopo,
    grupo = grupoId,
    encerradoEm = encerradoEm,
    criadoEm = criadoEm,
)
