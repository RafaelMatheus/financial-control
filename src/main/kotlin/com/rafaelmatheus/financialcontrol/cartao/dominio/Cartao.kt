package com.rafaelmatheus.financialcontrol.cartao.dominio

import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.common.persistencia.RepositorioComVisibilidade
import java.time.Instant
import java.util.UUID

/**
 * Cartao de credito e o seu ciclo (RF-23, RF-24).
 *
 * `diaFechamento` e `diaVencimento` aceitam **qualquer valor de 1 a 31**,
 * inclusive os que nao existem em todo mes. A alternativa era recusar dias
 * acima de 28, o que eliminaria o caso de borda ao custo de recusar cartoes
 * reais — ha cartao que fecha dia 30. D-69 resolve por queda para o ultimo dia
 * do mes, na `CalculadoraDeCompetencia`.
 *
 * **Sem `limite`**: nenhum requisito o pede, e um limite sem controle de
 * utilizacao e um numero que nao faz nada. Se surgir, vem com a regra do que
 * acontece ao estoura-lo.
 */
data class Cartao(
    val id: UUID,
    val apelido: String,
    val diaFechamento: Int,
    val diaVencimento: Int,
    val dono: UUID,
    val escopo: Escopo,
    val grupo: UUID?,
    val encerradoEm: Instant? = null,
    val criadoEm: Instant,
) {
    init {
        require(apelido.isNotBlank()) { "Apelido do cartao nao pode ser vazio" }
        require(diaFechamento in 1..31) { "Dia de fechamento precisa estar entre 1 e 31" }
        require(diaVencimento in 1..31) { "Dia de vencimento precisa estar entre 1 e 31" }
        require((escopo == Escopo.GRUPO) == (grupo != null)) {
            "Escopo GRUPO exige grupo, e escopo PESSOAL nao aceita grupo"
        }
    }

    fun estaAtivo(): Boolean = encerradoEm == null

    /** RN-K04: encerrar nao apaga historico; so impede lancamentos novos. */
    fun encerrado(quando: Instant): Cartao {
        check(estaAtivo()) { "Cartao ja encerrado" }
        return copy(encerradoEm = quando)
    }

    fun editado(
        apelido: String,
        diaFechamento: Int,
        diaVencimento: Int,
        escopo: Escopo,
        grupo: UUID?,
    ) = copy(
        apelido = apelido.trim(),
        diaFechamento = diaFechamento,
        diaVencimento = diaVencimento,
        escopo = escopo,
        grupo = grupo,
    )

    companion object {
        fun novo(
            apelido: String,
            diaFechamento: Int,
            diaVencimento: Int,
            dono: UUID,
            escopo: Escopo,
            grupo: UUID?,
            criadoEm: Instant,
        ) = Cartao(
            id = UUID.randomUUID(),
            apelido = apelido.trim(),
            diaFechamento = diaFechamento,
            diaVencimento = diaVencimento,
            dono = dono,
            escopo = escopo,
            grupo = grupo,
            criadoEm = criadoEm,
        )
    }
}

/** Porta de `cartao` (D-52, D-63). Toda operacao passa pela visibilidade. */
interface CartaoRepositorio : RepositorioComVisibilidade<Cartao> {

    fun salvar(cartao: Cartao): Cartao
}

/**
 * ⚠️ Consulta **sem filtro de visibilidade**, para uso exclusivo do job de
 * fechamento (D-71).
 *
 * ## Por que uma interface separada, e nao um metodo em `CartaoRepositorio`
 *
 * O job roda **sem usuario autenticado**: nao ha requisicao, logo nao ha
 * `ContextoUsuario` de onde tirar o criterio. Ele precisa enxergar os cartoes
 * de todo mundo, porque a fatura de todo mundo fecha.
 *
 * Posto como metodo da porta de visibilidade, isso reabriria exatamente o
 * buraco que D-52 e D-63 fecharam: uma operacao que devolve colecao sem exigir
 * filtro, disponivel a qualquer um que injete o repositorio. O
 * `ArquiteturaTest` de U2 reprovaria — e estaria certo.
 *
 * Separada, a excecao fica **nomeada, isolada e visivel**: quem injetar esta
 * interface num serviço de API esta declarando por escrito que quer dado de
 * todos os usuarios, e isso aparece na revisao. A regra de D-52 continua
 * valendo integralmente para `CartaoRepositorio`.
 */
interface CartoesParaFechamento {

    fun listarAtivos(): List<Cartao>
}
