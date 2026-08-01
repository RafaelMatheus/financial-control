package com.rafaelmatheus.financialcontrol.gasto.adaptador.web

import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.gasto.aplicacao.GastoDTO
import com.rafaelmatheus.financialcontrol.gasto.aplicacao.GastoService
import com.rafaelmatheus.financialcontrol.gasto.aplicacao.LancarGasto
import com.rafaelmatheus.financialcontrol.gasto.dominio.FiltroGasto
import com.rafaelmatheus.financialcontrol.gasto.dominio.ItemGasto
import com.rafaelmatheus.financialcontrol.gasto.dominio.PaginaDeGastos
import com.rafaelmatheus.financialcontrol.gasto.dominio.Paginacao
import com.rafaelmatheus.financialcontrol.gasto.dominio.TotaisDeGastos
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

/**
 * Bean Validation cobre **forma**, nunca regra de negocio — licao do defeito 2
 * de cd310cb. Nao ha `@Positive` no valor: quem decide isso e RN-L01, no
 * dominio, e dois validadores sobre o mesmo campo acabam discordando.
 */
data class GastoRequest(
    @field:NotBlank @field:Size(max = 200) val descricao: String,
    @field:NotNull val valor: BigDecimal,
    @field:NotNull val data: LocalDate,
    @field:NotNull val categoriaId: UUID,
    @field:NotNull val escopo: Escopo,
    val grupoId: UUID? = null,
) {
    fun paraComando() = LancarGasto(
        descricao = descricao,
        valor = Dinheiro.de(valor),
        data = data,
        categoriaId = categoriaId,
        escopo = escopo,
        grupoId = grupoId,
    )
}

data class ItemGastoDTO(
    val id: String,
    val descricao: String,
    val valor: String,
    val data: String,
    val categoriaId: String,
    val categoriaNome: String,
    /** RF-17, H-14: cada lancamento identifica o seu dono. */
    val donoId: String,
    val donoNome: String,
    val escopo: Escopo,
    val grupoId: String?,
)

data class PaginaGastosDTO(
    val itens: List<ItemGastoDTO>,
    val pagina: Int,
    val tamanho: Int,
    val total: Long,
)

data class TotalCategoriaDTO(val categoriaId: String, val categoriaNome: String, val total: String)

/**
 * **Nao existe campo `totalGeral`** (RN-T04, D-28), e a ausencia e a regra.
 * A soma de `totalPessoal` com `totalGrupo` nao tem significado: sao respostas a
 * perguntas diferentes sobre conjuntos que se sobrepoem.
 */
data class TotaisDTO(
    val totalPessoal: String,
    val totalGrupo: String,
    val porCategoriaPessoal: List<TotalCategoriaDTO>,
    val porCategoriaGrupo: List<TotalCategoriaDTO>,
)

@RestController
@RequestMapping("/gastos")
class GastoController(private val servico: GastoService) {

    /** H-15, H-09. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun lancar(@Valid @RequestBody corpo: GastoRequest): GastoDTO =
        servico.lancar(corpo.paraComando())

    /** H-13, RF-16: qualquer membro edita; o dono nao muda. */
    @PutMapping("/{id}")
    fun editar(@PathVariable id: UUID, @Valid @RequestBody corpo: GastoRequest): GastoDTO =
        servico.editar(id, corpo.paraComando())

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun excluir(@PathVariable id: UUID) = servico.excluir(id)

    /**
     * H-16, RF-21. Paginada, com teto de 100 por pagina.
     *
     * Os totais **nao** vem aqui: saem de `/gastos/totais` (D-57), para que nao
     * dependam de qual pagina esta aberta.
     */
    @GetMapping
    fun consultar(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) de: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) ate: LocalDate,
        @RequestParam(required = false) categoriaId: UUID?,
        @RequestParam(required = false) grupoId: UUID?,
        @RequestParam(required = false) escopo: Escopo?,
        @RequestParam(required = false) donoId: UUID?,
        @RequestParam(defaultValue = "0") pagina: Int,
        @RequestParam(defaultValue = "20") tamanho: Int,
    ): PaginaGastosDTO = servico.consultar(
        FiltroGasto(de, ate, categoriaId, grupoId, escopo, donoId),
        Paginacao(pagina, tamanho),
    ).paraDTO()

    /** H-17, RF-97: as duas grandezas, sobre o periodo inteiro. */
    @GetMapping("/totais")
    fun totalizar(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) de: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) ate: LocalDate,
        @RequestParam(required = false) categoriaId: UUID?,
        @RequestParam(required = false) grupoId: UUID?,
        @RequestParam(required = false) escopo: Escopo?,
        @RequestParam(required = false) donoId: UUID?,
    ): TotaisDTO = servico.totalizar(
        FiltroGasto(de, ate, categoriaId, grupoId, escopo, donoId),
    ).paraDTO()
}

private fun ItemGasto.paraDTO() = ItemGastoDTO(
    id = id.toString(),
    descricao = descricao,
    valor = valor.toString(),
    data = data.toString(),
    categoriaId = categoriaId.toString(),
    categoriaNome = categoriaNome,
    donoId = donoId.toString(),
    donoNome = donoNome,
    escopo = escopo,
    grupoId = grupoId?.toString(),
)

private fun PaginaDeGastos.paraDTO() =
    PaginaGastosDTO(itens.map { it.paraDTO() }, pagina, tamanho, total)

private fun TotaisDeGastos.paraDTO() = TotaisDTO(
    totalPessoal = totalPessoal.toString(),
    totalGrupo = totalGrupo.toString(),
    porCategoriaPessoal = porCategoriaPessoal.map {
        TotalCategoriaDTO(it.categoriaId.toString(), it.categoriaNome, it.total.toString())
    },
    porCategoriaGrupo = porCategoriaGrupo.map {
        TotalCategoriaDTO(it.categoriaId.toString(), it.categoriaNome, it.total.toString())
    },
)
