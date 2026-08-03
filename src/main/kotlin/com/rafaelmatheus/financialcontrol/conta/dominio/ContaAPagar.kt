package com.rafaelmatheus.financialcontrol.conta.dominio

import com.rafaelmatheus.financialcontrol.common.dominio.Competencia
import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.common.persistencia.RepositorioComVisibilidade
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** RF-56. Quatro tipos, e a fatura de cartao e um deles. */
enum class TipoConta { FATURA_CARTAO, PIX, BOLETO, FATURA_SERVICO }

enum class StatusConta { EM_ABERTO, PAGA }

enum class Frequencia { MENSAL }

/**
 * Onde **todo vencimento converge** (RF-55 a RF-67).
 *
 * Uma entidade so, e nao tres. RF-62 e explicito: *"contas recorrentes e avulsas
 * convivem no mesmo modelo"*. A visao consolidada de RF-58 precisa reunir tudo
 * ordenado por data — com tres entidades ela seria tres consultas e uma
 * ordenacao em memoria.
 *
 * O `valor` e **persistido, e deliberadamente**. Diferente do total da fatura,
 * que D-75 tornou derivado, este numero e **fato historico**: o que foi cobrado
 * no fechamento. Se ele derivasse, corrigir um gasto de marco mudaria o valor de
 * uma conta paga em abril, e o historico deixaria de bater com o extrato do
 * banco — que e o oposto do que H-24 existe para garantir.
 */
data class ContaAPagar(
    val id: UUID,
    val descricao: String,
    val valor: Dinheiro,
    val dataVencimento: LocalDate,
    val tipo: TipoConta,
    val status: StatusConta,
    val dataPagamento: LocalDate?,
    /**
     * **Nulo apenas na conta derivada de fatura.** Uma fatura mistura
     * categorias — escolher uma delas seria inventar dado, e uma categoria de
     * sistema chamada "Fatura" seria criar registro que o usuario nao pediu.
     */
    val categoria: UUID?,
    val dono: UUID,
    val escopo: Escopo,
    val grupo: UUID?,
    /** Nao-nulo = conta derivada de fatura; o valor nao e editavel (RN-A06). */
    val origemFatura: UUID? = null,
    /** Nao-nulo = ocorrencia materializada de uma recorrente (D-72). */
    val origemRecorrente: UUID? = null,
    val competenciaRecorrencia: Competencia? = null,
    val criadoEm: Instant,
) {
    init {
        require(descricao.isNotBlank()) { "Descricao nao pode ser vazia" }
        require(valor.ehPositivo()) { "Valor precisa ser maior que zero" }
        require((status == StatusConta.PAGA) == (dataPagamento != null)) {
            "Status PAGA exige data de pagamento, e EM_ABERTO a proibe"
        }
        require((escopo == Escopo.GRUPO) == (grupo != null)) {
            "Escopo GRUPO exige grupo, e escopo PESSOAL nao aceita grupo"
        }
        // RN-A09: uma conta tem NO MAXIMO uma origem.
        require(origemFatura == null || origemRecorrente == null) {
            "Uma conta nao pode vir de fatura e de recorrencia ao mesmo tempo"
        }
        // RN-A01: toda conta tem categoria, exceto a derivada de fatura.
        require(categoria != null || origemFatura != null) {
            "Conta precisa de categoria, salvo quando derivada de fatura"
        }
        require((origemRecorrente != null) == (competenciaRecorrencia != null)) {
            "Competencia da ocorrencia so existe com origem recorrente"
        }
    }

    fun ehDerivadaDeFatura(): Boolean = origemFatura != null

    /**
     * H-44, RF-57 e **RF-64**: o valor e ajustavel no pagamento.
     *
     * Contas como energia e gas variam mes a mes, e o ajuste e da **ocorrencia**
     * — nunca do valor base da regra recorrente (RN-R03).
     */
    fun paga(quando: LocalDate, valorAjustado: Dinheiro?) = copy(
        status = StatusConta.PAGA,
        dataPagamento = quando,
        valor = valorAjustado ?: valor,
    )

    /**
     * RF-94, H-23. **A unica via** para corrigir lancamentos que afetariam uma
     * fatura ja paga — sem ela, um erro ficaria preso para sempre, porque H-24
     * bloqueia alteracoes.
     */
    fun pagamentoDesmarcado() = copy(status = StatusConta.EM_ABERTO, dataPagamento = null)

    fun estaVencida(hoje: LocalDate): Boolean =
        status == StatusConta.EM_ABERTO && dataVencimento.isBefore(hoje)

    companion object {
        fun nova(
            descricao: String,
            valor: Dinheiro,
            dataVencimento: LocalDate,
            tipo: TipoConta,
            categoria: UUID?,
            dono: UUID,
            escopo: Escopo,
            grupo: UUID?,
            criadoEm: Instant,
            origemFatura: UUID? = null,
            origemRecorrente: UUID? = null,
            competenciaRecorrencia: Competencia? = null,
        ) = ContaAPagar(
            id = UUID.randomUUID(),
            descricao = descricao.trim(),
            valor = valor,
            dataVencimento = dataVencimento,
            tipo = tipo,
            status = StatusConta.EM_ABERTO,
            dataPagamento = null,
            categoria = categoria,
            dono = dono,
            escopo = escopo,
            grupo = grupo,
            origemFatura = origemFatura,
            origemRecorrente = origemRecorrente,
            competenciaRecorrencia = competenciaRecorrencia,
            criadoEm = criadoEm,
        )
    }
}

/**
 * A **regra** que gera ocorrencias — nao as ocorrencias (RF-62, RF-63, D-72).
 *
 * `valorBase` **nao muda** quando uma ocorrencia e ajustada no pagamento
 * (RN-R03, H-48). E esta exigencia que obriga a ocorrencia a ter identidade
 * propria: sem ela, D-72 poderia ser calculo puro.
 */
data class ContaRecorrente(
    val id: UUID,
    val descricao: String,
    val valorBase: Dinheiro,
    val diaVencimento: Int,
    val frequencia: Frequencia,
    val tipo: TipoConta,
    val categoria: UUID,
    val dono: UUID,
    val escopo: Escopo,
    val grupo: UUID?,
    val inicioEm: Competencia,
    val encerradaEm: Competencia? = null,
    val criadoEm: Instant,
) {
    init {
        require(descricao.isNotBlank()) { "Descricao nao pode ser vazia" }
        require(valorBase.ehPositivo()) { "Valor base precisa ser maior que zero" }
        require(diaVencimento in 1..31) { "Dia de vencimento precisa estar entre 1 e 31" }
        require(encerradaEm == null || encerradaEm >= inicioEm) {
            "Encerramento nao pode ser anterior ao inicio"
        }
        require((escopo == Escopo.GRUPO) == (grupo != null)) {
            "Escopo GRUPO exige grupo, e escopo PESSOAL nao aceita grupo"
        }
    }

    /** RF-67, H-51: interrompe a geracao **sem apagar** o que ja existe. */
    fun encerrada(aPartirDe: Competencia) = copy(encerradaEm = aPartirDe)

    fun geraEm(competencia: Competencia): Boolean =
        competencia >= inicioEm && (encerradaEm == null || competencia <= encerradaEm!!)

    companion object {
        fun nova(
            descricao: String,
            valorBase: Dinheiro,
            diaVencimento: Int,
            tipo: TipoConta,
            categoria: UUID,
            dono: UUID,
            escopo: Escopo,
            grupo: UUID?,
            inicioEm: Competencia,
            criadoEm: Instant,
        ) = ContaRecorrente(
            id = UUID.randomUUID(),
            descricao = descricao.trim(),
            valorBase = valorBase,
            diaVencimento = diaVencimento,
            frequencia = Frequencia.MENSAL,
            tipo = tipo,
            categoria = categoria,
            dono = dono,
            escopo = escopo,
            grupo = grupo,
            inicioEm = inicioEm,
            criadoEm = criadoEm,
        )
    }
}

/**
 * Um vencimento na visao consolidada (RF-58, H-43).
 *
 * **Materializada e projetada sao indistinguiveis aqui**, e isso e deliberado:
 * a diferenca e de armazenamento, nao de negocio. O `id` nulo denuncia a
 * projecao para quem precisar agir sobre ela, e so.
 */
data class Vencimento(
    val id: UUID?,
    val descricao: String,
    val valor: Dinheiro,
    val dataVencimento: LocalDate,
    val tipo: TipoConta,
    val status: StatusConta,
    val donoId: UUID,
    val escopo: Escopo,
    val grupoId: UUID?,
    val recorrenteId: UUID?,
    val competencia: Competencia?,
)

interface ContaRepositorio : RepositorioComVisibilidade<ContaAPagar> {

    fun salvar(conta: ContaAPagar): ContaAPagar

    fun excluir(id: UUID)

    fun listarPorVencimento(de: LocalDate, ate: LocalDate): List<ContaAPagar>

    fun buscarOcorrencia(recorrenteId: UUID, competencia: Competencia): ContaAPagar?

    /** Ocorrencias ja materializadas de um conjunto de recorrentes, no periodo. */
    fun ocorrenciasMaterializadas(de: LocalDate, ate: LocalDate): List<ContaAPagar>

    /** ⚠️ Sem visibilidade: uso exclusivo do job (D-71). */
    fun salvarSemContexto(conta: ContaAPagar): ContaAPagar
}

interface RecorrenteRepositorio : RepositorioComVisibilidade<ContaRecorrente> {

    fun salvar(recorrente: ContaRecorrente): ContaRecorrente
}

/** Violacao do indice unico parcial de ocorrencia (RN-R04). */
class OcorrenciaJaMaterializada : RuntimeException("Ocorrencia ja existe para esta competencia")
