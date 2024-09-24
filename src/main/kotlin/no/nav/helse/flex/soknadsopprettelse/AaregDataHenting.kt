package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.client.aareg.AaregClient
import no.nav.helse.flex.client.aareg.ArbeidsforholdOversikt
import no.nav.helse.flex.client.ereg.EregClient
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.isBeforeOrEqual
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
        sykepengesoknadId: String,
        soknadTom: LocalDate,
    ): List<ArbeidsforholdFraAAreg> {
        val arbeidsforholOversikt = aaregClient.hentArbeidsforholdoversikt(fnr).arbeidsforholdoversikter

        fun ArbeidsforholdOversikt.tilArbeidsforholdFraAAreg(): ArbeidsforholdFraAAreg {
            val arbeidstedOrgnummer =
                this.arbeidssted.identer.firstOrNull { it.type == "ORGANISASJONSNUMMER" }?.ident
                    ?: throw RuntimeException(
                        "Fant ikke orgnummer for arbeidsforhold " +
                            "ved henting av nye arbeidsforhold for søknad $sykepengesoknadId",
                    )

            val opplysningspliktigOrgnummer =
                this.opplysningspliktig.identer.firstOrNull { it.type == "ORGANISASJONSNUMMER" }?.ident
                    ?: throw RuntimeException(
                        "Fant ikke opplysningspliktig orgnummer for arbeidsforhold " +
                            "ved henting av nye arbeidsforhold for søknad $sykepengesoknadId",
                    )

            val hentBedrift = eregClient.hentBedrift(arbeidstedOrgnummer)
            return ArbeidsforholdFraAAreg(
                arbeidsstedOrgnummer = arbeidstedOrgnummer,
                arbeidsstedNavn = hentBedrift.navn.navnelinje1.prettyOrgnavn(),
                startdato = startdato,
                opplysningspliktigOrgnummer = opplysningspliktigOrgnummer,
            )
        }

        val arbeidsforholdSoknad =
            arbeidsforholOversikt.find { arbeidsforholdOversikt ->
                arbeidsforholdOversikt.arbeidssted.identer.firstOrNull { it.ident == arbeidsgiverOrgnummer } != null
            }
        val opplysningspliktigOrgnummer =
            arbeidsforholdSoknad?.opplysningspliktig?.identer?.firstOrNull { it.type == "ORGANISASJONSNUMMER" }?.ident

        return arbeidsforholOversikt
            .filter { it.startdato.isAfter(startSykeforlop) }
            .filter { it.startdato.isBeforeOrEqual(soknadTom) }
            .filter { it.erOrganisasjonArbeidsforhold() }
            .filterInterneOrgnummer(opplysningspliktigOrgnummer)
            .map { it.tilArbeidsforholdFraAAreg() }
            .sortedBy { it.startdato }
    }
}

private fun ArbeidsforholdOversikt.erOrganisasjonArbeidsforhold(): Boolean {
    return this.opplysningspliktig.type == "Hovedenhet"
}

private fun List<ArbeidsforholdOversikt>.filterInterneOrgnummer(opplysningspliktigOrgnummer: String?): List<ArbeidsforholdOversikt> {
    opplysningspliktigOrgnummer ?: return this
    return this.filter { arbeidsforhold ->
        arbeidsforhold.opplysningspliktig.identer.none { it.ident == opplysningspliktigOrgnummer }
    }
}

data class ArbeidsforholdFraAAreg(
    val opplysningspliktigOrgnummer: String,
    val arbeidsstedOrgnummer: String,
    val arbeidsstedNavn: String,
    val startdato: LocalDate,
)
