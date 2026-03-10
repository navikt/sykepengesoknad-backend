package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import org.amshove.kluent.`should be empty`
import org.amshove.kluent.`should contain`
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class SammenlignSykmeldingTest {
    val fnr = "12345678910"

    private fun lagKafkaMessage(
        fnr: String = this.fnr,
        timestamp: OffsetDateTime = OffsetDateTime.now(),
    ): SykmeldingKafkaMessage {
        val statusKafkaMessage = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr, timestamp = timestamp)
        val sykmelding =
            skapArbeidsgiverSykmelding(
                sykmeldingId = statusKafkaMessage.event.sykmeldingId,
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 31),
            )
        return SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = statusKafkaMessage.event,
            kafkaMetadata = statusKafkaMessage.kafkaMetadata,
        )
    }

    @Test
    fun `ingen forskjeller når meldingene er like`() {
        val original = lagKafkaMessage()
        original.copy().finnForskjeller(original).`should be empty`()
    }

    @Test
    fun `ingen forskjeller ved ulik timestamp på kafkaMetadata`() {
        val original = lagKafkaMessage(timestamp = OffsetDateTime.now().minusHours(1))
        val hentet =
            original.copy(
                kafkaMetadata = original.kafkaMetadata.copy(timestamp = OffsetDateTime.now()),
            )
        hentet.finnForskjeller(original).`should be empty`()
    }

    @Test
    fun `ingen forskjeller ved ulik timestamp på event`() {
        val original = lagKafkaMessage(timestamp = OffsetDateTime.now().minusHours(1))
        val hentet =
            original.copy(
                event = original.event.copy(timestamp = OffsetDateTime.now()),
            )
        hentet.finnForskjeller(original).`should be empty`()
    }

    @Test
    fun `finner forskjell på kafkaMetadata fnr`() {
        val original = lagKafkaMessage()
        val hentet =
            original.copy(
                kafkaMetadata = original.kafkaMetadata.copy(fnr = "99999999999"),
            )
        hentet.finnForskjeller(original) `should contain` "kafkaMetadata.fnr"
    }

    @Test
    fun `finner forskjell på event sykmeldingId`() {
        val original = lagKafkaMessage()
        val hentet =
            original.copy(
                event = original.event.copy(sykmeldingId = "annet-id"),
            )
        hentet.finnForskjeller(original) `should contain` "event.sykmeldingId"
    }

    @Test
    fun `finner forskjell på sykmelding`() {
        val original = lagKafkaMessage()
        val hentet =
            original.copy(
                sykmelding = original.sykmelding.copy(tiltakArbeidsplassen = "Tilrettelegging"),
            )
        hentet.finnForskjeller(original) `should contain` "sykmelding.tiltakArbeidsplassen"
    }

    @Test
    fun `finner forskjell rekursivt i nestet data class`() {
        val original = lagKafkaMessage()
        val hentet =
            original.copy(
                sykmelding =
                    original.sykmelding.copy(
                        arbeidsgiver = original.sykmelding.arbeidsgiver.copy(navn = "Annet firma"),
                    ),
            )
        hentet.finnForskjeller(original) `should contain` "sykmelding.arbeidsgiver.navn"
    }

    @Test
    fun `finner forskjell i liste`() {
        val original = lagKafkaMessage()
        val endretPeriode =
            original.sykmelding.sykmeldingsperioder
                .first()
                .copy(type = PeriodetypeDTO.BEHANDLINGSDAGER)
        val hentet =
            original.copy(
                sykmelding =
                    original.sykmelding.copy(
                        sykmeldingsperioder = listOf(endretPeriode),
                    ),
            )
        hentet.finnForskjeller(original) `should contain` "sykmelding.sykmeldingsperioder[0].type"
    }
}
