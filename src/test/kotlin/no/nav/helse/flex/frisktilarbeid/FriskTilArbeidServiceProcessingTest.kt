package no.nav.helse.flex.frisktilarbeid

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import mockwebserver3.MockResponse
import no.nav.helse.flex.client.arbeidssokerregister.ArbeidssokerperiodeResponse
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.frisktilarbeid.BehandletStatus.BEHANDLET
import no.nav.helse.flex.frisktilarbeid.BehandletStatus.INGEN_ARBEIDSSOKERPERIODE
import no.nav.helse.flex.frisktilarbeid.BehandletStatus.NY
import no.nav.helse.flex.frisktilarbeid.BehandletStatus.OVERLAPP_OK
import no.nav.helse.flex.frisktilarbeid.BehandletStatus.SISTE_ARBEIDSSOKERPERIODE_AVSLUTTET
import no.nav.helse.flex.mockdispatcher.ArbeidssokerregisterMockDispatcher
import no.nav.helse.flex.mockdispatcher.skapArbeidssokerperiodeResponse
import no.nav.helse.flex.util.serialisertTilString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.HttpServerErrorException
import java.time.LocalDate

class FriskTilArbeidServiceProcessingTest : FriskTilArbeidServiceTestBase() {
    @Test
    fun `Person er ikke i arbeidssøkerregisteret`() {
        arbeidssokerregisteretHarPerioder()
        val vedtakId = lagreVedtak(Periode(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7)))

        behandleVedtak()

        vedtak(vedtakId).behandletStatus shouldBe INGEN_ARBEIDSSOKERPERIODE
        forventIngenSoknaderEllerKafkaHendelser()
    }

    @Test
    fun `Person er avsluttet i arbeidssøkerregisteret`() {
        arbeidssokerregisteretHarPerioder(skapArbeidssokerperiodeResponse(avsluttet = true))
        val vedtakId = lagreVedtak(Periode(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7)))

        behandleVedtak()

        vedtak(vedtakId).behandletStatus shouldBe SISTE_ARBEIDSSOKERPERIODE_AVSLUTTET
        forventIngenSoknaderEllerKafkaHendelser()
    }

    @Test
    fun `Flere arbeidssøkerperioder gir ingen entydig periode og oppretter ikke søknader`() {
        arbeidssokerregisteretHarPerioder(
            skapArbeidssokerperiodeResponse(),
            skapArbeidssokerperiodeResponse(),
        )
        val vedtakId = lagreVedtak(Periode(LocalDate.of(2025, 4, 7), LocalDate.of(2025, 4, 20)))

        behandleVedtak().shouldBeEmpty()
        vedtak(vedtakId).behandletStatus shouldBe INGEN_ARBEIDSSOKERPERIODE
        forventIngenSoknaderEllerKafkaHendelser()
    }

    @Test
    fun `Klientfeil fra arbeidssøkerregisteret lar vedtaket stå ubehandlet uten søknader`() {
        ArbeidssokerregisterMockDispatcher.enqueue(MockResponse(code = 500))
        val vedtakId = lagreVedtak(Periode(LocalDate.of(2025, 4, 7), LocalDate.of(2025, 4, 20)))
        val vedtakFor = vedtak(vedtakId)

        val feil = assertThrows<HttpServerErrorException> { behandleVedtak() }

        feil.statusCode.value() shouldBe 500
        vedtak(vedtakId) shouldBe vedtakFor
        forventIngenSoknaderEllerKafkaHendelser()
    }

    @Test
    fun `Behandler ett av to vedtak`() {
        val førsteVedtak = lagreVedtak(personident = fnr)
        val andreVedtak = lagreVedtak(personident = "33333333333")

        friskTilArbeidService.behandleFriskTilArbeidVedtakStatus(1)

        val vedtakEtterBehandling = listOf(vedtak(førsteVedtak), vedtak(andreVedtak))
        vedtakEtterBehandling.count { it.behandletStatus == BEHANDLET } shouldBe 1
        vedtakEtterBehandling.count { it.behandletStatus == NY } shouldBe 1

        val behandletVedtak = vedtakEtterBehandling.single { it.behandletStatus == BEHANDLET }
        val ikkeBehandletVedtak = vedtakEtterBehandling.single { it.behandletStatus == NY }
        val soknader = sykepengesoknadRepository.findAll().toList()
        soknader shouldHaveSize 1
        soknader.single().fnr shouldBe behandletVedtak.fnr
        soknader.single().friskTilArbeidVedtakId shouldBe behandletVedtak.id
        ikkeBehandletVedtak.id shouldBe listOf(førsteVedtak, andreVedtak).single { it != behandletVedtak.id }
    }

    @Test
    fun `Behandler et vedtak andre gang for å generer søknad for dag som mangler`() {
        val vedtakId = lagreVedtak(Periode(LocalDate.of(2025, 4, 7), LocalDate.of(2025, 5, 4)), personident = "33333333333")

        behandleVedtak().sortedBy { it.fom }.also {
            it shouldHaveSize 2
            it[0].fom shouldBe LocalDate.of(2025, 4, 7)
            it[0].tom shouldBe LocalDate.of(2025, 4, 20)
            it[1].fom shouldBe LocalDate.of(2025, 4, 21)
            it[1].tom shouldBe LocalDate.of(2025, 5, 4)
        }
        vedtak(vedtakId).behandletStatus shouldBe BEHANDLET

        utvidVedtakOgSettTilNy(vedtakId, LocalDate.of(2025, 5, 5))

        behandleVedtak().also {
            it.size shouldBe 1
            it.first().fom shouldBe LocalDate.of(2025, 5, 5)
            it.first().tom shouldBe LocalDate.of(2025, 5, 5)
        }
        vedtak(vedtakId).behandletStatus shouldBe BEHANDLET
    }

    @Test
    fun `Filtrerer bort eksisterende vedtak med status OVERLAPP_OK`() {
        val vedtakId =
            lagreVedtak(
                Periode(LocalDate.of(2025, 3, 10), LocalDate.of(2025, 6, 1)),
                personident = "44444444444",
            )

        behandleVedtak() shouldHaveSize 6

        utvidVedtakOgSettTilNy(vedtakId, LocalDate.of(2025, 6, 2))

        val overlappendeVedtakId =
            lagreVedtak(
                Periode(LocalDate.of(2025, 3, 11), LocalDate.of(2025, 6, 2)),
                personident = "44444444444",
            )
        friskTilArbeidRepository.save(vedtak(overlappendeVedtakId).copy(behandletStatus = OVERLAPP_OK))

        val genererteSoknader = behandleVedtak()
        genererteSoknader shouldHaveSize 1
        genererteSoknader.single().fom shouldBe LocalDate.of(2025, 6, 2)
        genererteSoknader.single().tom shouldBe LocalDate.of(2025, 6, 2)
        genererteSoknader.single().friskTilArbeidVedtakId shouldBe vedtakId
        vedtak(vedtakId).behandletStatus shouldBe BEHANDLET
        vedtak(overlappendeVedtakId).behandletStatus shouldBe OVERLAPP_OK
    }

    private fun arbeidssokerregisteretHarPerioder(vararg perioder: ArbeidssokerperiodeResponse) {
        ArbeidssokerregisterMockDispatcher.enqueue(MockResponse(code = 200, body = perioder.toList().serialisertTilString()))
    }

    private fun utvidVedtakOgSettTilNy(
        vedtakId: String,
        nyTom: LocalDate,
    ) {
        friskTilArbeidRepository.save(
            vedtak(vedtakId).copy(tom = nyTom, behandletStatus = NY),
        )
    }
}
