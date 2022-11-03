package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be equal to`
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class SoknadIdTest {
    val fnr = "124919274"
    val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
        fnr = fnr,
        arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
        statusEvent = STATUS_SENDT,
        arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123454543", orgNavn = "Kebabbiten")
    )
    val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
    val sykmelding = skapArbeidsgiverSykmelding(
        sykmeldingId = sykmeldingId,
        fom = LocalDate.of(2020, 1, 1),
        tom = LocalDate.of(2020, 3, 15),
    )

    val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
        sykmelding = sykmelding,
        event = sykmeldingStatusKafkaMessageDTO.event,
        kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
    )

    @Test
    fun `skap søknad id gir samme resultat for samme sykmeldingKafkaMessage`() {
        val soknadIdEn = sykmeldingKafkaMessage.skapSoknadsId(LocalDate.now(), LocalDate.now().plusDays(1))
        val soknadIdTo = sykmeldingKafkaMessage.skapSoknadsId(LocalDate.now(), LocalDate.now().plusDays(1))

        soknadIdEn `should be equal to` soknadIdTo
    }

    @Test
    fun `skap søknad id gir ulikt resultat når tom er forskjellig`() {
        val soknadIdEn = sykmeldingKafkaMessage.skapSoknadsId(LocalDate.now(), LocalDate.now().plusDays(1))
        val soknadIdTo = sykmeldingKafkaMessage.skapSoknadsId(LocalDate.now(), LocalDate.now())

        soknadIdEn `should not be equal to` soknadIdTo
    }

    @Test
    fun `skap søknad id gir ulikt resultat når timestamp er forskjellig`() {
        val soknadIdEn = sykmeldingKafkaMessage.skapSoknadsId(LocalDate.now(), LocalDate.now())

        val soknadIdTo = sykmeldingKafkaMessage.copy(
            event = sykmeldingKafkaMessage.event.copy(
                timestamp = OffsetDateTime.now().plusDays(1)
            )
        ).skapSoknadsId(LocalDate.now(), LocalDate.now())

        soknadIdEn `should not be equal to` soknadIdTo
    }
}
