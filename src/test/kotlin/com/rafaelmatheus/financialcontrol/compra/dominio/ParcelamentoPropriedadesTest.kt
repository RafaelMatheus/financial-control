package com.rafaelmatheus.financialcontrol.compra.dominio

import com.rafaelmatheus.financialcontrol.common.dominio.Competencia
import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import java.math.BigDecimal
import java.util.UUID

/** Uma edicao da compra, para a propriedade sobre sequencias (H-29). */
private data class Edicao(val valor: Dinheiro, val n: Int)

/**
 * As duas invariantes monetarias que restavam no sistema (RF-32, H-28, H-29).
 *
 * Property-based testing (RNF-07, D-05, PBT-02, PBT-03, PBT-07, PBT-08). E aqui
 * que `Dinheiro.dividirEm` finalmente ganha o consumidor que justificou existir
 * — e onde a regra de residuo escolhida em D-68 e verificada em uso.
 */
class ParcelamentoPropriedadesTest : StringSpec({

    val valoresArb = Arb.long(1L, 100_000_000L).map { Dinheiro.de(BigDecimal.valueOf(it, 2)) }
    val parcelasArb = Arb.int(1..120)
    val primeira = Competencia.de(2026, 9)

    fun gerar(valor: Dinheiro, n: Int) =
        DivisorDeParcelas.gerar(UUID.randomUUID(), valor, n, primeira)

    // ----------------------------------------------------------- RF-32, H-28

    "a soma das parcelas e exatamente o valor total" {
        // A invariante monetaria mais importante do sistema. Se ela falhar, o
        // usuario ve um total que nao bate com o que a loja cobrou.
        checkAll(valoresArb, parcelasArb) { valor, n ->
            Dinheiro.soma(gerar(valor, n).map { it.valor }) shouldBe valor
        }
    }

    "nenhuma parcela e negativa" {
        checkAll(valoresArb, parcelasArb) { valor, n ->
            gerar(valor, n).none { it.valor.ehNegativo() } shouldBe true
        }
    }

    "as primeiras n-1 parcelas sao iguais entre si — D-68" {
        // A regra de residuo escolhida: so a ULTIMA difere. Substitui a
        // propriedade "partes diferem no maximo 0,01", que valia ate U2 e passou
        // a ser falsa com D-68.
        checkAll(valoresArb, parcelasArb) { valor, n ->
            val parcelas = gerar(valor, n).sortedBy { it.numero }
            if (n > 1) {
                parcelas.dropLast(1).map { it.valor }.distinct().size shouldBe 1
            }
        }
    }

    "sao geradas exatamente n parcelas, numeradas de 1 a n sem lacuna" {
        checkAll(valoresArb, parcelasArb) { valor, n ->
            val numeros = gerar(valor, n).map { it.numero }.sorted()
            numeros shouldBe (1..n).toList()
        }
    }

    // ----------------------------------------------------------- RN-P05

    "a competencia da parcela n e a da primeira mais n-1 meses" {
        // Atravessa virada de ano sem tratamento especial: quem cuida disso e o
        // YearMonth dentro de Competencia.
        checkAll(valoresArb, parcelasArb) { valor, n ->
            val parcelas = gerar(valor, n).sortedBy { it.numero }
            parcelas.forEachIndexed { indice, parcela ->
                parcela.competencia shouldBe primeira.mais(indice.toLong())
            }
        }
    }

    "120 parcelas atravessam dez anos de competencia" {
        val parcelas = gerar(Dinheiro.de("12000.00"), 120).sortedBy { it.numero }
        parcelas.first().competencia shouldBe Competencia.de(2026, 9)
        parcelas.last().competencia shouldBe Competencia.de(2036, 8)
    }

    // -------------------------------------------------- H-29: apos SEQUENCIAS

    /**
     * **O alvo mais valioso da unidade.**
     *
     * H-29 pede a invariante *"inclusive depois de eu editá-la"* — ou seja, uma
     * propriedade sobre **sequencias de operacoes**, nao sobre um valor. E o unico
     * teste que pega o defeito de a edicao regenerar parcelas sem revalidar a
     * soma, que e exatamente o modo de falha que RF-32 teme.
     */
    "a soma continua exata apos qualquer sequencia de edicoes" {
        val edicoesArb: Arb<List<Edicao>> =
            Arb.list(Arb.bind(valoresArb, parcelasArb) { v, n -> Edicao(v, n) }, 1..8)

        checkAll(valoresArb, parcelasArb, edicoesArb) { valorInicial, nInicial, edicoes ->
            val compraId = UUID.randomUUID()
            var estado = DivisorDeParcelas.gerar(compraId, valorInicial, nInicial, primeira)
            Dinheiro.soma(estado.map { it.valor }) shouldBe valorInicial

            edicoes.forEach { edicao ->
                estado = DivisorDeParcelas.gerar(compraId, edicao.valor, edicao.n, primeira)
                // A invariante vale a CADA passo, nao so no fim.
                Dinheiro.soma(estado.map { it.valor }) shouldBe edicao.valor
                estado.size shouldBe edicao.n
            }
        }
    }

    "CompraComParcelas recusa a construcao se a soma nao fechar" {
        // A invariante de RF-32 mora no construtor: uma compra com parcelas
        // erradas nao chega a existir, muito menos ao banco.
        val compra = Compra.nova(
            descricao = "X",
            valorTotal = Dinheiro.de("100.00"),
            numeroParcelas = 2,
            dataCompra = java.time.LocalDate.of(2026, 8, 1),
            cartao = UUID.randomUUID(),
            categoria = UUID.randomUUID(),
            dono = UUID.randomUUID(),
            escopo = com.rafaelmatheus.financialcontrol.common.dominio.Escopo.PESSOAL,
            grupo = null,
            criadoEm = java.time.Instant.EPOCH,
        )
        val erradas = listOf(
            Parcela.nova(compra.id, 1, Dinheiro.de("50.00"), primeira),
            Parcela.nova(compra.id, 2, Dinheiro.de("49.99"), primeira.proxima()),
        )

        var recusou = false
        try {
            CompraComParcelas(compra, erradas)
        } catch (_: IllegalArgumentException) {
            recusou = true
        }
        recusou shouldBe true
    }

    // ------------------------------------------- os exemplos que D-68 fixou

    "mil e duzentos em doze: o caso comum, sem residuo" {
        val parcelas = gerar(Dinheiro.de("1200.00"), 12)
        parcelas.map { it.valor }.distinct().size shouldBe 1
        parcelas.first().valor shouldBe Dinheiro.de("100.00")
    }

    "cem em sete: os quatro centavos vao todos para a ultima" {
        val parcelas = gerar(Dinheiro.de("100.00"), 7).sortedBy { it.numero }
        parcelas.dropLast(1).all { it.valor == Dinheiro.de("14.28") } shouldBe true
        parcelas.last().valor shouldBe Dinheiro.de("14.32")
    }
})
