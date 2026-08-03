package com.rafaelmatheus.financialcontrol.orcamento.aplicacao

import com.rafaelmatheus.financialcontrol.categoria.dominio.CategoriaRepositorio
import com.rafaelmatheus.financialcontrol.common.dominio.BaseDoRealizado
import com.rafaelmatheus.financialcontrol.common.dominio.Competencia
import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.common.seguranca.ContextoUsuario
import com.rafaelmatheus.financialcontrol.common.web.CodigoErro
import com.rafaelmatheus.financialcontrol.common.web.ErroDeNegocio
import com.rafaelmatheus.financialcontrol.gasto.dominio.ConsultaDeRealizado
import com.rafaelmatheus.financialcontrol.orcamento.dominio.Acompanhamento
import com.rafaelmatheus.financialcontrol.orcamento.dominio.LinhaDoAcompanhamento
import com.rafaelmatheus.financialcontrol.orcamento.dominio.Orcamento
import com.rafaelmatheus.financialcontrol.orcamento.dominio.OrcamentoDuplicado
import com.rafaelmatheus.financialcontrol.orcamento.dominio.OrcamentoRepositorio
import com.rafaelmatheus.financialcontrol.orcamento.dominio.TotalDaBase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

data class DefinirOrcamento(
    val categoriaId: UUID,
    val competencia: Competencia,
    val valorTeto: Dinheiro,
    val base: BaseDoRealizado,
    val escopo: Escopo,
    val grupoId: UUID?,
)

data class OrcamentoDTO(
    val id: String,
    val categoriaId: String,
    val competencia: String,
    val valorTeto: String,
    val base: BaseDoRealizado,
    val escopo: Escopo,
    val grupoId: String?,
)

fun Orcamento.paraDTO() = OrcamentoDTO(
    id.toString(), categoria.toString(), competencia.toString(),
    valorTeto.toString(), base, escopo, grupo?.toString(),
)

data class LinhaDTO(
    val orcamentoId: String,
    val categoriaId: String,
    val categoriaNome: String,
    val base: BaseDoRealizado,
    val orcado: String,
    val realizado: String,
    val estourado: Boolean,
    val excedente: String,
    val disponivel: String,
)

data class TotalDaBaseDTO(val base: BaseDoRealizado, val orcado: String, val realizado: String)

/**
 * **Nao existe campo de total geral**, e a ausencia e a regra (RN-O08).
 *
 * Com bases diferentes entre categorias, somar os realizados de todas produziria
 * a soma de "quanto me comprometi" com "quanto vou pagar" — um numero sem
 * significado.
 *
 * Terceira aplicacao do mesmo padrao no ciclo, depois de RF-97/D-28 e de D-78:
 * apresentar lado a lado e nunca somar.
 */
data class AcompanhamentoDTO(
    val competencia: String,
    val categorias: List<LinhaDTO>,
    val totaisPorBase: List<TotalDaBaseDTO>,
)

@Service
class OrcamentoService(
    private val repositorio: OrcamentoRepositorio,
    private val categorias: CategoriaRepositorio,
    private val realizado: ConsultaDeRealizado,
    private val contexto: ContextoUsuario,
    private val relogio: Clock,
) {

    /** H-39, RF-42. */
    @Transactional
    fun definir(comando: DefinirOrcamento): OrcamentoDTO {
        validar(comando)
        val dono = contexto.usuarioAtual()

        // Verificar para dar mensagem; o indice unico e que garante (RN-O01).
        if (repositorio.existeParaCategoria(
                comando.categoriaId, comando.competencia, dono, comando.escopo, comando.grupoId,
            )
        ) {
            throw ErroDeNegocio(CodigoErro.ORCAMENTO_DUPLICADO)
        }

        return try {
            repositorio.salvar(
                Orcamento.novo(
                    comando.categoriaId, comando.competencia, comando.valorTeto, comando.base,
                    dono, comando.escopo, comando.grupoId, relogio.instant(),
                ),
            ).paraDTO()
        } catch (_: OrcamentoDuplicado) {
            throw ErroDeNegocio(CodigoErro.ORCAMENTO_DUPLICADO)
        }
    }

    @Transactional
    fun remover(id: UUID) {
        repositorio.buscarVisivel(id) ?: throw ErroDeNegocio(CodigoErro.ORCAMENTO_NAO_ENCONTRADO)
        repositorio.excluir(id)
    }

    /**
     * H-40, H-41, RF-43, RF-44 — e **J-02**.
     *
     * D-84: **uma consulta agrupada por base**, no maximo duas no total,
     * independentemente de quantas categorias estejam orcadas. O casamento com os
     * tetos acontece em memoria, sobre um mapa.
     *
     * Uma consulta por orcamento seria um N+1 escrito de proposito — e o
     * `ArquiteturaTest` nao o pegaria, porque ele e estrutural e N+1 e
     * comportamental.
     */
    @Transactional(readOnly = true)
    fun acompanhar(competencia: Competencia): AcompanhamentoDTO {
        val tetos = repositorio.listarDaCompetencia(competencia)

        // Uma consulta por (base, escopo, grupo) distinto — nao por orcamento.
        val realizados = tetos
            .map { Triple(it.base, it.escopo, it.grupo) }
            .distinct()
            .associateWith { (base, escopo, grupo) ->
                realizado.somarPorCategoria(competencia, base, escopo, grupo)
            }

        val nomes = categorias.listarVisiveis().associate { it.id to it.nome }

        val linhas = tetos.map { teto ->
            val mapa = realizados[Triple(teto.base, teto.escopo, teto.grupo)].orEmpty()
            LinhaDoAcompanhamento(
                orcamentoId = teto.id,
                categoriaId = teto.categoria,
                categoriaNome = nomes[teto.categoria] ?: "",
                base = teto.base,
                orcado = teto.valorTeto,
                realizado = mapa[teto.categoria] ?: Dinheiro.ZERO,
            )
        }

        // RN-O08: totais SEPARADOS POR BASE. Nunca um escalar.
        val totais = linhas.groupBy { it.base }.map { (base, doGrupo) ->
            TotalDaBase(
                base = base,
                orcado = Dinheiro.soma(doGrupo.map { it.orcado }),
                realizado = Dinheiro.soma(doGrupo.map { it.realizado }),
            )
        }

        return Acompanhamento(competencia, linhas, totais).paraDTO()
    }

    private fun validar(comando: DefinirOrcamento) {
        // RN-O02: ZERO e valido. So negativo e recusado.
        if (comando.valorTeto.ehNegativo()) throw ErroDeNegocio(CodigoErro.VALOR_INVALIDO)
        if ((comando.escopo == Escopo.GRUPO) != (comando.grupoId != null)) {
            throw ErroDeNegocio(CodigoErro.GRUPO_INVALIDO)
        }
        if (comando.grupoId != null && comando.grupoId !in contexto.gruposDoUsuario()) {
            throw ErroDeNegocio(CodigoErro.GRUPO_NAO_ENCONTRADO)
        }
        categorias.buscarVisivel(comando.categoriaId)
            ?: throw ErroDeNegocio(CodigoErro.CATEGORIA_NAO_ENCONTRADA)
    }
}

private fun Acompanhamento.paraDTO() = AcompanhamentoDTO(
    competencia = competencia.toString(),
    categorias = categorias.map {
        LinhaDTO(
            orcamentoId = it.orcamentoId.toString(),
            categoriaId = it.categoriaId.toString(),
            categoriaNome = it.categoriaNome,
            base = it.base,
            orcado = it.orcado.toString(),
            realizado = it.realizado.toString(),
            estourado = it.estourado,
            excedente = it.excedente.toString(),
            disponivel = it.disponivel.toString(),
        )
    },
    totaisPorBase = totaisPorBase.map { TotalDaBaseDTO(it.base, it.orcado.toString(), it.realizado.toString()) },
)
