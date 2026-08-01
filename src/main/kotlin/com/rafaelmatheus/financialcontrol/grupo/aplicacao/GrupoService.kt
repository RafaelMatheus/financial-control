package com.rafaelmatheus.financialcontrol.grupo.aplicacao

import com.rafaelmatheus.financialcontrol.common.seguranca.ContextoUsuario
import com.rafaelmatheus.financialcontrol.common.web.CodigoErro
import com.rafaelmatheus.financialcontrol.common.web.DetalheErro
import com.rafaelmatheus.financialcontrol.common.web.ErroDeNegocio
import com.rafaelmatheus.financialcontrol.grupo.dominio.AssociacaoAtivaDuplicada
import com.rafaelmatheus.financialcontrol.grupo.dominio.Grupo
import com.rafaelmatheus.financialcontrol.grupo.dominio.GrupoRepositorio
import com.rafaelmatheus.financialcontrol.grupo.dominio.MembroGrupo
import com.rafaelmatheus.financialcontrol.usuario.dominio.UsuarioRepositorio
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

data class GrupoDTO(val id: String, val nome: String, val criadoEm: String)

data class MembroDTO(val usuarioId: String, val nome: String, val entrouEm: String)

data class GrupoDetalheDTO(val id: String, val nome: String, val membros: List<MembroDTO>)

fun Grupo.paraDTO() = GrupoDTO(id.toString(), nome, criadoEm.toString())

@Service
class GrupoService(
    private val repositorio: GrupoRepositorio,
    private val usuarios: UsuarioRepositorio,
    private val contexto: ContextoUsuario,
    private val relogio: Clock,
) {

    /**
     * H-05, RF-06.
     *
     * Cria o grupo **e** a associacao de quem criou, na mesma transacao. Sem
     * isso o grupo nasceria inacessivel ao proprio criador, por forca da guarda
     * de RN-G03. Nao e privilegio de criador (RN-G02) — e so que alguem precisa
     * estar dentro para o grupo ser alcancavel.
     */
    @Transactional
    fun criar(nome: String): GrupoDTO {
        if (nome.isBlank()) {
            throw ErroDeNegocio(CodigoErro.NOME_OBRIGATORIO, listOf(DetalheErro("nome", "obrigatorio")))
        }
        val agora = relogio.instant()
        val grupo = repositorio.salvar(Grupo.novo(nome, agora))
        repositorio.salvarMembro(MembroGrupo.nova(grupo.id, contexto.usuarioAtual(), agora))
        return grupo.paraDTO()
    }

    /** H-05, RF-06. Qualquer membro renomeia — sem hierarquia (RN-G02). */
    @Transactional
    fun renomear(grupoId: UUID, nome: String): GrupoDTO {
        if (nome.isBlank()) {
            throw ErroDeNegocio(CodigoErro.NOME_OBRIGATORIO, listOf(DetalheErro("nome", "obrigatorio")))
        }
        val grupo = exigirMembroAtivo(grupoId)
        return repositorio.salvar(grupo.comNome(nome)).paraDTO()
    }

    /** H-05, RF-07. Lista vazia e resultado valido: participar e opcional. */
    @Transactional(readOnly = true)
    fun listarMeusGrupos(): List<GrupoDTO> =
        repositorio.listarGruposDe(contexto.usuarioAtual()).map { it.paraDTO() }

    /** H-06, RF-08. */
    @Transactional(readOnly = true)
    fun consultar(grupoId: UUID): GrupoDetalheDTO {
        val grupo = exigirMembroAtivo(grupoId)
        val membros = repositorio.membrosAtivosDe(grupoId).map { membro ->
            MembroDTO(
                usuarioId = membro.usuarioId.toString(),
                nome = usuarios.buscarPorId(membro.usuarioId)?.nome ?: "",
                entrouEm = membro.entrouEm.toString(),
            )
        }
        return GrupoDetalheDTO(grupo.id.toString(), grupo.nome, membros)
    }

    /** H-06, RF-08. */
    @Transactional
    fun adicionarMembro(grupoId: UUID, usuarioId: UUID): GrupoDetalheDTO {
        exigirMembroAtivo(grupoId)

        if (usuarios.buscarPorId(usuarioId) == null) {
            throw ErroDeNegocio(CodigoErro.USUARIO_NAO_ENCONTRADO) // E-07
        }
        if (repositorio.buscarAssociacaoAtiva(grupoId, usuarioId) != null) {
            throw ErroDeNegocio(CodigoErro.JA_E_MEMBRO)
        }

        try {
            // Linha nova mesmo se houver associacao encerrada (RN-G07, D-45).
            repositorio.salvarMembro(MembroGrupo.nova(grupoId, usuarioId, relogio.instant()))
        } catch (_: AssociacaoAtivaDuplicada) {
            // Duas requisicoes simultaneas passam pela verificacao acima; quem
            // realmente garante a invariante e o indice unico parcial no banco.
            throw ErroDeNegocio(CodigoErro.JA_E_MEMBRO)
        }
        return consultar(grupoId)
    }

    /** H-06, RF-08. Qualquer membro remove qualquer outro, inclusive quem o adicionou. */
    @Transactional
    fun removerMembro(grupoId: UUID, usuarioId: UUID) {
        exigirMembroAtivo(grupoId)
        encerrarAssociacao(grupoId, usuarioId)
    }

    /**
     * H-08, RF-10. Mesma operacao de remover, com alvo fixo em quem chama.
     *
     * Nada e apagado: os lancamentos de que o usuario e dono permanecem e
     * continuam somando no total do grupo (RN-G06). Se era o ultimo membro, o
     * grupo fica vazio e permanece (RN-G08).
     */
    @Transactional
    fun sair(grupoId: UUID) {
        exigirMembroAtivo(grupoId)
        encerrarAssociacao(grupoId, contexto.usuarioAtual())
    }

    private fun encerrarAssociacao(grupoId: UUID, usuarioId: UUID) {
        val associacao = repositorio.buscarAssociacaoAtiva(grupoId, usuarioId)
            ?: throw ErroDeNegocio(CodigoErro.USUARIO_NAO_ENCONTRADO)
        repositorio.salvarMembro(associacao.encerrar(relogio.instant()))
    }

    /**
     * Guarda comum de RN-G03.
     *
     * Responde **404 e nao 403**: 403 confirmaria que o grupo existe, permitindo
     * descobrir identificadores validos por tentativa. Para quem nao e membro, o
     * grupo simplesmente nao existe — e quem nunca foi membro e quem ja saiu
     * recebem exatamente a mesma resposta.
     */
    private fun exigirMembroAtivo(grupoId: UUID): Grupo {
        val usuario = contexto.usuarioAtual()
        if (repositorio.buscarAssociacaoAtiva(grupoId, usuario) == null) {
            throw ErroDeNegocio(CodigoErro.GRUPO_NAO_ENCONTRADO)
        }
        return repositorio.buscarPorId(grupoId)
            ?: throw ErroDeNegocio(CodigoErro.GRUPO_NAO_ENCONTRADO)
    }
}
