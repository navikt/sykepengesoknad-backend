package no.nav.helse.flex.repository

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.Soknadstatus.*
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.mock.MockSoknadSelvstendigeOgFrilansere
import no.nav.helse.flex.mock.gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal
import no.nav.helse.flex.mock.gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal
import no.nav.helse.flex.mock.mockUtlandssoknad
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.soknadsopprettelse.FRAVAR_FOR_SYKMELDINGEN
import no.nav.helse.flex.soknadsopprettelse.FRAVAR_FOR_SYKMELDINGEN_NAR
import no.nav.helse.flex.soknadsopprettelse.UTLAND
import no.nav.helse.flex.soknadsopprettelse.UTLAND_NAR
import no.nav.helse.flex.util.tilOsloLocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class SykepengesoknadDAOTest : BaseTestClass() {

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var mockSoknadSelvstendigeOgFrilansere: MockSoknadSelvstendigeOgFrilansere

    @BeforeEach
    fun initDB() {
        cleanup()
    }

    @AfterEach
    fun cleanup() {
        jdbcTemplate.update("DELETE FROM SOKNADPERIODE")
        jdbcTemplate.update("DELETE FROM SVAR")
        jdbcTemplate.update("DELETE FROM SPORSMAL")
        jdbcTemplate.update("DELETE FROM SYKEPENGESOKNAD")
    }

    @Test
    fun henterUtSykepengesoknad() {
        val soknad = mockSoknadSelvstendigeOgFrilansere.opprettOgLagreNySoknad()
        val uuid = soknad.id

        val sykepengesoknadList = sykepengesoknadDAO.finnSykepengesoknader(listOf(soknad.fnr))

        assertThat(sykepengesoknadList.size).isEqualTo(1)
        assertThat(sykepengesoknadList[0].id).isEqualTo(uuid)
        assertThat(sykepengesoknadList[0].status).isEqualTo(NY)
    }

    @Test
    fun lagrerKorrigertAv() {
        val sykepengesoknad = mockSoknadSelvstendigeOgFrilansere.opprettSendtSoknad()
        val korrigererId = UUID.randomUUID().toString()
        val korrigertAvId = UUID.randomUUID().toString()
        sykepengesoknadDAO.lagreSykepengesoknad(sykepengesoknad.copy(id = korrigererId, korrigerer = korrigertAvId))
        sykepengesoknadDAO.lagreSykepengesoknad(sykepengesoknad.copy(id = korrigertAvId, korrigertAv = korrigererId, status = KORRIGERT))

        val sykepengesoknadList = sykepengesoknadDAO.finnSykepengesoknaderByUuid(listOf(korrigererId, korrigertAvId))

        assertThat(sykepengesoknadList).hasSize(2)
        val assertKorrigerende = assertThat(sykepengesoknadList).filteredOn { (id) -> id == korrigererId }.first()
        assertKorrigerende.hasFieldOrPropertyWithValue("status", SENDT)
        assertKorrigerende.hasFieldOrPropertyWithValue("korrigerer", korrigertAvId)

        val assertKorrigert = assertThat(sykepengesoknadList).filteredOn { (id) -> id == korrigertAvId }.first()
        assertKorrigert.hasFieldOrPropertyWithValue("status", KORRIGERT)
        assertKorrigert.hasFieldOrPropertyWithValue("korrigertAv", korrigererId)
    }

    @Test
    fun finnSykepengesoknaderByUuidEnSoknad() {
        val sykepengesoknad = mockSoknadSelvstendigeOgFrilansere.opprettNySoknad()
        val uuid1 = sykepengesoknadDAO.lagreSykepengesoknad(sykepengesoknad).id
        sykepengesoknadDAO.lagreSykepengesoknad(sykepengesoknad.copy(id = UUID.randomUUID().toString(), fnr = ("fnr2")))

        val sykepengesoknadList = sykepengesoknadDAO.finnSykepengesoknaderByUuid(listOf(uuid1))

        assertThat(sykepengesoknadList).hasSize(1)
        assertThat(sykepengesoknadList[0].status).isEqualTo(NY)
    }

    @Test
    fun finnSykepengesoknaderByUuidToSoknader() {
        val sykepengesoknad = mockSoknadSelvstendigeOgFrilansere.opprettNySoknad()
        val uuid1 = sykepengesoknadDAO.lagreSykepengesoknad(sykepengesoknad).id
        val uuid2 = sykepengesoknadDAO.lagreSykepengesoknad(sykepengesoknad.copy(id = UUID.randomUUID().toString(), fnr = "fnr2")).id

        val sykepengesoknadList = sykepengesoknadDAO.finnSykepengesoknaderByUuid(listOf(uuid1, uuid2)).sortedBy { it.fnr }

        assertThat(sykepengesoknadList).hasSize(2)
        assertThat(sykepengesoknadList[0].status).isEqualTo(NY)
        assertThat(sykepengesoknadList[1].fnr).isEqualTo("fnr2")
        assertThat(sykepengesoknadList[1].status).isEqualTo(NY)
    }

    @Test
    fun finnEldreSoknader() {
        val sykepengesoknad = mockSoknadSelvstendigeOgFrilansere.opprettNySoknad()
        val soknadUnderUtfylling = sykepengesoknadDAO.lagreSykepengesoknad(
            sykepengesoknad.copy(
                id = UUID.randomUUID().toString(),
                fom = LocalDate.of(2018, 6, 1)
            )
        )
        val eldreSoknad = sykepengesoknadDAO.lagreSykepengesoknad(
            sykepengesoknad.copy(
                id = UUID.randomUUID().toString(),
                fom = LocalDate.of(2018, 5, 29)
            )
        )

        val eldsteSoknaden = sykepengesoknadRepository.findEldsteSoknaden(
            listOf(soknadUnderUtfylling.fnr)
        )

        assertThat(eldsteSoknaden).isEqualTo(eldreSoknad.id)
        assertThat(soknadUnderUtfylling.fom).isAfter(eldreSoknad.fom)
    }

    @Test
    fun finnMottakerAvSoknad_mottakerErAgOgNav() {
        val soknadId = sykepengesoknadDAO.lagreSykepengesoknad(
            gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal()
        ).id

        val now = LocalDateTime.now()
        settSendt(soknadId, now, now)

        val mottaker = sykepengesoknadDAO.finnMottakerAvSoknad(soknadId)

        assertThat(mottaker).contains(Mottaker.ARBEIDSGIVER_OG_NAV)
    }

    @Test
    fun finnMottakerAvSoknad_mottakerErAg() {
        val soknadId = sykepengesoknadDAO.lagreSykepengesoknad(
            gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal()
        ).id

        settSendt(soknadId, null, LocalDateTime.now())

        val mottaker = sykepengesoknadDAO.finnMottakerAvSoknad(soknadId)

        assertThat(mottaker).contains(Mottaker.ARBEIDSGIVER)
    }

    @Test
    fun finnMottakerAvSoknad_mottakerErNav() {
        val soknadId = sykepengesoknadDAO.lagreSykepengesoknad(
            gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal()
        ).id

        settSendt(soknadId, LocalDateTime.now(), null)

        val mottaker = sykepengesoknadDAO.finnMottakerAvSoknad(soknadId)

        assertThat(mottaker).contains(Mottaker.NAV)
    }

    @Test
    fun finnMottakerAvSoknad_soknadIkkeSendt() {
        val soknadId = sykepengesoknadDAO.lagreSykepengesoknad(
            gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal()
        ).id

        settSendt(soknadId, null, null)

        val mottaker = sykepengesoknadDAO.finnMottakerAvSoknad(soknadId)

        assertThat(mottaker).isEmpty
    }

    private fun settSendt(soknadId: String, sendtNav: LocalDateTime?, sendtArbeidsgiver: LocalDateTime?) {
        jdbcTemplate.update(
            "UPDATE SYKEPENGESOKNAD " +
                "SET SENDT_NAV = ?, SENDT_ARBEIDSGIVER = ? " +
                "WHERE SYKEPENGESOKNAD_UUID = ?",
            sendtNav, sendtArbeidsgiver, soknadId
        )
    }

    @Test
    fun slettNySoknadOppholdUtland() {
        val uuid1 = mockSoknadSelvstendigeOgFrilansere.opprettOgLagreNySoknad().id
        val sykepengesoknad = mockUtlandssoknad()
        sykepengesoknadDAO.lagreSykepengesoknad(sykepengesoknad)
        sykepengesoknadDAO.lagreSykepengesoknad(mockUtlandssoknad().copy(fnr = ("etAnnetFnr")))
        val uuid2 = sykepengesoknadDAO.lagreSykepengesoknad(mockUtlandssoknad().copy(status = SENDT)).id

        val nysoknad = sykepengesoknadDAO.finnAlleredeOpprettetSoknad(FolkeregisterIdenter(sykepengesoknad.fnr, emptyList()))
        sykepengesoknadDAO.slettSoknad(nysoknad!!)

        val soknaderTestAktor = sykepengesoknadDAO.finnSykepengesoknader(listOf(sykepengesoknad.fnr))
        val soknaderAnnenAktor = sykepengesoknadDAO.finnSykepengesoknader(listOf("etAnnetFnr"))
        assertThat(soknaderTestAktor).hasSize(2)
        assertThat(soknaderTestAktor[0].id).isEqualTo(uuid1)
        assertThat(soknaderTestAktor[1].id).isEqualTo(uuid2)
        assertThat(soknaderAnnenAktor).hasSize(1)
    }

    @Test
    fun sjekkAtSendtTilNAVBlirSattSoknad() {
        val uuid = mockSoknadSelvstendigeOgFrilansere.opprettOgLagreNySoknad().id
        val (id) = sykepengesoknadDAO.finnSykepengesoknad(uuid)

        sykepengesoknadDAO.settSendtNav(id, LocalDateTime.now())

        val (_, _, _, _, _, _, sendtNav) = sykepengesoknadDAO.finnSykepengesoknad(uuid)
        assertThat(sendtNav!!.tilOsloLocalDateTime()).isEqualToIgnoringSeconds(Instant.now().tilOsloLocalDateTime())
    }

    @Test
    fun sjekkAtSendtTilAGBlirSattSoknad() {
        val uuid = sykepengesoknadDAO.lagreSykepengesoknad(gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal()).id

        val (id) = sykepengesoknadDAO.finnSykepengesoknad(uuid)

        sykepengesoknadDAO.settSendtAg(id, LocalDateTime.now())

        val (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, sendtArbeidsgiver) = sykepengesoknadDAO.finnSykepengesoknad(uuid)
        assertThat(sendtArbeidsgiver!!.tilOsloLocalDateTime()).isEqualToIgnoringSeconds(Instant.now().tilOsloLocalDateTime())
    }

    @Test
    fun slettSoknadSletterSoknad() {
        val uuid = mockSoknadSelvstendigeOgFrilansere.opprettOgLagreNySoknad().id
        mockSoknadSelvstendigeOgFrilansere.opprettOgLagreNySoknad()
        mockSoknadSelvstendigeOgFrilansere.opprettOgLagreNySoknad()

        val sykepengesoknad = sykepengesoknadDAO.finnSykepengesoknad(uuid)

        assertThat(sykepengesoknad.id).isEqualTo(uuid)

        sykepengesoknadDAO.slettSoknad(sykepengesoknad)

        val soknader = sykepengesoknadDAO.finnSykepengesoknader(listOf(sykepengesoknad.fnr))

        assertThat(soknader.size).isEqualTo(2)
        assertThat(soknader.stream().noneMatch { (id) -> id == uuid }).isTrue
    }

    @Test
    fun henterSoknaderTilknyttetSykmelding() {
        sykepengesoknadDAO.lagreSykepengesoknad(mockSoknadSelvstendigeOgFrilansere.opprettSendtSoknad().copy(sykmeldingId = ("sykmeldingId1")))
        sykepengesoknadDAO.lagreSykepengesoknad(mockUtlandssoknad())
        sykepengesoknadDAO.lagreSykepengesoknad(mockSoknadSelvstendigeOgFrilansere.opprettSendtSoknad().copy(sykmeldingId = ("sykmeldingId1")))
        sykepengesoknadDAO.lagreSykepengesoknad(mockSoknadSelvstendigeOgFrilansere.opprettSendtSoknad().copy(sykmeldingId = ("sykmeldingId3")))

        val soknader = sykepengesoknadDAO.finnSykepengesoknaderForSykmelding("sykmeldingId1")

        assertThat(soknader.size).isEqualTo(2)
        assertThat(soknader[1].status).isEqualTo(SENDT)
    }

    @Test
    fun kanOppretteArbeidstakerSoknad() {
        sykepengesoknadDAO.lagreSykepengesoknad(gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal().copy(sykmeldingId = ("sykmeldingId1")))

        val soknad = sykepengesoknadDAO.finnSykepengesoknaderForSykmelding("sykmeldingId1")

        assertThat(soknad).isNotNull
    }

    @Test
    fun sletterSvarPaaSoknad() {
        val uuid = sykepengesoknadDAO.lagreSykepengesoknad(gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal()).id
        val soknadFraBasen = sykepengesoknadDAO.finnSykepengesoknad(uuid).svarJaPaUtlandsporsmal()

        sykepengesoknadDAO.byttUtSporsmal(soknadFraBasen)
        sykepengesoknadDAO.slettAlleSvar(soknadFraBasen)

        val soknadFraBasenEtterSlettingAvSvar = sykepengesoknadDAO.finnSykepengesoknad(uuid)

        soknadFraBasenEtterSlettingAvSvar.alleSporsmalOgUndersporsmal()
            .forEach { (_, _, _, _, _, _, _, _, _, svar) -> svar.forEach { (_, verdi) -> assertThat(verdi).isNull() } }
    }

    @Test
    fun sletterBareSvarPaSoknadenSomSendesInn() {
        val uuid1 = sykepengesoknadDAO.lagreSykepengesoknad(gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal()).id
        val soknad1 = sykepengesoknadDAO.finnSykepengesoknad(uuid1).svarJaPaUtlandsporsmal()
        sykepengesoknadDAO.byttUtSporsmal(soknad1)

        val uuid2 = sykepengesoknadDAO.lagreSykepengesoknad(gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal()).id
        val soknad2 = sykepengesoknadDAO.finnSykepengesoknad(uuid2).svarJaPaEgenmelding()
        sykepengesoknadDAO.byttUtSporsmal(soknad2)

        sykepengesoknadDAO.slettAlleSvar(soknad1)

        val soknadFraBasenEtterSlettingAvSvar = sykepengesoknadDAO.finnSykepengesoknad(uuid1)
        soknadFraBasenEtterSlettingAvSvar.alleSporsmalOgUndersporsmal()
            .forEach { (_, _, _, _, _, _, _, _, _, svar) -> svar.forEach { (_, verdi) -> assertThat(verdi).isNull() } }

        val soknadFraBasenEtterSlettingAvSvarPaAnnenSoknad = sykepengesoknadDAO.finnSykepengesoknad(uuid2)
        assertThat(soknadFraBasenEtterSlettingAvSvarPaAnnenSoknad.getSporsmalMedTag(FRAVAR_FOR_SYKMELDINGEN).svar[0].verdi).isEqualTo("JA")
        assertThat(soknadFraBasenEtterSlettingAvSvarPaAnnenSoknad.getSporsmalMedTag(FRAVAR_FOR_SYKMELDINGEN_NAR).svar[0].verdi).isEqualTo("{\"fom\":\"2019-02-28\",\"tom\":\"2019-03-14\"}")
    }

    private fun Sykepengesoknad.svarJaPaEgenmelding(): Sykepengesoknad {
        val egenmldSpm = this.getSporsmalMedTag(FRAVAR_FOR_SYKMELDINGEN).copy(svar = listOf(Svar(null, "JA", null)))
        val egenmldNarSpm = this.getSporsmalMedTag(FRAVAR_FOR_SYKMELDINGEN_NAR).copy(
            svar = listOf(
                Svar(
                    null, "{\"fom\":\"2019-02-28\",\"tom\":\"2019-03-14\"}",
                    null
                )
            )
        )

        return this.replaceSporsmal(egenmldSpm).replaceSporsmal(egenmldNarSpm)
    }

    private fun Sykepengesoknad.svarJaPaUtlandsporsmal(): Sykepengesoknad {

        val utlandSpm = this.getSporsmalMedTag(UTLAND).copy(svar = listOf(Svar(null, "CHECKED", null)))
        val utlandNarSpm = this.getSporsmalMedTag(UTLAND_NAR).copy(
            svar = listOf(
                Svar(
                    null, "{\"fom\":\"2019-02-28\",\"tom\":\"2019-03-14\"}",
                    null
                )
            )
        )

        return this.replaceSporsmal(utlandSpm).replaceSporsmal(utlandNarSpm)
    }
}
