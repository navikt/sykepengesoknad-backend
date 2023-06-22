package no.nav.helse.flex.mock

import no.nav.helse.flex.domain.Arbeidssituasjon.ARBEIDSTAKER
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.SettOppSoknadOpts
import no.nav.helse.flex.testutil.besvarsporsmal
import no.nav.helse.flex.util.DatoUtil.periodeTilJson
import no.nav.helse.flex.util.tilOsloInstant
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import java.time.Instant
import java.time.LocalDate.now
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import java.util.*

fun deprecatedGetSoknadMedFeriesporsmalSomUndersporsmal(soknadMetadata: Sykepengesoknad): Sykepengesoknad {
    val sykepengesoknad = soknadMetadata.copy(
        sporsmal = settOppSoknadArbeidstaker(
            SettOppSoknadOpts(
                sykepengesoknad = soknadMetadata,
                erForsteSoknadISykeforlop = true,
                harTidligereUtenlandskSpm = false,
                yrkesskade = false
            ),
            andreKjenteArbeidsforhold = emptyList()
        )

    )
        .fjernSporsmal(FERIE_V2)
        .fjernSporsmal(PERMISJON_V2)
        .fjernSporsmal(UTLAND_V2)

    val spm = sykepengesoknad.sporsmal.toMutableList().also {
        it.add(
            gammeltFormatFeriePermisjonUtlandsoppholdSporsmal(
                soknadMetadata.fom!!,
                soknadMetadata.tom!!

            )
        )
    }
    return sykepengesoknad.copy(sporsmal = spm)
}

fun gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal(): Sykepengesoknad {
    val soknadMetadata = Sykepengesoknad(
        id = UUID.randomUUID().toString(),
        status = Soknadstatus.NY,
        opprettet = Instant.now(),
        sporsmal = emptyList(),
        fnr = "fnr-7454630",
        startSykeforlop = now().minusMonths(1),
        fom = now().minusMonths(1),
        tom = now().minusMonths(1).plusDays(8),
        arbeidssituasjon = ARBEIDSTAKER,
        arbeidsgiverOrgnummer = "123456789",
        arbeidsgiverNavn = "ARBEIDSGIVER A/S",
        sykmeldingId = "14e78e84-50a5-45bb-9919-191c54f99691",
        sykmeldingSkrevet = LocalDateTime.now().minusMonths(1).tilOsloInstant(),
        soknadPerioder = listOf(
            SykmeldingsperiodeAGDTO(
                now().minusMonths(1),
                now().minusMonths(1).plusDays(4),
                gradert = GradertDTO(grad = 100, reisetilskudd = false),
                type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false
            ),
            SykmeldingsperiodeAGDTO(
                now().minusMonths(1).plusDays(5),
                now().minusMonths(1).plusDays(8),
                gradert = GradertDTO(grad = 40, reisetilskudd = false),
                type = PeriodetypeDTO.GRADERT,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false
            )
        ).tilSoknadsperioder(),
        soknadstype = Soknadstype.ARBEIDSTAKERE,
        egenmeldtSykmelding = null,
        utenlandskSykmelding = false,
        egenmeldingsdagerFraSykmelding = null

    )

    val sykepengesoknad = leggSvarPaSoknad(deprecatedGetSoknadMedFeriesporsmalSomUndersporsmal(soknadMetadata))
    return sykepengesoknad.copy(sendtNav = Instant.now(), sendtArbeidsgiver = Instant.now())
}

fun opprettSendtSoknad(): Sykepengesoknad {
    val soknadMetadata = Sykepengesoknad(
        id = UUID.randomUUID().toString(),
        status = Soknadstatus.NY,
        opprettet = Instant.now(),
        sporsmal = emptyList(),
        fnr = "fnr-7454630",
        startSykeforlop = now().minusMonths(1),
        fom = now().minusMonths(1),
        tom = now().minusMonths(1).plusDays(8),
        arbeidssituasjon = ARBEIDSTAKER,
        arbeidsgiverOrgnummer = "123456789",
        arbeidsgiverNavn = "ARBEIDSGIVER A/S",
        sykmeldingId = "14e78e84-50a5-45bb-9919-191c54f99691",
        sykmeldingSkrevet = LocalDateTime.now().minusMonths(1).tilOsloInstant(),
        soknadPerioder = listOf(
            SykmeldingsperiodeAGDTO(
                fom = now().minusMonths(1),
                tom = now().minusMonths(1).plusDays(4),
                gradert = GradertDTO(grad = 100, reisetilskudd = false),
                type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false
            ),
            SykmeldingsperiodeAGDTO(
                fom = now().minusMonths(1).plusDays(5),
                tom = now().minusMonths(1).plusDays(8),
                gradert = GradertDTO(grad = 40, reisetilskudd = false),
                type = PeriodetypeDTO.GRADERT,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false
            )
        ).tilSoknadsperioder(),
        soknadstype = Soknadstype.ARBEIDSTAKERE,
        egenmeldtSykmelding = null,
        utenlandskSykmelding = false,
        egenmeldingsdagerFraSykmelding = null
    )

    val sykepengesoknad = leggSvarPaSoknad(
        soknadMetadata.copy(
            sporsmal = settOppSoknadArbeidstaker(
                SettOppSoknadOpts(
                    sykepengesoknad = soknadMetadata,
                    erForsteSoknadISykeforlop = true,
                    harTidligereUtenlandskSpm = false,
                    yrkesskade = false
                ),
                andreKjenteArbeidsforhold = emptyList()
            )
        )
    )
    return sykepengesoknad.copy(
        sendtNav = Instant.now(),
        sendtArbeidsgiver = Instant.now(),
        status = Soknadstatus.SENDT
    )
}

@Deprecated("")
fun opprettNySoknadMock(feriesporsmalSomHovedsporsmal: Boolean = true): Sykepengesoknad {
    val soknad = Sykepengesoknad(
        id = UUID.randomUUID().toString(),
        status = Soknadstatus.NY,
        opprettet = Instant.now(),
        sporsmal = emptyList(),
        fnr = "fnr-7454630",
        startSykeforlop = now().minusDays(24),
        fom = now().minusDays(19),
        tom = now().minusDays(10),
        arbeidssituasjon = ARBEIDSTAKER,
        arbeidsgiverOrgnummer = "123456789",
        arbeidsgiverNavn = "ARBEIDSGIVER A/S",
        sykmeldingId = "289148ba-4c3c-4b3f-b7a3-385b7e7c927d",
        sykmeldingSkrevet = LocalDateTime.now().minusDays(19).tilOsloInstant(),
        soknadPerioder = listOf(
            SykmeldingsperiodeAGDTO(
                now().minusDays(19),
                now().minusDays(15),
                gradert = GradertDTO(grad = 100, reisetilskudd = false),
                type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false
            ),
            SykmeldingsperiodeAGDTO(
                now().minusDays(14),
                now().minusDays(10),
                gradert = GradertDTO(grad = 40, reisetilskudd = false),
                type = PeriodetypeDTO.GRADERT,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false
            )
        ).tilSoknadsperioder(),
        soknadstype = Soknadstype.ARBEIDSTAKERE,
        egenmeldtSykmelding = null,
        utenlandskSykmelding = false,
        egenmeldingsdagerFraSykmelding = null
    )

    val sykepengesoknad = if (feriesporsmalSomHovedsporsmal) {
        soknad.copy(
            sporsmal = settOppSoknadArbeidstaker(
                SettOppSoknadOpts(
                    sykepengesoknad = soknad,
                    erForsteSoknadISykeforlop = true,
                    harTidligereUtenlandskSpm = false,
                    yrkesskade = false
                ),
                andreKjenteArbeidsforhold = emptyList()
            )
        )
    } else {
        deprecatedGetSoknadMedFeriesporsmalSomUndersporsmal(soknad)
    }

    return leggSvarPaSoknad(sykepengesoknad)
}

fun gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal(): Sykepengesoknad {
    @Suppress("DEPRECATION")
    return opprettNySoknadMock(false)
}

private fun leggSvarPaSoknad(sykepengesoknad: Sykepengesoknad): Sykepengesoknad {
    val s = sykepengesoknad
        .besvarsporsmal(ANSVARSERKLARING, "CHECKED")
        .tilbakeIFulltArbeid()
        .jobbetDu100Prosent()
        .jobbetDuGradert()
        .andreInntektskilder()
        .besvarsporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")

    return if (s.harFeriePermisjonEllerUtenlandsoppholdSporsmal()) {
        s.feriePermisjonUtland()
    } else {
        s.besvarsporsmal(PERMISJON_V2, "NEI")
            .utenlandsopphold()
            .ferie()
    }
}

private fun Sykepengesoknad.tilbakeIFulltArbeid(): Sykepengesoknad {
    return besvarsporsmal(TILBAKE_I_ARBEID, "JA")
        .besvarsporsmal(TILBAKE_NAR, fom!!.plusDays(7).format(ISO_LOCAL_DATE))
}

private fun Sykepengesoknad.jobbetDu100Prosent(): Sykepengesoknad {
    return besvarsporsmal(ARBEID_UNDERVEIS_100_PROSENT + "0", "JA")
        .besvarsporsmal(JOBBER_DU_NORMAL_ARBEIDSUKE + "0", "NEI")
        .besvarsporsmal(HVOR_MANGE_TIMER_PER_UKE + "0", "37,5")
        .besvarsporsmal(HVOR_MYE_PROSENT + "0", "CHECKED")
        .besvarsporsmal(HVOR_MYE_PROSENT_VERDI + "0", "79")
}

private fun Sykepengesoknad.jobbetDuGradert(): Sykepengesoknad {
    return besvarsporsmal(JOBBET_DU_GRADERT + "1", "JA")
        .besvarsporsmal(HVOR_MANGE_TIMER_PER_UKE + "1", "37.5")
        .besvarsporsmal(HVOR_MYE_TIMER + "1", "CHECKED")
        .besvarsporsmal(HVOR_MYE_TIMER_VERDI + "1", "66")
}

private fun Sykepengesoknad.feriePermisjonUtland(): Sykepengesoknad {
    return besvarsporsmal(FERIE_PERMISJON_UTLAND, "JA")
        .besvarsporsmal(FERIE, "CHECKED")
        .besvarsporsmal(FERIE_NAR, periodeTilJson(fom!!.plusDays(2), fom!!.plusDays(4)))
        .besvarsporsmal(UTLAND, "CHECKED")
        .besvarsporsmal(
            tag = UTLAND_NAR,
            svarListe = listOf(
                periodeTilJson(fom!!.plusDays(1), fom!!.plusDays(2)),
                periodeTilJson(fom!!.plusDays(4), fom!!.plusDays(6))
            )
        )
}

private fun Sykepengesoknad.ferie(): Sykepengesoknad {
    return besvarsporsmal(FERIE_V2, "JA")
        .besvarsporsmal(FERIE_NAR_V2, periodeTilJson(fom!!.plusDays(1), fom!!.plusDays(2)))
}

private fun Sykepengesoknad.utenlandsopphold(): Sykepengesoknad {
    return besvarsporsmal(UTLAND_V2, "JA")
        .besvarsporsmal(
            tag = UTLAND_NAR_V2,
            svarListe = listOf(
                periodeTilJson(fom!!.plusDays(1), fom!!.plusDays(1)),
                periodeTilJson(fom!!.plusDays(4), fom!!.plusDays(6))
            )
        )
}

private fun Sykepengesoknad.andreInntektskilder(): Sykepengesoknad {
    return besvarsporsmal(ANDRE_INNTEKTSKILDER_V2, "JA")
        .besvarsporsmal(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD, "CHECKED")
        .besvarsporsmal(INNTEKTSKILDE_SELVSTENDIG, "CHECKED")
        .besvarsporsmal(INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA, "CHECKED")
}
