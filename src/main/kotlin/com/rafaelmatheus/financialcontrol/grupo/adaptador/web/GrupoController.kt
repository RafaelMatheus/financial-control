package com.rafaelmatheus.financialcontrol.grupo.adaptador.web

import com.rafaelmatheus.financialcontrol.grupo.aplicacao.GrupoDTO
import com.rafaelmatheus.financialcontrol.grupo.aplicacao.GrupoDetalheDTO
import com.rafaelmatheus.financialcontrol.grupo.aplicacao.GrupoService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
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

data class GrupoRequest(@field:NotBlank @field:Size(max = 120) val nome: String)

data class AdicionarMembroRequest(@field:NotBlank val usuarioId: String)

@RestController
@RequestMapping("/grupos")
class GrupoController(private val servico: GrupoService) {

    /** H-05, RF-06. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun criar(@Valid @RequestBody corpo: GrupoRequest): GrupoDTO = servico.criar(corpo.nome)

    /** H-05, RF-07. Lista vazia e resposta valida. */
    @GetMapping
    fun listar(): List<GrupoDTO> = servico.listarMeusGrupos()

    /** H-06, RF-08. 404 para quem nao e membro — nunca 403. */
    @GetMapping("/{id}")
    fun consultar(@PathVariable id: UUID): GrupoDetalheDTO = servico.consultar(id)

    @PutMapping("/{id}")
    fun renomear(@PathVariable id: UUID, @Valid @RequestBody corpo: GrupoRequest): GrupoDTO =
        servico.renomear(id, corpo.nome)

    @PostMapping("/{id}/membros")
    fun adicionarMembro(
        @PathVariable id: UUID,
        @Valid @RequestBody corpo: AdicionarMembroRequest,
    ): GrupoDetalheDTO = servico.adicionarMembro(id, UUID.fromString(corpo.usuarioId))

    @DeleteMapping("/{id}/membros/{usuarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removerMembro(@PathVariable id: UUID, @PathVariable usuarioId: UUID) =
        servico.removerMembro(id, usuarioId)

    /** H-08, RF-10. */
    @DeleteMapping("/{id}/membros/eu")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun sair(@PathVariable id: UUID) = servico.sair(id)
}
