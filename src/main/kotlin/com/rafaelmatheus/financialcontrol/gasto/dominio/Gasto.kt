package com.rafaelmatheus.financialcontrol.gasto.dominio

import com.rafaelmatheus.financialcontrol.common.dominio.Competencia
import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.common.persistencia.RepositorioComVisibilidade
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Uma saida de dinheiro (RF-18 a RF-22).
 *
 * O atributo `dono` parece administrativo e nao e: **e o eixo da unidade**. Ele
 * separa as duas grandezas de RF-97 — `totalPessoal` soma onde `dono` e o
 * consultante, de qualquer escopo; `totalGrupo` soma o que e de escopo GRUPO
 * daquele grupo, de qualquer dono. Um gasto de grupo cujo dono e o consultante
 * entra nos dois, e nao e dupla contagem porque os dois nunca se somam (RN-T04).
 *
 * `dono` e imutavel (RN-L05). Se ele mudasse na edicao, `totalPessoal` deixaria
 * de significar o que RF-97 diz — e o erro seria **silencioso**, porque a soma
 * continuaria fechando.
 *
 * `cartao` e `competencia` nascem aqui e ficam **sempre nulos ate U3** (RN-L10).
 * Estao no modelo por antecipacao, para nao haver `ALTER TABLE` numa tabela que
 * ja tera dados. Nenhuma regra de U2 os le.
 */
data class Gasto(
    val id: UUID,
    val descricao: String,
    val valor: Dinheiro,
    val data: LocalDate,
    val categoria: UUID,
    val dono: UUID,
    val escopo: Escopo,
    val grupo: UUID?,
    val cartao: UUID? = null,
    val competencia: Competencia? = null,
    val criadoEm: Instant,
) {
    init {
        require(descricao.isNotBlank()) { "Descricao nao pode ser vazia" }
        require(valor.ehPositivo()) { "Valor precisa ser maior que zero" }
        require((escopo == Escopo.GRUPO) == (grupo != null)) {
            "Escopo GRUPO exige grupo, e escopo PESSOAL nao aceita grupo"
        }
    }

    /**
     * Edicao (RF-20, RN-L05, RN-L08).
     *
     * **Nao ha parametro de dono.** Nao e uma validacao que alguem pode
     * esquecer: e uma operacao que nao existe.
     *
     * O escopo e alteravel, inclusive de PESSOAL para GRUPO (D-58). O efeito
     * sobre a visibilidade e imediato e retroativo — coerente com D-13, em que
     * membro novo ja enxerga todo o historico do grupo.
     */
    fun editado(
        descricao: String,
        valor: Dinheiro,
        data: LocalDate,
        categoria: UUID,
        escopo: Escopo,
        grupo: UUID?,
    ) = copy(
        descricao = descricao.trim(),
        valor = valor,
        data = data,
        categoria = categoria,
        escopo = escopo,
        grupo = grupo,
    )

    companion object {
        fun novo(
            descricao: String,
            valor: Dinheiro,
            data: LocalDate,
            categoria: UUID,
            dono: UUID,
            escopo: Escopo,
            grupo: UUID?,
            criadoEm: Instant,
        ) = Gasto(
            id = UUID.randomUUID(),
            descricao = descricao.trim(),
            valor = valor,
            data = data,
            categoria = categoria,
            dono = dono,
            escopo = escopo,
            grupo = grupo,
            criadoEm = criadoEm,
        )
    }
}

/**
 * Filtro de consulta (RF-21).
 *
 * **`de` e `ate` sao obrigatorios**, e e o que sustenta a garantia de D-52 nesta
 * porta: nao existe forma de pedir "todos os gastos", porque o tipo nao permite
 * construir a pergunta. A ausencia de metodo cru virou ausencia de parametro
 * opcional.
 */
data class FiltroGasto(
    val de: LocalDate,
    val ate: LocalDate,
    val categoriaId: UUID? = null,
    val grupoId: UUID? = null,
    val escopo: Escopo? = null,
    val donoId: UUID? = null,
) {
    init {
        require(!ate.isBefore(de)) { "Data final nao pode ser anterior a inicial" }
    }
}

/** Teto de 100 para que `tamanho=1000000` nao vire caminho de exaustao de memoria. */
data class Paginacao(val pagina: Int = 0, val tamanho: Int = 20) {
    init {
        require(pagina >= 0) { "Pagina nao pode ser negativa" }
        require(tamanho in 1..MAXIMO) { "Tamanho de pagina precisa estar entre 1 e $MAXIMO" }
    }

    companion object {
        const val MAXIMO = 100
    }
}

// --- Projecoes de leitura (D-65) ---
//
// Nao sao entidades. Sao o resultado de uma consulta que ja seleciona o que a
// resposta precisa e monta o objeto direto, sem materializar `Gasto` e sem
// acesso preguicoso — que, com `open-in-view: false`, estouraria.
//
// Refinamento de D-29, nao revogacao: e a MESMA tabela lida de duas formas —
// entidade quando se vai escrever, projecao quando so se vai exibir. Nao ha
// segunda fonte de verdade nem sincronizacao.

data class ItemGasto(
    val id: UUID,
    val descricao: String,
    val valor: Dinheiro,
    val data: LocalDate,
    val categoriaId: UUID,
    val categoriaNome: String,
    val donoId: UUID,
    val donoNome: String,
    val escopo: Escopo,
    val grupoId: UUID?,
)

data class PaginaDeGastos(
    val itens: List<ItemGasto>,
    val pagina: Int,
    val tamanho: Int,
    val total: Long,
)

data class TotalPorCategoria(val categoriaId: UUID, val categoriaNome: String, val total: Dinheiro)

/**
 * As duas grandezas de RF-97, **lado a lado e nunca somadas** (RN-T04, D-28).
 *
 * Nao existe campo `totalGeral`, e nao e esquecimento: a soma das duas nao tem
 * significado. Sao respostas a perguntas diferentes — "quanto eu gastei" e
 * "quanto a casa gastou" — sobre conjuntos que se sobrepoem.
 */
data class TotaisDeGastos(
    val totalPessoal: Dinheiro,
    val totalGrupo: Dinheiro,
    val porCategoriaPessoal: List<TotalPorCategoria>,
    val porCategoriaGrupo: List<TotalPorCategoria>,
)

/**
 * Porta de `gasto` (D-63).
 *
 * Cada metodo exige `FiltroGasto`, que exige periodo. O criterio de
 * visibilidade **nunca e parametro**: o adaptador o obtem do contexto e o aplica
 * sempre. Nao ha como chamar nenhum destes metodos "sem visibilidade".
 */
interface GastoRepositorio : RepositorioComVisibilidade<Gasto> {

    fun salvar(gasto: Gasto): Gasto

    fun excluir(id: UUID)

    fun consultar(filtro: FiltroGasto, pagina: Paginacao): PaginaDeGastos

    fun totalizar(filtro: FiltroGasto): TotaisDeGastos

    /** Para RN-C05: a contagem que vai na mensagem de categoria em uso. */
    fun contarPorCategoria(categoriaId: UUID): Long

    /** Para RN-C06: realoca de qualquer dono, e devolve quantos foram. */
    fun realocarCategoria(de: UUID, para: UUID): Int
}
