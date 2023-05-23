package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.*
import no.nav.helse.flex.domain.Svartype.DATO
import no.nav.helse.flex.domain.Svartype.JA_NEI
import no.nav.helse.flex.domain.Visningskriterie.JA
import no.nav.helse.flex.soknadsopprettelse.sporsmal.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.utenlandsksykmelding.utenlandskSykmeldingSporsmal
import no.nav.helse.flex.soknadsopprettelse.undersporsmal.jobbetDuUndersporsmal
import no.nav.helse.flex.util.DatoUtil.formatterDato
import no.nav.helse.flex.util.DatoUtil.formatterPeriode
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

fun settOppSoknadSelvstendigOgFrilanser(
    sykepengesoknad: Sykepengesoknad,
    erForsteSoknadISykeforlop: Boolean,
    harTidligereUtenlandskSpm: Boolean,
    yrkesskade: Boolean
): List<Sporsmal> {
    val gradertReisetilskudd = sykepengesoknad.soknadstype == Soknadstype.GRADERT_REISETILSKUDD
    return mutableListOf(
        ansvarserklaringSporsmal(reisetilskudd = gradertReisetilskudd),
        if (gradertReisetilskudd) {
            tilbakeIFulltArbeidGradertReisetilskuddSporsmal(sykepengesoknad)
        } else {
            tilbakeIFulltArbeidSporsmal(sykepengesoknad)
        },
        andreInntektskilderSelvstendigOgFrilanser(sykepengesoknad.arbeidssituasjon!!),
        utlandsSporsmalSelvstendig(sykepengesoknad.fom!!, sykepengesoknad.tom!!),
        bekreftOpplysningerSporsmal(),
        vaerKlarOverAt(gradertReisetilskudd = gradertReisetilskudd)
    ).also {
        it.addAll(jobbetDuIPeriodenSporsmalSelvstendigFrilanser(sykepengesoknad.soknadPerioder!!, sykepengesoknad.arbeidssituasjon))
        if (erForsteSoknadISykeforlop) {
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
        if (gradertReisetilskudd) {
            it.add(brukteReisetilskuddetSpørsmål())
        }
    }.toList()
}

fun jobbetDuIPeriodenSporsmalSelvstendigFrilanser(
    soknadsperioder: List<Soknadsperiode>,
    arbeidssituasjon: Arbeidssituasjon
): List<Sporsmal> {
    return soknadsperioder
        .filter { it.sykmeldingstype == Sykmeldingstype.GRADERT || it.sykmeldingstype == Sykmeldingstype.AKTIVITET_IKKE_MULIG }
        .lastIndex.downTo(0)
        .reversed()
        .map { index ->
            val periode = soknadsperioder[index]
            if (periode.grad == 100) {
                jobbetDu100Prosent(periode, arbeidssituasjon, index)
            } else {
                jobbetDuGradert(periode, arbeidssituasjon, index)
            }
        }
}

private fun jobbetDu100Prosent(periode: Soknadsperiode, arbeidssituasjon: Arbeidssituasjon, index: Int): Sporsmal {
    return Sporsmal(
        tag = ARBEID_UNDERVEIS_100_PROSENT + index,
        sporsmalstekst = "I perioden ${
        formatterPeriode(
            periode.fom,
            periode.tom
        )
        } var du 100% sykmeldt som $arbeidssituasjon. Jobbet du noe i denne perioden?",
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal = jobbetDuUndersporsmal(periode, 1, index)
    )
}

fun jobbetDuGradert(
    periode: Soknadsperiode,
    arbeidssituasjon: Arbeidssituasjon,
    index: Int
): Sporsmal {
    return Sporsmal(
        tag = JOBBET_DU_GRADERT + index,
        sporsmalstekst = "Sykmeldingen sier du kunne jobbe ${100 - periode.grad} % i jobben din som $arbeidssituasjon. Jobbet du mer enn det?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal = jobbetDuGradertUndersporsmal(periode, 100 + 1 - periode.grad, index)
    )
}

private fun tilbakeIFulltArbeidSporsmal(soknadMetadata: Sykepengesoknad): Sporsmal {
    return Sporsmal(
        tag = TILBAKE_I_ARBEID,
        sporsmalstekst = "Var du tilbake i fullt arbeid som ${soknadMetadata.arbeidssituasjon} før sykmeldingsperioden utløp ${
        formatterDato(
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
                min = soknadMetadata.fom!!.format(ISO_LOCAL_DATE),
                max = soknadMetadata.tom.format(ISO_LOCAL_DATE),
                pavirkerAndreSporsmal = true
            )
        )
    )
}

fun utlandsSporsmalSelvstendig(fom: LocalDate, tom: LocalDate): Sporsmal {
    return Sporsmal(
        tag = UTLAND,
        sporsmalstekst = "Har du vært utenfor EØS mens du var sykmeldt " + formatterPeriode(fom, tom) + "?",
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal = listOf(
            Sporsmal(
                tag = PERIODER,
                sporsmalstekst = "Når var du utenfor EØS?",
                svartype = Svartype.PERIODER,
                min = fom.format(ISO_LOCAL_DATE),
                max = tom.format(ISO_LOCAL_DATE)
            ),
            Sporsmal(
                tag = UTLANDSOPPHOLD_SOKT_SYKEPENGER,
                sporsmalstekst = "Har du søkt om å beholde sykepengene for disse dagene?",
                svartype = JA_NEI,
                undersporsmal = emptyList()
            )
        )
    )
}
