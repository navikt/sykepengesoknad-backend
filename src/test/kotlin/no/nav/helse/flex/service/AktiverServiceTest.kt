package no.nav.helse.flex.service

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.mock.opprettBehandlingsdagsoknadTestadata
import no.nav.helse.flex.mock.opprettNySoknad
import no.nav.helse.flex.mock.opprettNySoknadMock
import no.nav.helse.flex.mock.opprettSendtFrilanserSoknad
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.ventPåRecords
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
        val soknad = opprettSendtFrilanserSoknad()
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
