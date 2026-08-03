package com.rafaelmatheus.financialcontrol.gasto.adaptador.persistencia

import com.rafaelmatheus.financialcontrol.common.dominio.BaseDoRealizado
import com.rafaelmatheus.financialcontrol.common.dominio.Competencia
import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.common.persistencia.CriterioVisibilidade
import com.rafaelmatheus.financialcontrol.common.seguranca.ContextoUsuario
import com.rafaelmatheus.financialcontrol.gasto.dominio.ConsultaDeRealizado
import com.rafaelmatheus.financialcontrol.gasto.dominio.FiltroGasto
import com.rafaelmatheus.financialcontrol.gasto.dominio.Gasto
import com.rafaelmatheus.financialcontrol.gasto.dominio.GastoRepositorio
import com.rafaelmatheus.financialcontrol.gasto.dominio.ItemGasto
import com.rafaelmatheus.financialcontrol.gasto.dominio.PaginaDeGastos
import com.rafaelmatheus.financialcontrol.gasto.dominio.Paginacao
import com.rafaelmatheus.financialcontrol.gasto.dominio.TotaisDeGastos
import com.rafaelmatheus.financialcontrol.gasto.dominio.TotalPorCategoria
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.TypedQuery
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "gasto")
class GastoJpa(
    @Id @Column(name = "id", nullable = false) var id: UUID,
    @Column(name = "descricao", nullable = false, length = 200) var descricao: String,
    /**
     * `numeric(15,2)`, nunca `double`. A escala 2 declarada aqui e na migration
     * e o que o `ddl-auto: validate` confere — e a unica protecao que sobra
     * contra as duas aritmeticas monetarias divergirem (D-64).
     */
    @Column(name = "valor", nullable = false, precision = 15, scale = 2) var valor: BigDecimal,
    @Column(name = "data", nullable = false) var data: LocalDate,
    @Column(name = "categoria_id", nullable = false) var categoriaId: UUID,
    @Column(name = "dono_id", nullable = false) var donoId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "escopo", nullable = false, length = 20) var escopo: Escopo,
    @Column(name = "grupo_id") var grupoId: UUID?,
    /** Sempre nulos ate U3 (RN-L10). Existem para nao haver `ALTER TABLE` la. */
    @Column(name = "cartao_id") var cartaoId: UUID?,
    @Column(name = "competencia", length = 7) var competencia: String?,
    @Column(name = "criado_em", nullable = false) var criadoEm: Instant,
)

/**
 * Linhas de projecao (D-65).
 *
 * Existem porque `Dinheiro` e value class de construtor privado, e a expressao
 * `new` do JPQL so chama construtor publico com os tipos que o banco devolve.
 * A conversao para o tipo de dominio acontece na borda, num unico lugar — que e
 * exatamente onde ela deve acontecer.
 */
class LinhaGasto(
    val id: UUID,
    val descricao: String,
    val valor: BigDecimal,
    val data: LocalDate,
    val categoriaId: UUID,
    val categoriaNome: String,
    val donoId: UUID,
    val donoNome: String,
    val escopo: Escopo,
    val grupoId: UUID?,
)

class LinhaTotalCategoria(val categoriaId: UUID, val categoriaNome: String, val total: BigDecimal)

interface GastoSpringData : JpaRepository<GastoJpa, UUID> {

    @Query(
        """
        select g from GastoJpa g
        where g.id = :id
          and (g.donoId = :usuario
               or (g.escopo = com.rafaelmatheus.financialcontrol.common.dominio.Escopo.GRUPO
                   and g.grupoId in :grupos))
        """,
    )
    fun buscarVisivel(
        @Param("id") id: UUID,
        @Param("usuario") usuario: UUID,
        @Param("grupos") grupos: Collection<UUID>,
    ): GastoJpa?

    fun countByCategoriaId(categoriaId: UUID): Long

    /**
     * RN-C06, D-59: realoca **de qualquer dono**. Nao ha filtro de visibilidade
     * aqui de proposito — quem chega neste ponto ja passou por `buscarVisivel`
     * na categoria de origem e na de destino, e a regra e justamente que a
     * realocacao alcance os gastos dos outros membros.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update GastoJpa g set g.categoriaId = :para where g.categoriaId = :de")
    fun realocarCategoria(@Param("de") de: UUID, @Param("para") para: UUID): Int
}

@Repository
class GastoRepositorioAdaptador(
    private val jpa: GastoSpringData,
    private val em: EntityManager,
    private val contexto: ContextoUsuario,
) : GastoRepositorio, ConsultaDeRealizado {

    /**
     * 🔑 D-81 e D-84 — o realizado do orcamento, somado por quem e dono do dado.
     *
     * **Uma consulta agrupada**, e nao uma por orcamento: com dez tetos no mes, a
     * diferenca e entre uma ida ao banco e dez.
     *
     * As duas bases produzem consultas de FORMA diferente, e nao so de filtro:
     *
     * - `DATA_DA_COMPRA` soma gastos pela `data` e compras parceladas pelo
     *   **valor total**, na `data_compra` — o comprometimento acontece de uma vez;
     * - `COMPETENCIA` soma gastos e **parcelas** pela `competencia` — o desembolso
     *   acontece mes a mes.
     *
     * E a diferenca que J-02 expos, escrita em SQL.
     */
    override fun somarPorCategoria(
        janela: Competencia,
        base: BaseDoRealizado,
        escopo: Escopo,
        grupo: UUID?,
    ): Map<UUID, Dinheiro> {
        val c = contexto.criterio()
        // RN-O05: o recorte depende do escopo do ORCAMENTO, nao do lancamento.
        val recorte = if (escopo == Escopo.GRUPO) {
            "g.escopo = 'GRUPO' and g.grupo_id = :grupo"
        } else {
            "g.dono_id = :usuario"
        }
        val recorteCompra = recorte.replace("g.", "cp.")

        val sql = if (base == BaseDoRealizado.DATA_DA_COMPRA) {
            """
            select categoria_id, sum(valor) from (
                -- Todo gasto conta pela sua data, com cartao ou sem: nesta base
                -- o que importa e QUANDO SE COMPROU.
                select g.categoria_id as categoria_id, g.valor as valor
                  from gasto g
                 where $recorte
                   and to_char(g.data, 'YYYY-MM') = :janela
                union all
                -- A compra parcelada conta pelo VALOR TOTAL, de uma vez: o
                -- comprometimento acontece no dia da compra, nao mes a mes.
                select cp.categoria_id, cp.valor_total
                  from compra cp
                 where $recorteCompra
                   and to_char(cp.data_compra, 'YYYY-MM') = :janela
            ) t group by categoria_id
            """
        } else {
            """
            select categoria_id, sum(valor) from (
                select g.categoria_id as categoria_id, g.valor as valor
                  from gasto g
                 where $recorte
                   and coalesce(g.competencia, to_char(g.data, 'YYYY-MM')) = :janela
                union all
                select cp.categoria_id, p.valor
                  from parcela p
                  join compra cp on cp.id = p.compra_id
                 where $recorteCompra
                   and p.competencia = :janela
            ) t group by categoria_id
            """
        }

        val consulta = em.createNativeQuery(sql).setParameter("janela", janela.toString())
        if (escopo == Escopo.GRUPO) consulta.setParameter("grupo", grupo)
        else consulta.setParameter("usuario", c.usuarioAtual)

        @Suppress("UNCHECKED_CAST")
        val linhas = consulta.resultList as List<Array<Any?>>
        return linhas.associate { (it[0] as UUID) to Dinheiro.de(it[1] as java.math.BigDecimal) }
    }

    override fun buscarVisivel(id: UUID): Gasto? {
        val c = contexto.criterio()
        return jpa.buscarVisivel(id, c.usuarioAtual, c.gruposDoUsuario.ouNulo())?.paraDominio()
    }

    /**
     * Existe porque a porta-base de U1 a declara. Nesta feature ela nao e usada:
     * toda leitura de lista passa por [consultar], que exige periodo. Se alguem
     * a chamar, ela ao menos continua filtrada pela visibilidade.
     */
    override fun listarVisiveis(): List<Gasto> {
        val c = contexto.criterio()
        return em.createQuery(
            "select g from GastoJpa g where $VISIVEL order by g.data desc",
            GastoJpa::class.java,
        ).comCriterio(c).resultList.map { it.paraDominio() }
    }

    override fun salvar(gasto: Gasto): Gasto = jpa.saveAndFlush(gasto.paraJpa()).paraDominio()

    override fun excluir(id: UUID) = jpa.deleteById(id)

    override fun contarPorCategoria(categoriaId: UUID): Long = jpa.countByCategoriaId(categoriaId)

    override fun realocarCategoria(de: UUID, para: UUID): Int = jpa.realocarCategoria(de, para)

    /**
     * Listagem por **projecao direta** (D-65).
     *
     * Uma consulta, com `join`, montando a linha sem materializar `GastoJpa` e
     * sem tocar associacao preguicosa. Com `open-in-view: false`, o N+1 nao e
     * evitado por configuracao de fetch — ele nao tem por onde acontecer.
     */
    override fun consultar(filtro: FiltroGasto, pagina: Paginacao): PaginaDeGastos {
        val c = contexto.criterio()
        val onde = "where ($VISIVEL) ${clausulasDoFiltro(filtro)}"

        val linhas = em.createQuery(
            """
            select new $PACOTE.LinhaGasto(
                g.id, g.descricao, g.valor, g.data,
                cat.id, cat.nome, dono.id, dono.nome, g.escopo, g.grupoId)
            from GastoJpa g
            join $CATEGORIA_JPA cat on cat.id = g.categoriaId
            join $USUARIO_JPA dono on dono.id = g.donoId
            $onde
            order by g.data desc, g.criadoEm desc
            """,
            LinhaGasto::class.java,
        ).comCriterio(c).comFiltro(filtro)
            .setFirstResult(pagina.pagina * pagina.tamanho)
            .setMaxResults(pagina.tamanho)
            .resultList

        val total = em.createQuery("select count(g) from GastoJpa g $onde", java.lang.Long::class.java)
            .comCriterio(c).comFiltro(filtro).singleResult.toLong()

        return PaginaDeGastos(linhas.map { it.paraItem() }, pagina.pagina, pagina.tamanho, total)
    }

    /**
     * As duas grandezas de RF-97, **cada uma com o seu recorte** (RN-T02,
     * RN-T03). Percorrem o mesmo conjunto visivel com filtros diferentes, e e
     * por isso que um gasto de escopo GRUPO cujo dono e o consultante entra nos
     * dois. Nao e dupla contagem: os numeros nunca se somam (RN-T04).
     *
     * `SUM` no banco (D-64). O banco pode somar; **dividir, nunca** — divisao
     * monetaria tem residuo, e o residuo mora em `Dinheiro`.
     */
    override fun totalizar(filtro: FiltroGasto): TotaisDeGastos {
        val c = contexto.criterio()
        val visivel = "($VISIVEL) ${clausulasDoFiltro(filtro)}"
        val pessoal = "g.donoId = :usuario"

        // Sem grupo no filtro, o total do grupo e zero por definicao, e a
        // consulta simplesmente nao e emitida. Preferido a um predicado sempre
        // falso em JPQL, que dependeria de o dialeto aceitar `1 = 0`.
        val temGrupo = filtro.grupoId != null
        val doGrupo = "g.escopo = $ESCOPO_GRUPO and g.grupoId = :grupoFiltro"

        return TotaisDeGastos(
            totalPessoal = somar(visivel, pessoal, c, filtro),
            totalGrupo = if (temGrupo) somar(visivel, doGrupo, c, filtro) else Dinheiro.ZERO,
            porCategoriaPessoal = somarPorCategoria(visivel, pessoal, c, filtro),
            porCategoriaGrupo = if (temGrupo) somarPorCategoria(visivel, doGrupo, c, filtro) else emptyList(),
        )
    }

    private fun somar(
        visivel: String,
        recorte: String,
        c: CriterioVisibilidade,
        filtro: FiltroGasto,
    ): Dinheiro {
        val soma: BigDecimal? = em.createQuery(
            "select sum(g.valor) from GastoJpa g where $visivel and ($recorte)",
            BigDecimal::class.java,
        ).comCriterio(c).comFiltro(filtro).singleResult
        return soma?.let { Dinheiro.de(it) } ?: Dinheiro.ZERO
    }

    private fun somarPorCategoria(
        visivel: String,
        recorte: String,
        c: CriterioVisibilidade,
        filtro: FiltroGasto,
    ): List<TotalPorCategoria> = em.createQuery(
        """
        select new $PACOTE.LinhaTotalCategoria(cat.id, cat.nome, sum(g.valor))
        from GastoJpa g
        join $CATEGORIA_JPA cat on cat.id = g.categoriaId
        where $visivel and ($recorte)
        group by cat.id, cat.nome
        order by cat.nome
        """,
        LinhaTotalCategoria::class.java,
    ).comCriterio(c).comFiltro(filtro).resultList
        .map { TotalPorCategoria(it.categoriaId, it.categoriaNome, Dinheiro.de(it.total)) }

    private fun clausulasDoFiltro(filtro: FiltroGasto): String = buildString {
        append(" and g.data between :de and :ate")
        if (filtro.categoriaId != null) append(" and g.categoriaId = :categoria")
        if (filtro.escopo != null) append(" and g.escopo = :escopo")
        if (filtro.donoId != null) append(" and g.donoId = :dono")
    }

    private fun <T> TypedQuery<T>.comCriterio(c: CriterioVisibilidade) = apply {
        setParameter("usuario", c.usuarioAtual)
        setParameter("grupos", c.gruposDoUsuario.ouNulo())
    }

    private fun <T> TypedQuery<T>.comFiltro(filtro: FiltroGasto) = apply {
        val declarados = parameters.mapNotNull { it.name }.toSet()
        setParameter("de", filtro.de)
        setParameter("ate", filtro.ate)
        if ("categoria" in declarados) setParameter("categoria", filtro.categoriaId)
        if ("escopo" in declarados) setParameter("escopo", filtro.escopo)
        if ("dono" in declarados) setParameter("dono", filtro.donoId)
        if ("grupoFiltro" in declarados) setParameter("grupoFiltro", filtro.grupoId)
    }

    private companion object {
        const val PACOTE = "com.rafaelmatheus.financialcontrol.gasto.adaptador.persistencia"
        const val CATEGORIA_JPA =
            "com.rafaelmatheus.financialcontrol.categoria.adaptador.persistencia.CategoriaJpa"
        const val USUARIO_JPA =
            "com.rafaelmatheus.financialcontrol.usuario.adaptador.persistencia.UsuarioJpa"
        const val ESCOPO_GRUPO = "com.rafaelmatheus.financialcontrol.common.dominio.Escopo.GRUPO"

        /**
         * Predicado de RN-V01, escrito **uma vez** e reaproveitado por toda
         * consulta desta feature:
         *
         * `dono == usuarioAtual OU (escopo == GRUPO E grupo in gruposDoUsuario)`
         *
         * Estar num lugar so nao e economia de digitacao: e a garantia de que
         * nao ha uma segunda versao do predicado para divergir da primeira.
         */
        const val VISIVEL =
            "g.donoId = :usuario or (g.escopo = $ESCOPO_GRUPO and g.grupoId in :grupos)"
    }
}

/**
 * `in ()` com colecao vazia e sintaxe invalida em varios bancos. O UUID zero
 * nunca casa com grupo nenhum, e mantem a consulta com uma forma so — a
 * alternativa seria duas consultas quase iguais, e duas consultas quase iguais
 * sao duas oportunidades de a segunda esquecer o predicado.
 */
private fun Set<UUID>.ouNulo(): Collection<UUID> = ifEmpty { setOf(UUID(0L, 0L)) }

private fun LinhaGasto.paraItem() = ItemGasto(
    id = id,
    descricao = descricao,
    valor = Dinheiro.de(valor),
    data = data,
    categoriaId = categoriaId,
    categoriaNome = categoriaNome,
    donoId = donoId,
    donoNome = donoNome,
    escopo = escopo,
    grupoId = grupoId,
)

private fun Gasto.paraJpa() = GastoJpa(
    id = id,
    descricao = descricao,
    valor = valor.valor,
    data = data,
    categoriaId = categoria,
    donoId = dono,
    escopo = escopo,
    grupoId = grupo,
    cartaoId = cartao,
    competencia = competencia?.toString(),
    criadoEm = criadoEm,
)

private fun GastoJpa.paraDominio() = Gasto(
    id = id,
    descricao = descricao,
    valor = Dinheiro.de(valor),
    data = data,
    categoria = categoriaId,
    dono = donoId,
    escopo = escopo,
    grupo = grupoId,
    cartao = cartaoId,
    competencia = competencia?.let { Competencia.de(it) },
    criadoEm = criadoEm,
)
