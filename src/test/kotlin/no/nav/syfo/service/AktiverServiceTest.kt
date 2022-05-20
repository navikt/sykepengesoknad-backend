package no.nav.syfo.service

import no.nav.syfo.BaseTestClass
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.mock.MockSoknadSelvstendigeOgFrilansere
import no.nav.syfo.mock.opprettBehandlingsdagsoknadTestadata
import no.nav.syfo.mock.opprettNySoknad
import no.nav.syfo.mock.opprettNySoknadMock
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.ventPåRecords
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.*

class AktiverServiceTest : BaseTestClass() {

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var aktiverService: AktiverService

    @Autowired
    private lateinit var mockSoknadSelvstendigeOgFrilansere: MockSoknadSelvstendigeOgFrilansere

    @Test
    fun `tom database inkluderer ingen søknader å aktivere`() {
        val antall = aktiverService.aktiverSoknader()
        assertThat(antall).isEqualTo(0)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0)
    }

    @Test
    fun `En fremtidig arbeidstakersøknad blir aktivert`() {

        @Suppress("DEPRECATION")
        val soknad = opprettNySoknadMock().copy(status = Soknadstatus.FREMTIDIG, tom = LocalDate.now().minusDays(1))
        sykepengesoknadDAO.lagreSykepengesoknad(soknad)
        sykepengesoknadDAO.lagreSykepengesoknad(
            soknad.copy(
                tom = LocalDate.now().plusDays(1),
                id = UUID.randomUUID().toString()
            )
        )

        val antall = aktiverService.aktiverSoknader()
        assertThat(antall).isEqualTo(1)

        val soknaden = sykepengesoknadDAO.finnSykepengesoknad(soknad.id)

        assertThat(soknaden.status).isEqualTo(Soknadstatus.NY)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    fun `En fremtidig næringsdrivendesøknad blir aktivert`() {
        val soknad = mockSoknadSelvstendigeOgFrilansere.opprettNySoknad()
            .copy(status = Soknadstatus.FREMTIDIG, tom = LocalDate.now().minusDays(1))
        sykepengesoknadDAO.lagreSykepengesoknad(soknad)

        val antall = aktiverService.aktiverSoknader()
        assertThat(antall).isEqualTo(1)

        val soknaden = sykepengesoknadDAO.finnSykepengesoknad(soknad.id)

        assertThat(soknaden.status).isEqualTo(Soknadstatus.NY)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    fun `En fremtidig arbeidsledigsøknad blir aktivert`() {
        val soknad = opprettNySoknad().copy(
            status = Soknadstatus.FREMTIDIG,
            tom = LocalDate.now().minusDays(1),
            fnr = "1234"
        )
        sykepengesoknadDAO.lagreSykepengesoknad(soknad)

        val antall = aktiverService.aktiverSoknader()
        assertThat(antall).isEqualTo(1)

        val soknaden = sykepengesoknadDAO.finnSykepengesoknad(soknad.id)

        assertThat(soknaden.status).isEqualTo(Soknadstatus.NY)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    fun `En fremtidig behandlingsdagsøknad blir aktivert`() {
        val soknad = opprettBehandlingsdagsoknadTestadata(
            status = Soknadstatus.FREMTIDIG,
            tom = LocalDate.now().minusDays(1)
        )
        sykepengesoknadDAO.lagreSykepengesoknad(soknad)

        val antall = aktiverService.aktiverSoknader()
        assertThat(antall).isEqualTo(1)

        val soknaden = sykepengesoknadDAO.finnSykepengesoknad(soknad.id)

        assertThat(soknaden.status).isEqualTo(Soknadstatus.NY)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }
}
