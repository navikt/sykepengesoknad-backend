package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.rest.SoknadMetadata
import no.nav.helse.flex.soknadsopprettelse.tilSoknadsperioder
import no.nav.helse.flex.util.tilOsloInstant
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class BehandlingsdagerKtTest {

    val testSoknad = SoknadMetadata(
        fnr = "fnr-7454630",
        status = Soknadstatus.SENDT,
        startSykeforlop = LocalDate.of(2019, 12, 5).minusMonths(1),
        fom = LocalDate.of(2019, 12, 5).minusMonths(1),
        tom = LocalDate.of(2019, 12, 5).minusMonths(1).plusDays(8),
        arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
        soknadstype = Soknadstype.BEHANDLINGSDAGER,
        arbeidsgiverOrgnummer = "123456789",
        arbeidsgiverNavn = "ARBEIDSGIVER A/S",
        sykmeldingId = "sykmeldingId",
        sykmeldingSkrevet = LocalDateTime.now().minusMonths(1).tilOsloInstant(),
        sykmeldingsperioder = listOf(
            SykmeldingsperiodeAGDTO(
                LocalDate.of(2019, 12, 5).minusMonths(1),
                LocalDate.of(2019, 12, 5).minusMonths(1).plusDays(4),
                gradert = GradertDTO(grad = 100, reisetilskudd = false),
                type = PeriodetypeDTO.BEHANDLINGSDAGER,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false,
            ),
        ).tilSoknadsperioder(),
        egenmeldtSykmelding = null
    )

    @Test
    fun `oppretter sp??rsm??l`() {

        val sporsmalList = behandlingsdagerSporsmal(testSoknad)
        sporsmalList.shouldHaveSize(1)

        sporsmalList[0] `should be equal to` Sporsmal(
            id = null,
            tag = "ENKELTSTAENDE_BEHANDLINGSDAGER_0",
            sporsmalstekst = "Hvilke dager m??tte du v??re helt borte fra jobben p?? grunn av behandling mellom 5. - 9. november 2019?",
            undertekst = null,
            svartype = Svartype.INFO_BEHANDLINGSDAGER,
            min = null,
            max = null,
            pavirkerAndreSporsmal = false,
            kriterieForVisningAvUndersporsmal = null,
            undersporsmal = listOf(
                Sporsmal(
                    id = null,
                    tag = "ENKELTSTAENDE_BEHANDLINGSDAGER_UKE_0",
                    sporsmalstekst = "05.11.2019 - 08.11.2019",
                    undertekst = null,
                    svartype = Svartype.RADIO_GRUPPE_UKEKALENDER,
                    min = "2019-11-05",
                    max = "2019-11-08",
                    pavirkerAndreSporsmal = false,
                    kriterieForVisningAvUndersporsmal = null
                )
            )
        )
    }

    @Test
    fun `oppretter sp??rsm??l for arbeidsledige`() {

        val sporsmalList = behandlingsdagerSporsmal(testSoknad.copy(arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG))
        sporsmalList.shouldHaveSize(1)
        sporsmalList[0] `should be equal to` Sporsmal(
            id = null,
            tag = "ENKELTSTAENDE_BEHANDLINGSDAGER_0",
            sporsmalstekst = "Hvilke dager kunne du ikke v??re arbeidss??ker p?? grunn av behandling mellom 5. - 9. november 2019?",
            undertekst = null,
            svartype = Svartype.INFO_BEHANDLINGSDAGER,
            min = null,
            max = null,
            pavirkerAndreSporsmal = false,
            kriterieForVisningAvUndersporsmal = null,
            undersporsmal = listOf(
                Sporsmal(
                    id = null,
                    tag = "ENKELTSTAENDE_BEHANDLINGSDAGER_UKE_0",
                    sporsmalstekst = "05.11.2019 - 08.11.2019",
                    undertekst = null,
                    svartype = Svartype.RADIO_GRUPPE_UKEKALENDER,
                    min = "2019-11-05",
                    max = "2019-11-08",
                    pavirkerAndreSporsmal = false,
                    kriterieForVisningAvUndersporsmal = null
                )
            )

        )
    }

    @Test
    fun `oppretter sp??rsm??l n??r det er flere perioder i soknaden`() {

        val soknadMedFlerePerioder = testSoknad
            .copy(
                sykmeldingsperioder = listOf(
                    SykmeldingsperiodeAGDTO(
                        LocalDate.of(2019, 12, 5).minusMonths(1),
                        LocalDate.of(2019, 12, 5).minusMonths(1).plusDays(4),
                        gradert = GradertDTO(grad = 100, reisetilskudd = false),
                        type = PeriodetypeDTO.BEHANDLINGSDAGER,
                        aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                        behandlingsdager = null,
                        innspillTilArbeidsgiver = null,
                        reisetilskudd = false,
                    ),
                    SykmeldingsperiodeAGDTO(
                        LocalDate.of(2019, 12, 5).minusMonths(1).plusDays(5),
                        LocalDate.of(2019, 12, 5).minusMonths(1).plusDays(30),
                        gradert = GradertDTO(grad = 100, reisetilskudd = false),
                        type = PeriodetypeDTO.BEHANDLINGSDAGER,
                        aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                        behandlingsdager = null,
                        innspillTilArbeidsgiver = null,
                        reisetilskudd = false,
                    ),
                ).tilSoknadsperioder()
            )
        val sporsmalList = behandlingsdagerSporsmal(soknadMedFlerePerioder)
        assertEquals(sporsmalList.size, 2)
        assertEquals(
            sporsmalList[0].sporsmalstekst,
            "Hvilke dager m??tte du v??re helt borte fra jobben p?? grunn av behandling mellom 5. - 9. november 2019?"
        )
        assertEquals(
            sporsmalList[1].sporsmalstekst,
            "Hvilke dager m??tte du v??re helt borte fra jobben p?? grunn av behandling mellom 10. november - 5. desember 2019?"
        )
    }

    @Test
    fun `oppretter sp??rsm??l n??r det starter p?? en s??ndag`() {
        val soknad = testSoknad
            .copy(
                sykmeldingsperioder = listOf(
                    SykmeldingsperiodeAGDTO(
                        LocalDate.of(2019, 12, 1),
                        LocalDate.of(2020, 1, 1),
                        gradert = GradertDTO(grad = 100, reisetilskudd = false),
                        type = PeriodetypeDTO.BEHANDLINGSDAGER,
                        aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                        behandlingsdager = null,
                        innspillTilArbeidsgiver = null,
                        reisetilskudd = false,
                    ),
                ).tilSoknadsperioder(),
            )
        val sporsmalList = behandlingsdagerSporsmal(soknad)
        assertEquals(sporsmalList.size, 1)
        assertEquals(
            sporsmalList[0].sporsmalstekst,
            "Hvilke dager m??tte du v??re helt borte fra jobben p?? grunn av behandling mellom 1. desember 2019 - 1. januar 2020?"
        )
        assertEquals(sporsmalList[0].undersporsmal[0].min, "2019-12-02")
        assertEquals(sporsmalList[0].undersporsmal[0].max, "2019-12-06")
        assertEquals(sporsmalList[0].undersporsmal[1].min, "2019-12-09")
        assertEquals(sporsmalList[0].undersporsmal[1].max, "2019-12-13")
        assertEquals(sporsmalList[0].undersporsmal[2].min, "2019-12-16")
        assertEquals(sporsmalList[0].undersporsmal[2].max, "2019-12-20")
        assertEquals(sporsmalList[0].undersporsmal[3].min, "2019-12-23")
        assertEquals(sporsmalList[0].undersporsmal[3].max, "2019-12-27")
        assertEquals(sporsmalList[0].undersporsmal[4].min, "2019-12-30")
        assertEquals(sporsmalList[0].undersporsmal[4].max, "2020-01-01")
    }

    @Test
    fun `test oppdeling i uker n??r vi starter p?? en s??ndag`() {
        val periode = SykmeldingsperiodeAGDTO(
            fom = LocalDate.of(2019, 12, 1),
            tom = LocalDate.of(2020, 1, 1),
            gradert = GradertDTO(grad = 100, reisetilskudd = false),
            type = PeriodetypeDTO.BEHANDLINGSDAGER,
            aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
            behandlingsdager = null,
            innspillTilArbeidsgiver = null,
            reisetilskudd = false,
        ).tilSoknadsperioder()
        val res = splittPeriodeIUker(periode)

        assertEquals(res[0].ukestart, LocalDate.of(2019, 12, 2))
        assertEquals(res[0].ukeslutt, LocalDate.of(2019, 12, 6))
        assertEquals(res[1].ukestart, LocalDate.of(2019, 12, 9))
        assertEquals(res[1].ukeslutt, LocalDate.of(2019, 12, 13))
        assertEquals(res[4].ukestart, LocalDate.of(2019, 12, 30))
        assertEquals(res[4].ukeslutt, LocalDate.of(2020, 1, 1))
    }

    @Test
    fun `test oppdeling i uker n??r vi slutter p?? en s??ndag`() {
        val periode = SykmeldingsperiodeAGDTO(
            fom = LocalDate.of(2019, 12, 2),
            tom = LocalDate.of(2019, 12, 8),
            gradert = GradertDTO(grad = 100, reisetilskudd = false),
            type = PeriodetypeDTO.BEHANDLINGSDAGER,
            aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
            behandlingsdager = null,
            innspillTilArbeidsgiver = null,
            reisetilskudd = false,
        ).tilSoknadsperioder()
        val res = splittPeriodeIUker(periode)

        assertEquals(res[0].ukestart, LocalDate.of(2019, 12, 2))
        assertEquals(res[0].ukeslutt, LocalDate.of(2019, 12, 6))
    }

    @Test
    fun `test oppdeling i uker n??r vi slutter p?? en mandag`() {
        val periode = SykmeldingsperiodeAGDTO(
            fom = LocalDate.of(2019, 12, 2),
            tom = LocalDate.of(2019, 12, 9),
            gradert = GradertDTO(grad = 100, reisetilskudd = false),
            type = PeriodetypeDTO.BEHANDLINGSDAGER,
            aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
            behandlingsdager = null,
            innspillTilArbeidsgiver = null,
            reisetilskudd = false,
        ).tilSoknadsperioder()
        val res = splittPeriodeIUker(periode)

        assertEquals(res[0].ukestart, LocalDate.of(2019, 12, 2))
        assertEquals(res[0].ukeslutt, LocalDate.of(2019, 12, 6))
        assertEquals(res[1].ukestart, LocalDate.of(2019, 12, 9))
        assertEquals(res[1].ukeslutt, LocalDate.of(2019, 12, 9))
    }

    @Test
    fun `test oppdeling i uker n??r hele greia er midt i en uke`() {
        val periode = SykmeldingsperiodeAGDTO(
            fom = LocalDate.of(2019, 12, 3),
            tom = LocalDate.of(2019, 12, 4),
            gradert = GradertDTO(grad = 100, reisetilskudd = false),
            type = PeriodetypeDTO.BEHANDLINGSDAGER,
            aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
            behandlingsdager = null,
            innspillTilArbeidsgiver = null,
            reisetilskudd = false,
        ).tilSoknadsperioder()
        val res = splittPeriodeIUker(periode)

        assertEquals(res[0].ukestart, LocalDate.of(2019, 12, 3))
        assertEquals(res[0].ukeslutt, LocalDate.of(2019, 12, 4))
    }

    @Test
    fun `test oppdeling i uker n??r hele greia er  en dag midt i en uke`() {
        val periode = SykmeldingsperiodeAGDTO(
            fom = LocalDate.of(2019, 12, 3),
            tom = LocalDate.of(2019, 12, 3),
            gradert = GradertDTO(grad = 100, reisetilskudd = false),
            type = PeriodetypeDTO.BEHANDLINGSDAGER,
            aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
            behandlingsdager = null,
            innspillTilArbeidsgiver = null,
            reisetilskudd = false,
        ).tilSoknadsperioder()
        val res = splittPeriodeIUker(periode)

        assertEquals(res[0].ukestart, LocalDate.of(2019, 12, 3))
        assertEquals(res[0].ukeslutt, LocalDate.of(2019, 12, 3))
    }

    @Test
    fun `n??r fom er etter tom forventer vi en exception`() {

        assertThrows(IllegalArgumentException::class.java) {
            val periode = SykmeldingsperiodeAGDTO(
                fom = LocalDate.of(2019, 12, 3),
                tom = LocalDate.of(2019, 12, 2),
                gradert = GradertDTO(grad = 100, reisetilskudd = false),
                type = PeriodetypeDTO.BEHANDLINGSDAGER,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false,
            ).tilSoknadsperioder()
            splittPeriodeIUker(periode)
        }
    }
}
