package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.ArbeidsforholdFraAAreg
import no.nav.helse.flex.util.DatoUtil
import no.nav.helse.flex.util.toJsonNode

fun tilkommenInntektSporsmal(
    nyeArbeidsforhold: List<ArbeidsforholdFraAAreg>,
    denneSoknaden: Sykepengesoknad,
    eksisterendeSoknader: List<Sykepengesoknad>,
): List<Sporsmal> {
    // TODO returner tidlig hvis flere søknader i parallell med denne

    val periodeTekst = DatoUtil.formatterPeriode(denneSoknaden.fom!!, denneSoknaden.tom!!)

    return nyeArbeidsforhold.mapNotNull { arbeidsforhold ->

        return@mapNotNull Sporsmal(
            tag = "TILKOMMEN_INNTEKT_FORSTEGANG",
            sporsmalstekst = "Har du startet å jobbe hos ${arbeidsforhold.navn}?",
            undertekst = null,
            svartype = Svartype.JA_NEI,
            min = null,
            max = null,
            metadata = mapOf("orgnummer" to arbeidsforhold.orgnummer).toJsonNode(),
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
