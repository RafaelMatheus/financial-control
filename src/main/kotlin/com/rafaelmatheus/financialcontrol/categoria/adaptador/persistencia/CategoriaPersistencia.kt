package com.rafaelmatheus.financialcontrol.categoria.adaptador.persistencia

import com.rafaelmatheus.financialcontrol.categoria.dominio.Categoria
import com.rafaelmatheus.financialcontrol.categoria.dominio.CategoriaDuplicada
import com.rafaelmatheus.financialcontrol.categoria.dominio.CategoriaRepositorio
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.common.seguranca.ContextoUsuario
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
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "categoria")
class CategoriaJpa(
    @Id @Column(name = "id", nullable = false) var id: UUID,
    @Column(name = "nome", nullable = false, length = 80) var nome: String,
    /**
     * Coluna propria com a forma normalizada. E sobre ela que os indices unicos
     * parciais existem — comparar `lower(trim(nome))` em indice funcional
     * tornaria a restricao ainda mais invisivel ao `ddl-auto: validate`, que ja
     * nao ve indice parcial nenhum.
     */
    @Column(name = "nome_chave", nullable = false, length = 80) var nomeChave: String,
    @Column(name = "dono_id", nullable = false) var donoId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "escopo", nullable = false, length = 20) var escopo: Escopo,
    @Column(name = "grupo_id") var grupoId: UUID?,
    @Column(name = "criado_em", nullable = false) var criadoEm: Instant,
)

interface CategoriaSpringData : JpaRepository<CategoriaJpa, UUID> {

    /**
     * Predicado de visibilidade de RN-V01, escrito uma vez.
     *
     * `dono == usuarioAtual OU (escopo == GRUPO E grupo in gruposDoUsuario)`.
     *
     * O `:grupos` chega **sempre** de `ContextoUsuario`, que so conhece
     * associacoes ativas — e por isso que D-44 (corte total ao sair) vale aqui
     * sem nenhuma linha a mais.
     */
    @Query(
        """
        select c from CategoriaJpa c
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
    ): CategoriaJpa?

    @Query(
        """
        select c from CategoriaJpa c
        where c.donoId = :usuario
           or (c.escopo = com.rafaelmatheus.financialcontrol.common.dominio.Escopo.GRUPO
               and c.grupoId in :grupos)
        order by c.nome
        """,
    )
    fun listarVisiveis(
        @Param("usuario") usuario: UUID,
        @Param("grupos") grupos: Collection<UUID>,
    ): List<CategoriaJpa>

    fun existsByNomeChaveAndEscopoAndDonoId(chave: String, escopo: Escopo, donoId: UUID): Boolean

    fun existsByNomeChaveAndEscopoAndGrupoId(chave: String, escopo: Escopo, grupoId: UUID): Boolean
}

@Repository
class CategoriaRepositorioAdaptador(
    private val jpa: CategoriaSpringData,
    private val contexto: ContextoUsuario,
) : CategoriaRepositorio {

    override fun buscarVisivel(id: UUID): Categoria? {
        val criterio = contexto.criterio()
        return jpa.buscarVisivel(id, criterio.usuarioAtual, criterio.gruposDoUsuario.ouNulo())
            ?.paraDominio()
    }

    override fun listarVisiveis(): List<Categoria> {
        val criterio = contexto.criterio()
        return jpa.listarVisiveis(criterio.usuarioAtual, criterio.gruposDoUsuario.ouNulo())
            .map { it.paraDominio() }
    }

    override fun salvar(categoria: Categoria): Categoria =
        try {
            // saveAndFlush, e nao save: com `save` o INSERT so vai ao banco no
            // flush do commit, DEPOIS de este try/catch ter saido de cena. E a
            // licao do defeito 1 de cd310cb, e ela vale identica aqui.
            jpa.saveAndFlush(categoria.paraJpa()).paraDominio()
        } catch (_: DataIntegrityViolationException) {
            throw CategoriaDuplicada()
        }

    override fun salvarTodas(categorias: List<Categoria>): List<Categoria> =
        try {
            jpa.saveAllAndFlush(categorias.map { it.paraJpa() }).map { it.paraDominio() }
        } catch (_: DataIntegrityViolationException) {
            throw CategoriaDuplicada()
        }

    /**
     * RN-C02, nas suas duas formas: nas PESSOAIS o nome e unico por **dono**;
     * nas de GRUPO, por **grupo** — de quem quer que seja o dono. E a razao de
     * existir de D-54: sem isso, Ana e Rafael criariam duas "Mercado" no mesmo
     * grupo, e o total por categoria mostraria duas linhas iguais.
     */
    override fun existeComNome(chave: String, escopo: Escopo, dono: UUID, grupo: UUID?): Boolean =
        if (escopo == Escopo.GRUPO && grupo != null) {
            jpa.existsByNomeChaveAndEscopoAndGrupoId(chave, escopo, grupo)
        } else {
            jpa.existsByNomeChaveAndEscopoAndDonoId(chave, escopo, dono)
        }

    override fun excluir(id: UUID) = jpa.deleteById(id)
}

/**
 * `in ()` com colecao vazia e sintaxe invalida em varios bancos. O UUID zero
 * nunca casa com grupo nenhum, e mantem a consulta com uma forma so — a
 * alternativa seria duas consultas quase iguais, e duas consultas quase iguais
 * sao duas oportunidades de a segunda esquecer o predicado.
 */
private fun Set<UUID>.ouNulo(): Collection<UUID> =
    ifEmpty { setOf(UUID(0L, 0L)) }

private fun Categoria.paraJpa() = CategoriaJpa(
    id = id,
    nome = nome,
    nomeChave = Categoria.chaveDeComparacao(nome),
    donoId = dono,
    escopo = escopo,
    grupoId = grupo,
    criadoEm = criadoEm,
)

private fun CategoriaJpa.paraDominio() = Categoria(
    id = id,
    nome = nome,
    dono = donoId,
    escopo = escopo,
    grupo = grupoId,
    criadoEm = criadoEm,
)
