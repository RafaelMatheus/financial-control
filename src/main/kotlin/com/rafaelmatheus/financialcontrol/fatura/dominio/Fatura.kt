package com.rafaelmatheus.financialcontrol.fatura.dominio

import com.rafaelmatheus.financialcontrol.common.dominio.Competencia
import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Fatura de um cartao numa competencia (RF-26, D-31).
 *
 * **Sem `valorTotal`** (D-75). O total e `SUM` sobre os lancamentos da
 * competencia, calculado na leitura. A invariante "total = soma dos lancamentos"
 * dependia de **oito caminhos de escrita** lembrarem de recalcular — lancar,
 * editar e excluir gasto, lancar, editar e excluir compra, reabrir fatura,
 * realocar categoria. Esquecer um faria o numero divergir **em silencio**.
 * Derivado, a invariante e verdadeira por construcao.
 *
 * **Sem `status`** (D-70). ABERTA e FECHADA saem de `dataFechamento`; PAGA vem
 * da `ContaAPagar` vinculada, que e onde o pagamento realmente acontece.
 * Persistir os dois criaria o estado `fatura.paga = true` com
 * `conta.status = EM_ABERTO`, que nenhum codigo produz de proposito e todo
 * sistema com dois campos acaba produzindo.
 */
data class Fatura(
    val id: UUID,
    val cartao: UUID,
    val competencia: Competencia,
    val dataFechamento: LocalDate?,
    val dataVencimento: LocalDate,
    val contaAPagar: UUID?,
    val criadoEm: Instant,
) {
    init {
        require(contaAPagar == null || dataFechamento != null) {
            "So ha conta a pagar depois do fechamento"
        }
    }

    fun estaAberta(): Boolean = dataFechamento == null

    fun fechada(quando: LocalDate, conta: UUID) =
        copy(dataFechamento = quando, contaAPagar = conta)

    /**
     * RN-F08, E-12: lancamento retroativo em fatura FECHADA nao paga a reabre.
     *
     * A conta a pagar gerada e **descartada** — ela renasce no proximo
     * fechamento, com o valor corrigido.
     */
    fun reaberta() = copy(dataFechamento = null, contaAPagar = null)

    companion object {
        fun nova(
            cartao: UUID,
            competencia: Competencia,
            dataVencimento: LocalDate,
            criadoEm: Instant,
        ) = Fatura(
            id = UUID.randomUUID(),
            cartao = cartao,
            competencia = competencia,
            dataFechamento = null,
            dataVencimento = dataVencimento,
            contaAPagar = null,
            criadoEm = criadoEm,
        )
    }
}

/** Status observavel da fatura (RN-F06, D-70). Nenhum deles e coluna. */
enum class StatusFatura { ABERTA, FECHADA, PAGA }

/** Um lancamento que compoe a fatura — gasto a vista no cartao, ou parcela. */
data class LancamentoDaFatura(
    val id: UUID,
    val descricao: String,
    val valor: Dinheiro,
    val data: LocalDate,
    val donoId: UUID,
    val donoNome: String,
    /** `"3/12"` em parcela; nulo em gasto avulso (RF-35, RN-P09). */
    val posicao: String?,
)

data class FaturaConsolidada(
    val fatura: Fatura,
    val status: StatusFatura,
    val valorTotal: Dinheiro,
    val lancamentos: List<LancamentoDaFatura>,
)

/**
 * A regra transversal de U3 (RN-F07, RF-95, H-24, E-13).
 *
 * Vive no **dominio de `fatura`** e e implementada na aplicacao, para que
 * `gasto` e `compra` possam depender dela sem depender do adaptador de fatura —
 * a seta continua apontando para dentro.
 *
 * ## Por que ela e invocada duas vezes (D-73)
 *
 * No **servico**, para dar mensagem acionavel: 409 com o texto que orienta a
 * desmarcar o pagamento. No **adaptador de persistencia**, para impedir a
 * gravacao. Esquecer no servico produz mensagem ruim; esquecer no adaptador nao
 * e possivel, porque toda gravacao passa por la.
 *
 * E o mesmo padrao que U1 e U2 usam para unicidade — verificar para dar
 * mensagem, restringir embaixo para garantir. A diferenca e que ali "embaixo"
 * era o banco com um indice, e aqui e o adaptador, porque a regra depende do
 * estado de **outra entidade** e nao cabe numa restricao declarativa.
 */
interface ProtecaoFatura {

    /** Lanca se qualquer das competencias estiver numa fatura PAGA. */
    fun exigirAlteracaoPermitida(cartaoId: UUID, competencias: Set<Competencia>)

    fun exigirAlteracaoPermitida(cartaoId: UUID, competencia: Competencia) =
        exigirAlteracaoPermitida(cartaoId, setOf(competencia))
}

/**
 * Porta de `fatura`.
 *
 * Nao estende `RepositorioComVisibilidade`: a fatura **nao tem dono proprio** —
 * ela pertence ao cartao, e a visibilidade dela e a do cartao. Quem faz a guarda
 * e o `CartaoRepositorio`, antes de qualquer operacao aqui.
 */
interface FaturaRepositorio {

    fun buscar(cartaoId: UUID, competencia: Competencia): Fatura?

    fun salvar(fatura: Fatura): Fatura

    /** Cria se nao existir; devolve a existente se ja houver (RN-F02). */
    fun obterOuCriar(cartaoId: UUID, competencia: Competencia, vencimento: LocalDate): Fatura

    /** `SUM` sobre gastos e parcelas da competencia (D-75, RN-F03). */
    fun somarLancamentos(cartaoId: UUID, competencia: Competencia): Dinheiro

    fun listarLancamentos(cartaoId: UUID, competencia: Competencia): List<LancamentoDaFatura>

    /** RN-F06: PAGA vem da conta a pagar vinculada, nunca da fatura. */
    fun estaPaga(fatura: Fatura): Boolean

    /** Competencias com fatura PAGA, entre as informadas. Base de RN-F07. */
    fun competenciasPagas(cartaoId: UUID, competencias: Set<Competencia>): Set<Competencia>

    /** ⚠️ Sem visibilidade: uso exclusivo do job (D-71). */
    fun listarAbertasParaFechamento(cartaoId: UUID): List<Fatura>
}
