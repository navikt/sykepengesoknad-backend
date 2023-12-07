package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.util.DatoUtil
import no.nav.helse.flex.util.DatoUtil.formatterDato
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

fun kjenteInntektskilderSporsmal(andreKjenteArbeidsforhold: List<String>, startSyketilfelle: LocalDate): Sporsmal {
    if (andreKjenteArbeidsforhold.isEmpty()) throw IllegalArgumentException("Kan ikke lage spørsmål om kjente inntektskilder uten andre kjente inntektskilder")

    val fjortenDagerFørStartSyketilfelle = DatoUtil.formatterPeriode(
        startSyketilfelle.minusDays(15),
        startSyketilfelle.minusDays(1)
    )
    return Sporsmal(
        tag = KJENTE_INNTEKTSKILDER,
        sporsmalstekst = "Du er oppført med flere inntektskilder i Arbeidsgiver- og arbeidstakerregisteret. Vi trenger mer informasjon om disse.",
        svartype = Svartype.GRUPPE_AV_UNDERSPORSMAL,
        undersporsmal = andreKjenteArbeidsforhold.mapIndexed { idx, arbeidsforhold ->
            Sporsmal(
                tag = KJENTE_INNTEKTSKILDER_GRUPPE + idx,
                svartype = Svartype.GRUPPE_AV_UNDERSPORSMAL,
                undersporsmal = listOf(
                    Sporsmal(
                        tag = KJENTE_INNTEKTSKILDER_GRUPPE_TITTEL + idx,
                        sporsmalstekst = arbeidsforhold,
                        svartype = Svartype.IKKE_RELEVANT
                    ),
                    Sporsmal(
                        tag = KJENTE_INNTEKTSKILDER_SLUTTET + idx,
                        sporsmalstekst = "Har du sluttet hos $arbeidsforhold før du ble sykmeldt ${formatterDato(startSyketilfelle)}?",
                        svartype = Svartype.RADIO_GRUPPE,
                        undersporsmal = listOf(
                            Sporsmal(
                                tag = KJENTE_INNTEKTSKILDER_SLUTTET_JA + idx,
                                sporsmalstekst = "Ja",
                                svartype = Svartype.RADIO,
                                kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                                undersporsmal = listOf(
                                    Sporsmal(
                                        tag = KJENTE_INNTEKTSKILDER_DATO_SLUTTET + idx,
                                        sporsmalstekst = "Når sluttet du?",
                                        svartype = Svartype.DATO,
                                        max = startSyketilfelle.minusDays(1).format(ISO_LOCAL_DATE)
                                    )
                                )
                            ),
                            Sporsmal(
                                tag = KJENTE_INNTEKTSKILDER_SLUTTET_NEI + idx,
                                sporsmalstekst = "Nei",
                                svartype = Svartype.RADIO,
                                kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                                undersporsmal = listOf(
                                    Sporsmal(
                                        tag = KJENTE_INNTEKTSKILDER_UTFORT_ARBEID + idx,
                                        sporsmalstekst = "Har du utført noe arbeid ved $arbeidsforhold i perioden $fjortenDagerFørStartSyketilfelle?",
                                        svartype = Svartype.JA_NEI,
                                        kriterieForVisningAvUndersporsmal = Visningskriterie.NEI,
                                        undersporsmal = listOf(
                                            Sporsmal(
                                                tag = KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET + idx,
                                                sporsmalstekst = "Velg en eller flere årsaker til at du ikke har jobbet",
                                                undertekst = null,
                                                svartype = Svartype.CHECKBOX_GRUPPE,
                                                undersporsmal = listOf(
                                                    KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_SYKMELDT + idx to "Jeg var sykmeldt",
                                                    KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_TURNUS + idx to "Jeg jobber turnus",
                                                    KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_FERIE + idx to "Jeg hadde lovbestemt ferie",
                                                    KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_AVSPASERING + idx to "Jeg avspaserte",
                                                    KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_PERMITTERT + idx to "Jeg var permittert",
                                                    KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_PERMISJON + idx to "Jeg hadde permisjon",
                                                    KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_ANNEN + idx to "Annen årsak"
                                                ).map {
                                                    Sporsmal(
                                                        tag = it.first,
                                                        sporsmalstekst = it.second,
                                                        svartype = Svartype.CHECKBOX
                                                    )
                                                }
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    )
}
