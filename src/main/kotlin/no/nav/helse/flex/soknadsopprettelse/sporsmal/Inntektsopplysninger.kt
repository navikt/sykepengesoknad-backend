@file:Suppress("ktlint:standard:max-line-length")

package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.service.SykepengegrunnlagNaeringsdrivende
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.util.DatoUtil
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private fun beregnDatoForVarigEndringEtter(startSykeforlop: LocalDate): LocalDate {
    return startSykeforlop.minusYears(5)
}

fun lagSporsmalOmInntektsopplyninger(
    soknad: Sykepengesoknad,
    sykepengegrunnlagNaeringsdrivende: SykepengegrunnlagNaeringsdrivende?,
): Sporsmal {
    val datoForVarigEndringEtter = beregnDatoForVarigEndringEtter(soknad.startSykeforlop!!)

    return Sporsmal(
        tag = INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET,
        sporsmalstekst = "Har du avviklet virksomheten din før du ble sykmeldt?",
        svartype = Svartype.RADIO_GRUPPE,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET_NEI,
                    sporsmalstekst = "Nei",
                    svartype = Svartype.RADIO,
                    kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                    undersporsmal =
                        listOf(
                            Sporsmal(
                                tag = INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET,
                                sporsmalstekst = "Utenom virksomheten din, har du hatt andre jobber før ${
                                    DatoUtil.formatterDato(
                                        datoForVarigEndringEtter,
                                    )
                                }?",
                                svartype = Svartype.RADIO_GRUPPE,
                                undersporsmal =
                                    listOf(
                                        Sporsmal(
                                            tag = INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_JA,
                                            sporsmalstekst = "Ja",
                                            svartype = Svartype.RADIO,
                                            kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                                            undersporsmal =
                                                listOf(
                                                    Sporsmal(
                                                        tag = INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_DATO,
                                                        sporsmalstekst = "Når begynte du i arbeidslivet?",
                                                        svartype = Svartype.DATO,
                                                        min = datoForVarigEndringEtter.format(DateTimeFormatter.ISO_LOCAL_DATE),
                                                        max = soknad.fom!!.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
                                                    ),
                                                ),
                                        ),
                                        Sporsmal(
                                            tag = INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_NEI,
                                            sporsmalstekst = "Nei",
                                            svartype = Svartype.RADIO,
                                            kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                                            undersporsmal =
                                                listOf(
                                                    Sporsmal(
                                                        tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING,
                                                        sporsmalstekst =
                                                            "Har det skjedd en varig endring i arbeidssituasjonen eller virksomheten din " +
                                                                "i mellom ${DatoUtil.formatterDato(datoForVarigEndringEtter)} og frem til" +
                                                                "sykmeldingstidspunktet?",
                                                        svartype = Svartype.JA_NEI,
                                                        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
                                                        undersporsmal =
                                                            listOf(
                                                                Sporsmal(
                                                                    tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE,
                                                                    sporsmalstekst = "Hvilken endring har skjedd i din arbeidssituasjon eller virksomhet?",
                                                                    undertekst = "Du kan velge en eller flere alternativer",
                                                                    svartype = Svartype.CHECKBOX_GRUPPE,
                                                                    undersporsmal =
                                                                        listOf(
                                                                            Sporsmal(
                                                                                tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE_OPPRETTELSE_NEDLEGGELSE,
                                                                                sporsmalstekst = "Opprettelse eller nedleggelse av næringsvirksomhet",
                                                                                svartype = Svartype.CHECKBOX,
                                                                            ),
                                                                            Sporsmal(
                                                                                tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE_ENDRET_INNSATS,
                                                                                sporsmalstekst = "Økt eller redusert innsats",
                                                                                svartype = Svartype.CHECKBOX,
                                                                            ),
                                                                            Sporsmal(
                                                                                tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE_OMLEGGING_AV_VIRKSOMHETEN,
                                                                                sporsmalstekst = "Omlegging av virksomheten",
                                                                                svartype = Svartype.CHECKBOX,
                                                                            ),
                                                                            Sporsmal(
                                                                                tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE_ENDRET_MARKEDSSITUASJON,
                                                                                sporsmalstekst = "Endret markedssituasjon",
                                                                                svartype = Svartype.CHECKBOX,
                                                                            ),
                                                                            Sporsmal(
                                                                                tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE_ANNET,
                                                                                sporsmalstekst = "Annet",
                                                                                svartype = Svartype.CHECKBOX,
                                                                            ),
                                                                        ),
                                                                ),
                                                                Sporsmal(
                                                                    tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_25_PROSENT,
                                                                    sporsmalstekst = "Har du hatt mer enn 25 prosent endring i årsinntekten din som følge av den varige endringen?",
                                                                    svartype = Svartype.JA_NEI,
                                                                    kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
                                                                    metadata = sykepengegrunnlagNaeringsdrivende?.toJsonNode(),
                                                                    undersporsmal =
                                                                        listOf(
                                                                            Sporsmal(
                                                                                tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_DATO,
                                                                                sporsmalstekst = "Når skjedde den siste varige endringen?",
                                                                                svartype = Svartype.DATO,
                                                                                min =
                                                                                    datoForVarigEndringEtter.format(
                                                                                        DateTimeFormatter.ISO_LOCAL_DATE,
                                                                                    ),
                                                                                max =
                                                                                    soknad.fom.format(
                                                                                        DateTimeFormatter.ISO_LOCAL_DATE,
                                                                                    ),
                                                                            ),
                                                                        ),
                                                                ),
                                                            ),
                                                    ),
                                                ),
                                        ),
                                    ),
                            ),
                        ),
                ),
                Sporsmal(
                    tag = INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET_JA,
                    sporsmalstekst = "Ja",
                    svartype = Svartype.RADIO,
                    kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                    undersporsmal =
                        listOf(
                            Sporsmal(
                                tag = INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET_NAR,
                                sporsmalstekst = "Når ble virksomheten avviklet?",
                                svartype = Svartype.DATO,
                                min = null,
                                max = soknad.fom.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
                            ),
                        ),
                ),
            ),
    )
}
