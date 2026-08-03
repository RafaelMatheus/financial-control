package com.rafaelmatheus.financialcontrol.investimento.adaptador.web

import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.investimento.aplicacao.CriarObjetivo
import com.rafaelmatheus.financialcontrol.investimento.aplicacao.InvestimentoService
import com.rafaelmatheus.financialcontrol.investimento.aplicacao.ObjetivoDTO
import com.rafaelmatheus.financialcontrol.investimento.aplicacao.PosicaoConsolidadaDTO
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class ObjetivoRequest(
    @field:NotBlank @field:Size(max = 120) val nome: String,
    /** RF-73: meta e **opcional** — "Geral" pode nao ter alvo. */
    val meta: BigDecimal? = null,
    /** RF-74: prazo tambem e opcional. */
    val prazoAlvo: LocalDate? = null,
    @field:NotNull val escopo: Escopo,
    val grupoId: UUID? = null,
) {
    fun paraComando() = CriarObjetivo(nome, meta?.let { Dinheiro.de(it) }, prazoAlvo, escopo, grupoId)
}

data class AporteRequest(
    @field:NotNull val valor: BigDecimal,
    @field:NotNull val data: LocalDate,
)

data class SaldoRequest(@field:NotNull val saldoAtual: BigDecimal)

@RestController
@RequestMapping("/investimentos")
class InvestimentoController(private val servico: InvestimentoService) {

    /** H-52, RF-68. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun criar(@Valid @RequestBody corpo: ObjetivoRequest): ObjetivoDTO =
        servico.criarObjetivo(corpo.paraComando())

    @PutMapping("/{id}")
    fun editar(@PathVariable id: UUID, @Valid @RequestBody corpo: ObjetivoRequest): ObjetivoDTO =
        servico.editarObjetivo(id, corpo.paraComando())

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun excluir(@PathVariable id: UUID) = servico.excluirObjetivo(id)

    @GetMapping("/{id}")
    fun consultar(@PathVariable id: UUID): ObjetivoDTO = servico.consultar(id)

    /** H-53, RF-69. D-80: soma ao saldo, e o rendimento nao se move. */
    @PostMapping("/{id}/aportes")
    fun aportar(@PathVariable id: UUID, @Valid @RequestBody corpo: AporteRequest): ObjetivoDTO =
        servico.aportar(id, Dinheiro.de(corpo.valor), corpo.data)

    /** D-83: simetrico ao aportar — subtrai do saldo. */
    @DeleteMapping("/aportes/{aporteId}")
    fun excluirAporte(@PathVariable aporteId: UUID): ObjetivoDTO = servico.excluirAporte(aporteId)

    /** H-54, RF-71. O sistema nao tem cotacao; o saldo e informacao do usuario. */
    @PutMapping("/{id}/saldo")
    fun atualizarSaldo(@PathVariable id: UUID, @Valid @RequestBody corpo: SaldoRequest): ObjetivoDTO =
        servico.atualizarSaldo(id, Dinheiro.de(corpo.saldoAtual))

    /** H-60, RF-77. */
    @GetMapping
    fun posicaoConsolidada(): PosicaoConsolidadaDTO = servico.posicaoConsolidada()
}
