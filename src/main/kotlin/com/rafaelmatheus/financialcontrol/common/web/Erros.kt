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

    // --- U2 Lancamentos ---

    VALOR_INVALIDO(HttpStatus.BAD_REQUEST, "O valor precisa ser maior que zero."),
    CATEGORIA_OBRIGATORIA(HttpStatus.BAD_REQUEST, "Informe uma categoria."),
    REALOCACAO_INVALIDA(HttpStatus.BAD_REQUEST, "Escolha outra categoria para receber os lancamentos."),

    /** Bicondicional escopo-grupo: escopo GRUPO exige grupo, PESSOAL o proibe. */
    GRUPO_INVALIDO(HttpStatus.BAD_REQUEST, "Escopo de grupo exige um grupo, e escopo pessoal nao aceita um."),

    /**
     * RN-T05, D-55. Com o usuario em mais de um grupo, somar todos daria um
     * numero que nao descreve nenhum deles — a casa somada com a viagem.
     */
    GRUPO_OBRIGATORIO(HttpStatus.BAD_REQUEST, "Informe de qual grupo voce quer os totais."),

    /** 404 e nao 403, pela mesma razao de GRUPO_NAO_ENCONTRADO (RN-C03, RN-L06). */
    CATEGORIA_NAO_ENCONTRADA(HttpStatus.NOT_FOUND, "Categoria nao encontrada."),
    LANCAMENTO_NAO_ENCONTRADO(HttpStatus.NOT_FOUND, "Lancamento nao encontrado."),

    CATEGORIA_DUPLICADA(HttpStatus.CONFLICT, "Ja existe uma categoria com este nome."),

    /**
     * A contagem de lancamentos vinculados vai nos `detalhes` (RN-C05). E o
     * numero com que o usuario decide se realoca ou desiste — H-34 pede isso
     * explicitamente, entao a contagem e parte da regra e nao enfeite.
     */
    CATEGORIA_EM_USO(HttpStatus.CONFLICT, "Esta categoria tem lancamentos vinculados."),

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
