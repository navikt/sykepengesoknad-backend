package no.nav.syfo.soknadsopprettelse

import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Soknadsperiode
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.Sporsmal
import no.nav.syfo.domain.Svartype
import no.nav.syfo.domain.Svartype.DATO
import no.nav.syfo.domain.Svartype.JA_NEI
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.Sykmeldingstype
import no.nav.syfo.domain.Visningskriterie.JA
import no.nav.syfo.domain.rest.SoknadMetadata
import no.nav.syfo.soknadsopprettelse.oppdateringhelpers.finnGyldigDatoSvar
import no.nav.syfo.soknadsopprettelse.oppdateringhelpers.skapOppdaterteSoknadsperioder
import no.nav.syfo.soknadsopprettelse.sporsmal.*
import no.nav.syfo.soknadsopprettelse.undersporsmal.jobbetDuUndersporsmal
import no.nav.syfo.util.DatoUtil.formatterDato
import no.nav.syfo.util.DatoUtil.formatterPeriode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime.now
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

fun settOppSoknadSelvstendigOgFrilanser(
    soknadMetadata: SoknadMetadata,
    erForsteSoknadISykeforlop: Boolean
): Sykepengesoknad {
    val gradertReisetilskudd = soknadMetadata.soknadstype == Soknadstype.GRADERT_REISETILSKUDD
    val sporsmal = mutableListOf(
        ansvarserklaringSporsmal(reisetilskudd = gradertReisetilskudd),
        if (gradertReisetilskudd) {
            tilbakeIFulltArbeidGradertReisetilskuddSporsmal(soknadMetadata)
        } else {
            tilbakeIFulltArbeidSporsmal(soknadMetadata)
        },
        andreInntektskilderSelvstendigOgFrilanser(soknadMetadata.arbeidssituasjon),
        utlandsSporsmal(soknadMetadata.fom, soknadMetadata.tom),
        utdanningsSporsmal(soknadMetadata.fom, soknadMetadata.tom),
        bekreftOpplysningerSporsmal(),
        vaerKlarOverAt(gradertReisetilskudd = gradertReisetilskudd)
    ).also {
        it.addAll(jobbetDuIPeriodenSporsmal(soknadMetadata.sykmeldingsperioder, soknadMetadata.arbeidssituasjon))
        if (erForsteSoknadISykeforlop) {
            it.add(arbeidUtenforNorge())
        }
        if (gradertReisetilskudd) {
            it.add(brukteReisetilskuddetSpørsmål())
        }
    }

    return Sykepengesoknad(
        id = soknadMetadata.id,
        soknadstype = soknadMetadata.soknadstype,
        status = soknadMetadata.status,
        fom = soknadMetadata.fom,
        tom = soknadMetadata.tom,
        opprettet = Instant.now(),
        startSykeforlop = soknadMetadata.startSykeforlop,
        sykmeldingSkrevet = soknadMetadata.sykmeldingSkrevet,
        sykmeldingId = soknadMetadata.sykmeldingId,
        arbeidssituasjon = soknadMetadata.arbeidssituasjon,
        soknadPerioder = soknadMetadata.sykmeldingsperioder,
        sporsmal = sporsmal,
        fnr = soknadMetadata.fnr,
        egenmeldtSykmelding = soknadMetadata.egenmeldtSykmelding,
        merknaderFraSykmelding = soknadMetadata.merknader,
    )
}

private fun jobbetDuIPeriodenSporsmal(
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
                jobbetDuGradert(periode, index)
            }
        }
}

private fun jobbetDu100Prosent(periode: Soknadsperiode, arbeidssituasjon: Arbeidssituasjon, index: Int): Sporsmal {
    return Sporsmal(
        tag = JOBBET_DU_100_PROSENT + index,
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

private fun tilbakeIFulltArbeidSporsmal(soknadMetadata: SoknadMetadata): Sporsmal {
    return Sporsmal(
        tag = TILBAKE_I_ARBEID,
        sporsmalstekst = "Var du tilbake i fullt arbeid som ${soknadMetadata.arbeidssituasjon} før sykmeldingsperioden utløp ${
        formatterDato(
            soknadMetadata.tom
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

private fun utdanningsSporsmal(fom: LocalDate, tom: LocalDate): Sporsmal {
    return Sporsmal(
        tag = UTDANNING,
        sporsmalstekst = "Har du vært under utdanning i løpet av perioden ${formatterPeriode(fom, tom)}?",
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal = listOf(
            Sporsmal(
                tag = UTDANNING_START,
                sporsmalstekst = "Når startet du på utdanningen?",
                svartype = DATO,
                max = tom.format(ISO_LOCAL_DATE)
            ),
            Sporsmal(
                tag = FULLTIDSSTUDIUM,
                sporsmalstekst = "Er utdanningen et fulltidsstudium?",
                svartype = JA_NEI
            )
        )
    )
}

private fun utlandsSporsmal(fom: LocalDate, tom: LocalDate): Sporsmal {
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

fun oppdaterMedSvarPaArbeidGjenopptattSelvstendig(sykepengesoknad: Sykepengesoknad): Sykepengesoknad {
    val arbeidGjenopptattDato = sykepengesoknad.finnGyldigDatoSvar(TILBAKE_I_ARBEID, TILBAKE_NAR)
    val soknadsTom =
        if (arbeidGjenopptattDato == null)
            sykepengesoknad.tom
        else
            arbeidGjenopptattDato.minusDays(1)

    val oppdaterteSporsmal = sykepengesoknad
        .sporsmal.asSequence()
        .filterNot { (_, tag) -> tag.startsWith(JOBBET_DU_GRADERT) }
        .filterNot { (_, tag) -> tag.startsWith(JOBBET_DU_100_PROSENT) }
        .filterNot { (_, tag) -> tag == UTLAND }
        .filterNot { (_, tag) -> tag == UTDANNING }
        .toMutableList()

    oppdaterteSporsmal.addAll(
        jobbetDuIPeriodenSporsmal(
            sykepengesoknad.skapOppdaterteSoknadsperioder(
                arbeidGjenopptattDato
            ),
            sykepengesoknad.arbeidssituasjon!!
        )
    )
    if (!soknadsTom!!.isBefore(sykepengesoknad.fom)) {
        oppdaterteSporsmal.add(utlandsSporsmal(sykepengesoknad.fom!!, soknadsTom))
        oppdaterteSporsmal.add(utdanningsSporsmal(sykepengesoknad.fom, soknadsTom))
    }

    return sykepengesoknad.copy(sporsmal = oppdaterteSporsmal)
}
