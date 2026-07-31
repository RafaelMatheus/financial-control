package com.rafaelmatheus.financialcontrol.common.web

import org.springframework.http.HttpStatus

/**
 * Taxonomia de erro da aplicacao (RNF-09, NFR-U1-09).
 *
 * A mensagem e **acionavel**: diz o que fazer, nao o que quebrou internamente.
 * Nenhuma expoe nome de tabela, de constraint ou de classe.
 */
enum class CodigoErro(val status: HttpStatus, val mensagem: String) {

    EMAIL_INVALIDO(HttpStatus.BAD_REQUEST, "Informe um e-mail em formato valido."),
    NOME_OBRIGATORIO(HttpStatus.BAD_REQUEST, "Informe um nome."),
    DADOS_INVALIDOS(HttpStatus.BAD_REQUEST, "Verifique os campos informados."),

    NAO_AUTENTICADO(HttpStatus.UNAUTHORIZED, "Autentique-se para continuar."),

    /**
     * Resposta unica para e-mail inexistente, senha errada E conta bloqueada
     * (RN-U04, NFR-U1-03).
     *
     * Distinguir os tres casos revelaria quais e-mails existem. E dizer "conta
     * bloqueada" e o pior dos tres: confirma a conta justamente para quem esta
     * tentando adivinhar a senha dela.
     */
    CREDENCIAIS_INVALIDAS(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos."),

    /**
     * 404 e nao 403 para quem nao e membro (RN-G03). 403 confirmaria que o grupo
     * existe, permitindo descobrir identificadores validos por tentativa.
     */
    GRUPO_NAO_ENCONTRADO(HttpStatus.NOT_FOUND, "Grupo nao encontrado."),
    USUARIO_NAO_ENCONTRADO(HttpStatus.NOT_FOUND, "Usuario nao encontrado."),

    EMAIL_JA_CADASTRADO(HttpStatus.CONFLICT, "Ja existe uma conta com este e-mail."),
    JA_E_MEMBRO(HttpStatus.CONFLICT, "Este usuario ja e membro do grupo."),

    BANCO_INDISPONIVEL(HttpStatus.SERVICE_UNAVAILABLE, "Servico temporariamente indisponivel."),
    ERRO_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "Nao foi possivel concluir a operacao."),
}

/** Excecao de negocio. Carrega o codigo; a mensagem vem dele. */
class ErroDeNegocio(
    val codigo: CodigoErro,
    val detalhes: List<DetalheErro> = emptyList(),
) : RuntimeException(codigo.mensagem)

data class DetalheErro(val campo: String, val problema: String)

/** Corpo unico de erro da API (RNF-09). */
data class RespostaErro(
    val codigo: String,
    val mensagem: String,
    val detalhes: List<DetalheErro> = emptyList(),
    /** Id de correlacao, para casar a resposta com a linha de log (D-53). */
    val correlacao: String? = null,
)
