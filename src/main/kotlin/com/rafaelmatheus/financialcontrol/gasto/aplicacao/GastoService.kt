package com.rafaelmatheus.financialcontrol.gasto.aplicacao

import com.rafaelmatheus.financialcontrol.cartao.aplicacao.CartaoService
import com.rafaelmatheus.financialcontrol.categoria.dominio.CategoriaRepositorio
import com.rafaelmatheus.financialcontrol.common.dominio.CalculadoraDeCompetencia
import com.rafaelmatheus.financialcontrol.common.dominio.Competencia
import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.common.seguranca.ContextoUsuario
import com.rafaelmatheus.financialcontrol.common.web.CodigoErro
import com.rafaelmatheus.financialcontrol.common.web.DetalheErro
import com.rafaelmatheus.financialcontrol.common.web.ErroDeNegocio
import com.rafaelmatheus.financialcontrol.gasto.dominio.FiltroGasto
import com.rafaelmatheus.financialcontrol.gasto.dominio.Gasto
import com.rafaelmatheus.financialcontrol.gasto.dominio.GastoRepositorio
import com.rafaelmatheus.financialcontrol.gasto.dominio.PaginaDeGastos
import com.rafaelmatheus.financialcontrol.gasto.dominio.Paginacao
import com.rafaelmatheus.financialcontrol.gasto.dominio.TotaisDeGastos
import com.rafaelmatheus.financialcontrol.fatura.aplicacao.FaturaService
import com.rafaelmatheus.financialcontrol.fatura.dominio.ProtecaoFatura
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * Comando de lancamento.
 *
 * **Nao tem `donoId`**, e a ausencia e a regra RN-L05 escrita na assinatura.
 * Mesma tecnica de `atualizarPerfil` em U1, que nao aceita `usuarioId`: um
 * metodo que aceitasse o parametro e depois verificasse seria uma verificacao
 * que alguem pode esquecer; um metodo que nao o aceita nao tem como errar.
 */
data class LancarGasto(
    val descricao: String,
    val valor: Dinheiro,
    val data: LocalDate,
    val categoriaId: UUID,
    val escopo: Escopo,
    val grupoId: UUID?,
    /**
     * ⚠️ **Acrescentado em U3** — a segunda metade do componente `gasto`.
     *
     * Nulo = gasto a vista, o unico caso que U2 conhecia. Preenchido = gasto no
     * cartao, e entao a competencia da fatura e calculada e o lancamento passa
     * a incidir nela (RF-19, RF-60).
     */
    val cartaoId: UUID? = null,
)

@Service
class GastoService(
    private val repositorio: GastoRepositorio,
    private val categorias: CategoriaRepositorio,
    private val contexto: ContextoUsuario,
    private val relogio: Clock,
    // --- U3: integracao com cartao ---
    private val cartoes: CartaoService,
    private val faturas: FaturaService,
    private val protecao: ProtecaoFatura,
) {

    /** H-15, H-09, RF-18, RF-19, RF-11. */
    @Transactional
    fun lancar(comando: LancarGasto): GastoDTO {
        validar(comando.descricao, comando.valor, comando.categoriaId, comando.escopo, comando.grupoId)

        val competencia = prepararCartao(comando)

        return repositorio.salvar(
            Gasto.novo(
                descricao = comando.descricao,
                valor = comando.valor,
                data = comando.data,
                categoria = comando.categoriaId,
                dono = contexto.usuarioAtual(),
                escopo = comando.escopo,
                grupo = comando.grupoId,
                criadoEm = relogio.instant(),
            ).copy(cartao = comando.cartaoId, competencia = competencia),
        ).paraDTO()
    }

    /**
     * H-13, RF-16, RF-20.
     *
     * `buscarVisivel` faz o trabalho de permissao **sozinho**: nesta unidade
     * enxergar e poder editar sao a mesma coisa (RN-L06). Uma verificacao
     * separada de "e o dono?" seria uma que pode divergir do predicado.
     *
     * O `dono` nao aparece em lugar nenhum deste metodo. Nao e validacao —
     * e operacao que nao existe (RN-L05).
     */
    @Transactional
    fun editar(id: UUID, comando: LancarGasto): GastoDTO {
        val gasto = exigirVisivel(id)
        validar(comando.descricao, comando.valor, comando.categoriaId, comando.escopo, comando.grupoId)

        // RN-P08 aplicado a gasto: a UNIAO da competencia antiga e da nova. Tirar
        // um gasto de uma fatura paga e alterar essa fatura tanto quanto por um.
        if (gasto.cartao != null && gasto.competencia != null) {
            protecao.exigirAlteracaoPermitida(gasto.cartao, gasto.competencia)
        }
        val competencia = prepararCartao(comando)

        return repositorio.salvar(
            gasto.editado(
                descricao = comando.descricao,
                valor = comando.valor,
                data = comando.data,
                categoria = comando.categoriaId,
                escopo = comando.escopo,
                grupo = comando.grupoId,
            ).copy(cartao = comando.cartaoId, competencia = competencia),
        ).paraDTO()
    }

    /** H-15, RF-20. */
    @Transactional
    fun excluir(id: UUID) {
        val gasto = exigirVisivel(id)
        // RF-95, H-24: excluir uma compra de fatura paga e bloqueado.
        if (gasto.cartao != null && gasto.competencia != null) {
            protecao.exigirAlteracaoPermitida(gasto.cartao, gasto.competencia)
        }
        repositorio.excluir(id)
    }

    /**
     * A integracao de U3 (RF-19, RF-25, RF-60, RF-95).
     *
     * Sem cartao, devolve nulo e nada muda — e o gasto a vista que U2 ja
     * conhecia. Com cartao, calcula a competencia pela **data da compra e pelo
     * dia de FECHAMENTO** (nunca o de vencimento, RF-61), verifica se aquela
     * fatura esta paga e prepara a fatura para receber o lancamento.
     */
    private fun prepararCartao(comando: LancarGasto): Competencia? {
        val cartaoId = comando.cartaoId ?: return null
        val cartao = cartoes.exigirAtivo(cartaoId)
        val competencia = CalculadoraDeCompetencia.competenciaDe(comando.data, cartao.diaFechamento)
        protecao.exigirAlteracaoPermitida(cartaoId, competencia)
        faturas.prepararParaLancamento(cartaoId, competencia)
        return competencia
    }

    /** H-16, RF-21. Paginada; os totais vem de [totalizar] (D-57). */
    @Transactional(readOnly = true)
    fun consultar(filtro: FiltroGasto, pagina: Paginacao): PaginaDeGastos =
        repositorio.consultar(exigirGrupoQuandoNecessario(filtro), pagina)

    /** H-17, RF-22, RF-97. */
    @Transactional(readOnly = true)
    fun totalizar(filtro: FiltroGasto): TotaisDeGastos =
        repositorio.totalizar(exigirGrupoQuandoNecessario(filtro))

    /**
     * RN-T05, D-55.
     *
     * Com o usuario em dois grupos e sem filtro, `totalGrupo` somaria a casa com
     * a viagem num numero que nao descreve nenhuma das duas. Com zero ou um
     * grupo nao ha ambiguidade, e o unico grupo e assumido.
     *
     * **Nao quebra H-16**: o filtro escolhe *qual* grupo, e a listagem continua
     * misturando os gastos pessoais com os daquele grupo.
     */
    private fun exigirGrupoQuandoNecessario(filtro: FiltroGasto): FiltroGasto {
        if (filtro.grupoId != null) {
            if (filtro.grupoId !in contexto.gruposDoUsuario()) {
                throw ErroDeNegocio(CodigoErro.GRUPO_NAO_ENCONTRADO)
            }
            return filtro
        }
        val grupos = contexto.gruposDoUsuario()
        return when (grupos.size) {
            0 -> filtro
            1 -> filtro.copy(grupoId = grupos.first())
            else -> throw ErroDeNegocio(
                CodigoErro.GRUPO_OBRIGATORIO,
                listOf(DetalheErro("grupoId", "obrigatorio quando voce participa de mais de um grupo")),
            )
        }
    }

    private fun validar(
        descricao: String,
        valor: Dinheiro,
        categoriaId: UUID,
        escopo: Escopo,
        grupoId: UUID?,
    ) {
        if (descricao.isBlank()) {
            throw ErroDeNegocio(CodigoErro.NOME_OBRIGATORIO, listOf(DetalheErro("descricao", "obrigatoria")))
        }
        // RN-L01. Zero e negativo sao rejeitados: estorno nao e gasto negativo,
        // e nao ha requisito de estorno.
        if (!valor.ehPositivo()) throw ErroDeNegocio(CodigoErro.VALOR_INVALIDO)

        // RN-L04, E-09.
        if ((escopo == Escopo.GRUPO) != (grupoId != null)) {
            throw ErroDeNegocio(CodigoErro.GRUPO_INVALIDO)
        }
        if (grupoId != null && grupoId !in contexto.gruposDoUsuario()) {
            throw ErroDeNegocio(CodigoErro.GRUPO_NAO_ENCONTRADO)
        }

        // RN-L02 e RN-L03: a categoria precisa ser **visivel**, e so isso. O
        // escopo dela nao precisa casar com o do gasto (D-60) — ele governa quem
        // a ve e quem a edita, nao a que lancamento ela se aplica.
        categorias.buscarVisivel(categoriaId)
            ?: throw ErroDeNegocio(CodigoErro.CATEGORIA_NAO_ENCONTRADA)
    }

    /** 404 e nao 403 (RN-L06): quem nao enxerga nao descobre que existe. */
    private fun exigirVisivel(id: UUID): Gasto =
        repositorio.buscarVisivel(id) ?: throw ErroDeNegocio(CodigoErro.LANCAMENTO_NAO_ENCONTRADO)
}

data class GastoDTO(
    val id: String,
    val descricao: String,
    val valor: String,
    val data: String,
    val categoriaId: String,
    val donoId: String,
    val escopo: Escopo,
    val grupoId: String?,
    /** U3: nulo em gasto a vista. */
    val cartaoId: String?,
    val competencia: String?,
)

fun Gasto.paraDTO() = GastoDTO(
    id = id.toString(),
    descricao = descricao,
    valor = valor.toString(),
    data = data.toString(),
    categoriaId = categoria.toString(),
    donoId = dono.toString(),
    escopo = escopo,
    grupoId = grupo?.toString(),
    cartaoId = cartao?.toString(),
    competencia = competencia?.toString(),
)
