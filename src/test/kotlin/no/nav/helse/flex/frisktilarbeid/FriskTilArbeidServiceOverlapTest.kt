package no.nav.helse.flex.frisktilarbeid

import io.kotest.matchers.shouldBe
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.frisktilarbeid.BehandletStatus.BEHANDLET
import no.nav.helse.flex.frisktilarbeid.BehandletStatus.NY
import no.nav.helse.flex.frisktilarbeid.BehandletStatus.OVERLAPP
import no.nav.helse.flex.util.tilOsloInstant
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FriskTilArbeidServiceOverlapTest : FriskTilArbeidServiceTestBase() {
    @Test
    fun `Lagrer to perioder som ikke overlapper`() {
        val førsteVedtak = lagreVedtak(Periode(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7)))
        val andreVedtak = lagreVedtak(Periode(LocalDate.of(2024, 1, 8), LocalDate.of(2024, 1, 15)))

        vedtak(førsteVedtak).behandletStatus shouldBe NY
        vedtak(andreVedtak).behandletStatus shouldBe NY
    }

    @Test
    fun `Lagrer to overlappende perioder for to forskjellige brukere`() {
        val førsteVedtak = lagreVedtak(Periode(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7)))
        val andreVedtak =
            lagreVedtak(
                Periode(LocalDate.of(2024, 1, 5), LocalDate.of(2024, 1, 13)),
                personident = "22222222222",
            )

        vedtak(førsteVedtak).behandletStatus shouldBe NY
        vedtak(andreVedtak).behandletStatus shouldBe NY
    }

    @Test
    fun `Overlapp ved lagring av to vedtak med samme fom og tom`() {
        val førsteVedtak = lagreVedtak(Periode(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7)))
        behandleVedtak()
        val andreVedtak = lagreVedtak(Periode(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7)))
        behandleVedtak()

        forventFørsteVedtakBehandletOgAndreHarStatus(førsteVedtak, andreVedtak, OVERLAPP)
    }

    @Test
    fun `Overlapp ved lagring av nytt vedtak med fom før eksisterende vedtaks tom`() {
        val førsteVedtak = lagreVedtak(Periode(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7)))
        behandleVedtak()
        val andreVedtak = lagreVedtak(Periode(LocalDate.of(2024, 1, 5), LocalDate.of(2024, 1, 13)))
        behandleVedtak()

        forventFørsteVedtakBehandletOgAndreHarStatus(førsteVedtak, andreVedtak, OVERLAPP)
    }

    @Test
    fun `Overlapp ved lagring av nytt vedtak med tom etter eksisterende vedtak fom`() {
        val førsteVedtak = lagreVedtak(Periode(LocalDate.of(2024, 1, 5), LocalDate.of(2024, 1, 13)))
        behandleVedtak()
        val andreVedtak = lagreVedtak(Periode(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7)))
        behandleVedtak()

        forventFørsteVedtakBehandletOgAndreHarStatus(førsteVedtak, andreVedtak, OVERLAPP)
    }

    @Test
    fun `Overlapp ved lagring av nytt vedtak med fom lik eksisterende vedtaks tom`() {
        val førsteVedtak = lagreVedtak(Periode(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7)))
        behandleVedtak()
        val andreVedtak = lagreVedtak(Periode(LocalDate.of(2024, 1, 7), LocalDate.of(2024, 1, 14)))
        behandleVedtak()

        forventFørsteVedtakBehandletOgAndreHarStatus(førsteVedtak, andreVedtak, OVERLAPP)
    }

    @Test
    fun `Overlapp ved lagring av nytt vedtak med tom lik eksisterende vedtaks fom`() {
        val førsteVedtak = lagreVedtak(Periode(LocalDate.of(2024, 1, 7), LocalDate.of(2024, 1, 14)))
        behandleVedtak()
        val andreVedtak = lagreVedtak(Periode(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7)))
        behandleVedtak()

        forventFørsteVedtakBehandletOgAndreHarStatus(førsteVedtak, andreVedtak, OVERLAPP)
    }

    @Test
    fun `Vedtak overlapper ikke når tom er før avsluttetTidspunkt, men etter første vedtaks tom`() {
        val førsteVedtak = lagreVedtak(Periode(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7)))
        behandleVedtak()
        settAvsluttetTidspunkt(førsteVedtak, LocalDate.of(2024, 1, 9))

        val andreVedtak = lagreVedtak(Periode(LocalDate.of(2024, 1, 8), LocalDate.of(2024, 1, 14)))
        behandleVedtak()

        vedtak(førsteVedtak).behandletStatus shouldBe BEHANDLET
        vedtak(andreVedtak).behandletStatus shouldBe BEHANDLET
    }

    @Test
    fun `Vedtak overlapper ikke når fom er før første vedtaks tom, men etter avsluttetTidspunkt`() {
        val førsteVedtak = lagreVedtak(Periode(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7)))
        behandleVedtak()
        settAvsluttetTidspunkt(førsteVedtak, LocalDate.of(2024, 1, 6))

        val andreVedtak = lagreVedtak(Periode(LocalDate.of(2024, 1, 7), LocalDate.of(2024, 1, 14)))
        behandleVedtak()

        vedtak(førsteVedtak).behandletStatus shouldBe BEHANDLET
        vedtak(andreVedtak).behandletStatus shouldBe BEHANDLET
    }

    @Test
    fun `Vedtak overlapper når fom er før avsluttetTidspunkt`() {
        val førsteVedtak = lagreVedtak(Periode(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7)))
        behandleVedtak()
        settAvsluttetTidspunkt(førsteVedtak, LocalDate.of(2024, 1, 6))

        val andreVedtak = lagreVedtak(Periode(LocalDate.of(2024, 1, 6), LocalDate.of(2024, 1, 14)))
        behandleVedtak()

        vedtak(førsteVedtak).behandletStatus shouldBe BEHANDLET
        vedtak(andreVedtak).behandletStatus shouldBe OVERLAPP
    }

    private fun settAvsluttetTidspunkt(
        vedtakId: String,
        dato: LocalDate,
    ) {
        friskTilArbeidRepository.save(
            vedtak(vedtakId).copy(
                avsluttetTidspunkt = dato.atStartOfDay().tilOsloInstant(),
            ),
        )
    }

    private fun forventFørsteVedtakBehandletOgAndreHarStatus(
        førsteVedtakId: String,
        andreVedtakId: String,
        forventetStatus: BehandletStatus,
    ) {
        vedtak(førsteVedtakId).behandletStatus shouldBe BEHANDLET
        vedtak(andreVedtakId).behandletStatus shouldBe forventetStatus
    }
}
