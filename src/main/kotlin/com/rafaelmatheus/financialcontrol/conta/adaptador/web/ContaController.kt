package com.rafaelmatheus.financialcontrol.conta.adaptador.web

import com.rafaelmatheus.financialcontrol.common.dominio.Competencia
import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.conta.aplicacao.CadastrarConta
import com.rafaelmatheus.financialcontrol.conta.aplicacao.CadastrarRecorrente
import com.rafaelmatheus.financialcontrol.conta.aplicacao.ContaDTO
import com.rafaelmatheus.financialcontrol.conta.aplicacao.ContaService
import com.rafaelmatheus.financialcontrol.conta.aplicacao.RecorrenteDTO
import com.rafaelmatheus.financialcontrol.conta.aplicacao.RecorrenteService
import com.rafaelmatheus.financialcontrol.conta.aplicacao.VencimentosDTO
import com.rafaelmatheus.financialcontrol.conta.dominio.TipoConta
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class ContaRequest(
    @field:NotBlank @field:Size(max = 200) val descricao: String,
    @field:NotNull val valor: BigDecimal,
    @field:NotNull val dataVencimento: LocalDate,
    @field:NotNull val tipo: TipoConta,
    @field:NotNull val categoriaId: UUID,
    @field:NotNull val escopo: Escopo,
    val grupoId: UUID? = null,
) {
    fun paraComando() = CadastrarConta(
        descricao, Dinheiro.de(valor), dataVencimento, tipo, categoriaId, escopo, grupoId,
    )
}

data class PagamentoRequest(
    @field:NotNull val dataPagamento: LocalDate,
    /** RF-64, H-48: energia e gas variam mes a mes. */
    val valorAjustado: BigDecimal? = null,
)

@RestController
@RequestMapping("/contas")
class ContaController(private val servico: ContaService) {

    /** H-42, RF-55. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun cadastrar(@Valid @RequestBody corpo: ContaRequest): ContaDTO =
        servico.cadastrar(corpo.paraComando())

    @PutMapping("/{id}")
    fun editar(@PathVariable id: UUID, @Valid @RequestBody corpo: ContaRequest): ContaDTO =
        servico.editar(id, corpo.paraComando())

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun excluir(@PathVariable id: UUID) = servico.excluir(id)

    /** H-44, RF-57. Quitar a conta de uma fatura **e** pagar a fatura (D-70). */
    @PostMapping("/{id}/pagamento")
    fun marcarPaga(@PathVariable id: UUID, @Valid @RequestBody corpo: PagamentoRequest): ContaDTO =
        servico.marcarPaga(id, corpo.dataPagamento, corpo.valorAjustado?.let { Dinheiro.de(it) })

    /** H-23, RF-94. A unica via de corrigir lancamento em fatura paga. */
    @DeleteMapping("/{id}/pagamento")
    fun desmarcarPagamento(@PathVariable id: UUID): ContaDTO = servico.desmarcarPagamento(id)

    /**
     * H-43, RF-58. **A visao onde tudo converge**: fatura de cartao, PIX, boleto
     * e conta de servico, ordenados por vencimento, com o total do periodo.
     *
     * Traz tambem as ocorrencias recorrentes ainda nao materializadas, por
     * projecao (D-72) — indistinguiveis das materializadas, exceto pelo `id`
     * nulo, que existe para quem precisar agir sobre elas.
     */
    @GetMapping("/vencimentos")
    fun vencimentos(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) de: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) ate: LocalDate,
    ): VencimentosDTO = servico.vencimentosDoPeriodo(de, ate)

    /** H-50, RF-66. */
    @GetMapping("/a-vencer")
    fun aVencer(@RequestParam(defaultValue = "30") dias: Int): VencimentosDTO = servico.aVencer(dias)

    @GetMapping("/vencidas")
    fun vencidas(): List<ContaDTO> = servico.vencidas()

    /**
     * D-72: materializa a ocorrencia projetada, para que ela possa receber
     * pagamento ou valor ajustado. Idempotente — se ja existir, devolve a linha.
     */
    @PostMapping("/recorrentes/{recorrenteId}/ocorrencias/{competencia}")
    fun materializar(
        @PathVariable recorrenteId: UUID,
        @PathVariable competencia: String,
    ): ContaDTO = servico.materializarOcorrencia(recorrenteId, Competencia.de(competencia))
}

data class RecorrenteRequest(
    @field:NotBlank @field:Size(max = 200) val descricao: String,
    @field:NotNull val valorBase: BigDecimal,
    @field:NotNull val diaVencimento: Int,
    @field:NotNull val tipo: TipoConta,
    @field:NotNull val categoriaId: UUID,
    @field:NotNull val escopo: Escopo,
    val grupoId: UUID? = null,
    @field:NotBlank val inicioEm: String,
) {
    fun paraComando() = CadastrarRecorrente(
        descricao, Dinheiro.de(valorBase), diaVencimento, tipo, categoriaId, escopo, grupoId,
        Competencia.de(inicioEm),
    )
}

@RestController
@RequestMapping("/recorrentes")
class RecorrenteController(private val servico: RecorrenteService) {

    /** H-46, RF-62. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun cadastrar(@Valid @RequestBody corpo: RecorrenteRequest): RecorrenteDTO =
        servico.cadastrar(corpo.paraComando())

    @GetMapping
    fun listar(): List<RecorrenteDTO> = servico.listar()

    /**
     * H-51, RF-67. **Encerra, nao apaga**: as ocorrencias ja materializadas
     * permanecem com o seu historico de pagamento.
     */
    @DeleteMapping("/{id}")
    fun encerrar(
        @PathVariable id: UUID,
        @RequestParam aPartirDe: String,
    ): RecorrenteDTO = servico.encerrar(id, Competencia.de(aPartirDe))
}
