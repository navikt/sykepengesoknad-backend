package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Sykmeldingstype
import no.nav.helse.flex.domain.sykmelding.Gradert
import no.nav.helse.flex.domain.sykmelding.Sykmeldingsperiode
import no.nav.helse.flex.domain.sykmelding.tilSykmeldingTilSoknadOpprettelse
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import org.amshove.kluent.`should be empty`
import org.amshove.kluent.`should contain`
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

class SammenlignSykmeldingTest {
    val fnr = "12345678910"

    private fun lagKafkaMessage(
        fnr: String = this.fnr,
        timestamp: OffsetDateTime = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS),
    ): SykmeldingKafkaMessageDTO {
        val statusKafkaMessage = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr, timestamp = timestamp)
        val sykmelding =
            skapArbeidsgiverSykmelding(
                sykmeldingId = statusKafkaMessage.event.sykmeldingId,
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 31),
            )
        return SykmeldingKafkaMessageDTO(
            sykmelding = sykmelding,
            event = statusKafkaMessage.event,
            kafkaMetadata = statusKafkaMessage.kafkaMetadata,
        )
    }

    @Test
    fun `ingen forskjeller når meldingene er like`() {
        val original = lagKafkaMessage()
        original.tilSykmeldingTilSoknadOpprettelse().finnForskjeller(original.tilSykmeldingTilSoknadOpprettelse()).`should be empty`()
    }

    @Test
    fun `finner forskjell på sykmeldingId`() {
        val original = lagKafkaMessage()
        val hentet = original.tilSykmeldingTilSoknadOpprettelse().copy(sykmeldingId = "annet-id")
        hentet.finnForskjeller(original.tilSykmeldingTilSoknadOpprettelse()) `should contain` "sykmeldingTilSoknadOpprettelse.sykmeldingId"
    }

    @Test
    fun `finner forskjell på erUtlandskSykmelding`() {
        val original = lagKafkaMessage()
        val hentet = original.tilSykmeldingTilSoknadOpprettelse().copy(erUtlandskSykmelding = true)
        hentet.finnForskjeller(original.tilSykmeldingTilSoknadOpprettelse()) `should contain`
            "sykmeldingTilSoknadOpprettelse.erUtlandskSykmelding"
    }

    @Test
    fun `finner forskjell i sykmeldingsperioder`() {
        val original = lagKafkaMessage()
        val endretPeriode =
            Sykmeldingsperiode(
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 31),
                type = Sykmeldingstype.BEHANDLINGSDAGER,
                gradert = null,
                reisetilskudd = false,
            )
        val hentet = original.tilSykmeldingTilSoknadOpprettelse().copy(sykmeldingsperioder = listOf(endretPeriode))
        hentet.finnForskjeller(original.tilSykmeldingTilSoknadOpprettelse()) `should contain`
            "sykmeldingTilSoknadOpprettelse.sykmeldingsperioder[0].type"
    }

    @Test
    fun `finner forskjell rekursivt i nestet data class i sykmeldingsperioder`() {
        val original = lagKafkaMessage()
        val originalPeriode = original.tilSykmeldingTilSoknadOpprettelse().sykmeldingsperioder.first()
        val endretPeriode = originalPeriode.copy(gradert = Gradert(grad = 50, reisetilskudd = false))
        val hentet = original.tilSykmeldingTilSoknadOpprettelse().copy(sykmeldingsperioder = listOf(endretPeriode))
        hentet.finnForskjeller(original.tilSykmeldingTilSoknadOpprettelse()) `should contain`
            "sykmeldingTilSoknadOpprettelse.sykmeldingsperioder[0].gradert"
    }

    @Test
    fun `ingen forskjell når original eventTimestamp har høyere presisjon enn hentet`() {
        val original = lagKafkaMessage(timestamp = OffsetDateTime.parse("2026-03-19T11:14:36.979635500Z"))
        val hentet = original.copy(event = original.event.copy(timestamp = OffsetDateTime.parse("2026-03-19T11:14:36.979636Z")))
        hentet.tilSykmeldingTilSoknadOpprettelse().finnForskjeller(original.tilSykmeldingTilSoknadOpprettelse()).`should be empty`()
    }

    @Test
    fun `forskjell når original eventTimestamp er litt forskjellig`() {
        val original = lagKafkaMessage(timestamp = OffsetDateTime.parse("2026-03-19T09:01:32.859068Z"))
        val hentet = original.copy(event = original.event.copy(timestamp = OffsetDateTime.parse("2026-03-19T09:01:32.859069Z")))
        hentet.tilSykmeldingTilSoknadOpprettelse().finnForskjeller(original.tilSykmeldingTilSoknadOpprettelse()) `should contain`
            "sykmeldingTilSoknadOpprettelse.eventTimestamp"
    }
}
