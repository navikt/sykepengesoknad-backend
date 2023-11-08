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
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmArbeidUtenforNorge
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmOppholdUtenforEos
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmOppholdUtenforNorge
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmOppholdstillatelse
import no.nav.helse.flex.soknadsopprettelse.sporsmal.utenlandsksykmelding.utenlandskSykmeldingSporsmal
import no.nav.helse.flex.soknadsopprettelse.undersporsmal.jobbetDuUndersporsmal
import no.nav.helse.flex.util.DatoUtil.formatterPeriode
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

interface MedlemskapSporsmalTag

enum class LovMeSporsmalTag : MedlemskapSporsmalTag {
    OPPHOLDSTILATELSE,
    ARBEID_UTENFOR_NORGE,
    OPPHOLD_UTENFOR_NORGE,
    OPPHOLD_UTENFOR_EØS_OMRÅDE
}

enum class SykepengesoknadSporsmalTag : MedlemskapSporsmalTag {
    ARBEID_UTENFOR_NORGE
}

fun settOppSoknadArbeidstaker(
    soknadOptions: SettOppSoknadOptions,
    andreKjenteArbeidsforhold: List<String>,
    toggle: Boolean = true
): List<Sporsmal> {
    val (sykepengesoknad, erForsteSoknadISykeforlop, harTidligereUtenlandskSpm, yrkesskade, medlemskapTags) = soknadOptions
    val erGradertReisetilskudd = sykepengesoknad.soknadstype == GRADERT_REISETILSKUDD
    return mutableListOf<Sporsmal>().apply {
        add(ansvarserklaringSporsmal(reisetilskudd = erGradertReisetilskudd))
        add(
            if (erGradertReisetilskudd) {
                tilbakeIFulltArbeidGradertReisetilskuddSporsmal(sykepengesoknad)
            } else {
                tilbakeIFulltArbeidSporsmal(sykepengesoknad)
            }
        )
        add(ferieSporsmal(sykepengesoknad.fom!!, sykepengesoknad.tom!!))
        add(permisjonSporsmal(sykepengesoknad.fom, sykepengesoknad.tom))
        add(utenlandsoppholdSporsmal(sykepengesoknad.fom, sykepengesoknad.tom))
        if (toggle) {
            add(tilSlutt())
        }
        if (!toggle) {
            add(vaerKlarOverAt(erGradertReisetilskudd))
            add(bekreftOpplysningerSporsmal())
        }
        addAll(yrkesskade.yrkeskadeSporsmal())

        if (sykepengesoknad.utenlandskSykmelding && (erForsteSoknadISykeforlop || !harTidligereUtenlandskSpm)) {
            addAll(utenlandskSykmeldingSporsmal(sykepengesoknad))
        }

        add(andreInntektskilderArbeidstakerV2(sykepengesoknad.arbeidsgiverNavn!!, andreKjenteArbeidsforhold))
        addAll(jobbetDuIPeriodenSporsmal(sykepengesoknad.soknadPerioder!!, sykepengesoknad.arbeidsgiverNavn))

        if (erGradertReisetilskudd) {
            add(brukteReisetilskuddetSpørsmål())
        }

        addAll(
            medlemskapTags!!.map {
                when (it) {
                    LovMeSporsmalTag.OPPHOLDSTILATELSE -> lagSporsmalOmOppholdstillatelse(sykepengesoknad.tom)
                    LovMeSporsmalTag.ARBEID_UTENFOR_NORGE -> lagSporsmalOmArbeidUtenforNorge(sykepengesoknad.tom)
                    LovMeSporsmalTag.OPPHOLD_UTENFOR_NORGE -> lagSporsmalOmOppholdUtenforNorge(sykepengesoknad.tom)
                    LovMeSporsmalTag.OPPHOLD_UTENFOR_EØS_OMRÅDE -> lagSporsmalOmOppholdUtenforEos(sykepengesoknad.tom)
                    SykepengesoknadSporsmalTag.ARBEID_UTENFOR_NORGE -> arbeidUtenforNorge()
                    else -> {
                        throw RuntimeException("Ukjent MedlemskapSporsmalTag: $it.")
                    }
                }
            }
        )
    }
}

fun Sykepengesoknad.harFeriePermisjonEllerUtenlandsoppholdSporsmal(): Boolean {
    return this.getSporsmalMedTagOrNull(FERIE_PERMISJON_UTLAND) != null
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
