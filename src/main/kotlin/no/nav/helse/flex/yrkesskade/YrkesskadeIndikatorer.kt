package no.nav.helse.flex.yrkesskade

import no.nav.helse.flex.client.yrkesskade.HarYsSak
import no.nav.helse.flex.client.yrkesskade.HarYsSakerRequest
import no.nav.helse.flex.client.yrkesskade.YrkesskadeClient
import no.nav.helse.flex.service.FolkeregisterIdenter
import org.springframework.stereotype.Component

@Component
class YrkesskadeIndikatorer(

    private val yrkesskadeClient: YrkesskadeClient,
    private val yrkesskadeSykmeldingRepository: YrkesskadeSykmeldingRepository
) {

    fun harYrkesskadeIndikatorer(identer: FolkeregisterIdenter, sykmeldingId: String?): Boolean {
        sykmeldingId?.let {
            if (yrkesskadeSykmeldingRepository.existsBySykmeldingId(sykmeldingId)) {
                return true
            }
        }

        val ysSakerResponse = yrkesskadeClient.hentYrkesskade(HarYsSakerRequest(identer.alle()))
        return ysSakerResponse.harYrkesskadeEllerYrkessykdom == HarYsSak.MAA_SJEKKES_MANUELT || ysSakerResponse.harYrkesskadeEllerYrkessykdom == HarYsSak.JA
    }
}
