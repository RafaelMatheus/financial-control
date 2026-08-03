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

    // --- U3 Credito ---

    DIA_INVALIDO(HttpStatus.BAD_REQUEST, "Informe um dia entre 1 e 31."),
    NUMERO_PARCELAS_INVALIDO(HttpStatus.BAD_REQUEST, "O numero de parcelas precisa ser pelo menos 1."),

    /** RN-P06, H-30: a edicao e sempre da compra inteira. */
    EDICAO_DE_PARCELA(HttpStatus.BAD_REQUEST, "Edite a compra inteira; parcelas nao mudam sozinhas."),

    CARTAO_NAO_ENCONTRADO(HttpStatus.NOT_FOUND, "Cartao nao encontrado."),
    FATURA_NAO_ENCONTRADA(HttpStatus.NOT_FOUND, "Fatura nao encontrada."),
    CONTA_NAO_ENCONTRADA(HttpStatus.NOT_FOUND, "Conta nao encontrada."),
    RECORRENTE_NAO_ENCONTRADA(HttpStatus.NOT_FOUND, "Conta recorrente nao encontrada."),

    CARTAO_ENCERRADO(HttpStatus.CONFLICT, "Este cartao foi encerrado e nao recebe lancamentos."),

    /**
     * RN-F07, RF-95, H-24, E-13.
     *
     * A mensagem **diz o que fazer**, e nao so o que impediu: sem a saida de
     * desmarcar o pagamento, um lancamento errado numa fatura paga ficaria preso
     * para sempre. Foi por isso que RF-94 nasceu.
     */
    FATURA_PAGA(
        HttpStatus.CONFLICT,
        "Esta fatura ja foi paga. Desmarque o pagamento antes de alterar os lancamentos.",
    ),

    // --- U4 Planejamento ---

    META_INVALIDA(HttpStatus.BAD_REQUEST, "A meta precisa ser maior que zero."),

    RECEITA_NAO_ENCONTRADA(HttpStatus.NOT_FOUND, "Receita nao encontrada."),
    ORCAMENTO_NAO_ENCONTRADO(HttpStatus.NOT_FOUND, "Orcamento nao encontrado."),
    OBJETIVO_NAO_ENCONTRADO(HttpStatus.NOT_FOUND, "Objetivo nao encontrado."),
    APORTE_NAO_ENCONTRADO(HttpStatus.NOT_FOUND, "Aporte nao encontrado."),

    /** RN-O01: um teto por categoria, competencia e escopo. */
    ORCAMENTO_DUPLICADO(
        HttpStatus.CONFLICT,
        "Ja existe um orcamento para esta categoria neste mes e escopo.",
    ),

    /** RN-A06, P-11: o valor deriva dos lancamentos da fatura. */
    CONTA_DERIVADA(
        HttpStatus.CONFLICT,
        "Esta conta veio do fechamento de uma fatura; o valor deriva dos lancamentos.",
    ),

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
