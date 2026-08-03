package com.rafaelmatheus.financialcontrol.common.dominio

/**
 * Como o "realizado" do orcamento conta um gasto de cartao (D-77, **J-02**).
 *
 * ```
 * Compra de R$ 1.200,00 em 12x, feita em 30/07, cartao que fecha dia 28:
 *
 *   DATA_DA_COMPRA -> R$ 1.200,00 em julho
 *   COMPETENCIA    -> R$ 100,00 por mes, de setembro a agosto do ano seguinte
 * ```
 *
 * As duas respondem a perguntas legitimas e diferentes: *"quanto me comprometi
 * neste mes"* e *"quanto vou pagar neste mes"*. Cada orcamento declara a sua.
 *
 * Para gasto **a vista** as duas coincidem, porque a competencia e nula e a data
 * e a unica referencia. A escolha so importa onde ha cartao.
 *
 * ## Por que este tipo mora em `common`, e nao em `orcamento`
 *
 * O conceito **nasceu em U4**, com D-77. Mas a porta que o consome —
 * `ConsultaDeRealizado` — vive no dominio de `gasto`, que e de **U2** (D-81).
 * Se o enum ficasse em `orcamento`, uma porta de U2 importaria um tipo de U4, e
 * a seta de dependencia apontaria da unidade mais antiga para a mais nova.
 *
 * Ele e vocabulario **compartilhado**: U2 e U3 sao quem **sabem as duas datas**,
 * U4 e quem **escolhe qual usar**. Nenhuma das duas e dona sozinha — que e
 * exatamente o criterio de `common`, o mesmo de `Escopo` e `Competencia`.
 *
 * E a primeira vez no ciclo que um conceito nasce numa unidade e **retrocede**
 * para `common`.
 */
enum class BaseDoRealizado {

    /** Conta pelo dia em que se comprou. Mede comprometimento. */
    DATA_DA_COMPRA,

    /** Conta pelo mes em que se paga. Mede desembolso. */
    COMPETENCIA,
}
