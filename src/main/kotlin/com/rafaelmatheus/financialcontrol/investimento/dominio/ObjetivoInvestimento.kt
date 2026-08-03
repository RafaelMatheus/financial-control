package com.rafaelmatheus.financialcontrol.investimento.dominio

import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.common.persistencia.RepositorioComVisibilidade
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Um bolso nomeado onde se guarda dinheiro com proposito (RF-68 a RF-77).
 *
 * `meta` e `prazoAlvo` sao **opcionais**: um objetivo aberto como "Geral" pode
 * nao ter alvo (RF-73, RF-74).
 *
 * ## A distincao entre `saldoAtual` e `totalAportado`
 *
 * | Valor | Natureza |
 * |---|---|
 * | `totalAportado` | **Derivado** — a soma dos aportes (D-82). Nao e coluna |
 * | `saldoAtual` | **Estado declarado pelo usuario** — quanto o dinheiro vale hoje |
 *
 * E a mesma distincao de U3 entre `Fatura.valorTotal` (derivado) e
 * `ContaAPagar.valor` (fato). O criterio consolidou-se em uma frase: **se o
 * numero e uma soma, calcule; se e um fato, guarde.**
 *
 * E por isso que RF-71 existe: o sistema nao tem cotacao, nao tem integracao com
 * corretora e nao pretende ter. O saldo e informacao que so o usuario possui — e
 * aceita-la a mao e o que permite calcular rendimento sem controlar ativos.
 */
data class ObjetivoInvestimento(
    val id: UUID,
    val nome: String,
    val meta: Dinheiro?,
    val prazoAlvo: LocalDate?,
    val saldoAtual: Dinheiro,
    val dono: UUID,
    val escopo: Escopo,
    val grupo: UUID?,
    val criadoEm: Instant,
) {
    init {
        require(nome.isNotBlank()) { "Nome do objetivo nao pode ser vazio" }
        require(meta == null || meta.ehPositivo()) { "A meta precisa ser maior que zero" }
        require((escopo == Escopo.GRUPO) == (grupo != null)) {
            "Escopo GRUPO exige grupo, e escopo PESSOAL nao aceita grupo"
        }
    }

    /** D-80: aportar **soma ao saldo**, e por isso o rendimento nao se move. */
    fun comAporte(valor: Dinheiro) = copy(saldoAtual = saldoAtual + valor)

    /**
     * D-83: excluir **subtrai**, simetrico a D-80.
     *
     * Sem isto, excluir um aporte de R$ 500 faria o rendimento **subir** R$ 500
     * do nada — porque `totalAportado` cairia (derivado, automatico) e o saldo
     * nao. Seria um erro sem excecao e sem log: apenas um numero que passou a
     * mentir.
     */
    fun semAporte(valor: Dinheiro) = copy(saldoAtual = saldoAtual - valor)

    fun comSaldo(novo: Dinheiro) = copy(saldoAtual = novo)

    fun editado(nome: String, meta: Dinheiro?, prazoAlvo: LocalDate?) =
        copy(nome = nome.trim(), meta = meta, prazoAlvo = prazoAlvo)

    companion object {
        fun novo(
            nome: String,
            meta: Dinheiro?,
            prazoAlvo: LocalDate?,
            dono: UUID,
            escopo: Escopo,
            grupo: UUID?,
            criadoEm: Instant,
        ) = ObjetivoInvestimento(
            UUID.randomUUID(), nome.trim(), meta, prazoAlvo, Dinheiro.ZERO,
            dono, escopo, grupo, criadoEm,
        )
    }
}

/**
 * Um aporte. Pertence ao agregado [ObjetivoInvestimento].
 *
 * **Cada aporte registra o seu dono** (RF-75): num objetivo de grupo, todos
 * aportam, e o saldo e a soma de todos os aportes — **sem rateio** (D-27).
 *
 * Valor estritamente positivo: resgate **nao** e aporte negativo. Ajusta-se o
 * `saldoAtual`.
 */
data class Aporte(
    val id: UUID,
    val objetivo: UUID,
    val valor: Dinheiro,
    val data: LocalDate,
    val dono: UUID,
    val criadoEm: Instant,
) {
    init {
        require(valor.ehPositivo()) { "Valor do aporte precisa ser maior que zero" }
    }

    companion object {
        fun novo(objetivo: UUID, valor: Dinheiro, data: LocalDate, dono: UUID, criadoEm: Instant) =
            Aporte(UUID.randomUUID(), objetivo, valor, data, dono, criadoEm)
    }
}

/** O objetivo com os numeros derivados (RF-72 a RF-74, RF-77). */
data class PosicaoDoObjetivo(
    val objetivo: ObjetivoInvestimento,
    val totalAportado: Dinheiro,
) {
    /**
     * RF-72, E-14, P-08. **Pode ser negativo, e deve ser exibido.**
     *
     * Prejuizo e resgate nao registrado sao casos reais. E a razao de `Dinheiro`
     * aceitar negativos desde U1, onde a decisao foi tomada sem consumidor a
     * vista — segunda vez que uma escolha de U1 sem consumidor imediato paga em
     * unidade posterior.
     */
    val rendimento: Dinheiro get() = objetivo.saldoAtual - totalAportado

    /** Sem meta, ausente — **nao zero** (RN-I07). */
    val progresso: BigDecimal? get() = objetivo.meta?.let {
        objetivo.saldoAtual.valor.divide(it.valor, 4, RoundingMode.HALF_UP)
    }

    val falta: Dinheiro? get() = objetivo.meta?.let {
        if (objetivo.saldoAtual >= it) Dinheiro.ZERO else it - objetivo.saldoAtual
    }

    val atrasado: Boolean get() =
        objetivo.prazoAlvo != null && objetivo.meta != null &&
            objetivo.prazoAlvo!!.isBefore(LocalDate.now()) && objetivo.saldoAtual < objetivo.meta!!
}

/**
 * Quanto aportar por mes para chegar a meta no prazo (RF-74, RN-I08, H-57).
 *
 * ## O arredondamento e PARA CIMA, e e o ponto da regra
 *
 * Esta e a **segunda divisao monetaria do sistema**, e ela tem direcao **oposta**
 * a do parcelamento:
 *
 * - no parcelamento, a soma tem que ser **exata** — as parcelas somam o total;
 * - aqui, a soma tem que ser **suficiente** — arredondar para baixo faria o
 *   usuario chegar ao prazo faltando centavos, o que derrota o proposito do
 *   numero.
 *
 * A propriedade verificada e `aporteMensal x meses >= falta`, sempre.
 */
object CalculadoraDeAporte {

    fun mensalNecessario(
        meta: Dinheiro?,
        saldoAtual: Dinheiro,
        prazoAlvo: LocalDate?,
        hoje: LocalDate,
    ): Dinheiro? {
        if (meta == null || prazoAlvo == null) return null
        if (saldoAtual >= meta) return Dinheiro.ZERO

        val meses = ChronoUnit.MONTHS.between(
            java.time.YearMonth.from(hoje),
            java.time.YearMonth.from(prazoAlvo),
        )
        // Prazo vencido: o objetivo e sinalizado como atrasado (E-15), e novos
        // aportes continuam permitidos. Nao ha aporte mensal a calcular.
        if (meses <= 0) return null

        val falta = (meta - saldoAtual).valor
        return Dinheiro.de(falta.divide(BigDecimal.valueOf(meses), 2, RoundingMode.CEILING))
    }
}

interface ObjetivoRepositorio : RepositorioComVisibilidade<ObjetivoInvestimento> {

    fun salvar(objetivo: ObjetivoInvestimento): ObjetivoInvestimento

    fun excluir(id: UUID)

    /** D-82: `SUM` na leitura, nunca coluna. */
    fun totalAportado(objetivoId: UUID): Dinheiro

    fun salvarAporte(aporte: Aporte): Aporte

    fun buscarAporte(id: UUID): Aporte?

    fun excluirAporte(id: UUID)

    /** RF-76, D-18: o aporte conta como gasto no balanco. */
    fun somarAportesDoConsultante(de: LocalDate, ate: LocalDate): Dinheiro
}
