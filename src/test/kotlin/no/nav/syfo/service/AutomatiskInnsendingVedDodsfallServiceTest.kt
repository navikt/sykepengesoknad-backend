package no.nav.syfo.service

import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.syfo.BaseTestClass
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.mock.opprettNySoknad
import no.nav.syfo.repository.DodsmeldingDAO
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.tilSoknader
import no.nav.syfo.ventPåRecords
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.LocalDate

class AutomatiskInnsendingVedDodsfallServiceTest : BaseTestClass() {

    @Autowired
    private lateinit var automatiskInnsendingVedDodsfallService: AutomatiskInnsendingVedDodsfallService

    @Autowired
    private lateinit var namedParameterJdbcTemplate: NamedParameterJdbcTemplate

    @Autowired
    private lateinit var dodsmeldingDAO: DodsmeldingDAO

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @BeforeEach
    @AfterEach
    fun setUp() {
        slettDodsmeldinger()
    }

    @Test
    fun `Tom database inkluderer ingen dødsfall å prosessere`() {
        antallDodsmeldingerIDb().shouldBeEqualTo(0)

        automatiskInnsendingVedDodsfallService.sendSoknaderForDode().shouldBeEqualTo(0)
        verifyNoInteractions(automatiskInnsendingService)

        antallDodsmeldingerIDb().shouldBeEqualTo(0)
    }

    @Test
    fun `Prossererer ikke dødsfall som er ikke er mottatt for mer enn 14 dager siden`() {
        dodsmeldingDAO.lagreDodsmelding("aktor1", LocalDate.now().minusDays(10), LocalDate.now().minusDays(10))
        dodsmeldingDAO.lagreDodsmelding("aktor2", LocalDate.now().minusDays(10), LocalDate.now().minusDays(10))

        antallDodsmeldingerIDb().shouldBeEqualTo(2)

        automatiskInnsendingVedDodsfallService.sendSoknaderForDode().shouldBeEqualTo(0)
        verifyNoInteractions(automatiskInnsendingService)

        antallDodsmeldingerIDb().shouldBeEqualTo(2)
    }

    @Test
    fun `Prossererer dødsfall som er mottatt for over 14 dager siden `() {
        dodsmeldingDAO.lagreDodsmelding("aktor1", LocalDate.now().minusDays(10), LocalDate.now().minusDays(10))
        dodsmeldingDAO.lagreDodsmelding("aktor2", LocalDate.now().minusDays(10), LocalDate.now().minusDays(10))
        dodsmeldingDAO.lagreDodsmelding("aktor3", LocalDate.now().minusDays(16), LocalDate.now().minusDays(16))
        dodsmeldingDAO.lagreDodsmelding("aktor4", LocalDate.now().minusDays(17), LocalDate.now().minusDays(16))

        antallDodsmeldingerIDb().shouldBeEqualTo(4)
        automatiskInnsendingVedDodsfallService.sendSoknaderForDode().shouldBeEqualTo(2)

        verify(automatiskInnsendingService).automatiskInnsending("aktor3", LocalDate.now().minusDays(16))
        verify(automatiskInnsendingService).automatiskInnsending("aktor4", LocalDate.now().minusDays(17))
        verifyNoMoreInteractions(automatiskInnsendingService)

        antallDodsmeldingerIDb().shouldBeEqualTo(2)
    }

    @Test
    fun `Automatisk innsending av fremtidig søknad`() {
        val soknad = opprettNySoknad().copy(
            status = Soknadstatus.FREMTIDIG,
            sporsmal = emptyList(),
            fnr = "aktor1",
            fom = LocalDate.of(2022, 1, 1),
            tom = LocalDate.of(2022, 1, 30),
        )

        sykepengesoknadDAO.lagreSykepengesoknad(soknad)

        dodsmeldingDAO.lagreDodsmelding(
            aktorId = "aktor1",
            dodsdato = soknad.tom!!.minusDays(1),
            meldingMottattDato = soknad.tom!!.minusDays(1),
        )

        antallDodsmeldingerIDb().shouldBeEqualTo(1)

        automatiskInnsendingVedDodsfallService.sendSoknaderForDode().shouldBeEqualTo(1)

        verify(automatiskInnsendingService).automatiskInnsending("aktor1", soknad.tom!!.minusDays(1))
        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2).tilSoknader()
        soknader.shouldHaveSize(2)
        soknader.first().status.shouldBeEqualTo(SoknadsstatusDTO.NY)
        soknader.last().status.shouldBeEqualTo(SoknadsstatusDTO.SENDT)

        antallDodsmeldingerIDb().shouldBeEqualTo(0)
    }

    fun antallDodsmeldingerIDb(): Int {
        return namedParameterJdbcTemplate.queryForObject(
            """
                SELECT COUNT(1) FROM DODSMELDING 
            """,

            MapSqlParameterSource(),
            Integer::class.java
        )!!.toInt()
    }

    fun slettDodsmeldinger() {
        namedParameterJdbcTemplate.update(
            """
                DELETE FROM DODSMELDING 
            """,

            MapSqlParameterSource()
        )
    }
}
