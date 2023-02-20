package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.ANDRE_INNTEKTSKILDER
import no.nav.helse.flex.soknadsopprettelse.ANDRE_INNTEKTSKILDER_V2
import no.nav.helse.flex.soknadsopprettelse.ER_DU_SYKMELDT
import no.nav.helse.flex.soknadsopprettelse.HVILKE_ANDRE_INNTEKTSKILDER
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_ANNET
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_ARBEIDSFORHOLD
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_FOSTERHJEM
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_FRILANSER
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_FRILANSER_SELVSTENDIG
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_JORDBRUKER
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_OMSORGSLONN
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_SELVSTENDIG
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_STYREVERV
import no.nav.helse.flex.util.DatoUtil
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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

fun andreInntektskilderArbeidstakerV2(sykmeldingOrgnavn: String, andreKjenteArbeidsforhold: List<String>): Sporsmal {

    fun skapSporsmal(): String {

        val listen = mutableListOf(sykmeldingOrgnavn).also { it.addAll(andreKjenteArbeidsforhold) }

        fun virksomheterTekst(): String {
            if (listen.size < 3) {
                return listen.joinToString(" og ")
            }
            return "${listen.subList(0, listen.size - 1).joinToString(", ")} og ${listen.last()}"
        }

        return "Har du andre inntektskilder enn ${virksomheterTekst()}?"
    }

    return Sporsmal(
        tag = ANDRE_INNTEKTSKILDER_V2,
        sporsmalstekst = skapSporsmal(),
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal = listOf(
            Sporsmal(
                tag = HVILKE_ANDRE_INNTEKTSKILDER,
                sporsmalstekst = "Velg inntektskildene som passer for deg. Finner du ikke noe som passer for deg, svarer du nei",
                svartype = Svartype.CHECKBOX_GRUPPE,
                undersporsmal = listOf(
                    Sporsmal(
                        tag = INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD,
                        sporsmalstekst = "ansatt et annet sted enn nevnt over",
                        svartype = Svartype.CHECKBOX
                    ),
                    Sporsmal(
                        tag = INNTEKTSKILDE_SELVSTENDIG,
                        sporsmalstekst = "selvstendig næringsdrivende",
                        svartype = Svartype.CHECKBOX
                    ),
                    Sporsmal(
                        tag = INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA,
                        sporsmalstekst = "dagmammma",
                        svartype = Svartype.CHECKBOX
                    ),
                    Sporsmal(
                        tag = INNTEKTSKILDE_JORDBRUKER,
                        sporsmalstekst = "jordbruk / fiske / reindrift",
                        svartype = Svartype.CHECKBOX
                    ),
                    Sporsmal(
                        tag = INNTEKTSKILDE_FRILANSER,
                        sporsmalstekst = "frilanser",
                        svartype = Svartype.CHECKBOX
                    ),
                    Sporsmal(
                        tag = INNTEKTSKILDE_STYREVERV,
                        sporsmalstekst = "styreverv",
                        svartype = Svartype.CHECKBOX
                    ),
                    Sporsmal(
                        tag = INNTEKTSKILDE_OMSORGSLONN,
                        sporsmalstekst = "kommunal omsorgstønad",
                        svartype = Svartype.CHECKBOX
                    ),
                    Sporsmal(
                        tag = INNTEKTSKILDE_FOSTERHJEM,
                        sporsmalstekst = "fosterhjemsgodtgjørelse",
                        svartype = Svartype.CHECKBOX
                    ),
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
        min = fom.format(DateTimeFormatter.ISO_LOCAL_DATE), // For at mutering skal funke siden vi ignorerer sporsmalstekst
        max = tom.format(DateTimeFormatter.ISO_LOCAL_DATE),
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
                        "kommunal omsorgstønad"
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
