package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Opprinnelse
import no.nav.helse.flex.domain.Periode
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
import no.nav.helse.flex.domain.rest.SoknadMetadata
import no.nav.helse.flex.soknadsopprettelse.oppdateringhelpers.finnGyldigDatoSvar
import no.nav.helse.flex.soknadsopprettelse.oppdateringhelpers.skapOppdaterteSoknadsperioder
import no.nav.helse.flex.soknadsopprettelse.sporsmal.andreInntektskilderArbeidstaker
import no.nav.helse.flex.soknadsopprettelse.sporsmal.ansvarserklaringSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.arbeidUtenforNorge
import no.nav.helse.flex.soknadsopprettelse.sporsmal.bekreftOpplysningerSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.jobbetDuGradert
import no.nav.helse.flex.soknadsopprettelse.sporsmal.permisjonSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.tilbakeIFulltArbeidGradertReisetilskuddSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.vaerKlarOverAt
import no.nav.helse.flex.soknadsopprettelse.undersporsmal.jobbetDuUndersporsmal
import no.nav.helse.flex.util.DatoUtil.formatterDato
import no.nav.helse.flex.util.DatoUtil.formatterPeriode
import no.nav.helse.flex.util.DatoUtil.periodeErInnenforMinMax
import no.nav.helse.flex.util.DatoUtil.periodeErUtenforHelg
import no.nav.helse.flex.util.DatoUtil.periodeHarDagerUtenforAndrePerioder
import no.nav.helse.flex.util.PeriodeMapper
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import java.util.Collections.emptyList

fun settOppSoknadArbeidstaker(
    soknadMetadata: SoknadMetadata,
    erForsteSoknadISykeforlop: Boolean,
    tidligsteFomForSykmelding: LocalDate,
): Sykepengesoknad {
    val gradertResietilskudd = soknadMetadata.soknadstype == GRADERT_REISETILSKUDD

    val sporsmal = mutableListOf(
        ansvarserklaringSporsmal(reisetilskudd = gradertResietilskudd),
        if (gradertResietilskudd) {
            tilbakeIFulltArbeidGradertReisetilskuddSporsmal(soknadMetadata)
        } else {
            tilbakeIFulltArbeidSporsmal(soknadMetadata)
        },
        ferieSporsmal(soknadMetadata.fom, soknadMetadata.tom),
        permisjonSporsmal(soknadMetadata.fom, soknadMetadata.tom),
        utenlandsoppholdSporsmal(soknadMetadata.fom, soknadMetadata.tom),
        andreInntektskilderArbeidstaker(soknadMetadata.arbeidsgiverNavn),
        utdanningsSporsmal(soknadMetadata.fom, soknadMetadata.tom),
        vaerKlarOverAt(gradertReisetilskudd = gradertResietilskudd),
        bekreftOpplysningerSporsmal()
    ).also {
        if (erForsteSoknadISykeforlop) {
            it.add(fravarForSykmeldingen(tidligsteFomForSykmelding))
            it.add(arbeidUtenforNorge())
        }
        it.addAll(
            jobbetDuIPeriodenSporsmal(
                soknadMetadata.sykmeldingsperioder,
                soknadMetadata.arbeidsgiverNavn
            )
        )
        if (gradertResietilskudd) {
            it.add(brukteReisetilskuddetSp??rsm??l())
        }
    }

    return Sykepengesoknad(
        id = soknadMetadata.id,
        fnr = soknadMetadata.fnr,
        sykmeldingId = soknadMetadata.sykmeldingId,
        status = soknadMetadata.status,
        fom = soknadMetadata.fom,
        tom = soknadMetadata.tom,
        opprettet = Instant.now(),
        startSykeforlop = soknadMetadata.startSykeforlop,
        sykmeldingSkrevet = soknadMetadata.sykmeldingSkrevet,
        arbeidsgiverOrgnummer = soknadMetadata.arbeidsgiverOrgnummer!!,
        arbeidsgiverNavn = soknadMetadata.arbeidsgiverNavn!!,
        soknadPerioder = soknadMetadata.sykmeldingsperioder,
        sporsmal = sporsmal,
        opprinnelse = Opprinnelse.SYFOSOKNAD,
        soknadstype = soknadMetadata.soknadstype,
        arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
        egenmeldtSykmelding = soknadMetadata.egenmeldtSykmelding,
        merknaderFraSykmelding = soknadMetadata.merknader,
    )
}

fun harFeriePermisjonEllerUtenlandsoppholdSporsmal(sykepengesoknad: Sykepengesoknad): Boolean {
    return sykepengesoknad.getOptionalSporsmalMedTag(FERIE_PERMISJON_UTLAND).isPresent
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

fun oppdaterMedSvarPaArbeidGjenopptattArbeidstaker(sykepengesoknad: Sykepengesoknad): Sykepengesoknad {
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
        .filterNot { (_, tag) -> tag == FERIE_PERMISJON_UTLAND }
        .filterNot { (_, tag) -> tag == FERIE_V2 }
        .filterNot { (_, tag) -> tag == PERMISJON_V2 }
        .filterNot { (_, tag) -> tag == UTLAND_V2 }
        .filterNot { (_, tag) -> tag == UTDANNING }
        .toMutableList()

    oppdaterteSporsmal.addAll(
        jobbetDuIPeriodenSporsmal(
            sykepengesoknad.skapOppdaterteSoknadsperioder(
                arbeidGjenopptattDato
            ),
            sykepengesoknad.arbeidsgiverNavn
        )
    )
    if (!soknadsTom!!.isBefore(sykepengesoknad.fom)) {
        if (harFeriePermisjonEllerUtenlandsoppholdSporsmal(sykepengesoknad)) {
            oppdaterteSporsmal.add(gammeltFormatFeriePermisjonUtlandsoppholdSporsmal(sykepengesoknad.fom!!, soknadsTom))
        } else {
            oppdaterteSporsmal.add(ferieSporsmal(sykepengesoknad.fom!!, soknadsTom))
            oppdaterteSporsmal.add(permisjonSporsmal(sykepengesoknad.fom, soknadsTom))
            oppdaterteSporsmal.add(utenlandsoppholdSporsmal(sykepengesoknad.fom, soknadsTom))
        }
        oppdaterteSporsmal.add(utdanningsSporsmal(sykepengesoknad.fom, soknadsTom))
    }

    return sykepengesoknad.copy(sporsmal = oppdaterteSporsmal)
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

private fun getGyldigePeriodesvar(sporsmal: Sporsmal): List<Periode> {
    return sporsmal.svar.asSequence()
        .map { it.verdi }
        .map { PeriodeMapper.jsonTilOptionalPeriode(it) } // TODO: Kan enders?
        .filter { it.isPresent }
        .map { it.get() }
        .filter { periode -> periodeErInnenforMinMax(periode, sporsmal.min, sporsmal.max) }
        .toList()
        .toList()
}

private fun hentGyldigePerioder(sykepengesoknad: Sykepengesoknad, hva: String, nar: String): List<Periode> {
    return if (harFeriePermisjonEllerUtenlandsoppholdSporsmal(sykepengesoknad)) {
        val harFeriePermisjonUtlandsopphold =
            sykepengesoknad.getSporsmalMedTag(FERIE_PERMISJON_UTLAND).forsteSvar == "JA"
        if (harFeriePermisjonUtlandsopphold && sykepengesoknad.getSporsmalMedTag(hva).forsteSvar == "CHECKED")
            getGyldigePeriodesvar(sykepengesoknad.getSporsmalMedTag(nar))
        else
            emptyList()
    } else {
        val harSvartJa = sykepengesoknad.getSporsmalMedTag(hva).forsteSvar == "JA"
        if (harSvartJa)
            getGyldigePeriodesvar(sykepengesoknad.getSporsmalMedTag(nar))
        else
            emptyList()
    }
}

fun oppdaterMedSvarPaUtlandsopphold(soknad: Sykepengesoknad): Sykepengesoknad {
    var sykepengesoknad = soknad

    val gyldigeFerieperioder = hentGyldigePerioder(
        sykepengesoknad,
        if (harFeriePermisjonEllerUtenlandsoppholdSporsmal(sykepengesoknad)) FERIE else FERIE_V2,
        if (harFeriePermisjonEllerUtenlandsoppholdSporsmal(sykepengesoknad)) FERIE_NAR else FERIE_NAR_V2
    )

    val gyldigeUtlandsperioder = hentGyldigePerioder(
        sykepengesoknad,
        if (harFeriePermisjonEllerUtenlandsoppholdSporsmal(sykepengesoknad)) UTLAND else UTLAND_V2,
        if (harFeriePermisjonEllerUtenlandsoppholdSporsmal(sykepengesoknad)) UTLAND_NAR else UTLAND_NAR_V2
    )

    val harUtlandsoppholdUtenforHelgOgFerie = gyldigeUtlandsperioder
        .filter { periodeErUtenforHelg(it) }
        .any { periode -> periodeHarDagerUtenforAndrePerioder(periode, gyldigeFerieperioder) }

    val plasserEtterSporsmal =
        if (harFeriePermisjonEllerUtenlandsoppholdSporsmal(sykepengesoknad))
            sykepengesoknad.getSporsmalMedTag(FERIE_PERMISJON_UTLAND)
        else
            sykepengesoknad.getSporsmalMedTag(UTLAND_V2)

    val maybeSoktOmSykepengerSporsmal = sykepengesoknad.getOptionalSporsmalMedTag(UTLANDSOPPHOLD_SOKT_SYKEPENGER)

    sykepengesoknad = sykepengesoknad.fjernSporsmal(UTLANDSOPPHOLD_SOKT_SYKEPENGER)

    if (harUtlandsoppholdUtenforHelgOgFerie) {
        val oppholdUtland = Sporsmal(
            tag = UTLANDSOPPHOLD_SOKT_SYKEPENGER,
            sporsmalstekst = "Har du s??kt om ?? beholde sykepengene for de dagene du var utenfor E??S?",
            svartype = JA_NEI,
            pavirkerAndreSporsmal = false,
            svar = maybeSoktOmSykepengerSporsmal
                .map { it.svar }
                .orElse(emptyList()),
            undersporsmal = emptyList()
        )
        sykepengesoknad = sykepengesoknad.addHovedsporsmal(oppholdUtland, plasserEtterSporsmal)
    }

    return sykepengesoknad
}

private fun fravarForSykmeldingen(tidligsteFomForSykmelding: LocalDate): Sporsmal {
    return Sporsmal(
        tag = FRAVAR_FOR_SYKMELDINGEN,
        sporsmalstekst = "Var du syk og borte fra jobb f??r du ble sykmeldt, i perioden ${
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
                sporsmalstekst = "Hvilke dager var du syk og borte fra jobb, f??r du ble sykmeldt? Du trenger bare oppgi dager f??r ${
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

private fun tilbakeIFulltArbeidSporsmal(soknadMetadata: SoknadMetadata): Sporsmal {
    return Sporsmal(
        tag = TILBAKE_I_ARBEID,
        sporsmalstekst = "Var du tilbake i fullt arbeid hos ${soknadMetadata.arbeidsgiverNavn} i l??pet av perioden ${
        formatterPeriode(
            soknadMetadata.fom,
            soknadMetadata.tom
        )
        }?",
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        pavirkerAndreSporsmal = true,
        undersporsmal = listOf(
            Sporsmal(
                tag = TILBAKE_NAR,
                sporsmalstekst = "N??r begynte du ?? jobbe igjen?",
                svartype = DATO,
                min = soknadMetadata.fom.format(ISO_LOCAL_DATE),
                max = soknadMetadata.tom.format(ISO_LOCAL_DATE),
                pavirkerAndreSporsmal = true
            )
        )
    )
}

private fun ferieSporsmal(fom: LocalDate, tom: LocalDate): Sporsmal {
    return Sporsmal(
        tag = FERIE_V2,
        sporsmalstekst = "Tok du ut feriedager i tidsrommet ${formatterPeriode(fom, tom)}?",
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal = listOf(
            Sporsmal(
                tag = FERIE_NAR_V2,
                sporsmalstekst = "N??r tok du ut feriedager?",
                svartype = Svartype.PERIODER,
                min = fom.format(ISO_LOCAL_DATE),
                max = tom.format(ISO_LOCAL_DATE)
            )
        )
    )
}

private fun utenlandsoppholdSporsmal(fom: LocalDate, tom: LocalDate): Sporsmal {
    return Sporsmal(
        tag = UTLAND_V2,
        sporsmalstekst = "Var du p?? reise utenfor E??S mens du var sykmeldt ${formatterPeriode(fom, tom)}?",
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal = listOf(
            Sporsmal(
                tag = UTLAND_NAR_V2,
                sporsmalstekst = "N??r var du utenfor E??S?",
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
        sporsmalstekst = "Har du hatt ferie, permisjon eller v??rt utenfor E??S mens du var sykmeldt ${
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
                        sporsmalstekst = "Jeg var utenfor E??S",
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

private fun jobbetDuIPeriodenSporsmal(
    soknadsperioder: List<Soknadsperiode>,
    arbeidsgiverNavn: String?,
): List<Sporsmal> {
    return soknadsperioder
        .lastIndex.downTo(0)
        .reversed()
        .map { index ->
            val periode = soknadsperioder[index]
            if (periode.grad == 100) {
                jobbetDu100Prosent(periode, arbeidsgiverNavn, index)
            } else {
                jobbetDuGradert(periode, index)
            }
        }
}

private fun jobbetDu100Prosent(periode: Soknadsperiode, arbeidsgiver: String?, index: Int): Sporsmal {
    return Sporsmal(
        tag = JOBBET_DU_100_PROSENT + index,
        sporsmalstekst = "I perioden ${
        formatterPeriode(
            periode.fom,
            periode.tom
        )
        } var du 100 % sykmeldt fra $arbeidsgiver. Jobbet du noe i denne perioden?",
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal = jobbetDuUndersporsmal(periode = periode, minProsent = 1, index = index)
    )
}

private fun utdanningsSporsmal(fom: LocalDate, tom: LocalDate): Sporsmal {
    return Sporsmal(
        tag = UTDANNING,
        sporsmalstekst = "Har du v??rt under utdanning i l??pet av perioden ${formatterPeriode(fom, tom)}?",
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal = listOf(
            Sporsmal(
                tag = UTDANNING_START,
                sporsmalstekst = "N??r startet du p?? utdanningen?",
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
