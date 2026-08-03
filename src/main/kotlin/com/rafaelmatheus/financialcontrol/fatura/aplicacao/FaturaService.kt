package com.rafaelmatheus.financialcontrol.fatura.aplicacao

import com.rafaelmatheus.financialcontrol.cartao.aplicacao.CartaoService
import com.rafaelmatheus.financialcontrol.common.dominio.CalculadoraDeCompetencia
import com.rafaelmatheus.financialcontrol.common.dominio.Competencia
import com.rafaelmatheus.financialcontrol.common.web.CodigoErro
import com.rafaelmatheus.financialcontrol.common.web.ErroDeNegocio
import com.rafaelmatheus.financialcontrol.fatura.dominio.Fatura
import com.rafaelmatheus.financialcontrol.fatura.dominio.FaturaConsolidada
import com.rafaelmatheus.financialcontrol.fatura.dominio.FaturaRepositorio
import com.rafaelmatheus.financialcontrol.fatura.dominio.ProtecaoFatura
import com.rafaelmatheus.financialcontrol.fatura.dominio.StatusFatura
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * Implementacao da regra transversal RN-F07 (D-73).
 *
 * Fica em bean proprio, e nao dentro de `FaturaService`, porque quem a consome
 * sao `gasto`, `compra` e `conta` — e um deles injetar o `FaturaService` inteiro
 * criaria dependencia circular na primeira vez que a fatura precisasse consultar
 * lancamentos.
 */
@Service
class ProtecaoFaturaPadrao(private val repositorio: FaturaRepositorio) : ProtecaoFatura {

    override fun exigirAlteracaoPermitida(cartaoId: UUID, competencias: Set<Competencia>) {
        if (competencias.isEmpty()) return
        val pagas = repositorio.competenciasPagas(cartaoId, competencias)
        if (pagas.isNotEmpty()) {
            throw ErroDeNegocio(CodigoErro.FATURA_PAGA)
        }
    }
}

data class LancamentoDTO(
    val id: String,
    val descricao: String,
    val valor: String,
    val data: String,
    val donoId: String,
    val donoNome: String,
    val posicao: String?,
)

data class FaturaDTO(
    val id: String,
    val cartaoId: String,
    val competencia: String,
    val status: StatusFatura,
    val valorTotal: String,
    val dataFechamento: String?,
    val dataVencimento: String,
    val lancamentos: List<LancamentoDTO>,
)

fun FaturaConsolidada.paraDTO() = FaturaDTO(
    id = fatura.id.toString(),
    cartaoId = fatura.cartao.toString(),
    competencia = fatura.competencia.toString(),
    status = status,
    valorTotal = valorTotal.toString(),
    dataFechamento = fatura.dataFechamento?.toString(),
    dataVencimento = fatura.dataVencimento.toString(),
    lancamentos = lancamentos.map {
        LancamentoDTO(
            id = it.id.toString(),
            descricao = it.descricao,
            valor = it.valor.toString(),
            data = it.data.toString(),
            donoId = it.donoId.toString(),
            donoNome = it.donoNome,
            posicao = it.posicao,
        )
    },
)

@Service
class FaturaService(
    private val repositorio: FaturaRepositorio,
    private val cartoes: CartaoService,
    private val relogio: Clock,
) {

    /**
     * H-21, RF-26. Consolida a fatura da competencia.
     *
     * **Nao existe `fechar` publico** (D-71): o fechamento e consequencia da
     * data, e quem o dispara e o job. Expor a operacao convidaria a fechar
     * fatura a mao, criando um estado que a data ainda nao justifica.
     */
    @Transactional(readOnly = true)
    fun consultar(cartaoId: UUID, competencia: Competencia): FaturaDTO {
        // A visibilidade da fatura E a do cartao: sem cartao visivel, 404.
        val cartao = cartoes.consultar(cartaoId)
        val vencimento = CalculadoraDeCompetencia.dataDeVencimento(competencia, cartao.diaVencimento)

        val fatura = repositorio.buscar(cartaoId, competencia)
            ?: Fatura.nova(cartaoId, competencia, vencimento, relogio.instant())

        return consolidar(fatura).paraDTO()
    }

    /**
     * H-26, RF-28. Projeta as faturas ate a competencia informada, com as
     * parcelas ja comprometidas.
     */
    @Transactional(readOnly = true)
    fun consultarFuturas(cartaoId: UUID, ate: Competencia): List<FaturaDTO> {
        val cartao = cartoes.consultar(cartaoId)
        val hoje = java.time.LocalDate.now(relogio)
        var competencia = CalculadoraDeCompetencia.competenciaDe(hoje, cartao.diaFechamento)

        val resultado = mutableListOf<FaturaDTO>()
        while (competencia <= ate) {
            resultado += consultar(cartaoId, competencia)
            competencia = competencia.proxima()
        }
        return resultado
    }

    /**
     * RN-F08, E-12. Reabre a fatura FECHADA nao paga, para que um lancamento
     * retroativo entre na competencia correta **pela data**.
     *
     * Empurrar o lancamento para a fatura aberta faria o total bater com o
     * registro do sistema e nao com o extrato do banco — o oposto do que H-20
     * quer.
     */
    @Transactional
    fun reabrirSeNecessario(cartaoId: UUID, competencia: Competencia) {
        val fatura = repositorio.buscar(cartaoId, competencia) ?: return
        if (fatura.estaAberta()) return
        if (repositorio.estaPaga(fatura)) throw ErroDeNegocio(CodigoErro.FATURA_PAGA)
        repositorio.salvar(fatura.reaberta())
    }

    /** Obtem ou cria a fatura da competencia, para receber um lancamento. */
    @Transactional
    fun prepararParaLancamento(cartaoId: UUID, competencia: Competencia): Fatura {
        val cartao = cartoes.exigirAtivo(cartaoId)
        val vencimento = CalculadoraDeCompetencia.dataDeVencimento(competencia, cartao.diaVencimento)
        reabrirSeNecessario(cartaoId, competencia)
        return repositorio.obterOuCriar(cartaoId, competencia, vencimento)
    }

    private fun consolidar(fatura: Fatura): FaturaConsolidada {
        val status = when {
            fatura.estaAberta() -> StatusFatura.ABERTA
            repositorio.estaPaga(fatura) -> StatusFatura.PAGA
            else -> StatusFatura.FECHADA
        }
        return FaturaConsolidada(
            fatura = fatura,
            status = status,
            // D-75: somado no banco, na leitura. Nunca guardado.
            valorTotal = repositorio.somarLancamentos(fatura.cartao, fatura.competencia),
            lancamentos = repositorio.listarLancamentos(fatura.cartao, fatura.competencia),
        )
    }
}
