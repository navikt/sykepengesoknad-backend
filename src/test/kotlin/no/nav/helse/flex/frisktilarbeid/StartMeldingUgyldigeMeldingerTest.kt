package no.nav.helse.flex.frisktilarbeid

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import no.nav.helse.flex.sendStartMelding
import no.nav.helse.flex.sendStoppMelding
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class StartMeldingUgyldigeMeldingerTest : StartMeldingTestOppsett() {
    @Test
    fun `gitt vedtak uten avsluttet tidspunkt når START-melding mottas så kastes IllegalStateException uten sideeffekter`() {
        gittAtEtVedtakErMottattOgSoknaderOpprettet()
        val tilstandFor = hentSamletVedtakstilstand()

        val e = assertThrows<IllegalStateException> { mottaOgProsesserStartMelding() }

        e.message!! shouldStartWith "Kan ikke gjenopprette vedtaksperiode: ${tilstandFor.vedtak.id} når den aldri har blitt avsluttet."
        forventUendretVedtakSoknaderOgHendelser(tilstandFor)
    }

    @Test
    fun `gitt vedtak som ikke er behandlet når START-melding mottas så kastes IllegalStateException uten sideeffekter`() {
        gittAtEtVedtakErMottattOgSoknaderOpprettet()
        vedtakErAvsluttetMenIkkeBehandlet()
        val tilstandFor = hentSamletVedtakstilstand()

        val e = assertThrows<IllegalStateException> { mottaOgProsesserStartMelding() }

        e.message!! shouldStartWith
            "Kan ikke gjenopprette vedtaksperiode: ${tilstandFor.vedtak.id} som har status ${tilstandFor.vedtak.behandletStatus}."
        forventUendretVedtakSoknaderOgHendelser(tilstandFor)
    }

    @Test
    fun `gitt ukjent vedtak når START-melding mottas så kastes IllegalStateException uten sideeffekter`() {
        gittAtEtVedtakErMottattOgSoknaderOpprettet()
        val tilstandFor = hentSamletVedtakstilstand()
        val ukjentVedtaksperiodeId = UUID.randomUUID().toString()

        val e =
            assertThrows<IllegalStateException> {
                sendStartMelding(ukjentVedtaksperiodeId, fnr, startTidspunkt)
            }

        e.message shouldBe "Fant ikke vedtak for START-melding med vedtaksperiode: $ukjentVedtaksperiodeId."
        forventUendretVedtakSoknaderOgHendelser(tilstandFor)
    }

    @Test
    fun `gitt vedtak som ignorerer arbeidssøkerregisteret når STOPP-melding mottas så kastes IllegalStateException uten sideeffekter`() {
        gittAtEtVedtakErMottattOgSoknaderOpprettet()
        vedtakIgnorererArbeidssokerregisteret()
        val tilstandFor = hentSamletVedtakstilstand()

        val e =
            assertThrows<IllegalStateException> {
                sendStoppMelding(tilstandFor.vedtak.id!!, fnr, stoppTidspunkt)
            }

        e.message shouldBe
            "Kan ikke behandle STOPP-melding for vedtaksperiode: ${tilstandFor.vedtak.id} " +
            "siden vedtaket er satt til å ignorere arbeidssøkerregisteret."
        forventUendretVedtakSoknaderOgHendelser(tilstandFor)
    }
}
