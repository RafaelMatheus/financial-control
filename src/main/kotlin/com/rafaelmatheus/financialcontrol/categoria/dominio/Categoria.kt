package com.rafaelmatheus.financialcontrol.categoria.dominio

import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.common.persistencia.RepositorioComVisibilidade
import java.time.Instant
import java.util.UUID

/**
 * Classificacao de lancamentos (RF-36 a RF-38).
 *
 * **Tem escopo, como o gasto** (D-54). Sem isso, Ana e Rafael criariam cada um a
 * sua "Mercado" no mesmo grupo, e o total por categoria do grupo mostraria duas
 * linhas com o mesmo rotulo e UUIDs diferentes.
 *
 * O `dono` e quem criou, e **nunca muda** — nem quando outro membro renomeia
 * (RN-C04). Ele existe para a unicidade das categorias pessoais e para o
 * predicado de visibilidade, nao como autoridade.
 */
data class Categoria(
    val id: UUID,
    val nome: String,
    val dono: UUID,
    val escopo: Escopo,
    val grupo: UUID?,
    val criadoEm: Instant,
) {
    init {
        require(nome.isNotBlank()) { "Nome da categoria nao pode ser vazio" }
        require(escopoCasaComGrupo(escopo, grupo)) {
            "Escopo GRUPO exige grupo, e escopo PESSOAL nao aceita grupo"
        }
    }

    fun comNome(novoNome: String): Categoria {
        require(novoNome.isNotBlank()) { "Nome da categoria nao pode ser vazio" }
        return copy(nome = normalizar(novoNome))
    }

    companion object {
        fun nova(nome: String, dono: UUID, escopo: Escopo, grupo: UUID?, criadoEm: Instant) =
            Categoria(
                id = UUID.randomUUID(),
                nome = normalizar(nome),
                dono = dono,
                escopo = escopo,
                grupo = grupo,
                criadoEm = criadoEm,
            )

        /**
         * Conjunto inicial de RF-38 e H-35, criado na primeira listagem de quem
         * nao tem nenhuma categoria (D-56, RN-C08).
         *
         * Dez, e a ultima e "Outros". O conjunto precisa cobrir o gasto que o
         * usuario quer lancar no primeiro minuto — senao ele cria uma categoria
         * antes de conseguir usar o sistema, que e exatamente o que H-35 existe
         * para poupar. "Outros" e o escape que garante que sempre ha uma opcao.
         */
        val INICIAIS = listOf(
            "Alimentacao", "Mercado", "Moradia", "Transporte", "Saude",
            "Educacao", "Lazer", "Vestuario", "Servicos", "Outros",
        )

        /**
         * `trim` e minusculas para comparar (RN-C02). Mesma decisao que RN-U01
         * tomou para e-mail, pelo mesmo motivo: o usuario nao percebe a
         * diferenca entre "Mercado" e "  mercado ", e o sistema nao deveria
         * criar duas linhas por ela.
         *
         * O nome guardado preserva a caixa que o usuario escreveu; o que se
         * compara e a forma normalizada, guardada em coluna propria.
         */
        fun normalizar(nome: String): String = nome.trim()

        fun chaveDeComparacao(nome: String): String = nome.trim().lowercase()

        fun escopoCasaComGrupo(escopo: Escopo, grupo: UUID?): Boolean =
            (escopo == Escopo.GRUPO) == (grupo != null)
    }
}

/**
 * Porta de `categoria`, estendendo a base sem metodo cru (D-52, D-63).
 *
 * Repare no que **nao** existe: nenhum metodo devolve categoria sem que o
 * criterio de visibilidade seja aplicado pelo adaptador. O criterio nunca e
 * parametro — nao ha argumento que o desligue.
 */
interface CategoriaRepositorio : RepositorioComVisibilidade<Categoria> {

    fun salvar(categoria: Categoria): Categoria

    /** Existe categoria visivel com este nome no mesmo escopo? (RN-C02) */
    fun existeComNome(chave: String, escopo: Escopo, dono: UUID, grupo: UUID?): Boolean

    fun excluir(id: UUID)

    fun salvarTodas(categorias: List<Categoria>): List<Categoria>
}

/** Violacao da unicidade por escopo, vinda do banco (RN-C02). */
class CategoriaDuplicada : RuntimeException("Ja existe categoria com este nome no escopo")
