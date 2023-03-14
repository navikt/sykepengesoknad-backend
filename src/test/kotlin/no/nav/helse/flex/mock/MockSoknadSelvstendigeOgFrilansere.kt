package no.nav.helse.flex.mock

import no.nav.helse.flex.domain.Arbeidssituasjon.FRILANSER
import no.nav.helse.flex.domain.Arbeidssituasjon.NAERINGSDRIVENDE
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.testutil.besvarsporsmal
import no.nav.helse.flex.util.DatoUtil.periodeTilJson
import no.nav.helse.flex.util.tilOsloInstant
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate.of
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import java.util.*

@Component
class MockSoknadSelvstendigeOgFrilansere(private val sykepengesoknadDAO: SykepengesoknadDAO?) {

    fun opprettOgLagreNySoknad(): Sykepengesoknad {
        return sykepengesoknadDAO!!.lagreSykepengesoknad(opprettNyNaeringsdrivendeSoknad())
    }
}

fun opprettNyNaeringsdrivendeSoknad(): Sykepengesoknad {
    val soknadMetadata = Sykepengesoknad(
        id = UUID.randomUUID().toString(),
        status = Soknadstatus.NY,
        opprettet = Instant.now(),
        sporsmal = emptyList(),
        fnr = "fnr-7454630",
        startSykeforlop = of(2018, 6, 1),
        fom = of(2018, 6, 1),
        tom = of(2018, 6, 10),
        arbeidssituasjon = NAERINGSDRIVENDE, arbeidsgiverOrgnummer = null, arbeidsgiverNavn = null,
        sykmeldingId = "289148ba-4c3c-4b3f-b7a3-385b7e7c927d",
        sykmeldingSkrevet = of(2018, 6, 1).atStartOfDay().tilOsloInstant(),
        soknadPerioder = listOf(
            SykmeldingsperiodeAGDTO(
                fom = of(2018, 6, 1),
                tom = of(2018, 6, 5),
                gradert = GradertDTO(grad = 100, reisetilskudd = false),
                type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false
            ),
            SykmeldingsperiodeAGDTO(
                fom = of(2018, 6, 6),
                tom = of(2018, 6, 10),
                gradert = GradertDTO(grad = 40, reisetilskudd = false),
                type = PeriodetypeDTO.GRADERT,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false
            )
        ).tilSoknadsperioder(),
        soknadstype = Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
        egenmeldtSykmelding = null,
        utenlandskSykmelding = false
    )

    return (soknadMetadata).copy(
        sporsmal = settOppSoknadSelvstendigOgFrilanser(soknadMetadata, false, false, false),
        status = Soknadstatus.NY
    ).leggSvarPaSoknad()
}

fun opprettSendtFrilanserSoknad(): Sykepengesoknad {
    val soknadMetadata = Sykepengesoknad(
        id = UUID.randomUUID().toString(),
        status = Soknadstatus.NY,
        opprettet = Instant.now(),
        sporsmal = emptyList(),
        fnr = "fnr-7454630",
        startSykeforlop = of(2018, 5, 20),
        fom = of(2018, 5, 20),
        tom = of(2018, 5, 28),
        arbeidssituasjon = FRILANSER, arbeidsgiverOrgnummer = null, arbeidsgiverNavn = null,
        sykmeldingId = "14e78e84-50a5-45bb-9919-191c54f99691",
        sykmeldingSkrevet = of(2018, 5, 20).atStartOfDay().tilOsloInstant(),
        soknadPerioder = listOf(
            SykmeldingsperiodeAGDTO(
                fom = of(2018, 5, 20),
                tom = of(2018, 5, 24),
                gradert = GradertDTO(grad = 100, reisetilskudd = false),
                type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false
            ),
            SykmeldingsperiodeAGDTO(
                fom = of(2018, 5, 25),
                tom = of(2018, 5, 28),
                gradert = GradertDTO(grad = 40, reisetilskudd = false),
                type = PeriodetypeDTO.GRADERT,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false
            )
        ).tilSoknadsperioder(),
        soknadstype = Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
        egenmeldtSykmelding = null,
        utenlandskSykmelding = false

    )
    return (soknadMetadata).copy(
        sporsmal = settOppSoknadSelvstendigOgFrilanser(soknadMetadata, false, false, false),
        status = Soknadstatus.SENDT,
        sendtNav = Instant.now()
    ).leggSvarPaSoknad()
}

private fun Sykepengesoknad.leggSvarPaSoknad(): Sykepengesoknad {
    return this
        .besvarsporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
        .besvarsporsmal(ARBEID_UNDERVEIS_100_PROSENT + "0", "NEI")
        .besvarsporsmal(JOBBET_DU_GRADERT + "1", "NEI")
        .harDuOppholdtDegIUtlandet()
        .andreInntektskilder()
        .tilbakeIArbeid()
        .besvarsporsmal(ANSVARSERKLARING, "CHECKED")
}

private fun Sykepengesoknad.harDuOppholdtDegIUtlandet(): Sykepengesoknad {
    return besvarsporsmal(UTLAND, "JA")
        .besvarsporsmal(PERIODER, periodeTilJson(fom!!.plusDays(2), fom!!.plusDays(4)))
        .besvarsporsmal(UTLANDSOPPHOLD_SOKT_SYKEPENGER, "NEI")
}

private fun Sykepengesoknad.andreInntektskilder(): Sykepengesoknad {
    return besvarsporsmal(ANDRE_INNTEKTSKILDER, "JA")
        .besvarsporsmal(INNTEKTSKILDE_ARBEIDSFORHOLD, "CHECKED")
        .besvarsporsmal(INNTEKTSKILDE_ARBEIDSFORHOLD + ER_DU_SYKMELDT, "NEI")
        .besvarsporsmal(INNTEKTSKILDE_JORDBRUKER, "CHECKED")
        .besvarsporsmal(INNTEKTSKILDE_JORDBRUKER + ER_DU_SYKMELDT, "JA")
        .besvarsporsmal(INNTEKTSKILDE_FRILANSER_SELVSTENDIG, "CHECKED")
        .besvarsporsmal(INNTEKTSKILDE_FRILANSER_SELVSTENDIG + ER_DU_SYKMELDT, "JA")
        .besvarsporsmal(INNTEKTSKILDE_ANNET, "CHECKED")
}

private fun Sykepengesoknad.tilbakeIArbeid(): Sykepengesoknad {
    return besvarsporsmal(TILBAKE_I_ARBEID, "JA")
        .besvarsporsmal(TILBAKE_NAR, fom!!.plusDays(7).format(ISO_LOCAL_DATE))
}
