package com.rafaelmatheus.financialcontrol.orcamento.dominio

import com.rafaelmatheus.financialcontrol.common.dominio.BaseDoRealizado
import com.rafaelmatheus.financialcontrol.common.dominio.Competencia
import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.common.persistencia.RepositorioComVisibilidade
import java.time.Instant
import java.util.UUID

/**
 * Teto mensal por categoria (RF-42 a RF-44).
 *
 * O campo `base` e onde **J-02 se materializa** (D-77): cada orcamento declara
 * se o realizado conta pela data da compra ou pela competencia da fatura.
 *
 * O campo `escopo` e D-78: um teto pessoal compara contra `totalPessoal`, um
 * teto de grupo contra `totalGrupo`. Um gasto de escopo GRUPO cujo dono e o
 * consultante conta nos **dois** — e nao e dupla contagem, pelo mesmo motivo de
 * RN-T04: sao grandezas distintas que nunca se somam.
 *
 * **Sem `valorRealizado`**: e derivado, e a licao de D-75 vale inteira. Um
 * agregado persistido exigiria que todo lancamento, edicao e exclusao lembrasse
 * de recalcular.
 */
data class Orcamento(
    val id: UUID,
    val categoria: UUID,
    val competencia: Competencia,
    val valorTeto: Dinheiro,
    val base: BaseDoRealizado,
    val dono: UUID,
    val escopo: Escopo,
    val grupo: UUID?,
    val criadoEm: Instant,
) {
    init {
        // ZERO e teto valido: "nao quero gastar nada nesta categoria este mes".
        // Remover o orcamento e operacao distinta (RN-O02).
        require(!valorTeto.ehNegativo()) { "Teto nao pode ser negativo" }
        require((escopo == Escopo.GRUPO) == (grupo != null)) {
            "Escopo GRUPO exige grupo, e escopo PESSOAL nao aceita grupo"
        }
    }

    companion object {
        fun novo(
            categoria: UUID,
            competencia: Competencia,
            valorTeto: Dinheiro,
            base: BaseDoRealizado,
            dono: UUID,
            escopo: Escopo,
            grupo: UUID?,
            criadoEm: Instant,
        ) = Orcamento(
            UUID.randomUUID(), categoria, competencia, valorTeto, base, dono, escopo, grupo, criadoEm,
        )
    }
}

/** Uma linha do acompanhamento: teto contra realizado (H-40, H-41). */
data class LinhaDoAcompanhamento(
    val orcamentoId: UUID,
    val categoriaId: UUID,
    val categoriaNome: String,
    val base: BaseDoRealizado,
    val orcado: Dinheiro,
    val realizado: Dinheiro,
) {
    val estourado: Boolean get() = realizado > orcado
    val excedente: Dinheiro get() = if (estourado) realizado - orcado else Dinheiro.ZERO
    val disponivel: Dinheiro get() = if (estourado) Dinheiro.ZERO else orcado - realizado
}

/** Totais de uma base. */
data class TotalDaBase(val base: BaseDoRealizado, val orcado: Dinheiro, val realizado: Dinheiro)

/**
 * O acompanhamento do mes.
 *
 * **NAO existe um total geral**, e a ausencia e a regra (RN-O08). Com bases
 * diferentes entre categorias, somar os realizados de todas produziria a soma de
 * "quanto me comprometi" com "quanto vou pagar" — um numero sem significado.
 *
 * E a **terceira aplicacao do mesmo padrao no ciclo**: RF-97 separou total
 * pessoal de total de grupo, D-28 proibiu soma-los, e D-77 cria um terceiro par
 * incomensuravel. Em todos, a resposta foi apresentar lado a lado e nunca somar.
 */
data class Acompanhamento(
    val competencia: Competencia,
    val categorias: List<LinhaDoAcompanhamento>,
    val totaisPorBase: List<TotalDaBase>,
)

interface OrcamentoRepositorio : RepositorioComVisibilidade<Orcamento> {

    fun salvar(orcamento: Orcamento): Orcamento

    fun excluir(id: UUID)

    fun listarDaCompetencia(competencia: Competencia): List<Orcamento>

    fun existeParaCategoria(
        categoria: UUID,
        competencia: Competencia,
        dono: UUID,
        escopo: Escopo,
        grupo: UUID?,
    ): Boolean
}

/** Violacao da unicidade de RN-O01, vinda do banco. */
class OrcamentoDuplicado : RuntimeException("Ja existe orcamento para esta categoria neste escopo")
