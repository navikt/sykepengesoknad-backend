package no.nav.helse.flex.yrkesskade

import no.nav.helse.flex.client.yrkesskade.HarYsSakerRequest
import no.nav.helse.flex.client.yrkesskade.YrkesskadeClient
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.service.FolkeregisterIdenter
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class YrkesskadeIndikatorer(
    private val yrkesskadeClient: YrkesskadeClient,
) {
    val godkjenteSaker = setOf("DELVIS_GODKJENT", "GODKJENT")

    fun hentYrkesskadeSporsmalGrunnlag(
        identer: FolkeregisterIdenter,
        soknad: Sykepengesoknad,
        erForsteSoknadISykeforlop: Boolean,
    ): YrkesskadeSporsmalGrunnlag {
        if (!erForsteSoknadISykeforlop || soknad.soknadstype == Soknadstype.FRISKMELDT_TIL_ARBEIDSFORMIDLING) {
            return YrkesskadeSporsmalGrunnlag(
                godkjenteSaker = emptyList(),
            )
        }

        val ysSakerResponse = yrkesskadeClient.hentSaker(HarYsSakerRequest(identer.alle()))

        return YrkesskadeSporsmalGrunnlag(
            godkjenteSaker =
                ysSakerResponse.saker
                    .filter { godkjenteSaker.contains(it.resultat) }
                    .filter { it.vedtaksdato != null }
                    .map {
                        YrkesskadeSak(
                            skadedato = it.skadedato,
                            vedtaksdato = it.vedtaksdato!!,
                        )
                    },
        )
    }
}

data class YrkesskadeSporsmalGrunnlag(
    val godkjenteSaker: List<YrkesskadeSak> = emptyList(),
)

data class YrkesskadeSak(
    val skadedato: LocalDate?,
    val vedtaksdato: LocalDate,
)
