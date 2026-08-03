package com.rafaelmatheus.financialcontrol.compra.adaptador.web

import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.compra.aplicacao.CompraDTO
import com.rafaelmatheus.financialcontrol.compra.aplicacao.CompraService
import com.rafaelmatheus.financialcontrol.compra.aplicacao.LancarCompraParcelada
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * ⚠️ O campo e `valorTotal`, e nao `valorParcela` (D-67).
 *
 * RF-29 e H-27 descrevem o inverso e ficaram **desatualizados por decisao** —
 * pendencia de requisitos registrada na Functional Design.
 */
data class CompraRequest(
    @field:NotBlank @field:Size(max = 200) val descricao: String,
    @field:NotNull val valorTotal: BigDecimal,
    @field:NotNull val numeroParcelas: Int,
    @field:NotNull val dataCompra: LocalDate,
    @field:NotNull val cartaoId: UUID,
    @field:NotNull val categoriaId: UUID,
    @field:NotNull val escopo: Escopo,
    val grupoId: UUID? = null,
) {
    fun paraComando() = LancarCompraParcelada(
        descricao = descricao,
        valorTotal = Dinheiro.de(valorTotal),
        numeroParcelas = numeroParcelas,
        dataCompra = dataCompra,
        cartaoId = cartaoId,
        categoriaId = categoriaId,
        escopo = escopo,
        grupoId = grupoId,
    )
}

@RestController
@RequestMapping("/compras")
class CompraController(private val servico: CompraService) {

    /** H-27. A resposta ja traz as N parcelas com competência e posição. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun lancar(@Valid @RequestBody corpo: CompraRequest): CompraDTO =
        servico.lancar(corpo.paraComando())

    @GetMapping("/{id}")
    fun consultar(@PathVariable id: UUID): CompraDTO = servico.consultar(id)

    /**
     * H-30, RF-33. **Edicao por inteiro**: nao existe rota para editar parcela.
     * A ausencia e a regra — RN-P06 protege a invariante de RF-32, e uma rota de
     * parcela convidaria a viola-la.
     */
    @PutMapping("/{id}")
    fun editar(@PathVariable id: UUID, @Valid @RequestBody corpo: CompraRequest): CompraDTO =
        servico.editar(id, corpo.paraComando())

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun excluir(@PathVariable id: UUID) = servico.excluir(id)
}
