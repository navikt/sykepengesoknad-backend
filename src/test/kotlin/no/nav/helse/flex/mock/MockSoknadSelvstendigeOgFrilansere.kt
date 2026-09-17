package no.nav.helse.flex.mock

import no.nav.helse.flex.domain.Arbeidssituasjon.FRILANSER
import no.nav.helse.flex.domain.Arbeidssituasjon.NAERINGSDRIVENDE
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.aaregdata.ArbeidsforholdFraAAreg
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.medIndex
import no.nav.helse.flex.testutil.besvarsporsmal
import no.nav.helse.flex.util.oppsummering
import no.nav.helse.flex.util.periodeTilJson
import no.nav.helse.flex.util.tilOsloInstant
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDate.of
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import java.util.*

@Component
class MockSoknadSelvstendigeOgFrilansere(
    private val sykepengesoknadDAO: SykepengesoknadDAO?,
) {
    fun opprettOgLagreNySoknad(): Sykepengesoknad = sykepengesoknadDAO!!.lagreSykepengesoknad(opprettNyNaeringsdrivendeSoknadGradert())
}

fun opprettNyNaeringsdrivendeSoknadGradert(): Sykepengesoknad {
    val soknadMetadata =
        Sykepengesoknad(
            id = UUID.randomUUID().toString(),
            status = Soknadstatus.NY,
            opprettet = Instant.now(),
            sporsmal = emptyList(),
            fnr = "fnr-7454630",
            startSykeforlop = of(2018, 6, 1),
            fom = of(2018, 6, 1),
            tom = of(2018, 6, 10),
            arbeidssituasjon = NAERINGSDRIVENDE,
            arbeidsgiverOrgnummer = null,
            arbeidsgiverNavn = null,
            sykmeldingId = "289148ba-4c3c-4b3f-b7a3-385b7e7c927d",
            sykmeldingSkrevet = of(2018, 6, 1).atStartOfDay().tilOsloInstant(),
            soknadPerioder =
                listOf(
                    SykmeldingsperiodeAGDTO(
                        fom = of(2018, 6, 1),
                        tom = of(2018, 6, 5),
                        gradert = GradertDTO(grad = 100, reisetilskudd = false),
                        type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                        aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                        behandlingsdager = null,
                        innspillTilArbeidsgiver = null,
                        reisetilskudd = false,
                    ),
                    SykmeldingsperiodeAGDTO(
                        fom = of(2018, 6, 6),
                        tom = of(2018, 6, 10),
                        gradert = GradertDTO(grad = 40, reisetilskudd = false),
                        type = PeriodetypeDTO.GRADERT,
                        aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                        behandlingsdager = null,
                        innspillTilArbeidsgiver = null,
                        reisetilskudd = false,
                    ),
                ).tilSoknadsperioder(),
            soknadstype = Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
            egenmeldtSykmelding = null,
            utenlandskSykmelding = false,
            egenmeldingsdagerFraSykmelding = null,
            forstegangssoknad = false,
            meldingTilNavDagerFraSykmelding = listOf(Periode(of(2021, 2, 1), of(2021, 2, 15))),
        )

    return (soknadMetadata)
        .copy(
            sporsmal =
                settOppSoknadSelvstendigOgFrilanser(
                    sykepengesoknad = soknadMetadata,
                    harTidligereUtenlandskSpm = false,
                    erForsteSoknadISykeforlop = true,
                    andreKjenteArbeidsforholdFraInntektskomponenten = arbeidsforholdFraInntektskomponentens(),
                    arbeidsforholdoversiktResponse = arbeidsforholdFraAAregs(),
                ),
            status = Soknadstatus.NY,
        ).leggSvarPaSoknad()
        .besvarsporsmal(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT_GRADERT, "NEI")
        .besvarsporsmal(NARINGSDRIVENDE_OPPHOLD_I_UTLANDET, "NEI")
        .besvarsporsmal(FRAVAR_FOR_SYKMELDINGEN_V2, "JA")
}

fun opprettNyNaeringsdrivendeSoknad100Prosent(): Sykepengesoknad {
    val soknadMetadata =
        Sykepengesoknad(
            id = UUID.randomUUID().toString(),
            status = Soknadstatus.NY,
            opprettet = Instant.now(),
            sporsmal = emptyList(),
            fnr = "fnr-7454630",
            startSykeforlop = of(2018, 6, 1),
            fom = of(2018, 6, 1),
            tom = of(2018, 6, 10),
            arbeidssituasjon = NAERINGSDRIVENDE,
            arbeidsgiverOrgnummer = null,
            arbeidsgiverNavn = null,
            sykmeldingId = "289148ba-4c3c-4b3f-b7a3-385b7e7c927d",
            sykmeldingSkrevet = of(2018, 6, 1).atStartOfDay().tilOsloInstant(),
            soknadPerioder =
                listOf(
                    SykmeldingsperiodeAGDTO(
                        fom = of(2018, 6, 1),
                        tom = of(2018, 6, 5),
                        gradert = GradertDTO(grad = 100, reisetilskudd = false),
                        type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                        aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                        behandlingsdager = null,
                        innspillTilArbeidsgiver = null,
                        reisetilskudd = false,
                    ),
                    SykmeldingsperiodeAGDTO(
                        fom = of(2018, 6, 6),
                        tom = of(2018, 6, 10),
                        gradert = GradertDTO(grad = 100, reisetilskudd = false),
                        type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                        aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                        behandlingsdager = null,
                        innspillTilArbeidsgiver = null,
                        reisetilskudd = false,
                    ),
                ).tilSoknadsperioder(),
            soknadstype = Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
            egenmeldtSykmelding = null,
            utenlandskSykmelding = false,
            egenmeldingsdagerFraSykmelding = null,
            forstegangssoknad = false,
        )

    return (soknadMetadata)
        .copy(
            sporsmal =
                settOppSoknadSelvstendigOgFrilanser(
                    sykepengesoknad = soknadMetadata,
                    harTidligereUtenlandskSpm = false,
                    erForsteSoknadISykeforlop = true,
                    andreKjenteArbeidsforholdFraInntektskomponenten = arbeidsforholdFraInntektskomponentens(),
                    arbeidsforholdoversiktResponse = arbeidsforholdFraAAregs(),
                ),
            status = Soknadstatus.NY,
        ).leggSvarPaSoknad100Prosent()
        .besvarsporsmal(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT, "NEI")
        .besvarsporsmal(NARINGSDRIVENDE_OPPHOLD_I_UTLANDET, "NEI")
        .besvarsporsmal(FRAVAR_FOR_SYKMELDINGEN_V2, "JA")
}

fun opprettSendtFrilanserSoknad(): Sykepengesoknad {
    val soknadMetadata =
        Sykepengesoknad(
            id = UUID.randomUUID().toString(),
            status = Soknadstatus.NY,
            opprettet = Instant.now(),
            sporsmal = emptyList(),
            fnr = "fnr-7454630",
            startSykeforlop = of(2018, 5, 20),
            fom = of(2018, 5, 20),
            tom = of(2018, 5, 28),
            arbeidssituasjon = FRILANSER,
            arbeidsgiverOrgnummer = null,
            arbeidsgiverNavn = null,
            sykmeldingId = "14e78e84-50a5-45bb-9919-191c54f99691",
            sykmeldingSkrevet = of(2018, 5, 20).atStartOfDay().tilOsloInstant(),
            soknadPerioder =
                listOf(
                    SykmeldingsperiodeAGDTO(
                        fom = of(2018, 5, 20),
                        tom = of(2018, 5, 24),
                        gradert = GradertDTO(grad = 100, reisetilskudd = false),
                        type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                        aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                        behandlingsdager = null,
                        innspillTilArbeidsgiver = null,
                        reisetilskudd = false,
                    ),
                    SykmeldingsperiodeAGDTO(
                        fom = of(2018, 5, 25),
                        tom = of(2018, 5, 28),
                        gradert = GradertDTO(grad = 40, reisetilskudd = false),
                        type = PeriodetypeDTO.GRADERT,
                        aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                        behandlingsdager = null,
                        innspillTilArbeidsgiver = null,
                        reisetilskudd = false,
                    ),
                ).tilSoknadsperioder(),
            soknadstype = Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
            egenmeldtSykmelding = null,
            utenlandskSykmelding = false,
            egenmeldingsdagerFraSykmelding = null,
            forstegangssoknad = false,
        )
    return (soknadMetadata)
        .copy(
            sporsmal =
                settOppSoknadSelvstendigOgFrilanser(
                    sykepengesoknad = soknadMetadata,
                    harTidligereUtenlandskSpm = false,
                    erForsteSoknadISykeforlop = false,
                    andreKjenteArbeidsforholdFraInntektskomponenten = arbeidsforholdFraInntektskomponentens(),
                    arbeidsforholdoversiktResponse = arbeidsforholdFraAAregs(),
                ),
            status = Soknadstatus.SENDT,
            sendtNav = Instant.now(),
        ).leggSvarPaSoknad()
}

private fun arbeidsforholdFraAAregs(): List<ArbeidsforholdFraAAreg> =
    listOf(
        ArbeidsforholdFraAAreg(
            arbeidsstedNavn = "Brannvesenet",
            arbeidsstedOrgnummer = "222",
            startdato = LocalDate.now(),
            opplysningspliktigOrgnummer = "333",
            sluttdato = null,
        ),
        ArbeidsforholdFraAAreg(
            arbeidsstedNavn = "Sykebilen",
            arbeidsstedOrgnummer = "876",
            startdato = LocalDate.now(),
            opplysningspliktigOrgnummer = "232",
            sluttdato = null,
        ),
    )

private fun arbeidsforholdFraInntektskomponentens(): List<ArbeidsforholdFraInntektskomponenten> =
    listOf(
        ArbeidsforholdFraInntektskomponenten(
            navn = "Brannvesenet",
            orgnummer = "222",
            arbeidsforholdstype = Arbeidsforholdstype.ARBEIDSTAKER,
        ),
    )

private fun Sykepengesoknad.leggSvarPaSoknad(): Sykepengesoknad =
    this
        .oppsummering()
        .besvarsporsmal(ARBEID_UNDERVEIS_100_PROSENT + "0", "NEI")
        .besvarsporsmal(JOBBET_DU_GRADERT + "1", "NEI")
        .harDuOppholdtDegIUtlandet()
        .flereInntektskilderGhost()
        .tilbakeIArbeid()
        .besvarsporsmal(ANSVARSERKLARING, "CHECKED")

private fun Sykepengesoknad.leggSvarPaSoknad100Prosent(): Sykepengesoknad =
    this
        .oppsummering()
        .besvarsporsmal(ARBEID_UNDERVEIS_100_PROSENT + "0", "NEI")
        .harDuOppholdtDegIUtlandet()
        .flereInntektskilderGhost()
        .tilbakeIArbeid()
        .besvarsporsmal(ANSVARSERKLARING, "CHECKED")

private fun Sykepengesoknad.harDuOppholdtDegIUtlandet(): Sykepengesoknad =
    besvarsporsmal(OPPHOLD_UTENFOR_EOS, "JA")
        .besvarsporsmal(OPPHOLD_UTENFOR_EOS_NAR, periodeTilJson(fom!!.plusDays(2), fom.plusDays(4)))

private fun Sykepengesoknad.flereInntektskilderGhost(): Sykepengesoknad =
    besvarsporsmal(FLERE_INNTEKTSKILDER_GHOST, "JA")
        .besvarsporsmal(medIndex(JOBBET_MER_I_VALG, 0), svar = "CHECKED")
        .andreInntektskilder()

private fun Sykepengesoknad.andreInntektskilder(): Sykepengesoknad =
    besvarsporsmal(ANDRE_INNTEKTSKILDER_V2, "JA")
        .besvarsporsmal(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD, "CHECKED")
        .besvarsporsmal(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD_JOBBET_I_DET_SISTE, "JA")
        .besvarsporsmal(INNTEKTSKILDE_SELVSTENDIG, "CHECKED")
        .besvarsporsmal(INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA, "CHECKED")
        .besvarsporsmal(INNTEKTSKILDE_STYREVERV, "CHECKED")

private fun Sykepengesoknad.tilbakeIArbeid(): Sykepengesoknad =
    besvarsporsmal(TILBAKE_I_ARBEID, "JA")
        .besvarsporsmal(TILBAKE_NAR, fom!!.plusDays(7).format(ISO_LOCAL_DATE))
