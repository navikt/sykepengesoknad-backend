package no.nav.syfo.soknadsopprettelse.sporsmal

import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Sporsmal
import no.nav.syfo.domain.Svartype
import no.nav.syfo.domain.Visningskriterie
import no.nav.syfo.soknadsopprettelse.ANDRE_INNTEKTSKILDER
import no.nav.syfo.soknadsopprettelse.ER_DU_SYKMELDT
import no.nav.syfo.soknadsopprettelse.HVILKE_ANDRE_INNTEKTSKILDER
import no.nav.syfo.soknadsopprettelse.INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD
import no.nav.syfo.soknadsopprettelse.INNTEKTSKILDE_ANNET
import no.nav.syfo.soknadsopprettelse.INNTEKTSKILDE_ARBEIDSFORHOLD
import no.nav.syfo.soknadsopprettelse.INNTEKTSKILDE_FOSTERHJEM
import no.nav.syfo.soknadsopprettelse.INNTEKTSKILDE_FRILANSER
import no.nav.syfo.soknadsopprettelse.INNTEKTSKILDE_FRILANSER_SELVSTENDIG
import no.nav.syfo.soknadsopprettelse.INNTEKTSKILDE_JORDBRUKER
import no.nav.syfo.soknadsopprettelse.INNTEKTSKILDE_OMSORGSLONN
import no.nav.syfo.soknadsopprettelse.INNTEKTSKILDE_SELVSTENDIG
import no.nav.syfo.soknadsopprettelse.INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA
import no.nav.syfo.util.DatoUtil
import java.time.LocalDate

fun andreInntektskilderArbeidstaker(arbeidsgiver: String?): Sporsmal {
    return Sporsmal(
        tag = ANDRE_INNTEKTSKILDER,
        sporsmalstekst = "Har du andre inntektskilder enn $arbeidsgiver?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal = listOf(
            Sporsmal(
                tag = HVILKE_ANDRE_INNTEKTSKILDER,
                sporsmalstekst = "Hvilke andre inntektskilder har du?",
                svartype = Svartype.CHECKBOX_GRUPPE,
                undersporsmal = listOf(
                    andreInntektskilderCheckboxSporsmal(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD, "andre arbeidsforhold"),
                    andreInntektskilderCheckboxSporsmal(INNTEKTSKILDE_SELVSTENDIG, "selvstendig næringsdrivende"),
                    andreInntektskilderCheckboxSporsmal(INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA, "dagmamma"),
                    andreInntektskilderCheckboxSporsmal(INNTEKTSKILDE_JORDBRUKER, "jordbruk / fiske / reindrift"),
                    andreInntektskilderCheckboxSporsmal(INNTEKTSKILDE_FRILANSER, "frilanser"),
                    Sporsmal(
                        tag = INNTEKTSKILDE_ANNET,
                        sporsmalstekst = "annet",
                        svartype = Svartype.CHECKBOX
                    )
                )
            )
        )
    )
}

fun andreInntektskilderSelvstendigOgFrilanser(arbeidssituasjon: Arbeidssituasjon): Sporsmal {
    return Sporsmal(
        tag = ANDRE_INNTEKTSKILDER,
        sporsmalstekst = if (Arbeidssituasjon.FRILANSER == arbeidssituasjon)
            "Har du annen inntekt? Du trenger ikke oppgi penger fra NAV."
        else "Har du annen inntekt?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = (Visningskriterie.JA),
        undersporsmal = (
            listOf(
                Sporsmal(
                    tag = HVILKE_ANDRE_INNTEKTSKILDER,
                    sporsmalstekst = "Hvilke inntektskilder har du?",
                    svartype = Svartype.CHECKBOX_GRUPPE,
                    undersporsmal = listOf(
                        andreInntektskilderCheckboxSporsmal(INNTEKTSKILDE_ARBEIDSFORHOLD, "arbeidsforhold"),
                        andreInntektskilderCheckboxSporsmal(INNTEKTSKILDE_JORDBRUKER, "jordbruk / fiske / reindrift"),
                        andreInntektskilderCheckboxSporsmal(
                            INNTEKTSKILDE_FRILANSER_SELVSTENDIG,
                            if (Arbeidssituasjon.FRILANSER == arbeidssituasjon)
                                Arbeidssituasjon.NAERINGSDRIVENDE.toString()
                            else
                                Arbeidssituasjon.FRILANSER.toString()
                        ),
                        Sporsmal(
                            tag = INNTEKTSKILDE_ANNET,
                            sporsmalstekst = ("annet"),
                            svartype = (Svartype.CHECKBOX)
                        )
                    )
                )
            )
            )
    )
}

fun andreInntektskilderArbeidsledig(fom: LocalDate, tom: LocalDate): Sporsmal =
    Sporsmal(
        tag = ANDRE_INNTEKTSKILDER,
        sporsmalstekst = "Har du hatt inntekt mens du har vært sykmeldt i perioden ${
        DatoUtil.formatterPeriode(
            fom,
            tom
        )
        }?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal = listOf(
            Sporsmal(
                tag = HVILKE_ANDRE_INNTEKTSKILDER,
                sporsmalstekst = "Hvilke inntektskilder har du hatt?",
                svartype = Svartype.CHECKBOX_GRUPPE,
                undersporsmal = listOf(
                    andreInntektskilderCheckboxSporsmal(
                        INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD,
                        "andre arbeidsforhold"
                    ),
                    andreInntektskilderCheckboxSporsmal(
                        INNTEKTSKILDE_SELVSTENDIG,
                        "selvstendig næringsdrivende"
                    ),
                    andreInntektskilderCheckboxSporsmal(
                        INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA,
                        "dagmamma"
                    ),
                    andreInntektskilderCheckboxSporsmal(
                        INNTEKTSKILDE_JORDBRUKER,
                        "jordbruk / fiske / reindrift"
                    ),
                    andreInntektskilderCheckboxSporsmal(
                        INNTEKTSKILDE_FRILANSER,
                        "frilanser"
                    ),
                    andreInntektskilderCheckboxSporsmal(
                        INNTEKTSKILDE_OMSORGSLONN,
                        "omsorgslønn fra kommunen"
                    ),
                    andreInntektskilderCheckboxSporsmal(
                        INNTEKTSKILDE_FOSTERHJEM,
                        "fosterhjemgodtgjørelse"
                    ),
                    Sporsmal(
                        tag = INNTEKTSKILDE_ANNET,
                        sporsmalstekst = "annet",
                        svartype = Svartype.CHECKBOX
                    )
                )
            )
        )
    )

private fun andreInntektskilderCheckboxSporsmal(inntektskildeTag: String, inntektskildeTekst: String): Sporsmal {
    return Sporsmal(
        tag = inntektskildeTag,
        sporsmalstekst = inntektskildeTekst,
        svartype = Svartype.CHECKBOX,
        kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
        undersporsmal = listOf(
            Sporsmal(
                tag = inntektskildeTag + ER_DU_SYKMELDT,
                sporsmalstekst = "Er du sykmeldt fra dette?",
                svartype = Svartype.JA_NEI
            )
        )
    )
}
