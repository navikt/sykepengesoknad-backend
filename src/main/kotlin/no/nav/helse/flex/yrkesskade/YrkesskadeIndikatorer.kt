package no.nav.helse.flex.yrkesskade

import no.nav.helse.flex.client.yrkesskade.HarYsSak
import no.nav.helse.flex.client.yrkesskade.HarYsSakerRequest
import no.nav.helse.flex.client.yrkesskade.YrkesskadeClient
import no.nav.helse.flex.service.FolkeregisterIdenter
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class YrkesskadeIndikatorer(

    @Value("\${YRKESSKADE_ENABLET:false}")
    private val yrkesskadeEnablet: Boolean,
    private val yrkesskadeClient: YrkesskadeClient

) {

    fun harYrkesskadeIndikatorer(identer: FolkeregisterIdenter): Boolean {
        if (!yrkesskadeEnablet) {
            return false
        }

        val ysSakerResponse = yrkesskadeClient.hentYrkesskade(HarYsSakerRequest(identer.andreIdenter))
        return ysSakerResponse.harYrkesskadeEllerYrkessykdom == HarYsSak.MAA_SJEKKES_MANUELT || ysSakerResponse.harYrkesskadeEllerYrkessykdom == HarYsSak.JA
    }
}