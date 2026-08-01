package com.rafaelmatheus.financialcontrol.usuario.dominio

import java.time.Instant
import java.util.UUID

/**
 * Quem usa o sistema. Ancora todo o isolamento de dados (RF-01 a RF-05).
 *
 * Dominio puro: sem anotacao de JPA, sem Spring. O mapeamento vive no adaptador
 * de persistencia (D-51).
 *
 * O atributo se chama `senhaHash`, e nao `senha`, de proposito: o nome carrega a
 * invariante. Um campo chamado `senha` convida alguem, meses depois, a atribuir
 * texto claro a ele.
 */
data class Usuario(
    val id: UUID,
    val email: String,
    val senhaHash: String,
    val nome: String,
    val criadoEm: Instant,
) {
    init {
        require(email == normalizarEmail(email)) {
            "Email deve chegar normalizado a entidade — use Usuario.normalizarEmail"
        }
        require(nome.isNotBlank()) { "Nome nao pode ser vazio" }
    }

    /** Unico campo editavel por atualizarPerfil (RN-U07). */
    fun comNome(novoNome: String): Usuario {
        require(novoNome.isNotBlank()) { "Nome nao pode ser vazio" }
        return copy(nome = novoNome.trim())
    }

    companion object {
        private val FORMATO_EMAIL = Regex("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$")

        /**
         * Normalizacao de e-mail (RN-U01, D-46): `trim` e minusculas.
         * A unicidade vale sobre esta forma — `  Rafael@X.com ` e `rafael@x.com`
         * sao a mesma conta.
         */
        fun normalizarEmail(email: String): String = email.trim().lowercase()

        fun emailValido(email: String): Boolean = FORMATO_EMAIL.matches(email)

        fun novo(email: String, senhaHash: String, nome: String, criadoEm: Instant): Usuario =
            Usuario(
                id = UUID.randomUUID(),
                email = normalizarEmail(email),
                senhaHash = senhaHash,
                nome = nome.trim(),
                criadoEm = criadoEm,
            )
    }
}

/**
 * Porta de persistencia de `Usuario`.
 *
 * Deliberadamente **fora** de `RepositorioComVisibilidade`: usuario nao tem dono
 * no sentido de RF-03, e o cadastro precisa buscar por e-mail antes de existir
 * qualquer autenticacao.
 */
interface UsuarioRepositorio {
    fun salvar(usuario: Usuario): Usuario
    fun buscarPorId(id: UUID): Usuario?
    fun buscarPorEmail(emailNormalizado: String): Usuario?
    fun existePorEmail(emailNormalizado: String): Boolean
}

/** Sinaliza a violacao da restricao de unicidade vinda do banco (RN-U01). */
class EmailDuplicado : RuntimeException("E-mail ja cadastrado")
