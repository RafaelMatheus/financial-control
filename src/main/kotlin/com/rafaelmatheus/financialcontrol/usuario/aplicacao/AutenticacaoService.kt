package com.rafaelmatheus.financialcontrol.usuario.aplicacao

import com.rafaelmatheus.financialcontrol.common.seguranca.EmissorDeToken
import com.rafaelmatheus.financialcontrol.common.seguranca.RegistroDeTentativas
import com.rafaelmatheus.financialcontrol.common.web.CodigoErro
import com.rafaelmatheus.financialcontrol.common.web.ErroDeNegocio
import com.rafaelmatheus.financialcontrol.usuario.dominio.Usuario
import com.rafaelmatheus.financialcontrol.usuario.dominio.UsuarioRepositorio
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class Autenticar(val email: String, val senha: String)

data class TokenDTO(val token: String, val expiraEmSegundos: Long)

/**
 * Login (H-02, RF-02, D-02).
 *
 * Todo caminho de falha termina no **mesmo** erro, com o **mesmo** custo de
 * tempo. Ver o comentario em [autenticar] — a propriedade de tempo constante e
 * requisito (RN-U04), nao detalhe de implementacao.
 */
@Service
class AutenticacaoService(
    private val repositorio: UsuarioRepositorio,
    private val codificador: PasswordEncoder,
    private val emissor: EmissorDeToken,
    private val tentativas: RegistroDeTentativas,
) {

    @Transactional(readOnly = true)
    fun autenticar(comando: Autenticar): TokenDTO {
        val email = Usuario.normalizarEmail(comando.email)

        // Conta bloqueada responde EXATAMENTE como senha errada (NFR-U1-03).
        // Dizer "conta bloqueada" confirmaria que a conta existe, justamente para
        // quem esta tentando adivinhar a senha dela — o caminho de erro desfaria
        // a protecao que RN-U04 monta no caminho normal.
        if (tentativas.estaBloqueado(email)) {
            gastarTempoEquivalente(comando.senha)
            throw ErroDeNegocio(CodigoErro.CREDENCIAIS_INVALIDAS)
        }

        val usuario = repositorio.buscarPorEmail(email)

        if (usuario == null) {
            // Calcula um hash descartavel. Sem isto, o tempo de resposta revelaria
            // quais e-mails existem: quem mede a latencia enumera contas. O custo
            // e o mesmo ~250ms do BCrypt no caminho normal.
            gastarTempoEquivalente(comando.senha)
            tentativas.registrarFalha(email)
            throw ErroDeNegocio(CodigoErro.CREDENCIAIS_INVALIDAS)
        }

        if (!codificador.matches(comando.senha, usuario.senhaHash)) {
            tentativas.registrarFalha(email)
            throw ErroDeNegocio(CodigoErro.CREDENCIAIS_INVALIDAS)
        }

        tentativas.registrarSucesso(email)
        return TokenDTO(
            token = emissor.emitir(usuario.id),
            expiraEmSegundos = emissor.validadeEmSegundos(),
        )
    }

    private fun gastarTempoEquivalente(senha: String) {
        codificador.encode(senha)
    }
}
