package com.rafaelmatheus.financialcontrol.investimento.aplicacao

import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.common.seguranca.ContextoUsuario
import com.rafaelmatheus.financialcontrol.common.web.CodigoErro
import com.rafaelmatheus.financialcontrol.common.web.ErroDeNegocio
import com.rafaelmatheus.financialcontrol.investimento.dominio.Aporte
import com.rafaelmatheus.financialcontrol.investimento.dominio.CalculadoraDeAporte
import com.rafaelmatheus.financialcontrol.investimento.dominio.ObjetivoInvestimento
import com.rafaelmatheus.financialcontrol.investimento.dominio.ObjetivoRepositorio
import com.rafaelmatheus.financialcontrol.investimento.dominio.PosicaoDoObjetivo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

data class CriarObjetivo(
    val nome: String,
    val meta: Dinheiro?,
    val prazoAlvo: LocalDate?,
    val escopo: Escopo,
    val grupoId: UUID?,
)

data class ObjetivoDTO(
    val id: String,
    val nome: String,
    val meta: String?,
    val prazoAlvo: String?,
    val saldoAtual: String,
    val totalAportado: String,
    /** RF-72, E-14: pode ser negativo, e e exibido. */
    val rendimento: String,
    val progresso: String?,
    val falta: String?,
    val aporteMensalNecessario: String?,
    val atrasado: Boolean,
    val escopo: Escopo,
    val grupoId: String?,
    val donoId: String,
)

data class PosicaoConsolidadaDTO(
    val objetivos: List<ObjetivoDTO>,
    val totalAportado: String,
    val saldoTotal: String,
    val rendimentoAgregado: String,
)

@Service
class InvestimentoService(
    private val repositorio: ObjetivoRepositorio,
    private val contexto: ContextoUsuario,
    private val relogio: Clock,
) {

    /** H-52, RF-68. Meta e prazo **opcionais**. */
    @Transactional
    fun criarObjetivo(comando: CriarObjetivo): ObjetivoDTO {
        validar(comando)
        return posicao(
            repositorio.salvar(
                ObjetivoInvestimento.novo(
                    comando.nome, comando.meta, comando.prazoAlvo,
                    contexto.usuarioAtual(), comando.escopo, comando.grupoId, relogio.instant(),
                ),
            ),
        )
    }

    @Transactional
    fun editarObjetivo(id: UUID, comando: CriarObjetivo): ObjetivoDTO {
        val objetivo = exigirVisivel(id)
        validar(comando)
        return posicao(
            repositorio.salvar(objetivo.editado(comando.nome, comando.meta, comando.prazoAlvo)),
        )
    }

    @Transactional
    fun excluirObjetivo(id: UUID) {
        exigirVisivel(id)
        repositorio.excluir(id)
    }

    /**
     * H-53, RF-69, RF-70 e **D-80**.
     *
     * O aporte soma ao saldo. Como `totalAportado` tambem sobe (derivado), o
     * **rendimento nao se move** — nasce zero em vez de nascer negativo e esperar
     * correcao manual.
     */
    @Transactional
    fun aportar(objetivoId: UUID, valor: Dinheiro, data: LocalDate): ObjetivoDTO {
        val objetivo = exigirVisivel(objetivoId)
        if (!valor.ehPositivo()) throw ErroDeNegocio(CodigoErro.VALOR_INVALIDO)

        repositorio.salvarAporte(
            Aporte.novo(objetivo.id, valor, data, contexto.usuarioAtual(), relogio.instant()),
        )
        return posicao(repositorio.salvar(objetivo.comAporte(valor)))
    }

    /**
     * **D-83** — simetrico a D-80.
     *
     * Sem a subtracao, excluir um aporte de R$ 500 faria o rendimento **subir**
     * R$ 500 do nada: `totalAportado` cairia sozinho, por ser derivado, e o saldo
     * ficaria. Seria um erro sem excecao e sem log.
     */
    @Transactional
    fun excluirAporte(aporteId: UUID): ObjetivoDTO {
        val aporte = repositorio.buscarAporte(aporteId)
            ?: throw ErroDeNegocio(CodigoErro.APORTE_NAO_ENCONTRADO)
        val objetivo = exigirVisivel(aporte.objetivo)

        repositorio.excluirAporte(aporteId)
        return posicao(repositorio.salvar(objetivo.semAporte(aporte.valor)))
    }

    /** H-54, RF-71. O sistema nao tem cotacao; o saldo e informacao do usuario. */
    @Transactional
    fun atualizarSaldo(objetivoId: UUID, saldo: Dinheiro): ObjetivoDTO =
        posicao(repositorio.salvar(exigirVisivel(objetivoId).comSaldo(saldo)))

    @Transactional(readOnly = true)
    fun consultar(objetivoId: UUID): ObjetivoDTO = posicao(exigirVisivel(objetivoId))

    /** H-60, RF-77. */
    @Transactional(readOnly = true)
    fun posicaoConsolidada(): PosicaoConsolidadaDTO {
        val objetivos = repositorio.listarVisiveis().map { posicao(it) }
        return PosicaoConsolidadaDTO(
            objetivos = objetivos,
            totalAportado = somar(objetivos.map { it.totalAportado }),
            saldoTotal = somar(objetivos.map { it.saldoAtual }),
            rendimentoAgregado = somar(objetivos.map { it.rendimento }),
        )
    }

    private fun somar(valores: List<String>) =
        Dinheiro.soma(valores.map { Dinheiro.de(java.math.BigDecimal(it)) }).toString()

    private fun posicao(objetivo: ObjetivoInvestimento): ObjetivoDTO {
        // D-82: SUM na leitura, nunca coluna.
        val p = PosicaoDoObjetivo(objetivo, repositorio.totalAportado(objetivo.id))
        return ObjetivoDTO(
            id = objetivo.id.toString(),
            nome = objetivo.nome,
            meta = objetivo.meta?.toString(),
            prazoAlvo = objetivo.prazoAlvo?.toString(),
            saldoAtual = objetivo.saldoAtual.toString(),
            totalAportado = p.totalAportado.toString(),
            rendimento = p.rendimento.toString(),
            progresso = p.progresso?.toPlainString(),
            falta = p.falta?.toString(),
            aporteMensalNecessario = CalculadoraDeAporte.mensalNecessario(
                objetivo.meta, objetivo.saldoAtual, objetivo.prazoAlvo, LocalDate.now(relogio),
            )?.toString(),
            atrasado = p.atrasado,
            escopo = objetivo.escopo,
            grupoId = objetivo.grupo?.toString(),
            donoId = objetivo.dono.toString(),
        )
    }

    private fun validar(comando: CriarObjetivo) {
        if (comando.nome.isBlank()) throw ErroDeNegocio(CodigoErro.NOME_OBRIGATORIO)
        if (comando.meta != null && !comando.meta.ehPositivo()) {
            throw ErroDeNegocio(CodigoErro.META_INVALIDA)
        }
        if ((comando.escopo == Escopo.GRUPO) != (comando.grupoId != null)) {
            throw ErroDeNegocio(CodigoErro.GRUPO_INVALIDO)
        }
        if (comando.grupoId != null && comando.grupoId !in contexto.gruposDoUsuario()) {
            throw ErroDeNegocio(CodigoErro.GRUPO_NAO_ENCONTRADO)
        }
    }

    private fun exigirVisivel(id: UUID): ObjetivoInvestimento =
        repositorio.buscarVisivel(id) ?: throw ErroDeNegocio(CodigoErro.OBJETIVO_NAO_ENCONTRADO)
}
