package com.rafaelmatheus.financialcontrol.receita.aplicacao

import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.seguranca.ContextoUsuario
import com.rafaelmatheus.financialcontrol.common.web.CodigoErro
import com.rafaelmatheus.financialcontrol.common.web.ErroDeNegocio
import com.rafaelmatheus.financialcontrol.gasto.dominio.FiltroGasto
import com.rafaelmatheus.financialcontrol.gasto.dominio.GastoRepositorio
import com.rafaelmatheus.financialcontrol.investimento.dominio.ObjetivoRepositorio
import com.rafaelmatheus.financialcontrol.receita.dominio.Receita
import com.rafaelmatheus.financialcontrol.receita.dominio.ReceitaRepositorio
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

data class CadastrarReceita(val descricao: String, val valor: Dinheiro, val data: LocalDate)

data class ReceitaDTO(
    val id: String,
    val descricao: String,
    val valor: String,
    val data: String,
    val donoId: String,
)

fun Receita.paraDTO() = ReceitaDTO(
    id.toString(), descricao, valor.toString(), data.toString(), dono.toString(),
)

data class PeriodoDeReceitasDTO(val itens: List<ReceitaDTO>, val total: String)

/**
 * O balanco do periodo (RF-41, H-38).
 *
 * **Nao ha versao de grupo**, e nao por simplificacao: sem receita de grupo
 * (P-05, RN-RC02), nao existe o outro lado da conta.
 */
data class BalancoDTO(
    val de: String,
    val ate: String,
    val receitas: String,
    val gastos: String,
    /** RF-76, D-18: o aporte conta como saida. */
    val aportes: String,
    val resultado: String,
)

@Service
class ReceitaService(
    private val repositorio: ReceitaRepositorio,
    private val gastos: GastoRepositorio,
    private val objetivos: ObjetivoRepositorio,
    private val contexto: ContextoUsuario,
    private val relogio: Clock,
) {

    /** H-36, RF-39. */
    @Transactional
    fun cadastrar(comando: CadastrarReceita): ReceitaDTO {
        validar(comando)
        return repositorio.salvar(
            Receita.nova(
                comando.descricao, comando.valor, comando.data,
                contexto.usuarioAtual(), relogio.instant(),
            ),
        ).paraDTO()
    }

    @Transactional
    fun editar(id: UUID, comando: CadastrarReceita): ReceitaDTO {
        val receita = exigirVisivel(id)
        validar(comando)
        return repositorio.salvar(
            receita.editada(comando.descricao, comando.valor, comando.data),
        ).paraDTO()
    }

    @Transactional
    fun excluir(id: UUID) {
        exigirVisivel(id)
        repositorio.excluir(id)
    }

    /** H-37, RF-40. */
    @Transactional(readOnly = true)
    fun consultar(de: LocalDate, ate: LocalDate): PeriodoDeReceitasDTO {
        val itens = repositorio.listarPorPeriodo(de, ate)
        return PeriodoDeReceitasDTO(
            itens = itens.map { it.paraDTO() },
            total = Dinheiro.soma(itens.map { it.valor }).toString(),
        )
    }

    /**
     * H-38, RF-41, RF-76. **Receitas menos gastos menos aportes.**
     *
     * O aporte entra com sinal negativo, e isso e a decisao D-18: investir
     * **reduz** o resultado do mes, embora o patrimonio nao diminua. O balanco
     * mede **fluxo de caixa**, nao variacao patrimonial — escolha consciente que
     * define o significado do indicador.
     *
     * Os gastos vem de `totalPessoal` (U2): so os lancamentos de que o
     * consultante e dono, de qualquer escopo. O balanco e sempre pessoal.
     */
    @Transactional(readOnly = true)
    fun balanco(de: LocalDate, ate: LocalDate): BalancoDTO {
        val receitas = repositorio.somarPeriodo(de, ate)
        val totais = gastos.totalizar(FiltroGasto(de = de, ate = ate, donoId = contexto.usuarioAtual()))
        val aportes = objetivos.somarAportesDoConsultante(de, ate)

        val resultado = receitas - totais.totalPessoal - aportes
        return BalancoDTO(
            de = de.toString(),
            ate = ate.toString(),
            receitas = receitas.toString(),
            gastos = totais.totalPessoal.toString(),
            aportes = aportes.toString(),
            resultado = resultado.toString(),
        )
    }

    private fun validar(comando: CadastrarReceita) {
        if (comando.descricao.isBlank()) throw ErroDeNegocio(CodigoErro.NOME_OBRIGATORIO)
        if (!comando.valor.ehPositivo()) throw ErroDeNegocio(CodigoErro.VALOR_INVALIDO)
    }

    private fun exigirVisivel(id: UUID): Receita =
        repositorio.buscarVisivel(id) ?: throw ErroDeNegocio(CodigoErro.RECEITA_NAO_ENCONTRADA)
}
