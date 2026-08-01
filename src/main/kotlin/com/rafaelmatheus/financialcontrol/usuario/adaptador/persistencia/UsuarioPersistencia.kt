package com.rafaelmatheus.financialcontrol.usuario.adaptador.persistencia

import com.rafaelmatheus.financialcontrol.usuario.dominio.EmailDuplicado
import com.rafaelmatheus.financialcontrol.usuario.dominio.Usuario
import com.rafaelmatheus.financialcontrol.usuario.dominio.UsuarioRepositorio
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "usuario")
class UsuarioJpa(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID,

    @Column(name = "email", nullable = false, unique = true, length = 320)
    var email: String,

    @Column(name = "senha_hash", nullable = false, length = 72)
    var senhaHash: String,

    @Column(name = "nome", nullable = false, length = 120)
    var nome: String,

    @Column(name = "criado_em", nullable = false)
    var criadoEm: Instant,
)

interface UsuarioSpringData : JpaRepository<UsuarioJpa, UUID> {
    fun findByEmail(email: String): UsuarioJpa?
    fun existsByEmail(email: String): Boolean
}

/**
 * Adaptador entre a porta do dominio e o JPA (D-51).
 *
 * O mapeador e burro de proposito: sem regra de negocio, so traducao de forma.
 * Toda decisao mora no dominio ou na aplicacao.
 */
@Repository
class UsuarioRepositorioAdaptador(private val jpa: UsuarioSpringData) : UsuarioRepositorio {

    override fun salvar(usuario: Usuario): Usuario =
        try {
            jpa.save(usuario.paraJpa()).paraDominio()
        } catch (_: DataIntegrityViolationException) {
            // A restricao de unicidade do banco e a garantia real de RN-U01. Aqui
            // ela vira excecao de dominio, para nao vazar detalhe de persistencia
            // camada acima.
            throw EmailDuplicado()
        }

    override fun buscarPorId(id: UUID): Usuario? = jpa.findById(id).orElse(null)?.paraDominio()

    override fun buscarPorEmail(emailNormalizado: String): Usuario? =
        jpa.findByEmail(emailNormalizado)?.paraDominio()

    override fun existePorEmail(emailNormalizado: String): Boolean =
        jpa.existsByEmail(emailNormalizado)
}

private fun Usuario.paraJpa() = UsuarioJpa(
    id = id,
    email = email,
    senhaHash = senhaHash,
    nome = nome,
    criadoEm = criadoEm,
)

private fun UsuarioJpa.paraDominio() = Usuario(
    id = id,
    email = email,
    senhaHash = senhaHash,
    nome = nome,
    criadoEm = criadoEm,
)
