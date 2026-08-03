package com.rafaelmatheus.financialcontrol.compra.aplicacao

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
import com.rafaelmatheus.financialcontrol.compra.dominio.Compra
import com.rafaelmatheus.financialcontrol.compra.dominio.CompraComParcelas
import com.rafaelmatheus.financialcontrol.compra.dominio.CompraRepositorio
import com.rafaelmatheus.financialcontrol.compra.dominio.DivisorDeParcelas
import com.rafaelmatheus.financialcontrol.fatura.aplicacao.FaturaService
import com.rafaelmatheus.financialcontrol.fatura.dominio.ProtecaoFatura
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

data class LancarCompraParcelada(
    val descricao: String,
    /** **Valor TOTAL da compra** (D-67), nao o valor da parcela. */
    val valorTotal: Dinheiro,
    val numeroParcelas: Int,
    val dataCompra: LocalDate,
    val cartaoId: UUID,
    val categoriaId: UUID,
    val escopo: Escopo,
    val grupoId: UUID?,
)

data class ParcelaDTO(
    val id: String,
    val numero: Int,
    val posicao: String,
    val valor: String,
    val competencia: String,
)

data class CompraDTO(
    val id: String,
    val descricao: String,
    val valorTotal: String,
    val numeroParcelas: Int,
    val dataCompra: String,
    val cartaoId: String,
    val categoriaId: String,
    val donoId: String,
    val escopo: Escopo,
    val grupoId: String?,
    val parcelas: List<ParcelaDTO>,
)

fun CompraComParcelas.paraDTO() = CompraDTO(
    id = compra.id.toString(),
    descricao = compra.descricao,
    valorTotal = compra.valorTotal.toString(),
    numeroParcelas = compra.numeroParcelas,
    dataCompra = compra.dataCompra.toString(),
    cartaoId = compra.cartao.toString(),
    categoriaId = compra.categoria.toString(),
    donoId = compra.dono.toString(),
    escopo = compra.escopo,
    grupoId = compra.grupo?.toString(),
    parcelas = parcelas.sortedBy { it.numero }.map {
        ParcelaDTO(
            id = it.id.toString(),
            numero = it.numero,
            // RF-35, RN-P09: derivado, nunca campo.
            posicao = "${it.numero}/${compra.numeroParcelas}",
            valor = it.valor.toString(),
            competencia = it.competencia.toString(),
        )
    },
)

@Service
class CompraService(
    private val repositorio: CompraRepositorio,
    private val categorias: CategoriaRepositorio,
    private val cartoes: CartaoService,
    private val faturas: FaturaService,
    private val protecao: ProtecaoFatura,
    private val contexto: ContextoUsuario,
    private val relogio: Clock,
) {

    /** H-27, RF-29, RF-30. */
    @Transactional
    fun lancar(comando: LancarCompraParcelada): CompraDTO {
        val cartao = validar(comando)
        val competencias = competenciasDe(comando.dataCompra, cartao.diaFechamento, comando.numeroParcelas)

        // RN-P08: TODAS as competencias, e nao so a primeira. Uma compra de 12
        // parcelas toca 12 faturas; verificar so a primeira deixaria passar a
        // alteracao de uma parcela ja paga la na frente.
        protecao.exigirAlteracaoPermitida(comando.cartaoId, competencias)
        competencias.forEach { faturas.prepararParaLancamento(comando.cartaoId, it) }

        val compra = Compra.nova(
            descricao = comando.descricao,
            valorTotal = comando.valorTotal,
            numeroParcelas = comando.numeroParcelas,
            dataCompra = comando.dataCompra,
            cartao = comando.cartaoId,
            categoria = comando.categoriaId,
            dono = contexto.usuarioAtual(),
            escopo = comando.escopo,
            grupo = comando.grupoId,
            criadoEm = relogio.instant(),
        )
        val parcelas = DivisorDeParcelas.gerar(
            compraId = compra.id,
            valorTotal = compra.valorTotal,
            numeroParcelas = compra.numeroParcelas,
            primeiraCompetencia = competencias.first(),
        )
        return repositorio.salvar(compra, parcelas).paraDTO()
    }

    /**
     * H-30, RF-33. Edicao **por inteiro**: descarta e regenera as parcelas.
     *
     * ## O ponto facil de errar
     *
     * A verificacao de fatura paga cobre a **uniao das competencias antigas e
     * novas**. Mudar de 12 para 6 parcelas esvazia 6 faturas que antes tinham
     * valor — verificar so as novas deixaria alterar uma fatura paga **por
     * subtracao**, que e uma alteracao tao real quanto somar.
     */
    @Transactional
    fun editar(id: UUID, comando: LancarCompraParcelada): CompraDTO {
        val existente = repositorio.buscarComParcelas(id)
            ?: throw ErroDeNegocio(CodigoErro.LANCAMENTO_NAO_ENCONTRADO)
        val cartao = validar(comando)

        val antigas = repositorio.competenciasDe(id)
        val novas = competenciasDe(comando.dataCompra, cartao.diaFechamento, comando.numeroParcelas)

        protecao.exigirAlteracaoPermitida(comando.cartaoId, antigas + novas)
        novas.forEach { faturas.prepararParaLancamento(comando.cartaoId, it) }

        val compra = existente.compra.editada(
            descricao = comando.descricao,
            valorTotal = comando.valorTotal,
            numeroParcelas = comando.numeroParcelas,
            dataCompra = comando.dataCompra,
            categoria = comando.categoriaId,
            escopo = comando.escopo,
            grupo = comando.grupoId,
        )
        val parcelas = DivisorDeParcelas.gerar(
            compraId = compra.id,
            valorTotal = compra.valorTotal,
            numeroParcelas = compra.numeroParcelas,
            primeiraCompetencia = novas.first(),
        )
        return repositorio.salvar(compra, parcelas).paraDTO()
    }

    /** H-31, RF-34. As parcelas saem junto — `CASCADE` no banco. */
    @Transactional
    fun excluir(id: UUID) {
        val existente = repositorio.buscarComParcelas(id)
            ?: throw ErroDeNegocio(CodigoErro.LANCAMENTO_NAO_ENCONTRADO)
        protecao.exigirAlteracaoPermitida(existente.compra.cartao, repositorio.competenciasDe(id))
        repositorio.excluir(id)
    }

    @Transactional(readOnly = true)
    fun consultar(id: UUID): CompraDTO =
        repositorio.buscarComParcelas(id)?.paraDTO()
            ?: throw ErroDeNegocio(CodigoErro.LANCAMENTO_NAO_ENCONTRADO)

    private fun competenciasDe(data: LocalDate, diaFechamento: Int, n: Int): Set<Competencia> {
        val primeira = CalculadoraDeCompetencia.competenciaDe(data, diaFechamento)
        return (0 until n).map { primeira.mais(it.toLong()) }.toSet()
    }

    private fun validar(comando: LancarCompraParcelada) =
        cartoes.exigirAtivo(comando.cartaoId).also {
            if (comando.descricao.isBlank()) {
                throw ErroDeNegocio(
                    CodigoErro.NOME_OBRIGATORIO,
                    listOf(DetalheErro("descricao", "obrigatoria")),
                )
            }
            if (!comando.valorTotal.ehPositivo()) throw ErroDeNegocio(CodigoErro.VALOR_INVALIDO)
            if (comando.numeroParcelas < 1) {
                throw ErroDeNegocio(CodigoErro.NUMERO_PARCELAS_INVALIDO)
            }
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
