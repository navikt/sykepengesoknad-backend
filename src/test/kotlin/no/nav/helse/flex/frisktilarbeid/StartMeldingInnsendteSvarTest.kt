package no.nav.helse.flex.frisktilarbeid

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Soknadstatus.FREMTIDIG
import no.nav.helse.flex.domain.Soknadstatus.SENDT
import no.nav.helse.flex.frisktilarbeid.BehandletStatus.BEHANDLET
import org.junit.jupiter.api.Test

class StartMeldingInnsendteSvarTest : StartMeldingTestOppsett() {
    @Test
    fun `gitt bruker har svart nei i aktiv søknad når START-melding mottas så er vedtak og søknader uendret`() {
        gittAtEtVedtakErMottattOgSoknaderOpprettet()
        stoppMeldingHarSlettetSoknader(stoppTidspunkt)
        brukerSvarerAtDeIkkeFortsattErFriskmeldt(hentNySoknad())
        val tilstandFor = hentSamletVedtakstilstand()

        mottaOgProsesserStartMelding(listOf(RSSoknadstatus.SENDT))

        val vedtakEtter = hentVedtak()
        vedtakEtter.also {
            it.behandletStatus shouldBe BEHANDLET
            it.avsluttetTidspunkt shouldBe tilstandFor.vedtak.avsluttetTidspunkt
        }
        val soknaderEtter = hentSoknader()
        soknaderEtter.map { it.fom to it.status } shouldBe listOf(forsteSoknadFom to SENDT)
        forventUendretVedtakSoknaderOgHendelser(tilstandFor)
    }

    @Test
    fun `gitt bruker har svart nei i eldre søknad når START-melding mottas så er vedtak og søknader uendret`() {
        gittAtEtVedtakErMottattOgSoknaderOpprettet()
        brukerSvarerAtDeIkkeFortsattErFriskmeldt(hentNySoknad())
        vedtakBlirManueltRebehandlet()
        val tilstandFor = hentSamletVedtakstilstand()
        check(tilstandFor.soknader.last().status == FREMTIDIG)
        tilstandFor.vedtak.avsluttetTidspunkt.shouldNotBeNull()

        mottaOgProsesserStartMelding(
            listOf(
                RSSoknadstatus.SENDT,
                RSSoknadstatus.FREMTIDIG,
                RSSoknadstatus.FREMTIDIG,
                RSSoknadstatus.FREMTIDIG,
            ),
        )

        forventUendretVedtakSoknaderOgHendelser(tilstandFor)
    }
}
