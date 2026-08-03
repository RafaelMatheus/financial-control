package com.rafaelmatheus.financialcontrol.conta.aplicacao

import com.rafaelmatheus.financialcontrol.categoria.dominio.CategoriaRepositorio
import com.rafaelmatheus.financialcontrol.common.dominio.CalculadoraDeCompetencia
import com.rafaelmatheus.financialcontrol.common.dominio.Competencia
import com.rafaelmatheus.financialcontrol.common.dominio.Dinheiro
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.common.seguranca.ContextoUsuario
import com.rafaelmatheus.financialcontrol.common.web.CodigoErro
import com.rafaelmatheus.financialcontrol.common.web.DetalheErro
import com.rafaelmatheus.financialcontrol.common.web.ErroDeNegocio
import com.rafaelmatheus.financialcontrol.conta.dominio.ContaAPagar
import com.rafaelmatheus.financialcontrol.conta.dominio.ContaRecorrente
import com.rafaelmatheus.financialcontrol.conta.dominio.ContaRepositorio
import com.rafaelmatheus.financialcontrol.conta.dominio.OcorrenciaJaMaterializada
import com.rafaelmatheus.financialcontrol.conta.dominio.RecorrenteRepositorio
import com.rafaelmatheus.financialcontrol.conta.dominio.StatusConta
import com.rafaelmatheus.financialcontrol.conta.dominio.TipoConta
import com.rafaelmatheus.financialcontrol.conta.dominio.Vencimento
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

data class CadastrarConta(
    val descricao: String,
    val valor: Dinheiro,
    val dataVencimento: LocalDate,
    val tipo: TipoConta,
    val categoriaId: UUID,
    val escopo: Escopo,
    val grupoId: UUID?,
)

data class ContaDTO(
    val id: String,
    val descricao: String,
    val valor: String,
    val dataVencimento: String,
    val tipo: TipoConta,
    val status: StatusConta,
    val dataPagamento: String?,
    val categoriaId: String?,
    val donoId: String,
    val escopo: Escopo,
    val grupoId: String?,
    val derivadaDeFatura: Boolean,
)

fun ContaAPagar.paraDTO() = ContaDTO(
    id = id.toString(),
    descricao = descricao,
    valor = valor.toString(),
    dataVencimento = dataVencimento.toString(),
    tipo = tipo,
    status = status,
    dataPagamento = dataPagamento?.toString(),
    categoriaId = categoria?.toString(),
    donoId = dono.toString(),
    escopo = escopo,
    grupoId = grupo?.toString(),
    derivadaDeFatura = ehDerivadaDeFatura(),
)

data class VencimentoDTO(
    val id: String?,
    val descricao: String,
    val valor: String,
    val dataVencimento: String,
    val tipo: TipoConta,
    val status: StatusConta,
    val recorrenteId: String?,
    val competencia: String?,
)

data class VencimentosDTO(val itens: List<VencimentoDTO>, val total: String)

@Service
class ContaService(
    private val repositorio: ContaRepositorio,
    private val recorrentes: RecorrenteRepositorio,
    private val categorias: CategoriaRepositorio,
    private val contexto: ContextoUsuario,
    private val relogio: Clock,
) {

    /** H-42, RF-55. */
    @Transactional
    fun cadastrar(comando: CadastrarConta): ContaDTO {
        validar(comando)
        return repositorio.salvar(
            ContaAPagar.nova(
                descricao = comando.descricao,
                valor = comando.valor,
                dataVencimento = comando.dataVencimento,
                tipo = comando.tipo,
                categoria = comando.categoriaId,
                dono = contexto.usuarioAtual(),
                escopo = comando.escopo,
                grupo = comando.grupoId,
                criadoEm = relogio.instant(),
            ),
        ).paraDTO()
    }

    /** H-42, H-49. Qualquer membro edita conta de grupo (RN-A07). */
    @Transactional
    fun editar(id: UUID, comando: CadastrarConta): ContaDTO {
        val conta = exigirVisivel(id)
        // RN-A06, P-11, H-45: o valor deriva dos lancamentos da fatura.
        if (conta.ehDerivadaDeFatura()) throw ErroDeNegocio(CodigoErro.CONTA_DERIVADA)
        validar(comando)
        return repositorio.salvar(
            conta.copy(
                descricao = comando.descricao.trim(),
                valor = comando.valor,
                dataVencimento = comando.dataVencimento,
                tipo = comando.tipo,
                categoria = comando.categoriaId,
                escopo = comando.escopo,
                grupo = comando.grupoId,
            ),
        ).paraDTO()
    }

    @Transactional
    fun excluir(id: UUID) {
        val conta = exigirVisivel(id)
        if (conta.ehDerivadaDeFatura()) throw ErroDeNegocio(CodigoErro.CONTA_DERIVADA)
        repositorio.excluir(id)
    }

    /**
     * H-44, RF-57, RF-64.
     *
     * `valorAjustado` atende H-48: contas como energia variam mes a mes, e o
     * ajuste e **da ocorrencia**. O `valorBase` da regra recorrente nao muda —
     * e as ocorrencias futuras continuam usando o base (RN-R03).
     */
    @Transactional
    fun marcarPaga(id: UUID, dataPagamento: LocalDate, valorAjustado: Dinheiro?): ContaDTO {
        val conta = exigirVisivel(id)
        if (valorAjustado != null && conta.ehDerivadaDeFatura()) {
            throw ErroDeNegocio(CodigoErro.CONTA_DERIVADA)
        }
        return repositorio.salvar(conta.paga(dataPagamento, valorAjustado)).paraDTO()
    }

    /**
     * H-23, RF-94. Desmarcar e a **unica via** de corrigir lancamentos numa
     * fatura paga — e por isso ela existe: sem a operacao inversa, H-24 deixaria
     * o erro preso para sempre.
     *
     * Como o status da fatura e derivado (D-70), desmarcar aqui reabre a fatura
     * para alteracoes sem tocar nela.
     */
    @Transactional
    fun desmarcarPagamento(id: UUID): ContaDTO =
        repositorio.salvar(exigirVisivel(id).pagamentoDesmarcado()).paraDTO()

    /**
     * H-43, RF-58. **Onde tudo converge**: fatura de cartao, PIX, boleto e conta
     * de servico, ordenados por vencimento, com o total do periodo.
     *
     * As ocorrencias recorrentes ainda **nao materializadas** entram por
     * projecao (RN-R02, D-72). Quem consulta nao distingue.
     */
    @Transactional(readOnly = true)
    fun vencimentosDoPeriodo(de: LocalDate, ate: LocalDate): VencimentosDTO {
        val materializadas = repositorio.listarPorVencimento(de, ate)
        val jaMaterializadas = materializadas
            .filter { it.origemRecorrente != null }
            .map { it.origemRecorrente to it.competenciaRecorrencia }
            .toSet()

        val projetadas = recorrentes.listarVisiveis().flatMap { regra ->
            competenciasEntre(de, ate)
                .filter { regra.geraEm(it) }
                .filter { (regra.id to it) !in jaMaterializadas }
                .mapNotNull { competencia -> projetar(regra, competencia, de, ate) }
        }

        val itens = (materializadas.map { it.paraVencimento() } + projetadas)
            .sortedBy { it.dataVencimento }

        return VencimentosDTO(
            itens = itens.map { it.paraDTO() },
            total = Dinheiro.soma(itens.map { it.valor }).toString(),
        )
    }

    /** H-50, RF-66. */
    @Transactional(readOnly = true)
    fun aVencer(dias: Int): VencimentosDTO {
        val hoje = LocalDate.now(relogio)
        return vencimentosDoPeriodo(hoje, hoje.plusDays(dias.toLong()))
    }

    /** H-50, RF-66. Vencida e vencimento passado **e** status EM_ABERTO. */
    @Transactional(readOnly = true)
    fun vencidas(): List<ContaDTO> {
        val hoje = LocalDate.now(relogio)
        return repositorio.listarPorVencimento(LocalDate.of(2000, 1, 1), hoje.minusDays(1))
            .filter { it.estaVencida(hoje) }
            .map { it.paraDTO() }
    }

    /**
     * D-72: a ocorrencia **materializa ao ser tocada**.
     *
     * A consulta so projeta; a linha vira registro quando ganha estado proprio —
     * pagamento ou valor ajustado. Materializar tudo criaria linhas ate o
     * infinito para contas que talvez sejam encerradas antes.
     */
    @Transactional
    fun materializarOcorrencia(recorrenteId: UUID, competencia: Competencia): ContaDTO {
        repositorio.buscarOcorrencia(recorrenteId, competencia)?.let { return it.paraDTO() }

        val regra = recorrentes.buscarVisivel(recorrenteId)
            ?: throw ErroDeNegocio(CodigoErro.RECORRENTE_NAO_ENCONTRADA)
        if (!regra.geraEm(competencia)) throw ErroDeNegocio(CodigoErro.RECORRENTE_NAO_ENCONTRADA)

        return try {
            repositorio.salvar(ocorrenciaDe(regra, competencia)).paraDTO()
        } catch (_: OcorrenciaJaMaterializada) {
            // Corrida: outra requisicao ganhou. A releitura NAO acontece aqui —
            // a transacao esta abortada (licao do 25P02 de U2). Quem chama rele.
            throw ErroDeNegocio(CodigoErro.RECORRENTE_NAO_ENCONTRADA)
        }
    }

    private fun ocorrenciaDe(regra: ContaRecorrente, competencia: Competencia) =
        ContaAPagar.nova(
            descricao = regra.descricao,
            valor = regra.valorBase,
            dataVencimento = CalculadoraDeCompetencia.dataDeVencimento(competencia, regra.diaVencimento),
            tipo = regra.tipo,
            categoria = regra.categoria,
            dono = regra.dono,
            escopo = regra.escopo,
            grupo = regra.grupo,
            criadoEm = relogio.instant(),
            origemRecorrente = regra.id,
            competenciaRecorrencia = competencia,
        )

    private fun projetar(
        regra: ContaRecorrente,
        competencia: Competencia,
        de: LocalDate,
        ate: LocalDate,
    ): Vencimento? {
        val vencimento = CalculadoraDeCompetencia.dataDeVencimento(competencia, regra.diaVencimento)
        if (vencimento < de || vencimento > ate) return null
        return Vencimento(
            id = null, // projetada: ainda nao tem linha
            descricao = regra.descricao,
            valor = regra.valorBase,
            dataVencimento = vencimento,
            tipo = regra.tipo,
            status = StatusConta.EM_ABERTO,
            donoId = regra.dono,
            escopo = regra.escopo,
            grupoId = regra.grupo,
            recorrenteId = regra.id,
            competencia = competencia,
        )
    }

    private fun competenciasEntre(de: LocalDate, ate: LocalDate): List<Competencia> {
        var atual = YearMonth.from(de)
        val fim = YearMonth.from(ate)
        val resultado = mutableListOf<Competencia>()
        while (atual <= fim) {
            resultado += Competencia(atual)
            atual = atual.plusMonths(1)
        }
        return resultado
    }

    private fun validar(comando: CadastrarConta) {
        if (comando.descricao.isBlank()) {
            throw ErroDeNegocio(
                CodigoErro.NOME_OBRIGATORIO,
                listOf(DetalheErro("descricao", "obrigatoria")),
            )
        }
        if (!comando.valor.ehPositivo()) throw ErroDeNegocio(CodigoErro.VALOR_INVALIDO)
        if ((comando.escopo == Escopo.GRUPO) != (comando.grupoId != null)) {
            throw ErroDeNegocio(CodigoErro.GRUPO_INVALIDO)
        }
        if (comando.grupoId != null && comando.grupoId !in contexto.gruposDoUsuario()) {
            throw ErroDeNegocio(CodigoErro.GRUPO_NAO_ENCONTRADO)
        }
        categorias.buscarVisivel(comando.categoriaId)
            ?: throw ErroDeNegocio(CodigoErro.CATEGORIA_NAO_ENCONTRADA)
    }

    private fun exigirVisivel(id: UUID): ContaAPagar =
        repositorio.buscarVisivel(id) ?: throw ErroDeNegocio(CodigoErro.CONTA_NAO_ENCONTRADA)
}

private fun ContaAPagar.paraVencimento() = Vencimento(
    id = id,
    descricao = descricao,
    valor = valor,
    dataVencimento = dataVencimento,
    tipo = tipo,
    status = status,
    donoId = dono,
    escopo = escopo,
    grupoId = grupo,
    recorrenteId = origemRecorrente,
    competencia = competenciaRecorrencia,
)

private fun Vencimento.paraDTO() = VencimentoDTO(
    id = id?.toString(),
    descricao = descricao,
    valor = valor.toString(),
    dataVencimento = dataVencimento.toString(),
    tipo = tipo,
    status = status,
    recorrenteId = recorrenteId?.toString(),
    competencia = competencia?.toString(),
)
