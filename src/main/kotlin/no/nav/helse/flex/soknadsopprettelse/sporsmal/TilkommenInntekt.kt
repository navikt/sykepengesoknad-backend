package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.client.aareg.ArbeidsforholdoversiktResponse
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.util.DatoUtil
import java.time.LocalDate

fun tilkommenInntektSporsmal(
    arbeidsforholdoversiktResponse: ArbeidsforholdoversiktResponse,
    denneSoknaden: Sykepengesoknad,
    eksisterendeSoknader: List<Sykepengesoknad>,
): List<Sporsmal> {
    // TODO returner tidlig hvis flere søknader i parallell med denne

    val periodeTekst = DatoUtil.formatterPeriode(denneSoknaden.fom!!, denneSoknaden.tom!!)
    val startSykeforlop: LocalDate = denneSoknaden.startSykeforlop ?: denneSoknaden.fom
    val nyeArbeidsforhold =
        arbeidsforholdoversiktResponse
            .arbeidsforholdoversikter
            .filter { it.startdato.isAfter(startSykeforlop) }
            .filter { arbeidsforhold -> !arbeidsforhold.arbeidssted.identer.any { it.ident == denneSoknaden.arbeidsgiverOrgnummer } }

    if (nyeArbeidsforhold.size > 1) {
        // TODO log at vi ikke takler flere
        return emptyList()
    }

    return nyeArbeidsforhold.mapNotNull { arbeidsforhold ->
        val orgnummer = arbeidsforhold.arbeidssted.identer.firstOrNull { it.type == "ORGANISASJONSNUMMER" }?.ident
        if (orgnummer == null) {
            // TODO logg at vi ikke takler dette
            return@mapNotNull null
        }

        return@mapNotNull Sporsmal(
            tag = "TILKOMMEN_INNTEKT_FORSTEGANG",
            sporsmalstekst = "Har du startet å jobbe hos $orgnummer?",
            undertekst = null,
            svartype = Svartype.JA_NEI,
            min = null,
            max = null,
            kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
            undersporsmal =
                listOf(
                    Sporsmal(
                        tag = "TILKOMMEN_INNTEKT_FORSTEGANG_FORSTE_ARBEIDSDAG",
                        sporsmalstekst = "Når hadde du din første arbeidsdag?",
                        undertekst = null,
                        svartype = Svartype.DATO,
                    ),
                    Sporsmal(
                        tag = "TILKOMMEN_INNTEKT_BRUTTO",
                        sporsmalstekst = "Hvor mye har du tjent i perioden $periodeTekst?",
                        undertekst =
                            "Oppgi det du har tjent brutto (før skatt) i perioden $periodeTekst. " +
                                "Se på lønnslippen eller kontrakten hvor mye du har tjent eller skal tjene.",
                        svartype = Svartype.BELOP,
                    ),
                ),
        )
    }
}
