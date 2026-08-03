package com.rafaelmatheus.financialcontrol.fatura.adaptador.web

import com.rafaelmatheus.financialcontrol.common.dominio.Competencia
import com.rafaelmatheus.financialcontrol.fatura.aplicacao.FaturaDTO
import com.rafaelmatheus.financialcontrol.fatura.aplicacao.FaturaService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * **Nao ha rota de fechar fatura** (D-71). O fechamento e consequencia da data,
 * disparado pelo job — expor a operacao convidaria a fechar a mao, criando um
 * estado que a data ainda nao justifica.
 *
 * Nao ha rota de pagar tambem: pagar e quitar a **conta a pagar** (RF-27, D-70),
 * e isso vive em `/contas`.
 */
@RestController
@RequestMapping("/cartoes/{cartaoId}/faturas")
class FaturaController(private val servico: FaturaService) {

    /** H-21, RF-26. Competencia no formato ISO `AAAA-MM`. */
    @GetMapping("/{competencia}")
    fun consultar(
        @PathVariable cartaoId: UUID,
        @PathVariable competencia: String,
    ): FaturaDTO = servico.consultar(cartaoId, Competencia.de(competencia))

    /** H-26, RF-28: projeta as faturas ate a competencia informada. */
    @GetMapping
    fun futuras(
        @PathVariable cartaoId: UUID,
        @RequestParam ate: String,
    ): List<FaturaDTO> = servico.consultarFuturas(cartaoId, Competencia.de(ate))
}
