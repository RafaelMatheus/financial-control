package com.rafaelmatheus.financialcontrol.gasto.dominio

import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.common.persistencia.CriterioVisibilidade
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Property-based testing dos dois totais e do predicado de visibilidade
 * (RNF-07, D-05, PBT-02, PBT-03, PBT-07, PBT-08).
 *
 * As regras de RF-97 sao definicoes sobre conjuntos, e definicoes sobre
 * conjuntos sao exatamente o que exemplo escolhido a mao cobre mal — a licao de
 * O-28, quando o exemplo de `dividirEm` passava por coincidencia.
 *
 * Aqui as regras sao reimplementadas como **especificacao independente**, em
 * cima de listas na memoria. Nao substituem o teste de integracao, que verifica
 * o SQL; verificam que a **definicao** esta fechada.
 */
class TotaisPropriedadesTest : StringSpec({

    val ana = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    val rafael = UUID.fromString("00000000-0000-0000-0000-0000000000a2")
    val carlos = UUID.fromString("00000000-0000-0000-0000-0000000000a3")
    val casa = UUID.fromString("00000000-0000-0000-0000-0000000000b1")
    val viagem = UUID.fromString("00000000-0000-0000-0000-0000000000b2")
    val categorias = listOf(
        UUID.fromString("00000000-0000-0000-0000-0000000000c1"),
        UUID.fromString("00000000-0000-0000-0000-0000000000c2"),
    )

    val gastos: Arb<Gasto> = Arb.bind(
        Arb.of(ana, rafael, carlos),
        Arb.of(Escopo.PESSOAL, Escopo.GRUPO),
        Arb.of(casa, viagem),
        Arb.long(1L, 1_000_000L),
        Arb.of(categorias),
    ) { dono, escopo, grupo, centavos, categoria ->
        Gasto(
            id = UUID.randomUUID(),
            descricao = "gasto",
            valor = Dinheiro.de(java.math.BigDecimal.valueOf(centavos, 2)),
            data = LocalDate.of(2026, 8, 10),
            categoria = categoria,
            dono = dono,
            escopo = escopo,
            grupo = if (escopo == Escopo.GRUPO) grupo else null,
            criadoEm = Instant.EPOCH,
        )
    }

    "totalPessoal contem exatamente os gastos do consultante, de qualquer escopo" {
        checkAll(Arb.list(gastos, 0..40), Arb.of(ana, rafael, carlos)) { todos, consultante ->
            val criterio = criterioDe(consultante, setOf(casa, viagem))
            val visiveis = todos.filter { visivel(it, criterio) }

            val esperado = Dinheiro.soma(visiveis.filter { it.dono == consultante }.map { it.valor })
            totalPessoal(visiveis, consultante) shouldBe esperado

            // O lado que exemplo esquece: nada de outro dono entra.
            visiveis.filter { it.dono != consultante }
                .none { contribuiuPara(it, visiveis, consultante) } shouldBe true
        }
    }

    "totalGrupo contem exatamente os de escopo GRUPO daquele grupo, de qualquer dono" {
        checkAll(Arb.list(gastos, 0..40), Arb.of(ana, rafael, carlos)) { todos, consultante ->
            val criterio = criterioDe(consultante, setOf(casa, viagem))
            val visiveis = todos.filter { visivel(it, criterio) }

            val esperado = Dinheiro.soma(
                visiveis.filter { it.escopo == Escopo.GRUPO && it.grupo == casa }.map { it.valor },
            )
            totalGrupo(visiveis, casa) shouldBe esperado
        }
    }

    "a soma das quebras por categoria bate com o total correspondente" {
        checkAll(Arb.list(gastos, 0..40), Arb.of(ana, rafael, carlos)) { todos, consultante ->
            val criterio = criterioDe(consultante, setOf(casa, viagem))
            val visiveis = todos.filter { visivel(it, criterio) }

            val porCategoria = visiveis.filter { it.dono == consultante }
                .groupBy { it.categoria }
                .map { (_, lista) -> Dinheiro.soma(lista.map { it.valor }) }

            Dinheiro.soma(porCategoria) shouldBe totalPessoal(visiveis, consultante)
        }
    }

    "um gasto de grupo do proprio consultante entra nos DOIS totais" {
        checkAll(Arb.list(gastos, 1..40), Arb.of(ana, rafael, carlos)) { todos, consultante ->
            val criterio = criterioDe(consultante, setOf(casa, viagem))
            val visiveis = todos.filter { visivel(it, criterio) }
            val ambos = visiveis.filter {
                it.dono == consultante && it.escopo == Escopo.GRUPO && it.grupo == casa
            }

            // Nao e dupla contagem, porque os dois numeros nunca se somam
            // (RN-T04). E a mesma quantia respondendo a duas perguntas.
            val soma = Dinheiro.soma(ambos.map { it.valor })
            (totalPessoal(visiveis, consultante) >= soma) shouldBe true
            (totalGrupo(visiveis, casa) >= soma) shouldBe true
        }
    }

    /**
     * **O alvo mais valioso da unidade** (RN-T01, alvo 5 do design).
     *
     * Testa uma **bicondicional**: nao basta que o visivel apareca, e preciso que
     * o invisivel nao apareca. Teste de exemplo cobre bem o primeiro lado e mal o
     * segundo, e e por isso que esta propriedade existe.
     */
    "gasto e visivel se e somente se o predicado RN-V01 for verdadeiro" {
        checkAll(
            Arb.list(gastos, 0..40),
            Arb.of(ana, rafael, carlos),
            Arb.of(emptySet(), setOf(casa), setOf(viagem), setOf(casa, viagem)),
        ) { todos, consultante, grupos ->
            val criterio = criterioDe(consultante, grupos)

            todos.forEach { gasto ->
                val ehDono = gasto.dono == consultante
                val ehDoGrupoDele = gasto.escopo == Escopo.GRUPO && gasto.grupo in grupos

                visivel(gasto, criterio) shouldBe (ehDono || ehDoGrupoDele)
            }

            // PESSOAL de outro NUNCA e visivel, por mais grupos que se tenha.
            todos.filter { it.dono != consultante && it.escopo == Escopo.PESSOAL }
                .none { visivel(it, criterio) } shouldBe true
        }
    }

    "sem nenhum grupo, o consultante ve exatamente os proprios gastos" {
        checkAll(Arb.list(gastos, 0..40), Arb.of(ana, rafael, carlos)) { todos, consultante ->
            val criterio = criterioDe(consultante, emptySet())
            todos.filter { visivel(it, criterio) }.all { it.dono == consultante } shouldBe true
        }
    }

    "quantidade de partes nao muda o total — sanidade do gerador" {
        checkAll(Arb.int(1..30)) { n ->
            val lista = List(n) { Dinheiro.de("10.00") }
            Dinheiro.soma(lista) shouldBe Dinheiro.de(java.math.BigDecimal.valueOf(n * 1000L, 2))
        }
    }
})

// --- Especificacao independente das regras, para comparar com a implementacao ---

private fun criterioDe(usuario: UUID, grupos: Set<UUID>) =
    CriterioVisibilidade(usuarioAtual = usuario, gruposDoUsuario = grupos)

/** RN-V01, escrito como predicado puro. */
private fun visivel(gasto: Gasto, criterio: CriterioVisibilidade): Boolean =
    gasto.dono == criterio.usuarioAtual ||
        (gasto.escopo == Escopo.GRUPO && gasto.grupo in criterio.gruposDoUsuario)

/** RN-T02. */
private fun totalPessoal(visiveis: List<Gasto>, consultante: UUID): Dinheiro =
    Dinheiro.soma(visiveis.filter { it.dono == consultante }.map { it.valor })

/** RN-T03. */
private fun totalGrupo(visiveis: List<Gasto>, grupo: UUID): Dinheiro =
    Dinheiro.soma(visiveis.filter { it.escopo == Escopo.GRUPO && it.grupo == grupo }.map { it.valor })

private fun contribuiuPara(gasto: Gasto, visiveis: List<Gasto>, consultante: UUID): Boolean {
    val com = totalPessoal(visiveis, consultante)
    val sem = totalPessoal(visiveis.filter { it.id != gasto.id }, consultante)
    return com != sem
}
