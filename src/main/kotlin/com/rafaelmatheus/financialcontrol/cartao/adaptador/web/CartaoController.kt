package com.rafaelmatheus.financialcontrol.cartao.adaptador.web

import com.rafaelmatheus.financialcontrol.cartao.aplicacao.CadastrarCartao
import com.rafaelmatheus.financialcontrol.cartao.aplicacao.CartaoDTO
import com.rafaelmatheus.financialcontrol.cartao.aplicacao.CartaoService
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
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
import java.util.UUID

/**
 * Bean Validation cobre **forma**, nunca regra de negocio — licao do defeito 2
 * de `cd310cb`.
 *
 * Sem `@Min`/`@Max` nos dias: quem decide o intervalo e RN-K01, no dominio.
 * Dois validadores sobre o mesmo campo acabam discordando, e o de baixo e o que
 * conhece a regra.
 */
data class CartaoRequest(
    @field:NotBlank @field:Size(max = 80) val apelido: String,
    @field:NotNull val diaFechamento: Int,
    @field:NotNull val diaVencimento: Int,
    @field:NotNull val escopo: Escopo,
    val grupoId: UUID? = null,
) {
    fun paraComando() = CadastrarCartao(apelido, diaFechamento, diaVencimento, escopo, grupoId)
}

@RestController
@RequestMapping("/cartoes")
class CartaoController(private val servico: CartaoService) {

    /** H-18, H-19. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun cadastrar(@Valid @RequestBody corpo: CartaoRequest): CartaoDTO =
        servico.cadastrar(corpo.paraComando())

    @GetMapping
    fun listar(): List<CartaoDTO> = servico.listar()

    @GetMapping("/{id}")
    fun consultar(@PathVariable id: UUID): CartaoDTO = servico.consultar(id)

    @PutMapping("/{id}")
    fun editar(@PathVariable id: UUID, @Valid @RequestBody corpo: CartaoRequest): CartaoDTO =
        servico.editar(id, corpo.paraComando())

    /**
     * RN-K04. **Encerra, nao apaga**: o historico de faturas e parcelas
     * permanece. Por isso `DELETE` aqui significa encerrar, e nao remover — a
     * alternativa seria um `POST /cartoes/{id}/encerramento`, mais fiel e menos
     * previsivel para quem consome.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun encerrar(@PathVariable id: UUID) = servico.encerrar(id)
}
