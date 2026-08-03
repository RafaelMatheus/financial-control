package com.rafaelmatheus.financialcontrol.compra.dominio

import com.rafaelmatheus.financialcontrol.common.dominio.Competencia
import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.common.persistencia.RepositorioComVisibilidade
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Compra parcelada no cartao (RF-29 a RF-35).
 *
 * **A entrada e o valor TOTAL** (D-67), nao o valor da parcela. A Application
 * Design desenhou `LancarCompraParcelada(valorParcela, n)`; foi invertido por
 * decisao do usuario na Functional Design, ao resolver a contradicao de tres
 * pontas entre RF-29/H-27, RF-31/H-28 e o `dividirEm` de U1.
 *
 * ⚠️ **RF-29 e H-27 ficaram desatualizados**: o texto deles diz o inverso. E
 * pendencia de requisitos registrada, nao reinterpretacao.
 */
data class Compra(
    val id: UUID,
    val descricao: String,
    val valorTotal: Dinheiro,
    val numeroParcelas: Int,
    val dataCompra: LocalDate,
    val cartao: UUID,
    val categoria: UUID,
    val dono: UUID,
    val escopo: Escopo,
    val grupo: UUID?,
    val criadoEm: Instant,
) {
    init {
        require(descricao.isNotBlank()) { "Descricao nao pode ser vazia" }
        require(valorTotal.ehPositivo()) { "Valor total precisa ser maior que zero" }
        require(numeroParcelas >= 1) { "Numero de parcelas precisa ser pelo menos 1" }
        require((escopo == Escopo.GRUPO) == (grupo != null)) {
            "Escopo GRUPO exige grupo, e escopo PESSOAL nao aceita grupo"
        }
    }

    /**
     * RN-P06, H-30. A edicao e **sempre da compra inteira**: as parcelas sao
     * descartadas e regeradas. `dono` nao aparece — nao e validacao, e operacao
     * que nao existe.
     */
    fun editada(
        descricao: String,
        valorTotal: Dinheiro,
        numeroParcelas: Int,
        dataCompra: LocalDate,
        categoria: UUID,
        escopo: Escopo,
        grupo: UUID?,
    ) = copy(
        descricao = descricao.trim(),
        valorTotal = valorTotal,
        numeroParcelas = numeroParcelas,
        dataCompra = dataCompra,
        categoria = categoria,
        escopo = escopo,
        grupo = grupo,
    )

    companion object {
        fun nova(
            descricao: String,
            valorTotal: Dinheiro,
            numeroParcelas: Int,
            dataCompra: LocalDate,
            cartao: UUID,
            categoria: UUID,
            dono: UUID,
            escopo: Escopo,
            grupo: UUID?,
            criadoEm: Instant,
        ) = Compra(
            id = UUID.randomUUID(),
            descricao = descricao.trim(),
            valorTotal = valorTotal,
            numeroParcelas = numeroParcelas,
            dataCompra = dataCompra,
            cartao = cartao,
            categoria = categoria,
            dono = dono,
            escopo = escopo,
            grupo = grupo,
            criadoEm = criadoEm,
        )
    }
}

/**
 * Uma parcela. Pertence ao agregado [Compra] e **nao e editavel isoladamente**
 * (RF-33, RN-P06).
 *
 * Modelar a parcela como raiz de agregado convidaria a edita-la sozinha — e
 * isso quebraria a invariante `soma(parcelas) == valorTotal` sem que ninguem
 * percebesse, porque a soma continuaria fechando com um numero errado.
 */
data class Parcela(
    val id: UUID,
    val compra: UUID,
    val numero: Int,
    val valor: Dinheiro,
    val competencia: Competencia,
) {
    init {
        require(numero >= 1) { "Numero da parcela precisa ser pelo menos 1" }
    }

    companion object {
        fun nova(compra: UUID, numero: Int, valor: Dinheiro, competencia: Competencia) =
            Parcela(UUID.randomUUID(), compra, numero, valor, competencia)
    }
}

/**
 * Gera as parcelas de uma compra (RN-P03, RN-P05, D-68).
 *
 * Logica pura, testavel sem banco. Delega a divisao a `Dinheiro.dividirEm`, que
 * concentra o residuo na ultima parte e tem teste de propriedade.
 *
 * **O banco pode somar; dividir, nunca.** Esta e a unica divisao monetaria do
 * sistema, e por isso ela mora aqui e nao num `SUM`/`/` em SQL.
 */
object DivisorDeParcelas {

    fun gerar(
        compraId: UUID,
        valorTotal: Dinheiro,
        numeroParcelas: Int,
        primeiraCompetencia: Competencia,
    ): List<Parcela> {
        val valores = valorTotal.dividirEm(numeroParcelas)
        return valores.mapIndexed { indice, valor ->
            Parcela.nova(
                compra = compraId,
                numero = indice + 1,
                valor = valor,
                // RN-P05: a competencia da parcela n e a da primeira mais n-1 meses.
                competencia = primeiraCompetencia.mais(indice.toLong()),
            )
        }
    }
}

/** Compra com as suas parcelas, para consulta e para a invariante de RN-P04. */
data class CompraComParcelas(val compra: Compra, val parcelas: List<Parcela>) {
    init {
        require(Dinheiro.soma(parcelas.map { it.valor }) == compra.valorTotal) {
            "Invariante RF-32 violada: soma das parcelas difere do valor total"
        }
    }
}

/** Porta de `compra` (D-52, D-63). */
interface CompraRepositorio : RepositorioComVisibilidade<Compra> {

    fun salvar(compra: Compra, parcelas: List<Parcela>): CompraComParcelas

    fun buscarComParcelas(id: UUID): CompraComParcelas?

    fun excluir(id: UUID)

    /** Competencias que a compra ocupa hoje — base da uniao de RN-P08. */
    fun competenciasDe(compraId: UUID): Set<Competencia>
}
