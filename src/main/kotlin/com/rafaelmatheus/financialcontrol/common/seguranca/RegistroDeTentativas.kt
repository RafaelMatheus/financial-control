package com.rafaelmatheus.financialcontrol.common.seguranca

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Bloqueio temporario por conta apos falhas seguidas de login (NFR-U1-03, D-49).
 *
 * RN-U04 ja iguala o tempo de resposta para nao revelar quais e-mails existem.
 * Isso nao impede tentar milhares de senhas contra uma conta conhecida — e o que
 * este componente trata.
 *
 * **Estado em memoria.** E o unico componente com estado de toda a U1, e o unico
 * que quebraria com uma segunda instancia: cada uma contaria as suas, e 5
 * tentativas virariam 10 com duas instancias. Aceito por RNF-12, que exclui
 * escala horizontal, e registrado em `nfr-design-patterns.md` §4 para que a
 * busca seja curta no dia em que isso mudar.
 *
 * A chave e o e-mail **normalizado**, e nao o identificador do usuario: o
 * bloqueio precisa funcionar para e-mail que nem existe, senao a diferenca de
 * comportamento entre conta existente e inexistente volta a vazar.
 */
@Component
class RegistroDeTentativas(
    propriedades: PropriedadesAuth,
    private val relogio: Clock = Clock.systemUTC(),
) {
    private val maxTentativas = propriedades.maxTentativas
    private val bloqueioSegundos = propriedades.bloqueioMinutos * 60

    private data class Registro(val falhas: Int, val ultimaFalha: Instant)

    private val registros = ConcurrentHashMap<String, Registro>()

    fun estaBloqueado(chave: String): Boolean {
        val registro = registros[chave] ?: return false
        if (registro.falhas < maxTentativas) return false

        val expirouEm = registro.ultimaFalha.plusSeconds(bloqueioSegundos)
        if (relogio.instant().isAfter(expirouEm)) {
            registros.remove(chave)
            return false
        }
        return true
    }

    fun registrarFalha(chave: String) {
        registros.compute(chave) { _, atual ->
            val falhasAnteriores = atual?.falhas ?: 0
            Registro(falhas = falhasAnteriores + 1, ultimaFalha = relogio.instant())
        }
    }

    /** Login bem-sucedido zera o contador. */
    fun registrarSucesso(chave: String) {
        registros.remove(chave)
    }

    /**
     * Descarta registros vencidos. Sem isto o mapa cresceria indefinidamente com
     * e-mails tentados uma vez — que num sistema exposto e a maioria deles.
     */
    fun limparVencidos() {
        val limite = relogio.instant().minusSeconds(bloqueioSegundos)
        registros.entries.removeIf { it.value.ultimaFalha.isBefore(limite) }
    }
}
