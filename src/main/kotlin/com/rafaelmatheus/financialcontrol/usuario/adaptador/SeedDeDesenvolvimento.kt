package com.rafaelmatheus.financialcontrol.usuario.adaptador

import com.rafaelmatheus.financialcontrol.usuario.dominio.Usuario
import com.rafaelmatheus.financialcontrol.usuario.dominio.UsuarioRepositorio
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * Cria um usuario de testes na subida.
 *
 * **`@Profile("dev")` e a parte que importa.** Este bean nao existe em `prod` —
 * nao e uma verificacao que alguem pode remover por engano, e o Spring nem
 * instancia a classe fora do perfil.
 *
 * Deliberadamente NAO foi feito como migration Flyway: migration roda em todo
 * ambiente, e um usuario com senha conhecida em producao seria uma porta dos
 * fundos versionada no repositorio.
 *
 * Idempotente: se o usuario ja existe, nao faz nada. Nao ressincroniza a senha,
 * para nao desfazer uma troca feita a mao durante um teste.
 */
@Component
@Profile("dev")
class SeedDeDesenvolvimento(
    private val repositorio: UsuarioRepositorio,
    private val codificador: PasswordEncoder,
    private val relogio: Clock,
    @Value("\${app.seed.email:admin@financial-control.com}") private val email: String,
    @Value("\${app.seed.senha:admin12345}") private val senha: String,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val normalizado = Usuario.normalizarEmail(email)

        if (repositorio.existePorEmail(normalizado)) {
            log.info("Usuario de testes ja existe: {}", normalizado)
            return
        }

        repositorio.salvar(
            Usuario.novo(
                email = normalizado,
                senhaHash = codificador.encode(senha),
                nome = "Admin de Testes",
                criadoEm = relogio.instant(),
            ),
        )

        // A senha aparece no log DE PROPOSITO, e so aqui: e um valor de teste,
        // publico por natureza, num perfil que nao existe em producao. Em
        // qualquer outro lugar isso violaria NFR-U1-05.
        log.warn("Usuario de testes criado: {} / senha: {}", normalizado, senha)
    }
}
