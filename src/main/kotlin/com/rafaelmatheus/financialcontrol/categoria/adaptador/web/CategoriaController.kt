package com.rafaelmatheus.financialcontrol.categoria.adaptador.web

import com.rafaelmatheus.financialcontrol.categoria.aplicacao.CategoriaDTO
import com.rafaelmatheus.financialcontrol.categoria.aplicacao.CategoriaService
import com.rafaelmatheus.financialcontrol.categoria.aplicacao.CriarCategoria
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Bean Validation aqui cobre **forma**, nunca regra de negocio.
 *
 * A licao do defeito 2 de cd310cb: um `@Email` no DTO rodava ANTES da
 * normalizacao e rejeitava espacos que a regra de dominio mandava remover — dois
 * validadores discordando sobre o mesmo campo. Tamanho e obrigatoriedade sao
 * seguros porque nao dependem de nenhuma transformacao posterior.
 */
data class CategoriaRequest(
    @field:NotBlank @field:Size(max = 80) val nome: String,
    @field:NotNull val escopo: Escopo,
    val grupoId: UUID? = null,
)

data class RenomearRequest(@field:NotBlank @field:Size(max = 80) val nome: String)

@RestController
@RequestMapping("/categorias")
class CategoriaController(private val servico: CategoriaService) {

    /** H-33, RF-36. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun criar(@Valid @RequestBody corpo: CategoriaRequest): CategoriaDTO =
        servico.criar(CriarCategoria(corpo.nome, corpo.escopo, corpo.grupoId))

    /**
     * H-33 e **H-35**: sem nenhuma categoria visivel, esta chamada cria o
     * conjunto inicial e o devolve (RN-C08, D-56).
     */
    @GetMapping
    fun listar(): List<CategoriaDTO> = servico.listar()

    @PutMapping("/{id}")
    fun renomear(@PathVariable id: UUID, @Valid @RequestBody corpo: RenomearRequest): CategoriaDTO =
        servico.renomear(id, corpo.nome)

    /**
     * H-34, RF-37. Sem `realocarPara`, responde 409 com a contagem de
     * lancamentos vinculados nos detalhes.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun excluir(@PathVariable id: UUID, @RequestParam(required = false) realocarPara: UUID?) =
        servico.excluir(id, realocarPara)
}
