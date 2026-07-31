package com.rafaelmatheus.financialcontrol.common.dominio

/**
 * Alcance de um lancamento (RF-11).
 *
 * PESSOAL e impenetravel: visivel apenas ao dono, mesmo entre membros do mesmo
 * grupo (RN-V03). GRUPO e visivel a quem tem associacao ativa no grupo.
 *
 * Nenhuma entidade de U1 o usa. Existe aqui porque `Visibilidade` precisa
 * conhece-lo para escrever o predicado, e a partir de U2 todo lancamento o
 * carrega.
 */
enum class Escopo {
    PESSOAL,
    GRUPO,
}
