package no.nav.helse.flex.transaksjonshandtering

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doCallRealMethod
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.sendSoknadMedResult
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@TestMethodOrder(MethodOrderer.MethodName::class)
class TransaksjonshandteringTest : FellesTestOppsett() {
    final val fnr = "123456789"

    @Test
    fun `01 - vi oppretter en arbeidsledigsøknad`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                fnr = fnr,
            ),
        )
    }

    @Test
    fun `02 - vi besvarer alle sporsmal`() {
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(FRISKMELDT, "JA")
            .besvarSporsmal(TIL_SLUTT, "svar", ferdigBesvart = false)
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "JA")
            .besvarSporsmal(ANDRE_INNTEKTSKILDER, "NEI")
            .besvarSporsmal(ARBEIDSLEDIG_UTLAND, "NEI")
    }

    @Test
    fun `04 - vi sender inn søknaden, den feiler på kafka`() {
        doThrow(RuntimeException("sdfsdf")).whenever(aivenKafkaProducer).produserMelding(any())
        sendSoknadMedResult(fnr, hentSoknaderMetadata(fnr).first().id)
            .andExpect(((MockMvcResultMatchers.status().isInternalServerError)))

        val soknaden = hentSoknaderMetadata(fnr).first()
        assertThat(soknaden.status).isEqualTo(RSSoknadstatus.NY)
    }

    @Test
    fun `05 - reset aivekafkaproducer`() {
        doCallRealMethod().whenever(aivenKafkaProducer).produserMelding(any())
    }
}
