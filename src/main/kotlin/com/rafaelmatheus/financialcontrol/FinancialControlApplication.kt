package com.rafaelmatheus.financialcontrol

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.boot.runApplication

/**
 * `@EnableScheduling` acrescentado em U3 (D-71): o fechamento diario de faturas
 * e o **primeiro componente agendado do sistema**. Ate U2, a tabela de ausencias
 * deliberadas de U1 listava "agendador" como algo que nao existia.
 */
@EnableScheduling
@SpringBootApplication
class FinancialControlApplication

fun main(args: Array<String>) {
    runApplication<FinancialControlApplication>(*args)
}
