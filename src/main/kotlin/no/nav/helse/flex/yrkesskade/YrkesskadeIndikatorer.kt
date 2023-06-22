package no.nav.helse.flex.yrkesskade

import no.nav.helse.flex.client.yrkesskade.HarYsSak
import no.nav.helse.flex.client.yrkesskade.HarYsSakerRequest
import no.nav.helse.flex.client.yrkesskade.YrkesskadeClient
import no.nav.helse.flex.service.FolkeregisterIdenter
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class YrkesskadeIndikatorer(

    private val yrkesskadeClient: YrkesskadeClient,
    private val yrkesskadeSykmeldingRepository: YrkesskadeSykmeldingRepository,
    @Value("\${YRKESSKADE_V2}") var yrkesskadeV2: Boolean

) {

    val godkjenteSaker = setOf("DELVIS_INNVILGET", "INNVILGET", "DELVIS_GODKJENT", "GODKJENT")

    fun hentYrkesskadeSporsmalGrunnlag(
        identer: FolkeregisterIdenter,
        sykmeldingId: String?,
        erForsteSoknadISykeforlop: Boolean
    ): YrkesskadeSporsmalGrunnlag {
        if (!erForsteSoknadISykeforlop) {
            return YrkesskadeSporsmalGrunnlag(
                v2Enabled = false,
                v1Indikator = false,
                v2GodkjenteSaker = emptyList()
            )
        }

        if (yrkesskadeV2) {
            val ysSakerResponse = yrkesskadeClient.hentSaker(HarYsSakerRequest(identer.alle()))

            return YrkesskadeSporsmalGrunnlag(
                v2Enabled = true,
                v1Indikator = false,
                v2GodkjenteSaker = ysSakerResponse.saker
                    .filter { godkjenteSaker.contains(it.resultat) }
                    .filter { it.vedtaksdato != null }
                    .map {
                        YrkesskadeSak(
                            skadedato = it.skadedato,
                            vedtaksdato = it.vedtaksdato!!
                        )
                    }
            )
        }
        sykmeldingId?.let {
            if (yrkesskadeSykmeldingRepository.existsBySykmeldingId(sykmeldingId)) {
                return YrkesskadeSporsmalGrunnlag(v2Enabled = false, v1Indikator = true, v2GodkjenteSaker = emptyList())
            }
        }

        val ysSakerResponse = yrkesskadeClient.hentYrkesskade(HarYsSakerRequest(identer.alle()))
        val v1Svar =
            ysSakerResponse.harYrkesskadeEllerYrkessykdom == HarYsSak.MAA_SJEKKES_MANUELT || ysSakerResponse.harYrkesskadeEllerYrkessykdom == HarYsSak.JA
        return YrkesskadeSporsmalGrunnlag(v2Enabled = false, v1Indikator = v1Svar, v2GodkjenteSaker = emptyList())
    }
}

data class YrkesskadeSporsmalGrunnlag(
    val v2Enabled: Boolean = false,
    val v1Indikator: Boolean = false,
    val v2GodkjenteSaker: List<YrkesskadeSak> = emptyList()
)

data class YrkesskadeSak(
    val skadedato: LocalDate?,
    val vedtaksdato: LocalDate
)
