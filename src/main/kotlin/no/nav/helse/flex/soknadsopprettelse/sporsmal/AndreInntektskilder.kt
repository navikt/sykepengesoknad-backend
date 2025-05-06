@file:Suppress("ktlint:standard:max-line-length")

package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.aaregdata.ArbeidsforholdFraAAreg
import no.nav.helse.flex.util.DatoUtil
import no.nav.helse.flex.util.toJsonNode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun andreInntektskilderArbeidstaker(arbeidsgiver: String?): Sporsmal =
    Sporsmal(
        tag = ANDRE_INNTEKTSKILDER,
        sporsmalstekst = "Har du andre inntektskilder enn $arbeidsgiver?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = HVILKE_ANDRE_INNTEKTSKILDER,
                    sporsmalstekst = "Hvilke andre inntektskilder har du?",
                    svartype = Svartype.CHECKBOX_GRUPPE,
                    undersporsmal =
                        listOf(
                            andreInntektskilderCheckboxSporsmal(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD, "andre arbeidsforhold"),
                            andreInntektskilderCheckboxSporsmal(INNTEKTSKILDE_SELVSTENDIG, "selvstendig næringsdrivende"),
                            andreInntektskilderCheckboxSporsmal(INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA, "dagmamma"),
                            andreInntektskilderCheckboxSporsmal(INNTEKTSKILDE_JORDBRUKER, "jordbruk / fiske / reindrift"),
                            andreInntektskilderCheckboxSporsmal(INNTEKTSKILDE_FRILANSER, "frilanser"),
                            Sporsmal(
                                tag = INNTEKTSKILDE_ANNET,
                                sporsmalstekst = "annet",
                                svartype = Svartype.CHECKBOX,
                            ),
                        ),
                ),
            ),
    )

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

fun andreInntektskilderArbeidstakerV2(
    sykmeldingOrgnavn: String,
    sykmeldingOrgnr: String,
    andreKjenteArbeidsforholdFraInntektskomponenten: List<ArbeidsforholdFraInntektskomponenten>,
    nyeArbeidsforholdFraAareg: List<ArbeidsforholdFraAAreg>?,
): Sporsmal {
    val alleArbeidsforhold = mutableListOf(KjentInntektskilde(sykmeldingOrgnavn, Kilde.SYKMELDING, sykmeldingOrgnr))
    alleArbeidsforhold.addAll(
        andreKjenteArbeidsforholdFraInntektskomponenten.map {
            KjentInntektskilde(
                it.navn,
                Kilde.INNTEKTSKOMPONENTEN,
                it.orgnummer,
            )
        },
    )
    nyeArbeidsforholdFraAareg
        ?.filter { arbeidsforhold ->
            !alleArbeidsforhold.map { it.orgnummer }.contains(arbeidsforhold.arbeidsstedOrgnummer)
        }?.forEach {
            alleArbeidsforhold.add(KjentInntektskilde(it.arbeidsstedNavn, Kilde.AAAREG, it.arbeidsstedOrgnummer))
        }

    fun skapSporsmal(): String {
        val alleNavn = alleArbeidsforhold.map { it.navn }

        fun virksomheterTekst(): String {
            if (alleNavn.size < 3) {
                return alleNavn.joinToString(" og ")
            }
            return "${alleNavn.subList(0, alleNavn.size - 1).joinToString(", ")} og ${alleNavn.last()}"
        }

        return "Har du andre inntektskilder enn ${virksomheterTekst()}?"
    }

    return Sporsmal(
        tag = ANDRE_INNTEKTSKILDER_V2,
        sporsmalstekst = skapSporsmal(),
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        metadata =
            AndreInntektskilderMetadata(
                kjenteInntektskilder = alleArbeidsforhold,
            ).toJsonNode(),
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = HVILKE_ANDRE_INNTEKTSKILDER,
                    sporsmalstekst = "Velg inntektskildene som passer for deg:",
                    undertekst = "Finner du ikke noe som passer for deg, svarer du nei på spørsmålet over",
                    svartype = Svartype.CHECKBOX_GRUPPE,
                    undersporsmal =
                        listOf(
                            Sporsmal(
                                tag = INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD,
                                sporsmalstekst = "Ansatt andre steder enn nevnt over",
                                svartype = Svartype.CHECKBOX,
                                kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                                undersporsmal =
                                    listOf(
                                        Sporsmal(
                                            tag = INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD_JOBBET_I_DET_SISTE,
                                            sporsmalstekst = "Har du jobbet for eller mottatt inntekt fra én eller flere av disse arbeidsgiverne de siste 14 dagene før du ble sykmeldt?",
                                            svartype = Svartype.JA_NEI,
                                        ),
                                    ),
                            ),
                            Sporsmal(
                                tag = INNTEKTSKILDE_SELVSTENDIG,
                                sporsmalstekst = "Selvstendig næringsdrivende",
                                svartype = Svartype.CHECKBOX,
                            ),
                            Sporsmal(
                                tag = INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA,
                                sporsmalstekst = "Dagmamma",
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
                                tag = INNTEKTSKILDE_STYREVERV,
                                sporsmalstekst = "Styreverv",
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
                        ),
                ),
            ),
    )
}

fun andreInntektskilderSelvstendigOgFrilanser(arbeidssituasjon: Arbeidssituasjon): Sporsmal =
    Sporsmal(
        tag = ANDRE_INNTEKTSKILDER,
        sporsmalstekst =
            if (Arbeidssituasjon.FRILANSER == arbeidssituasjon) {
                "Har du annen inntekt? Du trenger ikke oppgi penger fra NAV."
            } else {
                "Har du annen inntekt?"
            },
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = (Visningskriterie.JA),
        undersporsmal = (
            listOf(
                Sporsmal(
                    tag = HVILKE_ANDRE_INNTEKTSKILDER,
                    sporsmalstekst = "Hvilke inntektskilder har du?",
                    svartype = Svartype.CHECKBOX_GRUPPE,
                    undersporsmal =
                        listOf(
                            andreInntektskilderCheckboxSporsmal(INNTEKTSKILDE_ARBEIDSFORHOLD, "arbeidsforhold"),
                            andreInntektskilderCheckboxSporsmal(INNTEKTSKILDE_JORDBRUKER, "jordbruk / fiske / reindrift"),
                            andreInntektskilderCheckboxSporsmal(
                                INNTEKTSKILDE_FRILANSER_SELVSTENDIG,
                                if (Arbeidssituasjon.FRILANSER == arbeidssituasjon) {
                                    Arbeidssituasjon.NAERINGSDRIVENDE.toString()
                                } else {
                                    Arbeidssituasjon.FRILANSER.toString()
                                },
                            ),
                            Sporsmal(
                                tag = INNTEKTSKILDE_ANNET,
                                sporsmalstekst = ("annet"),
                                svartype = (Svartype.CHECKBOX),
                            ),
                        ),
                ),
            )
        ),
    )

fun andreInntektskilderArbeidsledig(
    fom: LocalDate,
    tom: LocalDate,
): Sporsmal =
    Sporsmal(
        tag = ANDRE_INNTEKTSKILDER,
        sporsmalstekst = "Har du hatt inntekt mens du har vært sykmeldt i perioden ${
            DatoUtil.formatterPeriode(
                fom,
                tom,
            )
        }?",
        // For at mutering skal funke siden vi ignorerer sporsmalstekst.
        min = fom.format(DateTimeFormatter.ISO_LOCAL_DATE),
        max = tom.format(DateTimeFormatter.ISO_LOCAL_DATE),
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = HVILKE_ANDRE_INNTEKTSKILDER,
                    sporsmalstekst = "Hvilke inntektskilder har du hatt?",
                    svartype = Svartype.CHECKBOX_GRUPPE,
                    undersporsmal =
                        listOf(
                            andreInntektskilderCheckboxSporsmal(
                                INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD,
                                "andre arbeidsforhold",
                            ),
                            andreInntektskilderCheckboxSporsmal(
                                INNTEKTSKILDE_SELVSTENDIG,
                                "selvstendig næringsdrivende",
                            ),
                            andreInntektskilderCheckboxSporsmal(
                                INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA,
                                "dagmamma",
                            ),
                            andreInntektskilderCheckboxSporsmal(
                                INNTEKTSKILDE_JORDBRUKER,
                                "jordbruk / fiske / reindrift",
                            ),
                            andreInntektskilderCheckboxSporsmal(
                                INNTEKTSKILDE_FRILANSER,
                                "frilanser",
                            ),
                            andreInntektskilderCheckboxSporsmal(
                                INNTEKTSKILDE_OMSORGSLONN,
                                "kommunal omsorgstønad",
                            ),
                            andreInntektskilderCheckboxSporsmal(
                                INNTEKTSKILDE_FOSTERHJEM,
                                "fosterhjemgodtgjørelse",
                            ),
                            Sporsmal(
                                tag = INNTEKTSKILDE_ANNET,
                                sporsmalstekst = "annet",
                                svartype = Svartype.CHECKBOX,
                            ),
                        ),
                ),
            ),
    )

private fun andreInntektskilderCheckboxSporsmal(
    inntektskildeTag: String,
    inntektskildeTekst: String,
): Sporsmal =
    Sporsmal(
        tag = inntektskildeTag,
        sporsmalstekst = inntektskildeTekst,
        svartype = Svartype.CHECKBOX,
        kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = inntektskildeTag + ER_DU_SYKMELDT,
                    sporsmalstekst = "Er du sykmeldt fra dette?",
                    svartype = Svartype.JA_NEI,
                ),
            ),
    )
