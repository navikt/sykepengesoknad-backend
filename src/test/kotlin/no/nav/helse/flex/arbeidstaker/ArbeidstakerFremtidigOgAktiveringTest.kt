package no.nav.helse.flex.arbeidstaker

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.aktivering.AktiveringJob
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ArbeidstakerFremtidigOgAktiveringTest : BaseTestClass() {

    @Autowired
    private lateinit var aktiveringJob: AktiveringJob

    private final val fnr = "123456789"
    private final val basisdato = LocalDate.now()

    @Test
    @Order(1)
    fun `Fremtidig arbeidstakersøknad opprettes for en sykmelding`() {

        val kafkaSoknader = sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.minusDays(1),
                    tom = basisdato.plusDays(15),
                ),
            )
        )
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.FREMTIDIG)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(1)
        assertThat(hentetViaRest[0].soknadstype).isEqualTo(RSSoknadstype.ARBEIDSTAKERE)
        assertThat(hentetViaRest[0].status).isEqualTo(RSSoknadstatus.FREMTIDIG)
    }

    @Test
    @Order(2)
    fun `søknaden har ingen spørsmål som fremtidig`() {
        val soknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )
        assertThat(soknad.sporsmal!!).hasSize(0)
    }

    @Test
    @Order(3)
    fun `Vi aktiverer søknaden`() {
        aktiveringJob.bestillAktivering(now = basisdato.plusDays(16))
        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(kafkaSoknader).hasSize(1)
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.NY)
    }

    @Test
    @Order(4)
    fun `søknaden har forventa spørsmål som NY`() {
        val soknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        assertThat(soknad.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                "ANSVARSERKLARING",
                "FRAVAR_FOR_SYKMELDINGEN",
                "TILBAKE_I_ARBEID",
                "FERIE_V2",
                "PERMISJON_V2",
                "UTLAND_V2",
                "JOBBET_DU_100_PROSENT_0",
                "ARBEID_UTENFOR_NORGE",
                "ANDRE_INNTEKTSKILDER_V2",
                "UTDANNING",
                "VAER_KLAR_OVER_AT",
                "BEKREFT_OPPLYSNINGER"
            )
        )
    }

    @Test
    @Order(5)
    fun `vi besvarer og sender inn søknaden`() {
        flexSyketilfelleMockRestServiceServer?.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
            fnr = fnr
        )

        val sendtSoknad = SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "FRAVAR_FOR_SYKMELDINGEN", svar = "NEI")
            .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
            .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
            .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
            .besvarSporsmal(tag = "UTLAND_V2", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UTENFOR_NORGE", svar = "NEI")
            .besvarSporsmal(tag = "JOBBET_DU_100_PROSENT_0", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
            .besvarSporsmal(tag = "UTDANNING", svar = "NEI")
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(kafkaSoknader).hasSize(1)
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.SENDT)
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }
}
