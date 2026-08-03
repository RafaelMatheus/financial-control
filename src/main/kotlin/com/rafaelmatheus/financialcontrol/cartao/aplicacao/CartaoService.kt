package com.rafaelmatheus.financialcontrol.cartao.aplicacao

import com.rafaelmatheus.financialcontrol.cartao.dominio.Cartao
import com.rafaelmatheus.financialcontrol.cartao.dominio.CartaoRepositorio
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.common.seguranca.ContextoUsuario
import com.rafaelmatheus.financialcontrol.common.web.CodigoErro
import com.rafaelmatheus.financialcontrol.common.web.DetalheErro
import com.rafaelmatheus.financialcontrol.common.web.ErroDeNegocio
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

data class CartaoDTO(
    val id: String,
    val apelido: String,
    val diaFechamento: Int,
    val diaVencimento: Int,
    val escopo: Escopo,
    val grupoId: String?,
    val donoId: String,
    val ativo: Boolean,
)

fun Cartao.paraDTO() = CartaoDTO(
    id = id.toString(),
    apelido = apelido,
    diaFechamento = diaFechamento,
    diaVencimento = diaVencimento,
    escopo = escopo,
    grupoId = grupo?.toString(),
    donoId = dono.toString(),
    ativo = estaAtivo(),
)

data class CadastrarCartao(
    val apelido: String,
    val diaFechamento: Int,
    val diaVencimento: Int,
    val escopo: Escopo,
    val grupoId: UUID?,
)

@Service
class CartaoService(
    private val repositorio: CartaoRepositorio,
    private val contexto: ContextoUsuario,
    private val relogio: Clock,
) {

    /** H-18, H-19, RF-23, RF-24. */
    @Transactional
    fun cadastrar(comando: CadastrarCartao): CartaoDTO {
        validar(comando)
        return repositorio.salvar(
            Cartao.novo(
                apelido = comando.apelido,
                diaFechamento = comando.diaFechamento,
                diaVencimento = comando.diaVencimento,
                dono = contexto.usuarioAtual(),
                escopo = comando.escopo,
                grupo = comando.grupoId,
                criadoEm = relogio.instant(),
            ),
        ).paraDTO()
    }

    /**
     * H-18. Qualquer membro edita cartao de grupo — `buscarVisivel` ja resolve a
     * permissao, como em toda esta unidade. O `dono` permanece quem cadastrou.
     */
    @Transactional
    fun editar(id: UUID, comando: CadastrarCartao): CartaoDTO {
        val cartao = exigirVisivel(id)
        validar(comando)
        return repositorio.salvar(
            cartao.editado(
                apelido = comando.apelido,
                diaFechamento = comando.diaFechamento,
                diaVencimento = comando.diaVencimento,
                escopo = comando.escopo,
                grupo = comando.grupoId,
            ),
        ).paraDTO()
    }

    /**
     * RN-K04. Encerrar **nao apaga historico**: faturas e parcelas existentes
     * permanecem, e continuam sendo consultadas normalmente. O que muda e que o
     * cartao deixa de receber lancamentos novos.
     */
    @Transactional
    fun encerrar(id: UUID) {
        val cartao = exigirVisivel(id)
        if (!cartao.estaAtivo()) return // idempotente
        repositorio.salvar(cartao.encerrado(relogio.instant()))
    }

    @Transactional(readOnly = true)
    fun listar(): List<CartaoDTO> = repositorio.listarVisiveis().map { it.paraDTO() }

    @Transactional(readOnly = true)
    fun consultar(id: UUID): CartaoDTO = exigirVisivel(id).paraDTO()

    /**
     * Usado pelos demais servicos de U3 antes de aceitar um lancamento
     * (RN-K04). Devolve o cartao **visivel e ativo**, ou lanca.
     */
    @Transactional(readOnly = true)
    fun exigirAtivo(id: UUID): Cartao {
        val cartao = exigirVisivel(id)
        if (!cartao.estaAtivo()) throw ErroDeNegocio(CodigoErro.CARTAO_ENCERRADO)
        return cartao
    }

    private fun validar(comando: CadastrarCartao) {
        if (comando.apelido.isBlank()) {
            throw ErroDeNegocio(CodigoErro.NOME_OBRIGATORIO, listOf(DetalheErro("apelido", "obrigatorio")))
        }
        // RN-K01: qualquer dia de 1 a 31 e aceito. A queda para o ultimo dia do
        // mes acontece na CalculadoraDeCompetencia (D-69), nao aqui.
        if (comando.diaFechamento !in 1..31) {
            throw ErroDeNegocio(CodigoErro.DIA_INVALIDO, listOf(DetalheErro("diaFechamento", "1 a 31")))
        }
        if (comando.diaVencimento !in 1..31) {
            throw ErroDeNegocio(CodigoErro.DIA_INVALIDO, listOf(DetalheErro("diaVencimento", "1 a 31")))
        }
        if ((comando.escopo == Escopo.GRUPO) != (comando.grupoId != null)) {
            throw ErroDeNegocio(CodigoErro.GRUPO_INVALIDO)
        }
        if (comando.grupoId != null && comando.grupoId !in contexto.gruposDoUsuario()) {
            throw ErroDeNegocio(CodigoErro.GRUPO_NAO_ENCONTRADO)
        }
    }

    /** 404 e nao 403 — E-08 e a mesma razao de RN-G03. */
    private fun exigirVisivel(id: UUID): Cartao =
        repositorio.buscarVisivel(id) ?: throw ErroDeNegocio(CodigoErro.CARTAO_NAO_ENCONTRADO)
}
