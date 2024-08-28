package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.client.aareg.AaregClient
import no.nav.helse.flex.client.aareg.ArbeidsforholdOversikt
import no.nav.helse.flex.client.ereg.EregClient
import no.nav.helse.flex.logger
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class AaregDataHenting(
    val aaregClient: AaregClient,
    val eregClient: EregClient,
) {
    val log = logger()

    fun hentNyeArbeidsforhold(
        fnr: String,
        arbeidsgiverOrgnummer: String,
        startSykeforlop: LocalDate,
    ): List<ArbeidsforholdFraAAreg> {
        val arbeidsforholOversikt = aaregClient.hentArbeidsforholdoversikt(fnr).arbeidsforholdoversikter

        return arbeidsforholOversikt
            .filter { it.startdato.isAfter(startSykeforlop) }
            .filter { arbeidsforhold -> !arbeidsforhold.arbeidssted.identer.any { it.ident == arbeidsgiverOrgnummer } }
            .mapNotNull { it.tilArbeidsforholdFraInntektskomponenten() }
            .sortedBy { it.arbeidsforholdsoversikt.startdato }
    }

    private fun ArbeidsforholdOversikt.tilArbeidsforholdFraInntektskomponenten(): ArbeidsforholdFraAAreg? {
        val orgnummer = this.arbeidssted.identer.firstOrNull { it.type == "ORGANISASJONSNUMMER" }?.ident
        if (orgnummer == null) {
            log.error("Fant ikke orgnummer for arbeidsforhold") // TODO mere metadata
            return null
        }
        val hentBedrift = eregClient.hentBedrift(orgnummer)
        return ArbeidsforholdFraAAreg(
            orgnummer = orgnummer,
            navn = hentBedrift.navn.navnelinje1.prettyOrgnavn(),
            arbeidsforholdsoversikt = this,
        )
    }
}

data class ArbeidsforholdFraAAreg(
    val orgnummer: String,
    val navn: String,
    val arbeidsforholdsoversikt: ArbeidsforholdOversikt,
)
