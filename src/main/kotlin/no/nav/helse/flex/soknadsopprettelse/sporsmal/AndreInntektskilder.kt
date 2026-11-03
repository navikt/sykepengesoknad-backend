@file:Suppress("ktlint:standard:max-line-length")

package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.*

data class AndreInntektskilderMetadata(
    val kjenteInntektskilder: List<KjentInntektskilde>,
)

enum class Kilde {
    INNTEKTSKOMPONENTEN,
    AAAREG,
    SYKMELDING,
}

data class KjentInntektskilde(
    val navn: String,
    val kilde: Kilde,
    val orgnummer: String,
)

fun andreInntektskilderV2(gruppe: Boolean = false): Sporsmal {
    val tekst = if (gruppe) "Har du annen inntekt eller oppdrag i tillegg til dette?" else "Har du annen inntekt eller oppdrag?"
    return Sporsmal(
        tag = ANDRE_INNTEKTSKILDER_V2,
        sporsmalstekst = tekst,
        undertekst = "Med inntekt mener vi betaling som du får for arbeid du har gjort.",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = HVILKE_ANDRE_INNTEKTSKILDER,
                    sporsmalstekst = "Hva slags arbeid eller inntekt gjelder dette?",
                    undertekst = "Du kan velge en eller flere.",
                    svartype = Svartype.CHECKBOX_GRUPPE,
                    undersporsmal =
                        listOf(
                            Sporsmal(
                                tag = INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD,
                                sporsmalstekst = "Ansatt andre steder",
                                undertekst = "En annen jobb enn Nav har registrert",
                                svartype = Svartype.CHECKBOX,
                            ),
                            Sporsmal(
                                tag = INNTEKTSKILDE_SELVSTENDIG,
                                sporsmalstekst = "Selvstendig næringsdrivende",
                                svartype = Svartype.CHECKBOX,
                            ),
                            Sporsmal(
                                tag = INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA,
                                sporsmalstekst = "Barnepasser / Dagmamma",
                                svartype = Svartype.CHECKBOX,
                            ),
                            Sporsmal(
                                tag = INNTEKTSKILDE_JORDBRUKER,
                                sporsmalstekst = "Jordbruk / Fiske / Reindrift",
                                svartype = Svartype.CHECKBOX,
                            ),
                            Sporsmal(
                                tag = INNTEKTSKILDE_FRILANSER,
                                sporsmalstekst = "Frilanser",
                                svartype = Svartype.CHECKBOX,
                            ),
                            Sporsmal(
                                tag = INNTEKTSKILDE_OMSORGSLONN,
                                sporsmalstekst = "Kommunal omsorgstønad",
                                svartype = Svartype.CHECKBOX,
                            ),
                            Sporsmal(
                                tag = INNTEKTSKILDE_FOSTERHJEM,
                                sporsmalstekst = "Fosterhjemsgodtgjørelse",
                                svartype = Svartype.CHECKBOX,
                            ),
                            Sporsmal(
                                tag = INNTEKTSKILDE_STYREVERV,
                                sporsmalstekst = "Styreverv",
                                svartype = Svartype.CHECKBOX,
                            ),
                        ),
                ),
            ),
    )
}
