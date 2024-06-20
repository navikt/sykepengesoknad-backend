package no.nav.helse.flex.service

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.MethodName::class)
class OppholdUtenforEOSSporsmalTest : FellesTestOppsett() {
    private final val fnr = "123456789"

    @BeforeEach
    fun setUp() {
        flexSyketilfelleMockRestServiceServer.reset()
        fakeUnleash.resetAll()
        fakeUnleash.disable(UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS)
    }

    @Test
    fun `1 - arbeidsledigsøknader opprettes`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                sykmeldingsperioder =
                heltSykmeldt(
                    fom = LocalDate.of(2018, 1, 1),
                    tom = LocalDate.of(2018, 1, 10),
                ),
            ),
        )

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(1)
        assertThat(hentetViaRest[0].soknadstype).isEqualTo(RSSoknadstype.ARBEIDSLEDIG)
        assertThat(hentetViaRest[0].status).isEqualTo(RSSoknadstatus.NY)

        val rsSykepengesoknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        SoknadBesvarer(rSSykepengesoknad = rsSykepengesoknad, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "FRISKMELDT", svar = "JA")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UTENFOR_NORGE", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER", svar = "NEI")
            .besvarSporsmal(tag = "ARBEIDSLEDIG_UTLAND", svar = "NEI")
            .besvarSporsmal(tag = "TIL_SLUTT", svar = "svar 1, og 2", ferdigBesvart = false)
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .sendSoknad()

        val soknadPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().last()

        assertThat(soknadPaKafka.status).isEqualTo(SoknadsstatusDTO.SENDT)
    }

}
