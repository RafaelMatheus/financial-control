package com.rafaelmatheus.financialcontrol.usuario.adaptador.web

import com.rafaelmatheus.financialcontrol.usuario.aplicacao.AtualizarPerfil
import com.rafaelmatheus.financialcontrol.usuario.aplicacao.Autenticar
import com.rafaelmatheus.financialcontrol.usuario.aplicacao.AutenticacaoService
import com.rafaelmatheus.financialcontrol.usuario.aplicacao.CadastrarUsuario
import com.rafaelmatheus.financialcontrol.usuario.aplicacao.TokenDTO
import com.rafaelmatheus.financialcontrol.usuario.aplicacao.UsuarioDTO
import com.rafaelmatheus.financialcontrol.usuario.aplicacao.UsuarioService
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

// Bean Validation em todo DTO de entrada (RNF-10, NFR-U1-08).
// O tamanho minimo de senha e 8: nao ha requisito que o defina, e 8 e o piso
// abaixo do qual nem o BCrypt salva. Registrado como escolha, nao como omissao.
data class CadastroRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank @field:Size(min = 8, max = 72) val senha: String,
    @field:NotBlank @field:Size(max = 120) val nome: String,
)

data class LoginRequest(
    @field:NotBlank val email: String,
    @field:NotBlank val senha: String,
)

data class AtualizarPerfilRequest(
    @field:NotBlank @field:Size(max = 120) val nome: String,
)

@RestController
@RequestMapping("/usuarios")
class UsuarioController(private val servico: UsuarioService) {

    /** H-01, RF-01. Rota publica. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun cadastrar(@Valid @RequestBody corpo: CadastroRequest): UsuarioDTO =
        servico.cadastrar(CadastrarUsuario(corpo.email, corpo.senha, corpo.nome))

    /** H-04, RF-05. Sem identificador na rota — o perfil e sempre o proprio. */
    @GetMapping("/eu")
    fun consultarPerfil(): UsuarioDTO = servico.consultarPerfil()

    @PutMapping("/eu")
    fun atualizarPerfil(@Valid @RequestBody corpo: AtualizarPerfilRequest): UsuarioDTO =
        servico.atualizarPerfil(AtualizarPerfil(corpo.nome))
}

@RestController
@RequestMapping("/auth")
class AutenticacaoController(private val servico: AutenticacaoService) {

    /** H-02, RF-02. Rota publica. */
    @PostMapping("/login")
    fun login(@Valid @RequestBody corpo: LoginRequest): TokenDTO =
        servico.autenticar(Autenticar(corpo.email, corpo.senha))
}
