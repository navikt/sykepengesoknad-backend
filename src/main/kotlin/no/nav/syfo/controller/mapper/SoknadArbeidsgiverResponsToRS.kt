package no.nav.syfo.controller.mapper

import no.nav.syfo.controller.domain.soknadarbeidsgiver.RSSoknadArbeidsgiverRespons
import no.nav.syfo.controller.domain.soknadarbeidsgiver.RSSoknadrelasjon
import no.nav.syfo.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.soknadarbeidsgiver.SoknadArbeidsgiverRespons
import no.nav.syfo.domain.soknadarbeidsgiver.Soknadrelasjon
import java.util.stream.Collectors

object SoknadArbeidsgiverResponsToRS {
    private fun mapSykepengesoknad(sykepengesoknader: List<Sykepengesoknad>?): List<RSSykepengesoknad> {
        return if (sykepengesoknader!!.isEmpty()) emptyList() else sykepengesoknader
            .stream()
            .map { it.tilRSSykepengesoknad() }
            .collect(Collectors.toList())
    }

    private fun mapTilgjengeligRessurs(soknadrelasjoner: List<Soknadrelasjon>?): List<RSSoknadrelasjon> {
        return if (soknadrelasjoner!!.isEmpty()) emptyList() else soknadrelasjoner
            .stream()
            .map { (fnr, orgnummer, navn, soknader) ->
                RSSoknadrelasjon(
                    fnr = fnr,
                    orgnummer = orgnummer,
                    navn = navn,
                    soknader = mapSykepengesoknad(soknader)
                )
            }
            .collect(Collectors.toList())
    }

    fun mapSoknadArbeidsgiverRespons(soknadArbeidsgiverRespons: SoknadArbeidsgiverRespons): RSSoknadArbeidsgiverRespons {
        return RSSoknadArbeidsgiverRespons(
            narmesteLedere = mapTilgjengeligRessurs(soknadArbeidsgiverRespons.narmesteLedere),
            humanResources = mapTilgjengeligRessurs(soknadArbeidsgiverRespons.humanResources)
        )
    }
}
