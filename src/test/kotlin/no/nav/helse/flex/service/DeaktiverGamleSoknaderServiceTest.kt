package no.nav.helse.flex.service

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.mock.opprettNySoknad
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadOppholdUtland
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.util.tilOsloInstant
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.`should be equal to`
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class DeaktiverGamleSoknaderServiceTest : BaseTestClass() {

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var publiserUtgaatteSoknader: PubliserUtgaatteSoknader

    @Autowired
    private lateinit var deaktiverGamleSoknaderService: DeaktiverGamleSoknaderService

    @BeforeEach
    @AfterEach
    fun nullstillDatabase() {
        sykepengesoknadDAO.nullstillSoknader("aktorId-745463060")
    }

    @Test
    fun `Tom database inkluderer ingen søknader å deaktivere`() {
        val antall = deaktiverGamleSoknaderService.deaktiverSoknader()
        assertThat(antall).isEqualTo(0)

        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 0
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0)
    }

    @Test
    fun `En gammel arbeidstakersøknad blir deaktivert`() {
        val nySoknad = opprettNySoknad().copy(
            status = Soknadstatus.NY,
            fnr = "12345784312",
            tom = LocalDate.now().minusMonths(4).minusDays(1),
            opprettet = LocalDate.now().minusMonths(4).minusDays(1).atStartOfDay().tilOsloInstant()
        )
        sykepengesoknadDAO.lagreSykepengesoknad(nySoknad)

        val sendtSoknad = nySoknad.copy(status = Soknadstatus.SENDT, id = UUID.randomUUID().toString())
        sykepengesoknadDAO.lagreSykepengesoknad(sendtSoknad)

        val antall = deaktiverGamleSoknaderService.deaktiverSoknader()
        assertThat(antall).isEqualTo(1)

        assertThat(sykepengesoknadDAO.finnSykepengesoknad(nySoknad.id).status).isEqualTo(Soknadstatus.UTGATT)
        assertThat(sykepengesoknadDAO.finnSykepengesoknad(sendtSoknad.id).status).isEqualTo(Soknadstatus.SENDT)

        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 1

        val soknadPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        soknadPaKafka.status `should be equal to` SoknadsstatusDTO.UTGAATT

        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 0
    }

    @Test
    fun `En avbrutt søknad blir deaktivert`() {
        val avbruttSoknad = opprettNySoknad().copy(
            status = Soknadstatus.AVBRUTT,
            fnr = "12345784312",
            tom = LocalDate.now().minusMonths(4).minusDays(1),
            opprettet = LocalDate.now().minusMonths(4).minusDays(1).atStartOfDay().tilOsloInstant()
        )
        sykepengesoknadDAO.lagreSykepengesoknad(avbruttSoknad)

        val sendtSoknad = avbruttSoknad.copy(status = Soknadstatus.SENDT, id = UUID.randomUUID().toString())
        sykepengesoknadDAO.lagreSykepengesoknad(sendtSoknad)

        val antall = deaktiverGamleSoknaderService.deaktiverSoknader()
        assertThat(antall).isEqualTo(1)

        assertThat(sykepengesoknadDAO.finnSykepengesoknad(avbruttSoknad.id).status).isEqualTo(Soknadstatus.UTGATT)
        assertThat(sykepengesoknadDAO.finnSykepengesoknad(sendtSoknad.id).status).isEqualTo(Soknadstatus.SENDT)

        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 1

        val soknadPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        soknadPaKafka.status `should be equal to` SoknadsstatusDTO.UTGAATT

        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 0
    }

    @Test
    fun `En gammel utenlandssoknad blir deaktivert`() {
        val nySoknad = settOppSoknadOppholdUtland("fnr")
            .copy(opprettet = LocalDateTime.now().minusMonths(4).minusDays(1).tilOsloInstant(), status = Soknadstatus.NY)
        sykepengesoknadDAO.lagreSykepengesoknad(nySoknad)

        val sendtSoknad = nySoknad.copy(status = Soknadstatus.SENDT, id = UUID.randomUUID().toString())
        sykepengesoknadDAO.lagreSykepengesoknad(sendtSoknad)

        val antall = deaktiverGamleSoknaderService.deaktiverSoknader()
        assertThat(antall).isEqualTo(1)

        assertThat(sykepengesoknadDAO.finnSykepengesoknad(nySoknad.id).status).isEqualTo(Soknadstatus.UTGATT)
        assertThat(sykepengesoknadDAO.finnSykepengesoknad(sendtSoknad.id).status).isEqualTo(Soknadstatus.SENDT)

        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 1

        val soknadPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        soknadPaKafka.status `should be equal to` SoknadsstatusDTO.UTGAATT

        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 0
    }

    @Test
    fun `Søknader blir publisert uten spørsmål`() {
        val nySoknad = opprettNySoknad().copy(
            status = Soknadstatus.NY,
            tom = LocalDate.now().minusMonths(4).minusDays(1),
            opprettet = LocalDate.now().minusMonths(4).minusDays(1).atStartOfDay().tilOsloInstant()
        )
        sykepengesoknadDAO.lagreSykepengesoknad(nySoknad)

        val antall = deaktiverGamleSoknaderService.deaktiverSoknader()
        assertThat(antall).isEqualTo(1)

        assertThat(sykepengesoknadDAO.finnSykepengesoknad(nySoknad.id).status).isEqualTo(Soknadstatus.UTGATT)
        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 1

        val soknadPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        soknadPaKafka.status `should be equal to` SoknadsstatusDTO.UTGAATT

        soknadPaKafka.sporsmal?.size `should be equal to` 0
    }
}
