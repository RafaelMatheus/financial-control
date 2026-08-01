package com.rafaelmatheus.financialcontrol.common

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class ConfiguracaoComum {

    /**
     * Relogio injetado em vez de `Instant.now()` espalhado pelo codigo.
     *
     * Nao e purismo: sem isto, testar expiracao de bloqueio ou data de criacao
     * exigiria dormir de verdade — teste lento, e depois intermitente.
     *
     * UTC porque RNF-03 manda persistir timestamp em UTC.
     */
    @Bean
    fun relogio(): Clock = Clock.systemUTC()
}
