package no.nav.helse.flex.frisktilarbeid

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.frisktilarbeid.BehandletStatus.NY
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class FriskTilArbeidServiceStorageTest : FriskTilArbeidServiceTestBase() {
    @Test
    fun `Lagrer vedtak med status FATTET`() {
        val vedtakId = lagreVedtak()

        vedtak(vedtakId).also {
            it.behandletStatus shouldBe NY
            it.ignorerArbeidssokerregister shouldBe null
        }
    }

    @Test
    fun `Lagrer vedtak med status FATTET som ikke skal sjekkes mot Arbeidssøkerregisteret`() {
        val status = lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET)
        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            vedtakStatusMelding(status, ignorerArbeidssokerregister = true),
        )

        friskTilArbeidRepository.findAll().single().also {
            it.vedtak
                .shouldNotBeNull()
                .value
                .shouldNotBeNull()
                .tilFriskTilArbeidVedtakStatus() shouldBe status
            it.behandletStatus shouldBe NY
            it.ignorerArbeidssokerregister shouldBe true
        }
    }

    @Test
    fun `Lagrer ikke vedtak med status FERDIG_BEHANDLET`() {
        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            vedtakStatusMelding(lagFriskTilArbeidVedtakStatus(fnr, Status.FERDIG_BEHANDLET)),
        )

        friskTilArbeidRepository.findAll().toList().shouldBeEmpty()
    }

    @Test
    fun `Lagrer ikke vedtak med statusAt før tidspunktForOvertakelse norsk tid`() {
        val vedtak =
            lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET)
                .copy(statusAt = OffsetDateTime.of(LocalDateTime.of(2025, 3, 9, 22, 59, 0), ZoneOffset.UTC))

        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            vedtakStatusMelding(vedtak),
        )

        friskTilArbeidRepository.findAll().toList().shouldBeEmpty()
    }

    @Test
    fun `Lagrer vedtak med statusAt etter tidspunktForOvertakelse norsk tid`() {
        val status =
            lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET)
                .copy(statusAt = OffsetDateTime.of(LocalDateTime.of(2025, 3, 9, 23, 1, 0), ZoneOffset.UTC))

        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            vedtakStatusMelding(status),
        )

        friskTilArbeidRepository.findAll().single().also {
            it.vedtak
                .shouldNotBeNull()
                .value
                .shouldNotBeNull()
                .tilFriskTilArbeidVedtakStatus() shouldBe status
            it.behandletStatus shouldBe NY
        }
    }

    @Test
    fun `Lagrer vedtak nøyaktig ved tidspunktForOvertakelse norsk tid`() {
        val status =
            lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET)
                .copy(statusAt = OffsetDateTime.parse("2025-03-09T23:00:00Z"))

        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            vedtakStatusMelding(status),
        )

        friskTilArbeidRepository.findAll().single().also {
            it.vedtak
                .shouldNotBeNull()
                .value
                .shouldNotBeNull()
                .tilFriskTilArbeidVedtakStatus() shouldBe status
            it.behandletStatus shouldBe NY
        }
    }
}
