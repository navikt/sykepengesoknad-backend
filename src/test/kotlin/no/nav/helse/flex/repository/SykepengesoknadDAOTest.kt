package no.nav.helse.flex.repository

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.Soknadstatus.KORRIGERT
import no.nav.helse.flex.domain.Soknadstatus.NY
import no.nav.helse.flex.domain.Soknadstatus.SENDT
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.mock.MockSoknadSelvstendigeOgFrilansere
import no.nav.helse.flex.mock.mockUtlandssoknad
import no.nav.helse.flex.mock.opprettNyArbeidstakerSoknad
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknad
import no.nav.helse.flex.mock.opprettSendtFrilanserSoknad
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.soknadsopprettelse.OPPHOLD_UTENFOR_EOS
import no.nav.helse.flex.soknadsopprettelse.OPPHOLD_UTENFOR_EOS_NAR
import org.amshove.kluent.shouldBe
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class SykepengesoknadDAOTest : FellesTestOppsett() {
    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

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
        val sykepengesoknad = opprettSendtFrilanserSoknad()
        val korrigererId = UUID.randomUUID().toString()
        val korrigertAvId = UUID.randomUUID().toString()
        sykepengesoknadDAO.lagreSykepengesoknad(sykepengesoknad.copy(id = korrigererId, korrigerer = korrigertAvId))
        sykepengesoknadDAO.lagreSykepengesoknad(sykepengesoknad.copy(id = korrigertAvId, korrigertAv = korrigererId, status = KORRIGERT))

        val sykepengesoknadList = sykepengesoknadDAO.finnSykepengesoknader(listOf(sykepengesoknad.fnr))

        assertThat(sykepengesoknadList).hasSize(2)
        val assertKorrigerende = assertThat(sykepengesoknadList).filteredOn { (id) -> id == korrigererId }.first()
        assertKorrigerende.hasFieldOrPropertyWithValue("status", SENDT)
        assertKorrigerende.hasFieldOrPropertyWithValue("korrigerer", korrigertAvId)

        val assertKorrigert = assertThat(sykepengesoknadList).filteredOn { (id) -> id == korrigertAvId }.first()
        assertKorrigert.hasFieldOrPropertyWithValue("status", KORRIGERT)
        assertKorrigert.hasFieldOrPropertyWithValue("korrigertAv", korrigererId)
    }

    @Test
    fun finnEldreSoknader() {
        val sykepengesoknad = opprettNyNaeringsdrivendeSoknad()
        val soknadUnderUtfylling =
            sykepengesoknadDAO.lagreSykepengesoknad(
                sykepengesoknad.copy(
                    id = UUID.randomUUID().toString(),
                    fom = LocalDate.of(2018, 6, 1),
                ),
            )
        val eldreSoknad =
            sykepengesoknadDAO.lagreSykepengesoknad(
                sykepengesoknad.copy(
                    id = UUID.randomUUID().toString(),
                    fom = LocalDate.of(2018, 5, 29),
                ),
            )

        val eldsteSoknaden =
            sykepengesoknadRepository.findEldsteSoknaden(
                listOf(soknadUnderUtfylling.fnr),
                soknadUnderUtfylling.fom,
            )

        assertThat(eldsteSoknaden).isEqualTo(eldreSoknad.id)
        assertThat(soknadUnderUtfylling.fom).isAfter(eldreSoknad.fom)
    }

    @Test
    fun finnMottakerAvSoknad_mottakerErAgOgNav() {
        val soknadId =
            sykepengesoknadDAO
                .lagreSykepengesoknad(
                    opprettNyArbeidstakerSoknad(),
                ).id

        val now = LocalDateTime.now()
        settSendt(soknadId, now, now)

        val mottaker = sykepengesoknadDAO.finnMottakerAvSoknad(soknadId)

        assertThat(mottaker).isEqualTo(Mottaker.ARBEIDSGIVER_OG_NAV)
    }

    @Test
    fun finnMottakerAvSoknad_mottakerErAg() {
        val soknadId =
            sykepengesoknadDAO
                .lagreSykepengesoknad(
                    opprettNyArbeidstakerSoknad(),
                ).id

        settSendt(soknadId, null, LocalDateTime.now())

        val mottaker = sykepengesoknadDAO.finnMottakerAvSoknad(soknadId)

        assertThat(mottaker).isEqualTo(Mottaker.ARBEIDSGIVER)
    }

    @Test
    fun finnMottakerAvSoknad_mottakerErNav() {
        val soknadId =
            sykepengesoknadDAO
                .lagreSykepengesoknad(
                    opprettNyArbeidstakerSoknad(),
                ).id

        settSendt(soknadId, LocalDateTime.now(), null)

        val mottaker = sykepengesoknadDAO.finnMottakerAvSoknad(soknadId)

        assertThat(mottaker).isEqualTo(Mottaker.NAV)
    }

    @Test
    fun finnMottakerAvSoknad_soknadIkkeSendt() {
        val soknadId =
            sykepengesoknadDAO
                .lagreSykepengesoknad(
                    opprettNyArbeidstakerSoknad(),
                ).id

        settSendt(soknadId, null, null)

        val mottaker = sykepengesoknadDAO.finnMottakerAvSoknad(soknadId)

        assertThat(mottaker).isNull()
    }

    private fun settSendt(
        soknadId: String,
        sendtNav: LocalDateTime?,
        sendtArbeidsgiver: LocalDateTime?,
    ) {
        jdbcTemplate.update(
            """
            UPDATE sykepengesoknad
            SET sendt_nav = ?, 
                sendt_arbeidsgiver = ?
            WHERE sykepengesoknad_uuid = ?
            """.trimIndent(),
            sendtNav,
            sendtArbeidsgiver,
            soknadId,
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

        val soknad = sykepengesoknadDAO.finnSykepengesoknad(uuid)
        soknad.sendtNav!!.isWithinDurationFromNow(Duration.ofSeconds(1)) shouldBe true
    }

    @Test
    fun sjekkAtSendtTilAGBlirSattSoknad() {
        val uuid = sykepengesoknadDAO.lagreSykepengesoknad(opprettNyArbeidstakerSoknad()).id

        val (id) = sykepengesoknadDAO.finnSykepengesoknad(uuid)

        sykepengesoknadDAO.settSendtAg(id, LocalDateTime.now())

        val soknad = sykepengesoknadDAO.finnSykepengesoknad(uuid)
        soknad.sendtNav!!.isWithinDurationFromNow(Duration.ofSeconds(1)) shouldBe true
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
        sykepengesoknadDAO.lagreSykepengesoknad(opprettSendtFrilanserSoknad().copy(sykmeldingId = ("sykmeldingId1")))
        sykepengesoknadDAO.lagreSykepengesoknad(mockUtlandssoknad())
        sykepengesoknadDAO.lagreSykepengesoknad(opprettSendtFrilanserSoknad().copy(sykmeldingId = ("sykmeldingId1")))
        sykepengesoknadDAO.lagreSykepengesoknad(opprettSendtFrilanserSoknad().copy(sykmeldingId = ("sykmeldingId3")))

        val soknader = sykepengesoknadDAO.finnSykepengesoknaderForSykmelding("sykmeldingId1")

        assertThat(soknader.size).isEqualTo(2)
        assertThat(soknader[1].status).isEqualTo(SENDT)
    }

    @Test
    fun kanOppretteArbeidstakerSoknad() {
        sykepengesoknadDAO.lagreSykepengesoknad(
            opprettNyArbeidstakerSoknad().copy(sykmeldingId = ("sykmeldingId1")),
        )

        val soknad = sykepengesoknadDAO.finnSykepengesoknaderForSykmelding("sykmeldingId1")

        assertThat(soknad).isNotNull
    }

    @Test
    fun sletterSvarPaaSoknad() {
        val uuid = sykepengesoknadDAO.lagreSykepengesoknad(opprettNyArbeidstakerSoknad()).id
        val soknadFraBasen = sykepengesoknadDAO.finnSykepengesoknad(uuid).svarJaPaUtlandsporsmal()

        sykepengesoknadDAO.byttUtSporsmal(soknadFraBasen)
        sykepengesoknadDAO.slettAlleSvar(soknadFraBasen)

        val soknadFraBasenEtterSlettingAvSvar = sykepengesoknadDAO.finnSykepengesoknad(uuid)

        soknadFraBasenEtterSlettingAvSvar
            .alleSporsmalOgUndersporsmal()
            .forEach { sporsmal -> sporsmal.svar.forEach { svar -> assertThat(svar.verdi).isNull() } }
    }

    // TODO: isEqualToIgnoringSeconds() og isCloseTo() funker ikke med LocaDateTime, så bytt dette med noe som funker.
    fun Instant.isWithinDurationFromNow(duration: Duration): Boolean = this.isBefore(this.plus(duration))

    private fun Sykepengesoknad.svarJaPaUtlandsporsmal(): Sykepengesoknad {
        val utlandSpm = this.getSporsmalMedTag(OPPHOLD_UTENFOR_EOS).copy(svar = listOf(Svar(null, "CHECKED")))
        val utlandNarSpm =
            this.getSporsmalMedTag(OPPHOLD_UTENFOR_EOS_NAR).copy(
                svar =
                    listOf(
                        Svar(
                            null,
                            "{\"fom\":\"2019-02-28\",\"tom\":\"2019-03-14\"}",
                        ),
                    ),
            )

        return this.replaceSporsmal(utlandSpm).replaceSporsmal(utlandNarSpm)
    }
}
