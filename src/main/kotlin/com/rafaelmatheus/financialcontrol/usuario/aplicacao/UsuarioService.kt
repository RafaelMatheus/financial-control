package com.rafaelmatheus.financialcontrol.usuario.aplicacao

import com.rafaelmatheus.financialcontrol.common.seguranca.ContextoUsuario
import com.rafaelmatheus.financialcontrol.common.web.CodigoErro
import com.rafaelmatheus.financialcontrol.common.web.DetalheErro
import com.rafaelmatheus.financialcontrol.common.web.ErroDeNegocio
import com.rafaelmatheus.financialcontrol.usuario.dominio.EmailDuplicado
import com.rafaelmatheus.financialcontrol.usuario.dominio.Usuario
import com.rafaelmatheus.financialcontrol.usuario.dominio.UsuarioRepositorio
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

data class CadastrarUsuario(val email: String, val senha: String, val nome: String)

data class AtualizarPerfil(val nome: String)

/** Projecao de saida. **Nunca** carrega `senhaHash` (RN-U03). */
data class UsuarioDTO(val id: String, val email: String, val nome: String, val criadoEm: String)

fun Usuario.paraDTO() = UsuarioDTO(
    id = id.toString(),
    email = email,
    nome = nome,
    criadoEm = criadoEm.toString(),
)

@Service
class UsuarioService(
    private val repositorio: UsuarioRepositorio,
    private val codificador: PasswordEncoder,
    private val contexto: ContextoUsuario,
    private val relogio: Clock,
) {

    /** H-01, RF-01. */
    @Transactional
    fun cadastrar(comando: CadastrarUsuario): UsuarioDTO {
        val email = Usuario.normalizarEmail(comando.email)

        if (!Usuario.emailValido(email)) {
            throw ErroDeNegocio(
                CodigoErro.EMAIL_INVALIDO,
                listOf(DetalheErro("email", "formato invalido")),
            )
        }

        // Verificacao para dar boa mensagem; a garantia real vem da restricao no
        // banco, capturada abaixo. So a verificacao e uma corrida entre duas
        // requisicoes simultaneas; so a restricao da uma mensagem ruim.
        if (repositorio.existePorEmail(email)) {
            throw ErroDeNegocio(CodigoErro.EMAIL_JA_CADASTRADO)
        }

        val usuario = Usuario.novo(
            email = email,
            senhaHash = codificador.encode(comando.senha),
            nome = comando.nome,
            criadoEm = relogio.instant(),
        )

        return try {
            repositorio.salvar(usuario).paraDTO()
        } catch (_: EmailDuplicado) {
            throw ErroDeNegocio(CodigoErro.EMAIL_JA_CADASTRADO)
        }
    }

    /**
     * H-04, RF-05.
     *
     * Sem parametro de identificador, de proposito (RN-U06): a regra fica na
     * **assinatura**, e nao numa validacao que alguem pode esquecer. Um metodo
     * que aceitasse `usuarioId` e depois verificasse se e o proprio seria uma
     * verificacao esquecivel; um metodo que nao aceita o parametro nao tem como
     * errar.
     */
    @Transactional(readOnly = true)
    fun consultarPerfil(): UsuarioDTO =
        repositorio.buscarPorId(contexto.usuarioAtual())?.paraDTO()
            ?: throw ErroDeNegocio(CodigoErro.USUARIO_NAO_ENCONTRADO)

    /** H-04, RF-05. Apenas `nome` e editavel (RN-U07). */
    @Transactional
    fun atualizarPerfil(comando: AtualizarPerfil): UsuarioDTO {
        if (comando.nome.isBlank()) {
            throw ErroDeNegocio(
                CodigoErro.NOME_OBRIGATORIO,
                listOf(DetalheErro("nome", "obrigatorio")),
            )
        }
        val atual = repositorio.buscarPorId(contexto.usuarioAtual())
            ?: throw ErroDeNegocio(CodigoErro.USUARIO_NAO_ENCONTRADO)

        return repositorio.salvar(atual.comNome(comando.nome)).paraDTO()
    }
}
