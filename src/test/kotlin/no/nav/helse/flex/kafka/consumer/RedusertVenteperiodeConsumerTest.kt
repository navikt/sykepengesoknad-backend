package no.nav.helse.flex.kafka.consumer

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.repository.RedusertVenteperiodeRepository
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.helse.flex.util.serialisertTilString
import no.nav.syfo.sykmelding.kafka.model.STATUS_BEKREFTET
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class RedusertVenteperiodeConsumerTest : FellesTestOppsett() {
    @Autowired
    private lateinit var redusertVenteperiodeRepository: RedusertVenteperiodeRepository

    @Autowired
    private lateinit var redusertVenteperiodeConsumer: RedusertVenteperiodeConsumer

    final val kafkaMessage = redusertVenteperiodeSykmelding()
    final val sykmeldingId = kafkaMessage.sykmelding.id

    @Test
    @Order(1)
    fun `Sykmelding med redusert venteperiode lagres`() {
        redusertVenteperiodeConsumer.prosesserKafkaMelding(
            sykmeldingId,
            kafkaMessage.serialisertTilString(),
        )

        redusertVenteperiodeRepository.existsBySykmeldingId(sykmeldingId) shouldBeEqualTo true
    }

    @Test
    @Order(2)
    fun `Kan lese inn samme sykmelding flere ganger`() {
        redusertVenteperiodeConsumer.prosesserKafkaMelding(
            sykmeldingId,
            kafkaMessage.serialisertTilString(),
        )

        redusertVenteperiodeRepository.existsBySykmeldingId(sykmeldingId) shouldBeEqualTo true
    }

    @Test
    @Order(3)
    fun `Fjernes ved tombstone event`() {
        redusertVenteperiodeConsumer.prosesserKafkaMelding(
            sykmeldingId,
            null,
        )

        redusertVenteperiodeRepository.existsBySykmeldingId(sykmeldingId) shouldBeEqualTo false
    }

    private fun redusertVenteperiodeSykmelding(): SykmeldingKafkaMessage {
        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(
                fnr = "12345678901",
                arbeidssituasjon = Arbeidssituasjon.FRILANSER,
                statusEvent = STATUS_BEKREFTET,
            )

        val sykmelding =
            skapArbeidsgiverSykmelding(
                sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
                fom = LocalDate.now(),
                tom = LocalDate.now(),
            ).copy(
                harRedusertArbeidsgiverperiode = true,
            )

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        return sykmeldingKafkaMessage
    }
}
