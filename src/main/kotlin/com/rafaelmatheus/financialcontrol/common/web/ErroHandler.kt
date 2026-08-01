package com.rafaelmatheus.financialcontrol.common.web

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Formato unico de erro da API (RNF-09, NFR-U1-09).
 *
 * Nenhum caminho daqui devolve mensagem de excecao ao cliente: a mensagem vem
 * sempre do [CodigoErro]. Mensagem de excecao carrega nome de classe, de tabela e
 * as vezes o valor que causou o problema.
 */
@RestControllerAdvice
class ErroHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ErroDeNegocio::class)
    fun negocio(erro: ErroDeNegocio): ResponseEntity<RespostaErro> {
        // Nivel INFO: erro de negocio e fluxo esperado, nao incidente.
        log.info("Erro de negocio: {}", erro.codigo.name)
        return responder(erro.codigo, erro.detalhes)
    }

    /** Bean Validation nos DTOs de entrada (RNF-10, NFR-U1-08). */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validacao(erro: MethodArgumentNotValidException): ResponseEntity<RespostaErro> {
        val detalhes = erro.bindingResult.fieldErrors.map {
            DetalheErro(campo = it.field, problema = it.defaultMessage ?: "invalido")
        }
        return responder(CodigoErro.DADOS_INVALIDOS, detalhes)
    }

    /**
     * Invariante de construcao violada — `require` em value object, filtro ou
     * paginacao. **Acrescentado por U2.**
     *
     * Sem isto, `?tamanho=1000000` cairia no handler generico e viraria 500: um
     * erro de cliente respondido como falha do servidor, que e enganoso para
     * quem consome e ruido para quem opera.
     *
     * O escopo e estreito de proposito. Os servicos validam antes e lancam
     * `ErroDeNegocio` com codigo especifico; o `require` do construtor e a
     * ultima linha de defesa, e toda violacao dele nesta aplicacao vem de dado
     * que entrou pela borda.
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun invariante(erro: IllegalArgumentException): ResponseEntity<RespostaErro> {
        log.info("Invariante violada na entrada: {}", erro.message)
        return responder(
            CodigoErro.DADOS_INVALIDOS,
            listOf(DetalheErro("requisicao", erro.message ?: "invalido")),
        )
    }

    /** Banco indisponivel — o unico modo de falha externo de U1. */
    @ExceptionHandler(DataAccessResourceFailureException::class)
    fun bancoIndisponivel(erro: DataAccessResourceFailureException): ResponseEntity<RespostaErro> {
        log.error("Banco indisponivel", erro)
        return responder(CodigoErro.BANCO_INDISPONIVEL)
    }

    @ExceptionHandler(Exception::class)
    fun naoTratado(erro: Exception): ResponseEntity<RespostaErro> {
        // Aqui a stack vai para o log, e so para o log. O cliente recebe uma
        // mensagem generica, com o id de correlacao para casar as duas pontas.
        log.error("Erro nao tratado", erro)
        return responder(CodigoErro.ERRO_INTERNO)
    }

    private fun responder(
        codigo: CodigoErro,
        detalhes: List<DetalheErro> = emptyList(),
    ): ResponseEntity<RespostaErro> =
        ResponseEntity.status(codigo.status).body(
            RespostaErro(
                codigo = codigo.name,
                mensagem = codigo.mensagem,
                detalhes = detalhes,
                correlacao = MDC.get(CHAVE_CORRELACAO),
            ),
        )
}
