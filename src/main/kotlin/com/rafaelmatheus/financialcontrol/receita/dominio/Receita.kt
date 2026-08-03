package com.rafaelmatheus.financialcontrol.receita.dominio

import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.persistencia.RepositorioComVisibilidade
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * O dinheiro que entra (RF-39 a RF-41).
 *
 * ## A unica entidade com dono e SEM escopo do sistema
 *
 * Nao existe "receita da casa" — premissa **P-05**, e H-36 e explicita: receitas
 * sao individuais, nao sao compartilhadas em grupo.
 *
 * A consequencia nao e de modelagem, e de produto: **nao ha renda familiar**, e
 * por isso o balanco e sempre pessoal. Um requisito de renda compartilhada seria
 * requisito novo, nao ajuste.
 *
 * Ela **continua estendendo `RepositorioComVisibilidade`** — o que muda e que o
 * predicado de RN-V01 se reduz a primeira metade, `dono == usuarioAtual`. Essa
 * metade nunca tinha sido exercitada sozinha em tres unidades: ate aqui, toda
 * entidade com dono tambem tinha escopo.
 */
data class Receita(
    val id: UUID,
    val descricao: String,
    val valor: Dinheiro,
    val data: LocalDate,
    val dono: UUID,
    val criadoEm: Instant,
) {
    init {
        require(descricao.isNotBlank()) { "Descricao nao pode ser vazia" }
        // Receita negativa e gasto, e gasto tem entidade propria.
        require(valor.ehPositivo()) { "Valor precisa ser maior que zero" }
    }

    fun editada(descricao: String, valor: Dinheiro, data: LocalDate) =
        copy(descricao = descricao.trim(), valor = valor, data = data)

    companion object {
        fun nova(descricao: String, valor: Dinheiro, data: LocalDate, dono: UUID, criadoEm: Instant) =
            Receita(UUID.randomUUID(), descricao.trim(), valor, data, dono, criadoEm)
    }
}

/** Porta de `receita`. Sem escopo, mas dentro do mesmo padrao (D-52, D-63). */
interface ReceitaRepositorio : RepositorioComVisibilidade<Receita> {

    fun salvar(receita: Receita): Receita

    fun excluir(id: UUID)

    fun listarPorPeriodo(de: LocalDate, ate: LocalDate): List<Receita>

    fun somarPeriodo(de: LocalDate, ate: LocalDate): Dinheiro
}
