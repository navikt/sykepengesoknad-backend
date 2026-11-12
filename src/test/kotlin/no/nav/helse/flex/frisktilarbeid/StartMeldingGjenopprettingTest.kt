package no.nav.helse.flex.frisktilarbeid

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.helse.flex.domain.Soknadstatus.FREMTIDIG
import no.nav.helse.flex.domain.Soknadstatus.NY
import no.nav.helse.flex.frisktilarbeid.BehandletStatus.BEHANDLET
import no.nav.helse.flex.util.osloZone
import org.junit.jupiter.api.Test

class StartMeldingGjenopprettingTest : StartMeldingTestOppsett() {
    @Test
    fun `gitt slettede søknader i vedtaksperioden når START-melding mottas så gjenopprettes søknadene`() {
        gittAtEtVedtakErMottattOgSoknaderOpprettet()
        stoppMeldingHarSlettetSoknader(stoppTidspunkt)

        mottaOgProsesserStartMelding()

        val soknader = hentSoknader()
        hentVedtak().also {
            it.behandletStatus shouldBe BEHANDLET
            it.avsluttetTidspunkt shouldBe null
        }
        soknader.map { it.fom to it.status } shouldBe
            listOf(
                forsteSoknadFom to NY,
                andreSoknadFom to FREMTIDIG,
                tredjeSoknadFom to FREMTIDIG,
                fjerdeSoknadFom to FREMTIDIG,
            )
        val gjenopprettedeSoknadIder = soknader.filter { it.fom!! > tidspunkt }.map { it.sykepengesoknadUuid }
        publisertFremtidigEventFor(gjenopprettedeSoknadIder)
    }

    @Test
    fun `gitt alle søknader slettet når START-melding mottas så gjenopprettes alle med passert tom aktivert`() {
        gittAtEtVedtakErMottattOgSoknaderOpprettet()
        val slettedeSoknadIder =
            stoppMeldingHarSlettetSoknader(vedtakFom.minusDays(1).atStartOfDay(osloZone).toInstant())
        hentSoknader().shouldBeEmpty()

        mottaOgProsesserStartMelding()

        soknaderMedPassertTomErAktivert()
        hentVedtak().avsluttetTidspunkt shouldBe null

        val soknader = hentSoknader()
        soknader.map { it.fom to it.status } shouldBe
            listOf(
                forsteSoknadFom to NY,
                andreSoknadFom to FREMTIDIG,
                tredjeSoknadFom to FREMTIDIG,
                fjerdeSoknadFom to FREMTIDIG,
            )
        soknader.map { it.sykepengesoknadUuid }.intersect(slettedeSoknadIder.toSet()).shouldBeEmpty()
    }

    @Test
    fun `gitt søknader gjenopprettet flere ganger når STOPP og START gjentas så får søknadene nye ID-er`() {
        gittAtEtVedtakErMottattOgSoknaderOpprettet()
        val forsteSlettedeIder = stoppMeldingHarSlettetSoknader(stoppTidspunkt)
        mottaOgProsesserStartMelding()

        val andreSlettedeIder = stoppMeldingHarSlettetSoknader(stoppTidspunkt)
        mottaOgProsesserStartMelding()

        val gjenopprettedeSoknadIder =
            hentSoknader().filter { it.fom!! > tidspunkt }.map { it.sykepengesoknadUuid }
        gjenopprettedeSoknadIder.size shouldBe 2
        andreSlettedeIder shouldNotBe forsteSlettedeIder
        gjenopprettedeSoknadIder.intersect((forsteSlettedeIder + andreSlettedeIder).toSet()).shouldBeEmpty()
    }
}
