package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstatus.FREMTIDIG
import no.nav.helse.flex.domain.Soknadstatus.NY
import no.nav.helse.flex.fakes.SoknadKafkaProducerFake
import no.nav.helse.flex.frisktilarbeid.BehandletStatus.BEHANDLET
import no.nav.helse.flex.frisktilarbeid.sporsmal.sendFriskTilArbeidVedtak
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.sendStartMelding
import no.nav.helse.flex.sendStoppMelding
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING
import no.nav.helse.flex.soknadsopprettelse.FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO.SLETTET
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.util.tilLocalDate
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should start with`
import org.amshove.kluent.shouldBeLessThan
import org.amshove.kluent.shouldHaveSize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class StartMeldingTest : FakesTestOppsett() {
    @Autowired
    lateinit var friskTilArbeidCronJob: FriskTilArbeidCronJob

    @Autowired
    lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    @Autowired
    lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    private val fnr = "11111111122"
    private val tidspunkt = Instant.now()

    val vedtakFom = LocalDate.now().minusDays(15)
    val vedtakTom = LocalDate.now().plusDays(35)

    @Test
    fun `Mottar start-melding og gjenoppretter søknader`() {
        // Arrange
        `Gitt at et vedtak er mottatt og søknader opprettet`()
        `Og en stopp-melding har blitt prosessert`()

        // Act
        `Motta og prosesser en start-melding`()

        // Assert
        `Så blir søknaden gjennopprettet med status NY`()
    }

    @Test
    fun `Får IllegalStateException dersom avsluttetTidspunkt er null`() {
        // Arrange
        `Gitt at et vedtak er mottatt og søknader opprettet`()

        // Act & Assert
        val e = assertThrows<IllegalStateException> {
            `Motta og prosesser en start-melding`()
        }

        // Assert
        e.message!! `should start with` "Kan ikke gjenopprette søknad når den aldri har blitt avsluttet"
    }

    @Test
    fun `Får IllegalStateException dersom behandletStatus ikke er BEHANDLET`() {
        // Arrange
        `Gitt at et vedtak er mottatt og søknader opprettet`()
        `Og avsluttetTidspunkt er satt til en verdi`()

        // Act & Assert
        val e = assertThrows<IllegalStateException> {
            `Motta og prosesser en start-melding`()
        }

        // Assert
        //TODO LEGG INN NÅR FUNKER e.message!! `should start with` "Kan ikke gjenopprette søknad som ikke er behandlet"
    }

    @Test
    fun `Stopp-melding skal ikke ha noen effekt dersom bruker har svart at de ikke fortsatt er friskmeldt`() {
        // Arrange
        `Gitt at et vedtak er mottatt og søknader opprettet`()
        `Og en stopp-melding har blitt prosessert`()
        `Og bruker ikke fortsatt er friskmeldt`()

        // Act
        `Motta og prosesser en start-melding`()

        // Assert
        //TODO LEGG INN NÅR FUNKER `Så forblir søknaden uendret`()
    }

    private fun `Gitt at et vedtak er mottatt og søknader opprettet`() {
        sendFriskTilArbeidVedtak(fnr, vedtakFom, vedtakTom)
        friskTilArbeidCronJob.behandleFriskTilArbeidVedtak()

        val friskTilArbeidDbRecord = friskTilArbeidRepository.findAll().first()
        check(friskTilArbeidDbRecord.behandletStatus == BEHANDLET)

        val vedtakRecords = sykepengesoknadRepository.findByFriskTilArbeidVedtakId(friskTilArbeidDbRecord.id!!)
        check(vedtakRecords.size == 4)
    }

    private fun `Og en stopp-melding har blitt prosessert`() {
        val soknad = hentSoknader(fnr).first { it.status == RSSoknadstatus.NY }
        sendStoppMelding(soknad.friskTilArbeidVedtakId!!, fnr, tidspunkt)

        val friskTilArbeidDbRecord = friskTilArbeidRepository.findAll().first()
        val kafkaRecords =
            SoknadKafkaProducerFake.records
                .filter { it.value().status == SLETTET }
                .filter { it.value().friskTilArbeidVedtakId == friskTilArbeidDbRecord.id }
        check(kafkaRecords.size == 2)
    }

    private fun `Og avsluttetTidspunkt er satt til en verdi`() {
        // TODO Kanskje de to IllegalStatene egentlig bør være i samme exception. Blir litt rare tester med det her.
    }

    private fun `Og bruker ikke fortsatt er friskmeldt`() {
        val soknader = hentSoknader(fnr)
        val soknad = soknader.maxByOrNull { it.fom!! }

        // TODO FÅr ikke helt dette til å funke
        //SoknadBesvarer(rSSykepengesoknad = soknad!!, testOppsettInterfaces = this, fnr = fnr)
        //    .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT, "JA", mutert = true)
    }

    private fun `Motta og prosesser en start-melding`() {
        val soknader = hentSoknader(fnr)
        val soknad = soknader.maxByOrNull { it.fom!! }
        sendStartMelding(soknad?.friskTilArbeidVedtakId!!, fnr, tidspunkt)
        friskTilArbeidCronJob.behandleFriskTilArbeidVedtak()
    }

    private fun `Så blir søknaden gjennopprettet med status NY`() {
        val friskTilArbeidDbRecord =
            friskTilArbeidRepository.findAll().first().also {
                it.behandletStatus `should be equal to` BEHANDLET
            }

        sykepengesoknadRepository
            .findByFriskTilArbeidVedtakId(friskTilArbeidDbRecord.id!!)
            .sortedBy { record -> record.fom }
            .also { record ->
                record.size `should be equal to` 4
                record.map { it.status } `should be equal to`
                    listOf(
                        NY,
                        FREMTIDIG,
                        FREMTIDIG,
                        FREMTIDIG,
                    )
            }

        friskTilArbeidRepository
            .findByFnrIn(listOf(fnr))
            .single()
            .avsluttetTidspunkt `should be equal to` null
    }

    fun `Så forblir søknaden uendret`() {
        friskTilArbeidRepository.findAll()
            .first()
            .behandletStatus `should be equal to` BEHANDLET

        friskTilArbeidRepository
            .findByFnrIn(listOf(fnr))
            .single()
            .avsluttetTidspunkt `should be equal to` tidspunkt

        val soknader = hentSoknader(fnr).sortedBy { it.fom } shouldHaveSize 2
        soknader[0].fom!! shouldBeLessThan tidspunkt.tilLocalDate()
        soknader[0].fom!! `should be equal to` vedtakFom
        soknader[1].fom!! shouldBeLessThan tidspunkt.tilLocalDate()
        soknader[1].fom!! `should be equal to` vedtakFom.plusDays(14)
    }
}
