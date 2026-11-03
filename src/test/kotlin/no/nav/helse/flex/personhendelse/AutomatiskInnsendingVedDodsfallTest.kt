package no.nav.helse.flex.personhendelse

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.mock.opprettNySoknad
import no.nav.helse.flex.repository.DodsmeldingDAO
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.util.tilOsloInstant
import no.nav.helse.flex.util.tilOsloZone
import no.nav.helse.flex.ventPåRecords
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.LocalDate
import java.time.OffsetDateTime

class AutomatiskInnsendingVedDodsfallTest : FellesTestOppsett() {
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
        antallDodsmeldingerIDb().shouldBe(0)

        automatiskInnsendingVedDodsfall.sendSoknaderForDode().shouldBe(0)

        verify(automatiskInnsendingVedDodsfall).sendSoknaderForDode()
        verifyNoMoreInteractions(automatiskInnsendingVedDodsfall)

        antallDodsmeldingerIDb().shouldBe(0)
    }

    @Test
    fun `Prossererer ikke dødsfall som er ikke er mottatt for mer enn 14 dager siden`() {
        dodsmeldingDAO.lagreDodsmelding(
            FolkeregisterIdenter("12345678900", emptyList()),
            LocalDate.now().minusDays(10),
            OffsetDateTime.now().minusDays(10),
        )
        dodsmeldingDAO.lagreDodsmelding(
            FolkeregisterIdenter("12345678901", emptyList()),
            LocalDate.now().minusDays(10),
            OffsetDateTime.now().minusDays(10),
        )

        antallDodsmeldingerIDb().shouldBe(2)

        automatiskInnsendingVedDodsfall.sendSoknaderForDode().shouldBe(0)

        verify(automatiskInnsendingVedDodsfall).sendSoknaderForDode()
        verifyNoMoreInteractions(automatiskInnsendingVedDodsfall)

        antallDodsmeldingerIDb().shouldBe(2)
    }

    @Test
    fun `Prossererer dødsfall som er mottatt for over 14 dager siden `() {
        dodsmeldingDAO.lagreDodsmelding(
            FolkeregisterIdenter("12345678900", emptyList()),
            LocalDate.now().minusDays(10),
            OffsetDateTime.now().minusDays(10),
        )
        dodsmeldingDAO.lagreDodsmelding(
            FolkeregisterIdenter("12345678901", emptyList()),
            LocalDate.now().minusDays(10),
            OffsetDateTime.now().minusDays(10),
        )
        dodsmeldingDAO.lagreDodsmelding(
            FolkeregisterIdenter("12345678902", emptyList()),
            LocalDate.now().minusDays(16),
            OffsetDateTime.now().minusDays(16),
        )
        dodsmeldingDAO.lagreDodsmelding(
            FolkeregisterIdenter("12345678903", emptyList()),
            LocalDate.now().minusDays(17),
            OffsetDateTime.now().minusDays(16),
        )

        antallDodsmeldingerIDb().shouldBe(4)
        automatiskInnsendingVedDodsfall.sendSoknaderForDode().shouldBe(2)

        verify(automatiskInnsendingVedDodsfall).sendSoknaderForDode()
        verify(automatiskInnsendingVedDodsfall).automatiskInnsending("12345678902", LocalDate.now().minusDays(16))
        verify(automatiskInnsendingVedDodsfall).automatiskInnsending("12345678903", LocalDate.now().minusDays(17))
        verifyNoMoreInteractions(automatiskInnsendingVedDodsfall)

        antallDodsmeldingerIDb().shouldBe(2)
    }

    @Test
    fun `Automatisk innsending av fremtidig søknad`() {
        val soknad =
            opprettNySoknad().copy(
                status = Soknadstatus.FREMTIDIG,
                sporsmal = emptyList(),
                fnr = "12345678900",
                fom = LocalDate.now().minusWeeks(3),
                tom = LocalDate.now().minusWeeks(2),
            )

        sykepengesoknadDAO.lagreSykepengesoknad(soknad)

        dodsmeldingDAO.lagreDodsmelding(
            identer = FolkeregisterIdenter("12345678900", emptyList()),
            dodsdato = soknad.tom!!.minusDays(1),
            meldingMottattDato =
                soknad.tom
                    .minusDays(1)
                    .atStartOfDay()
                    .tilOsloInstant()
                    .tilOsloZone(),
        )

        antallDodsmeldingerIDb().shouldBe(1)

        automatiskInnsendingVedDodsfall.sendSoknaderForDode().shouldBe(1)

        verify(automatiskInnsendingVedDodsfall).automatiskInnsending("12345678900", soknad.tom.minusDays(1))
        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2).tilSoknader()
        soknader.shouldHaveSize(2)
        soknader.first().status.shouldBe(SoknadsstatusDTO.NY)
        soknader.last().status.shouldBe(SoknadsstatusDTO.SENDT)

        antallDodsmeldingerIDb().shouldBe(0)
    }

    fun antallDodsmeldingerIDb(): Int =
        namedParameterJdbcTemplate
            .queryForObject(
                """
                SELECT COUNT(1) FROM DODSMELDING 
            """,
                MapSqlParameterSource(),
                Int::class.java,
            )!!

    fun slettDodsmeldinger() {
        namedParameterJdbcTemplate.update(
            """
                DELETE FROM DODSMELDING 
            """,
            MapSqlParameterSource(),
        )
    }
}
