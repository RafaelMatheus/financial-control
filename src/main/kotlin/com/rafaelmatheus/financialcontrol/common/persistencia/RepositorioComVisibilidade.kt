package com.rafaelmatheus.financialcontrol.common.persistencia

import java.util.UUID

/**
 * Porta de repositorio para entidades que tem dono (D-52, RN-V01, NFR-U1-04).
 *
 * **Nao existe `buscarTodos`. Nao existe `buscarPorId`.**
 *
 * Essa ausencia e a decisao de design mais importante de U1. Quem for escrever
 * uma consulta nova sem o filtro de visibilidade nao produz um bug: produz um
 * **erro de compilacao**, porque o metodo que ele quis chamar nao existe. A
 * garantia sai do dominio da disciplina e entra no do compilador.
 *
 * A alternativa avaliada foi o `@Filter` do Hibernate, que cobriria mais
 * caminhos automaticamente — inclusive consultas derivadas de nome — e falharia
 * **em silencio** se alguem esquecesse de habilitar o filtro na sessao. Trocaria
 * um erro impossivel por um erro invisivel, o que e mau negocio numa unidade
 * cuja razao de existir e o isolamento de dados.
 *
 * `UsuarioRepositorio` fica deliberadamente fora deste contrato: usuario nao tem
 * dono, e o cadastro precisa buscar por e-mail antes de haver autenticacao.
 *
 * Nenhuma entidade de U1 o implementa — `Usuario`, `Grupo` e `MembroGrupo` nao
 * tem dono no sentido de RF-03. A porta nasce aqui porque `common` e de U1, e
 * porque U2 em diante a implementam para `Gasto`, `Categoria` e o resto.
 */
interface RepositorioComVisibilidade<T> {

    /** Devolve a entidade **se o usuario atual puder ve-la**, senao `null`. */
    fun buscarVisivel(id: UUID): T?

    /** Lista somente o que o usuario atual enxerga. */
    fun listarVisiveis(): List<T>
}

/**
 * Predicado de visibilidade (RN-V01), na forma em que o dominio o entende.
 *
 * ```
 * dono == usuarioAtual  OU  (escopo == GRUPO E grupoId in gruposDoUsuario)
 * ```
 *
 * `gruposDoUsuario` considera apenas associacoes **ativas** — e onde D-44 (corte
 * total ao sair) se materializa.
 */
data class CriterioVisibilidade(
    val usuarioAtual: UUID,
    val gruposDoUsuario: Set<UUID>,
)
