package no.nav.helse.flex.mock

import no.nav.helse.flex.domain.Arbeidssituasjon.ARBEIDSTAKER
import no.nav.helse.flex.domain.Soknadstatus.NY
import no.nav.helse.flex.domain.Soknadstatus.SENDT
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.rest.SoknadMetadata
import no.nav.helse.flex.soknadsopprettelse.*
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
import java.util.Arrays.asList
import java.util.Collections.emptyList

fun deprecatedGetSoknadMedFeriesporsmalSomUndersporsmal(soknadMetadata: SoknadMetadata): Sykepengesoknad {
    val sykepengesoknad = settOppSoknadArbeidstaker(soknadMetadata, true, now())
        .fjernSporsmal(FERIE_V2)
        .fjernSporsmal(PERMISJON_V2)
        .fjernSporsmal(UTLAND_V2)
    (sykepengesoknad.sporsmal as ArrayList).add(
        gammeltFormatFeriePermisjonUtlandsoppholdSporsmal(
            soknadMetadata.fom,
            soknadMetadata.tom
        )
    )
    return sykepengesoknad.copy(sporsmal = sykepengesoknad.sporsmal)
}

fun deprecatedOpprettEnkelSendtSoknad(): Sykepengesoknad {
    val soknadMetadata = SoknadMetadata(
        id = UUID.randomUUID().toString(),
        fnr = "fnr-7454630",
        status = SENDT,
        startSykeforlop = now().minusMonths(1),
        fom = now().minusMonths(1),
        soknadstype = Soknadstype.ARBEIDSTAKERE,
        tom = now().minusMonths(1).plusDays(8),
        arbeidssituasjon = ARBEIDSTAKER,
        arbeidsgiverOrgnummer = "123456789",
        arbeidsgiverNavn = "ARBEIDSGIVER A/S",
        sykmeldingId = "sykmeldingId",
        sykmeldingSkrevet = LocalDateTime.now().minusMonths(1).tilOsloInstant(),
        sykmeldingsperioder = listOf(
            SykmeldingsperiodeAGDTO(
                fom = now().minusMonths(1),
                tom = now().minusMonths(1).plusDays(4),
                gradert = GradertDTO(grad = 100, reisetilskudd = false),
                type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false,
            ),
        ).tilSoknadsperioder(),
        egenmeldtSykmelding = null

    )

    val sykepengesoknad = leggNeiSvarPaSoknad(deprecatedGetSoknadMedFeriesporsmalSomUndersporsmal(soknadMetadata))
    return sykepengesoknad.copy(sendtNav = Instant.now(), sendtArbeidsgiver = Instant.now())
}

fun gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal(): Sykepengesoknad {
    val soknadMetadata = SoknadMetadata(
        fnr = "fnr-7454630",
        status = SENDT,
        startSykeforlop = now().minusMonths(1),
        fom = now().minusMonths(1),
        tom = now().minusMonths(1).plusDays(8),
        arbeidssituasjon = ARBEIDSTAKER,
        arbeidsgiverOrgnummer = "123456789",
        arbeidsgiverNavn = "ARBEIDSGIVER A/S",
        sykmeldingId = "14e78e84-50a5-45bb-9919-191c54f99691",
        sykmeldingSkrevet = LocalDateTime.now().minusMonths(1).tilOsloInstant(),
        sykmeldingsperioder = listOf(
            SykmeldingsperiodeAGDTO(
                now().minusMonths(1),
                now().minusMonths(1).plusDays(4),
                gradert = GradertDTO(grad = 100, reisetilskudd = false),
                type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false,
            ),
            SykmeldingsperiodeAGDTO(
                now().minusMonths(1).plusDays(5),
                now().minusMonths(1).plusDays(8),
                gradert = GradertDTO(grad = 40, reisetilskudd = false),
                type = PeriodetypeDTO.GRADERT,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false,
            ),
        ).tilSoknadsperioder(),
        soknadstype = Soknadstype.ARBEIDSTAKERE,
        egenmeldtSykmelding = null

    )

    val sykepengesoknad = leggSvarPaSoknad(deprecatedGetSoknadMedFeriesporsmalSomUndersporsmal(soknadMetadata))
    return sykepengesoknad.copy(sendtNav = Instant.now(), sendtArbeidsgiver = Instant.now())
}

fun gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal(meta: SoknadMetadata): Sykepengesoknad {
    val sykepengesoknad = leggSvarPaSoknad(settOppSoknadArbeidstaker(meta, true, now()))
    return sykepengesoknad.copy(sendtNav = Instant.now(), sendtArbeidsgiver = Instant.now())
}

fun opprettSendtSoknad(): Sykepengesoknad {
    val soknadMetadata = SoknadMetadata(
        fnr = "fnr-7454630",
        status = SENDT,
        startSykeforlop = now().minusMonths(1),
        fom = now().minusMonths(1),
        tom = now().minusMonths(1).plusDays(8),
        arbeidssituasjon = ARBEIDSTAKER,
        arbeidsgiverOrgnummer = "123456789",
        arbeidsgiverNavn = "ARBEIDSGIVER A/S",
        sykmeldingId = "14e78e84-50a5-45bb-9919-191c54f99691",
        sykmeldingSkrevet = LocalDateTime.now().minusMonths(1).tilOsloInstant(),
        sykmeldingsperioder = listOf(
            SykmeldingsperiodeAGDTO(
                fom = now().minusMonths(1),
                tom = now().minusMonths(1).plusDays(4),
                gradert = GradertDTO(grad = 100, reisetilskudd = false),
                type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false,
            ),
            SykmeldingsperiodeAGDTO(
                fom = now().minusMonths(1).plusDays(5),
                tom = now().minusMonths(1).plusDays(8),
                gradert = GradertDTO(grad = 40, reisetilskudd = false),
                type = PeriodetypeDTO.GRADERT,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false,
            ),
        ).tilSoknadsperioder(),
        soknadstype = Soknadstype.ARBEIDSTAKERE,
        egenmeldtSykmelding = null

    )

    val sykepengesoknad = leggSvarPaSoknad(settOppSoknadArbeidstaker(soknadMetadata, true, now()))
    return sykepengesoknad.copy(sendtNav = Instant.now(), sendtArbeidsgiver = Instant.now())
}

@Deprecated("")
fun opprettNySoknadMock(feriesporsmalSomHovedsporsmal: Boolean = true): Sykepengesoknad {
    val soknadMetadata = SoknadMetadata(
        fnr = "fnr-7454630",
        status = NY,
        startSykeforlop = now().minusDays(24),
        fom = now().minusDays(19),
        tom = now().minusDays(10),
        arbeidssituasjon = ARBEIDSTAKER,
        arbeidsgiverOrgnummer = "123456789",
        arbeidsgiverNavn = "ARBEIDSGIVER A/S",
        sykmeldingId = "289148ba-4c3c-4b3f-b7a3-385b7e7c927d",
        sykmeldingSkrevet = LocalDateTime.now().minusDays(19).tilOsloInstant(),
        sykmeldingsperioder = listOf(
            SykmeldingsperiodeAGDTO(
                now().minusDays(19),
                now().minusDays(15),
                gradert = GradertDTO(grad = 100, reisetilskudd = false),
                type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false,
            ),
            SykmeldingsperiodeAGDTO(
                now().minusDays(14),
                now().minusDays(10),
                gradert = GradertDTO(grad = 40, reisetilskudd = false),
                type = PeriodetypeDTO.GRADERT,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false,
            ),
        ).tilSoknadsperioder(),
        soknadstype = Soknadstype.ARBEIDSTAKERE,
        egenmeldtSykmelding = null

    )

    val sykepengesoknad = if (feriesporsmalSomHovedsporsmal)
        settOppSoknadArbeidstaker(soknadMetadata, true, now())
    else
        deprecatedGetSoknadMedFeriesporsmalSomUndersporsmal(soknadMetadata)

    return leggSvarPaSoknad(sykepengesoknad)
}

fun gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal(): Sykepengesoknad {
    @Suppress("DEPRECATION")
    return opprettNySoknadMock(false)
}

private fun leggNeiSvarPaSoknad(sykepengesoknad: Sykepengesoknad): Sykepengesoknad {
    var s = sykepengesoknad
    s = s.replaceSporsmal(ansvarserklaring(s))
        .replaceSporsmal(svarNei(s, FRAVAR_FOR_SYKMELDINGEN))
        .replaceSporsmal(svarNei(s, TILBAKE_I_ARBEID))
        .replaceSporsmal(svarNei(s, JOBBET_DU_100_PROSENT + "0"))
        .replaceSporsmal(svarNei(s, ANDRE_INNTEKTSKILDER))
        .replaceSporsmal(svarNei(s, UTDANNING))
        .replaceSporsmal(bekreftOpplysninger(s))

    return if (harFeriePermisjonEllerUtenlandsoppholdSporsmal(s)) {
        s.replaceSporsmal(svarNei(s, FERIE_PERMISJON_UTLAND))
    } else {
        s.replaceSporsmal(svarNei(s, FERIE))
            .replaceSporsmal(svarNei(s, PERMISJON))
            .replaceSporsmal(svarNei(s, UTLAND))
    }
}

private fun svarNei(sykepengesoknad: Sykepengesoknad, tag: String): Sporsmal {
    return sykepengesoknad.getSporsmalMedTag(tag).toBuilder()
        .svar(listOf(Svar(null, "NEI", null)))
        .build()
}

fun leggSvarPaSoknad(sykepengesoknad: Sykepengesoknad): Sykepengesoknad {
    var s = sykepengesoknad
    s = s.replaceSporsmal(ansvarserklaring(s))
        .replaceSporsmal(fravarForSykmeldingen(s))
        .replaceSporsmal(tilbakeIFulltArbeid(s))
        .replaceSporsmal(jobbetDu100Prosent(s))
        .replaceSporsmal(jobbetDuGradert(s))
        .replaceSporsmal(andreInntektskilder(s))
        .replaceSporsmal(utdanning(s))
        .replaceSporsmal(bekreftOpplysninger(s))

    return if (harFeriePermisjonEllerUtenlandsoppholdSporsmal(s)) {
        s.replaceSporsmal(feriePermisjonUtland(s))
    } else {
        s.replaceSporsmal(ferie(s))
            .replaceSporsmal(permisjon(s))
            .replaceSporsmal(utenlandsopphold(s))
    }
}

private fun ansvarserklaring(sykepengesoknad: Sykepengesoknad): Sporsmal {
    return sykepengesoknad.getSporsmalMedTag(ANSVARSERKLARING).toBuilder()
        .svar(listOf(Svar(null, "CHECKED", null)))
        .build()
}

private fun fravarForSykmeldingen(sykepengesoknad: Sykepengesoknad): Sporsmal {

    return sykepengesoknad.getSporsmalMedTag(FRAVAR_FOR_SYKMELDINGEN).toBuilder()
        .svar(listOf(Svar(null, "JA", null)))
        .undersporsmal(
            listOf(
                sykepengesoknad.getSporsmalMedTag(FRAVAR_FOR_SYKMELDINGEN_NAR).toBuilder()
                    .svar(
                        listOf(
                            Svar(
                                null,
                                periodeTilJson(
                                    sykepengesoknad.fom!!.minusDays(4),
                                    sykepengesoknad.fom!!.plusDays(2)
                                ),
                                null
                            )
                        )
                    )
                    .build()
            )
        )
        .build()
}

private fun tilbakeIFulltArbeid(sykepengesoknad: Sykepengesoknad): Sporsmal {
    return sykepengesoknad.getSporsmalMedTag(TILBAKE_I_ARBEID).toBuilder()
        .svar(listOf(Svar(null, "JA", null)))
        .undersporsmal(
            listOf(
                sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).toBuilder()
                    .svar(listOf(Svar(null, sykepengesoknad.fom!!.plusDays(7).format(ISO_LOCAL_DATE), null)))
                    .build()
            )
        )
        .build()
}

private fun jobbetDu100Prosent(sykepengesoknad: Sykepengesoknad): Sporsmal {
    return sykepengesoknad.getSporsmalMedTag(JOBBET_DU_100_PROSENT + "0").toBuilder()
        .svar(listOf(Svar(null, "JA", null)))
        .undersporsmal(
            asList(
                sykepengesoknad.getSporsmalMedTag(HVOR_MANGE_TIMER_PER_UKE + "0").toBuilder()
                    .svar(listOf(Svar(null, "37,5", null)))
                    .build(),
                sykepengesoknad.getSporsmalMedTag(HVOR_MYE_HAR_DU_JOBBET + "0").toBuilder()
                    .undersporsmal(
                        asList(
                            sykepengesoknad.getSporsmalMedTag(HVOR_MYE_PROSENT + "0").toBuilder()
                                .undersporsmal(
                                    listOf(
                                        sykepengesoknad.getSporsmalMedTag(HVOR_MYE_PROSENT_VERDI + "0").toBuilder()
                                            .svar(listOf(Svar(null, "79", null)))
                                            .build()
                                    )
                                )
                                .build(),
                            sykepengesoknad.getSporsmalMedTag(HVOR_MYE_TIMER + "0").toBuilder().build()
                        )
                    )
                    .build()
            )
        )
        .build()
}

private fun jobbetDuGradert(sykepengesoknad: Sykepengesoknad): Sporsmal {
    return sykepengesoknad.getSporsmalMedTag(JOBBET_DU_GRADERT + "1").toBuilder()
        .svar(listOf(Svar(null, "JA", null)))
        .undersporsmal(
            asList(
                sykepengesoknad.getSporsmalMedTag(HVOR_MANGE_TIMER_PER_UKE + "1").toBuilder()
                    .svar(listOf(Svar(null, "37.5", null)))
                    .build(),
                sykepengesoknad.getSporsmalMedTag(HVOR_MYE_HAR_DU_JOBBET + "1").toBuilder()
                    .undersporsmal(
                        asList(
                            sykepengesoknad.getSporsmalMedTag(HVOR_MYE_PROSENT + "1").toBuilder()
                                .svar(emptyList())
                                .undersporsmal(listOf(sykepengesoknad.getSporsmalMedTag(HVOR_MYE_PROSENT_VERDI + "1")))
                                .build(),
                            sykepengesoknad.getSporsmalMedTag(HVOR_MYE_TIMER + "1").toBuilder()
                                .svar(listOf(Svar(null, "CHECKED", null)))
                                .undersporsmal(
                                    listOf(
                                        sykepengesoknad.getSporsmalMedTag(HVOR_MYE_TIMER_VERDI + "1").toBuilder()
                                            .svar(listOf(Svar(null, "66", null)))
                                            .build()
                                    )
                                )
                                .build()
                        )
                    )
                    .build()
            )
        )
        .build()
}

private fun feriePermisjonUtland(sykepengesoknad: Sykepengesoknad): Sporsmal {
    return sykepengesoknad.getSporsmalMedTag(FERIE_PERMISJON_UTLAND).toBuilder()
        .svar(listOf(Svar(null, "JA", null)))
        .undersporsmal(
            listOf(
                sykepengesoknad.getSporsmalMedTag(FERIE_PERMISJON_UTLAND_HVA).toBuilder()
                    .undersporsmal(
                        asList(
                            sykepengesoknad.getSporsmalMedTag(FERIE).toBuilder()
                                .svar(listOf(Svar(null, "CHECKED", null)))
                                .undersporsmal(
                                    listOf(
                                        sykepengesoknad.getSporsmalMedTag(FERIE_NAR).toBuilder()
                                            .svar(
                                                listOf(
                                                    Svar(
                                                        null,
                                                        periodeTilJson(
                                                            sykepengesoknad.fom!!.plusDays(2),
                                                            sykepengesoknad.fom!!.plusDays(4)
                                                        ),
                                                        null
                                                    )
                                                )
                                            )
                                            .build()
                                    )
                                )
                                .build(),
                            sykepengesoknad.getSporsmalMedTag(PERMISJON),
                            sykepengesoknad.getSporsmalMedTag(UTLAND).toBuilder()
                                .svar(listOf(Svar(null, "CHECKED", null)))
                                .undersporsmal(
                                    listOf(
                                        sykepengesoknad.getSporsmalMedTag(UTLAND_NAR).toBuilder()
                                            .svar(
                                                asList(
                                                    Svar(
                                                        null,
                                                        periodeTilJson(
                                                            sykepengesoknad.fom!!.plusDays(1),
                                                            sykepengesoknad.fom!!.plusDays(2)
                                                        ),
                                                        null
                                                    ),
                                                    Svar(
                                                        null,
                                                        periodeTilJson(
                                                            sykepengesoknad.fom!!.plusDays(4),
                                                            sykepengesoknad.fom!!.plusDays(6)
                                                        ),
                                                        null
                                                    )
                                                )
                                            )
                                            .build()
                                    )
                                )
                                .build()
                        )
                    )
                    .build()
            )
        )
        .build()
}

private fun ferie(sykepengesoknad: Sykepengesoknad): Sporsmal {
    return sykepengesoknad.getSporsmalMedTag(FERIE_V2).toBuilder()
        .svar(listOf(Svar(null, "JA", null)))
        .undersporsmal(
            listOf(
                sykepengesoknad.getSporsmalMedTag(FERIE_NAR_V2).toBuilder()
                    .svar(
                        listOf(
                            Svar(
                                null,
                                periodeTilJson(sykepengesoknad.fom!!.plusDays(1), sykepengesoknad.fom!!.plusDays(2)),
                                null
                            )
                        )
                    )
                    .build()
            )
        )
        .build()
}

private fun permisjon(sykepengesoknad: Sykepengesoknad): Sporsmal {
    return sykepengesoknad.getSporsmalMedTag(PERMISJON_V2).toBuilder()
        .svar(listOf(Svar(null, "NEI", null)))
        .build()
}

private fun utenlandsopphold(sykepengesoknad: Sykepengesoknad): Sporsmal {
    return sykepengesoknad.getSporsmalMedTag(UTLAND_V2).toBuilder()
        .svar(listOf(Svar(null, "JA", null)))
        .undersporsmal(
            listOf(
                sykepengesoknad.getSporsmalMedTag(UTLAND_NAR_V2).toBuilder()
                    .svar(
                        asList(
                            Svar(
                                null,
                                periodeTilJson(sykepengesoknad.fom!!.plusDays(1), sykepengesoknad.fom!!.plusDays(1)),
                                null
                            ),
                            Svar(
                                null,
                                periodeTilJson(sykepengesoknad.fom!!.plusDays(4), sykepengesoknad.fom!!.plusDays(6)),
                                null
                            )
                        )
                    )
                    .build()
            )
        )
        .build()
}

private fun andreInntektskilder(sykepengesoknad: Sykepengesoknad): Sporsmal {
    return sykepengesoknad.getSporsmalMedTag(ANDRE_INNTEKTSKILDER).toBuilder()
        .svar(listOf(Svar(null, "JA", null)))
        .undersporsmal(
            listOf(
                sykepengesoknad.getSporsmalMedTag(HVILKE_ANDRE_INNTEKTSKILDER).toBuilder()
                    .undersporsmal(
                        asList(
                            sykepengesoknad.getSporsmalMedTag(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD).toBuilder()
                                .svar(listOf(Svar(null, "CHECKED", null)))
                                .undersporsmal(
                                    listOf(
                                        sykepengesoknad.getSporsmalMedTag(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD + ER_DU_SYKMELDT)
                                            .toBuilder()
                                            .svar(listOf(Svar(null, "JA", null)))
                                            .build()
                                    )
                                )
                                .build(),
                            sykepengesoknad.getSporsmalMedTag(INNTEKTSKILDE_SELVSTENDIG).toBuilder()
                                .svar(listOf(Svar(null, "CHECKED", null)))
                                .undersporsmal(
                                    listOf(
                                        sykepengesoknad.getSporsmalMedTag(INNTEKTSKILDE_SELVSTENDIG + ER_DU_SYKMELDT)
                                            .toBuilder()
                                            .svar(listOf(Svar(null, "JA", null)))
                                            .build()
                                    )
                                )
                                .build(),
                            sykepengesoknad.getSporsmalMedTag(INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA).toBuilder()
                                .svar(listOf(Svar(null, "CHECKED", null)))
                                .undersporsmal(
                                    listOf(
                                        sykepengesoknad.getSporsmalMedTag(INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA + ER_DU_SYKMELDT)
                                            .toBuilder()
                                            .svar(listOf(Svar(null, "NEI", null)))
                                            .build()
                                    )
                                )
                                .build(),
                            sykepengesoknad.getSporsmalMedTag(INNTEKTSKILDE_JORDBRUKER),
                            sykepengesoknad.getSporsmalMedTag(INNTEKTSKILDE_FRILANSER),
                            sykepengesoknad.getSporsmalMedTag(INNTEKTSKILDE_ANNET)
                        )
                    )
                    .build()
            )
        )
        .build()
}

private fun utdanning(sykepengesoknad: Sykepengesoknad): Sporsmal {
    return sykepengesoknad.getSporsmalMedTag(UTDANNING).toBuilder()
        .svar(listOf(Svar(null, "JA", null)))
        .undersporsmal(
            asList(
                sykepengesoknad.getSporsmalMedTag(UTDANNING_START).toBuilder()
                    .svar(listOf(Svar(null, sykepengesoknad.fom!!.plusDays(3).format(ISO_LOCAL_DATE), null)))
                    .build(),
                sykepengesoknad.getSporsmalMedTag(FULLTIDSSTUDIUM).toBuilder()
                    .svar(listOf(Svar(null, "NEI", null)))
                    .build()
            )
        )
        .build()
}

private fun bekreftOpplysninger(sykepengesoknad: Sykepengesoknad): Sporsmal {
    return sykepengesoknad.getSporsmalMedTag(BEKREFT_OPPLYSNINGER).toBuilder()
        .svar(listOf(Svar(null, "CHECKED", null)))
        .build()
}
