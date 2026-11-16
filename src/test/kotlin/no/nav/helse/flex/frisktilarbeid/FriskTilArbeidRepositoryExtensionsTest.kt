package no.nav.helse.flex.frisktilarbeid

import io.kotest.matchers.shouldBe
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.util.tilOsloInstant
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class FriskTilArbeidRepositoryExtensionsTest {
    @Test
    fun `skalSjekkeArbeidssokerregisteret er false når ignorerArbeidssokerregister er true`() {
        val vedtak = lagVedtak(ignorerArbeidssokerregister = true)

        vedtak.skalSjekkeArbeidssokerregisteret() shouldBe false
    }

    @Test
    fun `skalSjekkeArbeidssokerregisteret er true når ignorerArbeidssokerregister er false`() {
        val vedtak = lagVedtak(ignorerArbeidssokerregister = false)

        vedtak.skalSjekkeArbeidssokerregisteret() shouldBe true
    }

    @Test
    fun `skalSjekkeArbeidssokerregisteret er true når ignorerArbeidssokerregister er null`() {
        val vedtak = lagVedtak(ignorerArbeidssokerregister = null)

        vedtak.skalSjekkeArbeidssokerregisteret() shouldBe true
    }

    @Test
    fun `tilPeriode bruker tom når avsluttetTidspunkt er null`() {
        val vedtak = lagVedtak(avsluttetTidspunkt = null)

        vedtak.tilPeriode() shouldBe Periode(vedtak.fom, vedtak.tom)
    }

    @Test
    fun `tilPeriode bruker avsluttetTidspunkt når det er før tom`() {
        val vedtak =
            lagVedtak(
                tom = LocalDate.of(2024, 1, 31),
                avsluttetTidspunkt = LocalDateTime.of(2024, 1, 20, 12, 0).tilOsloInstant(),
            )

        vedtak.tilPeriode() shouldBe Periode(vedtak.fom, LocalDate.of(2024, 1, 20))
    }

    @Test
    fun `tilPeriode bruker tom når avsluttetTidspunkt er etter tom`() {
        val vedtak =
            lagVedtak(
                tom = LocalDate.of(2024, 1, 31),
                avsluttetTidspunkt = LocalDateTime.of(2024, 2, 1, 0, 0).tilOsloInstant(),
            )

        vedtak.tilPeriode() shouldBe Periode(vedtak.fom, LocalDate.of(2024, 1, 31))
    }

    @Test
    fun `tilPeriode beholder tom når avsluttetTidspunkt er på siste dag`() {
        val vedtak =
            lagVedtak(
                avsluttetTidspunkt = LocalDateTime.of(2024, 1, 31, 12, 0).tilOsloInstant(),
            )

        vedtak.tilPeriode() shouldBe Periode(vedtak.fom, vedtak.tom)
    }

    @ParameterizedTest(name = "{0} avslutter perioden på norsk dato {1}")
    @CsvSource(
        "2024-01-19T23:30:00Z, 2024-01-20",
        "2024-06-19T22:30:00Z, 2024-06-20",
    )
    fun `tilPeriode bruker norsk dato ved vintertid og sommertid`(
        tidspunkt: String,
        norskDato: String,
    ) {
        val dato = LocalDate.parse(norskDato)
        val vedtak =
            lagVedtak(
                fom = dato.minusDays(10),
                tom = dato.plusDays(10),
                avsluttetTidspunkt = Instant.parse(tidspunkt),
            )

        vedtak.tilPeriode() shouldBe Periode(vedtak.fom, dato)
    }

    private fun lagVedtak(
        fom: LocalDate = LocalDate.of(2024, 1, 1),
        tom: LocalDate = LocalDate.of(2024, 1, 31),
        avsluttetTidspunkt: Instant? = null,
        ignorerArbeidssokerregister: Boolean? = null,
    ): FriskTilArbeidVedtakDbRecord =
        FriskTilArbeidVedtakDbRecord(
            id = UUID.randomUUID().toString(),
            vedtakUuid = UUID.randomUUID().toString(),
            key = "test-key",
            opprettet = Instant.parse("2024-01-01T00:00:00Z"),
            fnr = "11111111111",
            fom = fom,
            tom = tom,
            vedtak = null,
            behandletStatus = BehandletStatus.NY,
            behandletTidspunkt = null,
            avsluttetTidspunkt = avsluttetTidspunkt,
            ignorerArbeidssokerregister = ignorerArbeidssokerregister,
        )
}
