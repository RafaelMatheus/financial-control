package com.rafaelmatheus.financialcontrol.conta.aplicacao

import com.rafaelmatheus.financialcontrol.categoria.dominio.CategoriaRepositorio
import com.rafaelmatheus.financialcontrol.common.dominio.Competencia
import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.common.seguranca.ContextoUsuario
import com.rafaelmatheus.financialcontrol.common.web.CodigoErro
import com.rafaelmatheus.financialcontrol.common.web.ErroDeNegocio
import com.rafaelmatheus.financialcontrol.conta.dominio.ContaRecorrente
import com.rafaelmatheus.financialcontrol.conta.dominio.Frequencia
import com.rafaelmatheus.financialcontrol.conta.dominio.RecorrenteRepositorio
import com.rafaelmatheus.financialcontrol.conta.dominio.TipoConta
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

data class CadastrarRecorrente(
    val descricao: String,
    val valorBase: Dinheiro,
    val diaVencimento: Int,
    val tipo: TipoConta,
    val categoriaId: UUID,
    val escopo: Escopo,
    val grupoId: UUID?,
    val inicioEm: Competencia,
)

data class RecorrenteDTO(
    val id: String,
    val descricao: String,
    val valorBase: String,
    val diaVencimento: Int,
    val frequencia: Frequencia,
    val tipo: TipoConta,
    val categoriaId: String,
    val escopo: Escopo,
    val grupoId: String?,
    val inicioEm: String,
    val encerradaEm: String?,
    val ativa: Boolean,
)

fun ContaRecorrente.paraDTO() = RecorrenteDTO(
    id = id.toString(),
    descricao = descricao,
    valorBase = valorBase.toString(),
    diaVencimento = diaVencimento,
    frequencia = frequencia,
    tipo = tipo,
    categoriaId = categoria.toString(),
    escopo = escopo,
    grupoId = grupo?.toString(),
    inicioEm = inicioEm.toString(),
    encerradaEm = encerradaEm?.toString(),
    ativa = encerradaEm == null,
)

/**
 * A **regra** de recorrencia (RF-62, RF-63, RF-67).
 *
 * As ocorrencias nao vivem aqui: elas sao projetadas pela consulta de
 * vencimentos e materializadas ao serem tocadas (D-72, `ContaService`).
 *
 * Frequencia **mensal** e a unica do MVP (P-10, H-46). O enum existe com um
 * valor so de proposito: acrescentar SEMANAL depois e adicionar um caso, e nao
 * reinterpretar um booleano.
 */
@Service
class RecorrenteService(
    private val repositorio: RecorrenteRepositorio,
    private val categorias: CategoriaRepositorio,
    private val contexto: ContextoUsuario,
    private val relogio: Clock,
) {

    /** H-46, RF-62. */
    @Transactional
    fun cadastrar(comando: CadastrarRecorrente): RecorrenteDTO {
        if (comando.descricao.isBlank()) throw ErroDeNegocio(CodigoErro.NOME_OBRIGATORIO)
        if (!comando.valorBase.ehPositivo()) throw ErroDeNegocio(CodigoErro.VALOR_INVALIDO)
        if (comando.diaVencimento !in 1..31) throw ErroDeNegocio(CodigoErro.DIA_INVALIDO)
        if ((comando.escopo == Escopo.GRUPO) != (comando.grupoId != null)) {
            throw ErroDeNegocio(CodigoErro.GRUPO_INVALIDO)
        }
        if (comando.grupoId != null && comando.grupoId !in contexto.gruposDoUsuario()) {
            throw ErroDeNegocio(CodigoErro.GRUPO_NAO_ENCONTRADO)
        }
        categorias.buscarVisivel(comando.categoriaId)
            ?: throw ErroDeNegocio(CodigoErro.CATEGORIA_NAO_ENCONTRADA)

        return repositorio.salvar(
            ContaRecorrente.nova(
                descricao = comando.descricao,
                valorBase = comando.valorBase,
                diaVencimento = comando.diaVencimento,
                tipo = comando.tipo,
                categoria = comando.categoriaId,
                dono = contexto.usuarioAtual(),
                escopo = comando.escopo,
                grupo = comando.grupoId,
                inicioEm = comando.inicioEm,
                criadoEm = relogio.instant(),
            ),
        ).paraDTO()
    }

    /**
     * H-51, RF-67. **Nada e apagado**: as ocorrencias ja materializadas
     * permanecem com o seu historico de pagamento. O que para e a geracao de
     * novas.
     */
    @Transactional
    fun encerrar(id: UUID, aPartirDe: Competencia): RecorrenteDTO {
        val regra = repositorio.buscarVisivel(id)
            ?: throw ErroDeNegocio(CodigoErro.RECORRENTE_NAO_ENCONTRADA)
        return repositorio.salvar(regra.encerrada(aPartirDe)).paraDTO()
    }

    @Transactional(readOnly = true)
    fun listar(): List<RecorrenteDTO> = repositorio.listarVisiveis().map { it.paraDTO() }
}
