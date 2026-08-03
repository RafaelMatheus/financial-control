package com.rafaelmatheus.financialcontrol.fatura.adaptador.persistencia

import com.rafaelmatheus.financialcontrol.common.dominio.Competencia
import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.fatura.dominio.Fatura
import com.rafaelmatheus.financialcontrol.fatura.dominio.FaturaRepositorio
import com.rafaelmatheus.financialcontrol.fatura.dominio.LancamentoDaFatura
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "fatura")
class FaturaJpa(
    @Id @Column(name = "id", nullable = false) var id: UUID,
    @Column(name = "cartao_id", nullable = false) var cartaoId: UUID,
    @Column(name = "competencia", nullable = false, length = 7) var competencia: String,
    @Column(name = "data_fechamento") var dataFechamento: LocalDate?,
    @Column(name = "data_vencimento", nullable = false) var dataVencimento: LocalDate,
    @Column(name = "conta_a_pagar_id") var contaAPagarId: UUID?,
    @Column(name = "criado_em", nullable = false) var criadoEm: Instant,
)

interface FaturaSpringData : JpaRepository<FaturaJpa, UUID> {

    fun findByCartaoIdAndCompetencia(cartaoId: UUID, competencia: String): FaturaJpa?

    fun findByCartaoIdAndDataFechamentoIsNull(cartaoId: UUID): List<FaturaJpa>
}

@Repository
class FaturaRepositorioAdaptador(
    private val jpa: FaturaSpringData,
    private val em: EntityManager,
) : FaturaRepositorio {

    override fun buscar(cartaoId: UUID, competencia: Competencia): Fatura? =
        jpa.findByCartaoIdAndCompetencia(cartaoId, competencia.toString())?.paraDominio()

    override fun salvar(fatura: Fatura): Fatura = jpa.saveAndFlush(fatura.paraJpa()).paraDominio()

    /**
     * RN-F02. A restricao unica `(cartao_id, competencia)` e a garantia real:
     * duas requisicoes simultaneas lancando na mesma competencia passam as duas
     * pelo `buscar`, e o banco barra a segunda. Mesmo padrao de U1 e U2.
     */
    override fun obterOuCriar(
        cartaoId: UUID,
        competencia: Competencia,
        vencimento: LocalDate,
    ): Fatura {
        buscar(cartaoId, competencia)?.let { return it }
        return try {
            salvar(Fatura.nova(cartaoId, competencia, vencimento, Instant.now()))
        } catch (_: DataIntegrityViolationException) {
            // A outra requisicao ganhou. A releitura NAO pode acontecer nesta
            // transacao — ela esta abortada (licao do defeito de U2, SQLSTATE
            // 25P02). Quem chama roda em transacao propria e relera.
            throw FaturaJaCriada()
        }
    }

    /**
     * D-75, RN-F03. O total e somado **no banco, na leitura**.
     *
     * Duas parcelas da soma: gastos a vista lancados no cartao com aquela
     * competencia, e parcelas de compras daquele cartao com aquela competencia.
     *
     * `SUM` e nao divisao: *o banco pode somar; dividir, nunca*. A divisao do
     * parcelamento vive em `Dinheiro.dividirEm`, com teste de propriedade.
     */
    override fun somarLancamentos(cartaoId: UUID, competencia: Competencia): Dinheiro {
        val sql = """
            select coalesce((
                select sum(g.valor) from gasto g
                where g.cartao_id = :cartao and g.competencia = :competencia
            ), 0) + coalesce((
                select sum(p.valor) from parcela p
                join compra c on c.id = p.compra_id
                where c.cartao_id = :cartao and p.competencia = :competencia
            ), 0)
        """
        val total = em.createNativeQuery(sql)
            .setParameter("cartao", cartaoId)
            .setParameter("competencia", competencia.toString())
            .singleResult as BigDecimal
        return Dinheiro.de(total)
    }

    /**
     * Os lancamentos que compoem a fatura, gastos e parcelas juntos.
     *
     * Consulta **nativa com `union all`**: JPQL nao expressa uniao, e a
     * alternativa seriam duas consultas e uma ordenacao em memoria. Como e
     * projecao pura de leitura (D-65), nenhuma entidade e materializada.
     */
    @Suppress("UNCHECKED_CAST")
    override fun listarLancamentos(
        cartaoId: UUID,
        competencia: Competencia,
    ): List<LancamentoDaFatura> {
        val sql = """
            select g.id, g.descricao, g.valor, g.data, u.id, u.nome, cast(null as varchar) as posicao
              from gasto g join usuario u on u.id = g.dono_id
             where g.cartao_id = :cartao and g.competencia = :competencia
            union all
            select p.id, c.descricao, p.valor, c.data_compra, u.id, u.nome,
                   concat(p.numero, '/', c.numero_parcelas)
              from parcela p
              join compra c on c.id = p.compra_id
              join usuario u on u.id = c.dono_id
             where c.cartao_id = :cartao and p.competencia = :competencia
             order by 4, 2
        """
        val linhas = em.createNativeQuery(sql)
            .setParameter("cartao", cartaoId)
            .setParameter("competencia", competencia.toString())
            .resultList as List<Array<Any?>>

        return linhas.map { linha ->
            LancamentoDaFatura(
                id = linha[0] as UUID,
                descricao = linha[1] as String,
                valor = Dinheiro.de(linha[2] as BigDecimal),
                data = (linha[3] as java.sql.Date).toLocalDate(),
                donoId = linha[4] as UUID,
                donoNome = linha[5] as String,
                posicao = linha[6] as String?,
            )
        }
    }

    /**
     * RN-F06, D-70. **PAGA vem da conta a pagar**, nunca de um campo da fatura.
     *
     * Consulta nativa de proposito: `conta_a_pagar` pertence a outra feature, e
     * mapear a entidade dela aqui acoplaria os dois adaptadores. A tabela e
     * contrato estavel; a entidade, nao.
     */
    override fun estaPaga(fatura: Fatura): Boolean {
        val contaId = fatura.contaAPagar ?: return false
        val pagas = em.createNativeQuery(
            "select count(*) from conta_a_pagar where id = :id and status = 'PAGA'",
        ).setParameter("id", contaId).singleResult as Number
        return pagas.toLong() > 0
    }

    /**
     * Base de RN-F07. Devolve **quais** competencias estao pagas, e nao apenas
     * se alguma esta — a mensagem de erro fica podendo dizer qual, e o teste
     * fica podendo verificar o conjunto.
     */
    @Suppress("UNCHECKED_CAST")
    override fun competenciasPagas(
        cartaoId: UUID,
        competencias: Set<Competencia>,
    ): Set<Competencia> {
        if (competencias.isEmpty()) return emptySet()
        val textos = competencias.map { it.toString() }
        val encontradas = em.createNativeQuery(
            """
            select f.competencia
              from fatura f
              join conta_a_pagar c on c.id = f.conta_a_pagar_id
             where f.cartao_id = :cartao
               and f.competencia in (:competencias)
               and c.status = 'PAGA'
            """,
        )
            .setParameter("cartao", cartaoId)
            .setParameter("competencias", textos)
            .resultList as List<String>

        return encontradas.map { Competencia.de(it) }.toSet()
    }

    /** ⚠️ Sem visibilidade: uso exclusivo do job (D-71). */
    override fun listarAbertasParaFechamento(cartaoId: UUID): List<Fatura> =
        jpa.findByCartaoIdAndDataFechamentoIsNull(cartaoId).map { it.paraDominio() }
}

/** Corrida na criacao da fatura; quem chama rele numa transacao nova. */
class FaturaJaCriada : RuntimeException("Fatura ja criada por outra requisicao")

private fun Fatura.paraJpa() = FaturaJpa(
    id = id,
    cartaoId = cartao,
    competencia = competencia.toString(),
    dataFechamento = dataFechamento,
    dataVencimento = dataVencimento,
    contaAPagarId = contaAPagar,
    criadoEm = criadoEm,
)

private fun FaturaJpa.paraDominio() = Fatura(
    id = id,
    cartao = cartaoId,
    competencia = Competencia.de(competencia),
    dataFechamento = dataFechamento,
    dataVencimento = dataVencimento,
    contaAPagar = contaAPagarId,
    criadoEm = criadoEm,
)
