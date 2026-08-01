package com.rafaelmatheus.financialcontrol.categoria.aplicacao

import com.rafaelmatheus.financialcontrol.categoria.dominio.Categoria
import com.rafaelmatheus.financialcontrol.categoria.dominio.CategoriaDuplicada
import com.rafaelmatheus.financialcontrol.categoria.dominio.CategoriaRepositorio
import com.rafaelmatheus.financialcontrol.common.dominio.Escopo
import com.rafaelmatheus.financialcontrol.common.seguranca.ContextoUsuario
import com.rafaelmatheus.financialcontrol.common.web.CodigoErro
import com.rafaelmatheus.financialcontrol.common.web.DetalheErro
import com.rafaelmatheus.financialcontrol.common.web.ErroDeNegocio
import com.rafaelmatheus.financialcontrol.gasto.dominio.GastoRepositorio
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

data class CategoriaDTO(
    val id: String,
    val nome: String,
    val escopo: Escopo,
    val grupoId: String?,
    val donoId: String,
)

fun Categoria.paraDTO() = CategoriaDTO(
    id = id.toString(),
    nome = nome,
    escopo = escopo,
    grupoId = grupo?.toString(),
    donoId = dono.toString(),
)

data class CriarCategoria(val nome: String, val escopo: Escopo, val grupoId: UUID?)

@Service
class CategoriaService(
    private val repositorio: CategoriaRepositorio,
    private val gastos: GastoRepositorio,
    private val contexto: ContextoUsuario,
    private val relogio: Clock,
) {

    /** H-33, RF-36. */
    @Transactional
    fun criar(comando: CriarCategoria): CategoriaDTO {
        if (comando.nome.isBlank()) {
            throw ErroDeNegocio(CodigoErro.NOME_OBRIGATORIO, listOf(DetalheErro("nome", "obrigatorio")))
        }
        exigirEscopoValido(comando.escopo, comando.grupoId)

        val dono = contexto.usuarioAtual()

        // Verificar para dar mensagem; o indice unico parcial e que garante
        // (RN-C02). Duas requisicoes simultaneas passam as duas por aqui.
        if (repositorio.existeComNome(
                Categoria.chaveDeComparacao(comando.nome), comando.escopo, dono, comando.grupoId,
            )
        ) {
            throw ErroDeNegocio(CodigoErro.CATEGORIA_DUPLICADA)
        }

        return try {
            repositorio.salvar(
                Categoria.nova(comando.nome, dono, comando.escopo, comando.grupoId, relogio.instant()),
            ).paraDTO()
        } catch (_: CategoriaDuplicada) {
            throw ErroDeNegocio(CodigoErro.CATEGORIA_DUPLICADA)
        }
    }

    /**
     * H-33, RF-36. **Qualquer membro renomeia** categoria de grupo (RN-C04) — o
     * `buscarVisivel` ja resolve a permissao, porque nesta unidade enxergar e
     * poder editar sao a mesma coisa. O `dono` permanece quem criou.
     */
    @Transactional
    fun renomear(id: UUID, nome: String): CategoriaDTO {
        if (nome.isBlank()) {
            throw ErroDeNegocio(CodigoErro.NOME_OBRIGATORIO, listOf(DetalheErro("nome", "obrigatorio")))
        }
        val categoria = exigirVisivel(id)

        val chave = Categoria.chaveDeComparacao(nome)
        if (chave != Categoria.chaveDeComparacao(categoria.nome) &&
            repositorio.existeComNome(chave, categoria.escopo, categoria.dono, categoria.grupo)
        ) {
            throw ErroDeNegocio(CodigoErro.CATEGORIA_DUPLICADA)
        }

        return try {
            repositorio.salvar(categoria.comNome(nome)).paraDTO()
        } catch (_: CategoriaDuplicada) {
            throw ErroDeNegocio(CodigoErro.CATEGORIA_DUPLICADA)
        }
    }

    /**
     * H-34, RF-37, E-06. Bloqueia se houver lancamentos vinculados, salvo se
     * [realocarPara] for informado.
     *
     * **Tudo numa transacao so**: se a realocacao afetasse metade dos gastos e a
     * exclusao falhasse, ficaria uma categoria viva com parte do historico
     * reclassificado — pior do que nao ter tentado.
     *
     * A realocacao alcanca gastos de **qualquer dono** (RN-C06, D-59): quem pode
     * editar o gasto do grupo (RF-16) pode reclassifica-lo.
     */
    @Transactional
    fun excluir(id: UUID, realocarPara: UUID? = null) {
        val categoria = exigirVisivel(id)
        val vinculados = gastos.contarPorCategoria(id)

        if (vinculados > 0) {
            if (realocarPara == null) {
                // A contagem faz parte da regra (RN-C05): e o numero com que o
                // usuario decide se realoca ou desiste. H-34 pede isso.
                throw ErroDeNegocio(
                    CodigoErro.CATEGORIA_EM_USO,
                    listOf(DetalheErro("lancamentosVinculados", vinculados.toString())),
                )
            }
            if (realocarPara == categoria.id) {
                throw ErroDeNegocio(CodigoErro.REALOCACAO_INVALIDA)
            }
            exigirVisivel(realocarPara)
            gastos.realocarCategoria(de = id, para = realocarPara)
        }

        repositorio.excluir(id)
    }

    /**
     * H-33 e **H-35 na mesma operacao** (RN-C08, D-56).
     *
     * Sem nenhuma categoria visivel, o conjunto inicial nasce aqui. O criterio e
     * "nenhuma **visivel**", nao "nenhuma propria": quem entra num grupo que ja
     * tem categorias nao recebe as iniciais, porque ja tem com o que classificar.
     *
     * Consequencia registrada e aceita: quem apagar todas ve as dez ressurgirem.
     * O preco de nao guardar estado de "ja recebeu".
     */
    @Transactional
    fun listar(): List<CategoriaDTO> {
        val existentes = repositorio.listarVisiveis()
        if (existentes.isNotEmpty()) return existentes.map { it.paraDTO() }

        val dono = contexto.usuarioAtual()
        val agora = relogio.instant()
        return try {
            repositorio.salvarTodas(
                Categoria.INICIAIS.map { Categoria.nova(it, dono, Escopo.PESSOAL, null, agora) },
            ).map { it.paraDTO() }
        } catch (_: CategoriaDuplicada) {
            // Duas requisicoes simultaneas de um usuario novo — o front
            // carregando duas telas ao mesmo tempo — chegam as duas com a lista
            // vazia. O indice unico absorve a corrida; o perdedor rele.
            repositorio.listarVisiveis().map { it.paraDTO() }
        }
    }

    /**
     * 404 e nao 403, pela mesma razao de RN-G03: quem nao enxerga a categoria
     * nao deve descobrir que ela existe.
     */
    private fun exigirVisivel(id: UUID): Categoria =
        repositorio.buscarVisivel(id) ?: throw ErroDeNegocio(CodigoErro.CATEGORIA_NAO_ENCONTRADA)

    private fun exigirEscopoValido(escopo: Escopo, grupoId: UUID?) {
        if (!Categoria.escopoCasaComGrupo(escopo, grupoId)) {
            throw ErroDeNegocio(CodigoErro.GRUPO_INVALIDO)
        }
        // Escopo GRUPO exige associacao ativa (RN-C03). O contexto so conhece os
        // grupos ativos, entao a checagem e a propria materializacao de D-44.
        if (grupoId != null && grupoId !in contexto.gruposDoUsuario()) {
            throw ErroDeNegocio(CodigoErro.GRUPO_NAO_ENCONTRADO)
        }
    }
}
