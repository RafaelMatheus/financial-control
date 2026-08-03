package com.rafaelmatheus.financialcontrol.orcamento.adaptador.web

import com.rafaelmatheus.financialcontrol.common.dominio.BaseDoRealizado
import com.rafaelmatheus.financialcontrol.common.dominio.Competencia
import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.orcamento.aplicacao.AcompanhamentoDTO
import com.rafaelmatheus.financialcontrol.orcamento.aplicacao.DefinirOrcamento
import com.rafaelmatheus.financialcontrol.orcamento.aplicacao.OrcamentoDTO
import com.rafaelmatheus.financialcontrol.orcamento.aplicacao.OrcamentoService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID

data class OrcamentoRequest(
    @field:NotNull val categoriaId: UUID,
    @field:NotBlank val competencia: String,
    @field:NotNull val valorTeto: BigDecimal,
    /** D-77, J-02: cada teto declara a sua base. */
    @field:NotNull val base: BaseDoRealizado,
    @field:NotNull val escopo: Escopo,
    val grupoId: UUID? = null,
) {
    fun paraComando() = DefinirOrcamento(
        categoriaId, Competencia.de(competencia), Dinheiro.de(valorTeto), base, escopo, grupoId,
    )
}

@RestController
@RequestMapping("/orcamentos")
class OrcamentoController(private val servico: OrcamentoService) {

    /** H-39, RF-42. Teto **zero** e valido. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun definir(@Valid @RequestBody corpo: OrcamentoRequest): OrcamentoDTO =
        servico.definir(corpo.paraComando())

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remover(@PathVariable id: UUID) = servico.remover(id)

    /**
     * H-40, H-41, RF-43, RF-44 — **e J-02**.
     *
     * A resposta traz a comparacao **por categoria** e os totais **separados por
     * base**. Nao existe um total geral: com bases diferentes, somar produziria a
     * soma de "quanto me comprometi" com "quanto vou pagar".
     */
    @GetMapping("/{competencia}")
    fun acompanhar(@PathVariable competencia: String): AcompanhamentoDTO =
        servico.acompanhar(Competencia.de(competencia))
}
