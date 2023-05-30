package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Soknadstype.GRADERT_REISETILSKUDD
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Svartype.CHECKBOX
import no.nav.helse.flex.domain.Svartype.CHECKBOX_GRUPPE
import no.nav.helse.flex.domain.Svartype.DATO
import no.nav.helse.flex.domain.Svartype.JA_NEI
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.Visningskriterie.CHECKED
import no.nav.helse.flex.domain.Visningskriterie.JA
import no.nav.helse.flex.soknadsopprettelse.sporsmal.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.utenlandsksykmelding.utenlandskSykmeldingSporsmal
import no.nav.helse.flex.soknadsopprettelse.undersporsmal.jobbetDuUndersporsmal
import no.nav.helse.flex.util.DatoUtil.formatterDato
import no.nav.helse.flex.util.DatoUtil.formatterPeriode
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

fun settOppSoknadArbeidstaker(
    sykepengesoknad: Sykepengesoknad,
    erForsteSoknadISykeforlop: Boolean,
    tidligsteFomForSykmelding: LocalDate,
    andreKjenteArbeidsforhold: List<String>,
    harTidligereUtenlandskSpm: Boolean,
    yrkesskade: Boolean,
    egenmeldingISykmeldingen: Boolean
): List<Sporsmal> {
    val gradertResietilskudd = sykepengesoknad.soknadstype == GRADERT_REISETILSKUDD

    return mutableListOf(
        ansvarserklaringSporsmal(reisetilskudd = gradertResietilskudd),
        if (gradertResietilskudd) {
            tilbakeIFulltArbeidGradertReisetilskuddSporsmal(sykepengesoknad)
        } else {
            tilbakeIFulltArbeidSporsmal(sykepengesoknad)
        },
        ferieSporsmal(sykepengesoknad.fom!!, sykepengesoknad.tom!!),
        permisjonSporsmal(sykepengesoknad.fom, sykepengesoknad.tom),
        utenlandsoppholdSporsmal(sykepengesoknad.fom, sykepengesoknad.tom),
        vaerKlarOverAt(gradertReisetilskudd = gradertResietilskudd),
        bekreftOpplysningerSporsmal()
    ).also {
        if (erForsteSoknadISykeforlop) {
            if (!egenmeldingISykmeldingen) {
                it.add(fravarForSykmeldingen(tidligsteFomForSykmelding))
            }
            it.add(arbeidUtenforNorge())
        }
        if (yrkesskade) {
            it.add(yrkesskadeSporsmal())
        }
        if (sykepengesoknad.utenlandskSykmelding) {
            if (erForsteSoknadISykeforlop || !harTidligereUtenlandskSpm) {
                it.addAll(utenlandskSykmeldingSporsmal(sykepengesoknad))
            }
        }

        it.add(andreInntektskilderArbeidstakerV2(sykepengesoknad.arbeidsgiverNavn!!, andreKjenteArbeidsforhold))

        it.addAll(
            jobbetDuIPeriodenSporsmal(
                sykepengesoknad.soknadPerioder!!,
                sykepengesoknad.arbeidsgiverNavn
            )
        )
        if (gradertResietilskudd) {
            it.add(brukteReisetilskuddetSpørsmål())
        }
    }.toList()
}

fun Sykepengesoknad.harFeriePermisjonEllerUtenlandsoppholdSporsmal(): Boolean {
    return this.getSporsmalMedTagOrNull(FERIE_PERMISJON_UTLAND) != null
}

fun harFeriesporsmal(sykepengesoknad: Sykepengesoknad): Boolean {
    return sykepengesoknad.getOptionalSporsmalMedTag(FERIE_V2).isPresent
}

fun harPermisjonsporsmal(sykepengesoknad: Sykepengesoknad): Boolean {
    return sykepengesoknad.getOptionalSporsmalMedTag(PERMISJON_V2).isPresent
}

fun harUtlandsporsmal(sykepengesoknad: Sykepengesoknad): Boolean {
    return sykepengesoknad.getOptionalSporsmalMedTag(UTLAND_V2).isPresent
}

fun oppdaterMedSvarPaaBrukteReisetilskuddet(sykepengesoknad: Sykepengesoknad): Sykepengesoknad {
    val oppdaterteSporsmal = sykepengesoknad
        .sporsmal
        .filterNot {
            it.tag == TRANSPORT_TIL_DAGLIG ||
                it.tag == REISE_MED_BIL ||
                it.tag == KVITTERINGER ||
                it.tag == UTBETALING
        }
        .toMutableList()

    if (sykepengesoknad.getSporsmalMedTagOrNull(BRUKTE_REISETILSKUDDET)?.forsteSvar == "JA") {
        oppdaterteSporsmal.addAll(
            reisetilskuddSporsmal(sykepengesoknad.fom!!, sykepengesoknad.tom!!, sykepengesoknad.arbeidssituasjon!!)
        )
    }

    return sykepengesoknad.copy(sporsmal = oppdaterteSporsmal)
}

private fun fravarForSykmeldingen(tidligsteFomForSykmelding: LocalDate): Sporsmal {
    return Sporsmal(
        tag = FRAVAR_FOR_SYKMELDINGEN,
        sporsmalstekst = "Var du syk og borte fra jobb før du ble sykmeldt, i perioden ${
        formatterPeriode(
            tidligsteFomForSykmelding.minusDays(16),
            tidligsteFomForSykmelding.minusDays(1)
        )
        }?",
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal = listOf(
            Sporsmal(
                tag = FRAVAR_FOR_SYKMELDINGEN_NAR,
                sporsmalstekst = "Hvilke dager var du syk og borte fra jobb, før du ble sykmeldt? Du trenger bare oppgi dager før ${
                formatterDato(
                    tidligsteFomForSykmelding
                )
                }.",
                svartype = Svartype.PERIODER,
                min = tidligsteFomForSykmelding.minusMonths(6).format(ISO_LOCAL_DATE),
                max = tidligsteFomForSykmelding.minusDays(1).format(ISO_LOCAL_DATE)
            )
        )
    )
}

private fun tilbakeIFulltArbeidSporsmal(soknadMetadata: Sykepengesoknad): Sporsmal {
    return Sporsmal(
        tag = TILBAKE_I_ARBEID,
        sporsmalstekst = "Var du tilbake i fullt arbeid hos ${soknadMetadata.arbeidsgiverNavn} i løpet av perioden ${
        formatterPeriode(
            soknadMetadata.fom!!,
            soknadMetadata.tom!!
        )
        }?",
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        pavirkerAndreSporsmal = true,
        undersporsmal = listOf(
            Sporsmal(
                tag = TILBAKE_NAR,
                sporsmalstekst = "Når begynte du å jobbe igjen?",
                svartype = DATO,
                min = soknadMetadata.fom.format(ISO_LOCAL_DATE),
                max = soknadMetadata.tom.format(ISO_LOCAL_DATE),
                pavirkerAndreSporsmal = true
            )
        )
    )
}

fun ferieSporsmal(fom: LocalDate, tom: LocalDate): Sporsmal {
    return Sporsmal(
        tag = FERIE_V2,
        sporsmalstekst = "Tok du ut feriedager i tidsrommet ${formatterPeriode(fom, tom)}?",
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal = listOf(
            Sporsmal(
                tag = FERIE_NAR_V2,
                sporsmalstekst = "Når tok du ut feriedager?",
                svartype = Svartype.PERIODER,
                min = fom.format(ISO_LOCAL_DATE),
                max = tom.format(ISO_LOCAL_DATE)
            )
        )
    )
}

fun utenlandsoppholdSporsmal(fom: LocalDate, tom: LocalDate): Sporsmal {
    return Sporsmal(
        tag = UTLAND_V2,
        sporsmalstekst = "Var du på reise utenfor EØS mens du var sykmeldt ${formatterPeriode(fom, tom)}?",
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal = listOf(
            Sporsmal(
                tag = UTLAND_NAR_V2,
                sporsmalstekst = "Når var du utenfor EØS?",
                svartype = Svartype.PERIODER,
                min = fom.format(ISO_LOCAL_DATE),
                max = tom.format(ISO_LOCAL_DATE)
            )
        )
    )
}

fun gammeltFormatFeriePermisjonUtlandsoppholdSporsmal(fom: LocalDate, tom: LocalDate): Sporsmal {
    return Sporsmal(
        tag = FERIE_PERMISJON_UTLAND,
        sporsmalstekst = "Har du hatt ferie, permisjon eller vært utenfor EØS mens du var sykmeldt ${
        formatterPeriode(
            fom,
            tom
        )
        }?",
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        pavirkerAndreSporsmal = true,
        undersporsmal = listOf(
            Sporsmal(
                tag = FERIE_PERMISJON_UTLAND_HVA,
                sporsmalstekst = "Kryss av alt som gjelder deg:",
                svartype = CHECKBOX_GRUPPE,
                undersporsmal = listOf(
                    Sporsmal(
                        tag = FERIE,
                        sporsmalstekst = "Jeg tok ut ferie",
                        svartype = CHECKBOX,
                        kriterieForVisningAvUndersporsmal = CHECKED,
                        pavirkerAndreSporsmal = true,
                        undersporsmal = listOf(
                            Sporsmal(
                                tag = FERIE_NAR,
                                svartype = Svartype.PERIODER,
                                min = fom.format(ISO_LOCAL_DATE),
                                max = tom.format(ISO_LOCAL_DATE),
                                pavirkerAndreSporsmal = true
                            )
                        )
                    ),
                    Sporsmal(
                        tag = PERMISJON,
                        sporsmalstekst = "Jeg hadde permisjon",
                        svartype = CHECKBOX,
                        kriterieForVisningAvUndersporsmal = CHECKED,
                        undersporsmal = listOf(
                            Sporsmal(
                                tag = PERMISJON_NAR,
                                svartype = Svartype.PERIODER,
                                min = fom.format(ISO_LOCAL_DATE),
                                max = tom.format(ISO_LOCAL_DATE)
                            )
                        )
                    ),
                    Sporsmal(
                        tag = UTLAND,
                        sporsmalstekst = "Jeg var utenfor EØS",
                        svartype = CHECKBOX,
                        kriterieForVisningAvUndersporsmal = CHECKED,
                        pavirkerAndreSporsmal = true,
                        undersporsmal = listOf(
                            Sporsmal(
                                tag = UTLAND_NAR,
                                svartype = Svartype.PERIODER,
                                min = fom.format(ISO_LOCAL_DATE),
                                max = tom.format(ISO_LOCAL_DATE),
                                pavirkerAndreSporsmal = true
                            )
                        )
                    )
                )
            )
        )
    )
}

fun jobbetDuIPeriodenSporsmal(
    soknadsperioder: List<Soknadsperiode>,
    arbeidsgiverNavn: String
): List<Sporsmal> {
    return soknadsperioder
        .lastIndex.downTo(0)
        .reversed()
        .map { index ->
            val periode = soknadsperioder[index]
            if (periode.grad == 100) {
                jobbetDu100Prosent(periode, arbeidsgiverNavn, index)
            } else {
                jobbetDuGradert(periode, arbeidsgiverNavn, index)
            }
        }
}

private fun jobbetDu100Prosent(periode: Soknadsperiode, arbeidsgiver: String, index: Int): Sporsmal {
    return Sporsmal(
        tag = ARBEID_UNDERVEIS_100_PROSENT + index,
        sporsmalstekst = "I perioden ${
        formatterPeriode(
            periode.fom,
            periode.tom
        )
        } var du 100 % sykmeldt fra $arbeidsgiver. Jobbet du noe hos $arbeidsgiver i denne perioden?",
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal = jobbetDuUndersporsmal(
            periode = periode,
            minProsent = 1,
            index = index,
            arbeidsgiverNavn = arbeidsgiver
        )
    )
}
