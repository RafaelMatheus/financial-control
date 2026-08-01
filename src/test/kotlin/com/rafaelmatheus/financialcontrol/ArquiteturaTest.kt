package com.rafaelmatheus.financialcontrol

import com.rafaelmatheus.financialcontrol.common.persistencia.RepositorioComVisibilidade
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test
import kotlin.test.fail

/**
 * D-66 — imposicao estrutural do isolamento.
 *
 * E o principio de D-52 uma camada acima. D-52 pos o **compilador** na frente da
 * consulta sem filtro: a porta nao tem metodo cru, entao quem tentar escrever
 * uma produz erro de compilacao. Este arquivo poe o **CI** na frente da entidade
 * que nasceu fora do padrao.
 *
 * A motivacao e de escala: U1 provou o isolamento com um teste escrito a mao.
 * U2 acrescenta 2 entidades com dono; **U3 acrescenta 6**. Escrever a prova a
 * cada uma funciona enquanto alguem lembrar — e a suposicao "alguem lembra" e
 * exatamente a que este projeto vem substituindo por mecanismo.
 *
 * O que ele **nao** substitui: o teste de comportamento. Este arquivo prova que
 * a estrutura esta no lugar; so `IsolamentoDeGastosTest` prova que o predicado
 * devolve as linhas certas. Perguntas diferentes.
 */
class ArquiteturaTest {

    private val classes: JavaClasses = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages(RAIZ)

    /**
     * A regra de dependencia da arquitetura hexagonal (D-51): a seta aponta
     * sempre para dentro. O dominio nao sabe que existe banco nem framework.
     */
    @Test
    fun `dominio nao conhece JPA nem Spring`() {
        noClasses().that().resideInAPackage("..dominio..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "jakarta.persistence..",
                "org.springframework..",
                "org.hibernate..",
            )
            .because(
                "o dominio nao pode saber que existe banco nem framework (D-51). " +
                    "A seta de dependencia aponta sempre para dentro.",
            )
            .check(classes)
    }

    /** O adaptador e a fronteira. Saltar o servico salta a transacao e o criterio. */
    @Test
    fun `controller nao fala com adaptador de persistencia`() {
        noClasses().that().resideInAPackage("..adaptador.web..")
            .should().dependOnClassesThat().resideInAPackage("..adaptador.persistencia..")
            .because(
                "quem define a transacao e obtem o criterio de visibilidade e o " +
                    "servico; um controller que fala direto com o adaptador contorna os dois.",
            )
            .check(classes)
    }

    /**
     * **A regra central de D-66.**
     *
     * Toda entidade de dominio com campo `dono` e dado isolado por RN-V01. Se o
     * repositorio dela nao estende a porta, ela nasceu fora do isolamento — e
     * ninguem vai perceber ate alguem ver o dado de outro.
     */
    @Test
    fun `toda entidade com dono tem repositorio que estende a porta de visibilidade`() {
        val entidadesComDono = classes
            .filter { it.packageName.endsWith(".dominio") }
            .filter { classe -> classe.fields.any { it.name == CAMPO_DONO } }
            .map { it.simpleName }
            .toSet()

        val protegidas = classes
            .filter { it.isInterface }
            .filter { candidata ->
                candidata.allRawInterfaces.any { it.name == RepositorioComVisibilidade::class.java.name }
            }
            .flatMap { porta -> entidadesComDono.filter { porta.simpleName.startsWith(it) } }
            .toSet()

        // GUARDA CONTRA VACUIDADE.
        //
        // Um teste de arquitetura que nao encontra nada passa — e passa em
        // silencio, para sempre. Se um refactor mudar o nome do campo, o pacote
        // ou a forma como o Kotlin gera os campos, a regra acima deixaria de
        // proteger qualquer coisa sem nenhum sinal.
        //
        // As duas entidades de U2 sao fato conhecido hoje. U3 acrescenta seis, e
        // a assercao continua valendo sem manutencao.
        val esperadas = setOf("Categoria", "Gasto")
        val naoEncontradas = esperadas - entidadesComDono
        if (naoEncontradas.isNotEmpty()) {
            fail(
                "A deteccao de entidades com dono parou de funcionar: $naoEncontradas nao " +
                    "foram encontradas. Sem isto a regra desta classe passa por nao achar nada.",
            )
        }

        val desprotegidas = entidadesComDono - protegidas
        if (desprotegidas.isNotEmpty()) {
            fail(
                "Entidades com campo `dono` sem porta que estenda RepositorioComVisibilidade: " +
                    "$desprotegidas. Toda entidade com dono e dado isolado por RN-V01 — " +
                    "sem a porta, ela nasce fora do isolamento (D-66).",
            )
        }
    }

    /**
     * A garantia de D-63: nao existe consulta de dominio sem filtro obrigatorio.
     *
     * A porta-base declara `listarVisiveis()` sem argumento, e ela e a **unica**
     * excecao aceita — o adaptador aplica o criterio internamente. Qualquer outro
     * metodo de porta que devolva colecao precisa receber um filtro, senao o tipo
     * volta a permitir construir a pergunta "todos os registros".
     */
    @Test
    fun `metodo de porta que devolve colecao recebe filtro`() {
        val infratores = classes
            .filter { it.isInterface && it.packageName.endsWith(".dominio") }
            .filter { candidata ->
                candidata.allRawInterfaces.any { it.name == RepositorioComVisibilidade::class.java.name }
            }
            .flatMap { porta -> porta.methods }
            .filter { it.rawReturnType.name in TIPOS_DE_COLECAO }
            .filter { it.name !in METODOS_DA_PORTA_BASE }
            .filter { it.rawParameterTypes.isEmpty() }
            .map { "${it.owner.simpleName}.${it.name}" }

        if (infratores.isNotEmpty()) {
            fail(
                "Metodos de porta que devolvem colecao sem receber filtro: $infratores. " +
                    "D-63: o filtro obrigatorio no tipo e o que impede construir a " +
                    "pergunta 'todos os registros'.",
            )
        }
    }

    private companion object {
        const val RAIZ = "com.rafaelmatheus.financialcontrol"
        const val CAMPO_DONO = "dono"
        val TIPOS_DE_COLECAO = setOf("java.util.List", "java.util.Set", "java.util.Collection")

        /** `listarVisiveis()` e a unica sem argumento por design: o criterio vem do contexto. */
        val METODOS_DA_PORTA_BASE = setOf("listarVisiveis", "buscarVisivel")
    }
}
