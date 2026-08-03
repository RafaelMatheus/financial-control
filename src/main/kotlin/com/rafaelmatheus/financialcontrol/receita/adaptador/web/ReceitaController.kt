package com.rafaelmatheus.financialcontrol.receita.adaptador.web

import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.receita.aplicacao.BalancoDTO
import com.rafaelmatheus.financialcontrol.receita.aplicacao.CadastrarReceita
import com.rafaelmatheus.financialcontrol.receita.aplicacao.PeriodoDeReceitasDTO
import com.rafaelmatheus.financialcontrol.receita.aplicacao.ReceitaDTO
import com.rafaelmatheus.financialcontrol.receita.aplicacao.ReceitaService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class ReceitaRequest(
    @field:NotBlank @field:Size(max = 200) val descricao: String,
    @field:NotNull val valor: BigDecimal,
    @field:NotNull val data: LocalDate,
) {
    fun paraComando() = CadastrarReceita(descricao, Dinheiro.de(valor), data)
}

/**
 * **Nao ha `escopo` nem `grupoId`** neste contrato, e a ausencia e a regra:
 * receitas sao individuais (P-05, RN-RC02). Nao existe receita da casa.
 */
@RestController
@RequestMapping("/receitas")
class ReceitaController(private val servico: ReceitaService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun cadastrar(@Valid @RequestBody corpo: ReceitaRequest): ReceitaDTO =
        servico.cadastrar(corpo.paraComando())

    @PutMapping("/{id}")
    fun editar(@PathVariable id: UUID, @Valid @RequestBody corpo: ReceitaRequest): ReceitaDTO =
        servico.editar(id, corpo.paraComando())

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun excluir(@PathVariable id: UUID) = servico.excluir(id)

    /** H-37, RF-40. */
    @GetMapping
    fun consultar(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) de: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) ate: LocalDate,
    ): PeriodoDeReceitasDTO = servico.consultar(de, ate)

    /** H-38, RF-41, RF-76: receitas − gastos − **aportes**. */
    @GetMapping("/balanco")
    fun balanco(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) de: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) ate: LocalDate,
    ): BalancoDTO = servico.balanco(de, ate)
}
