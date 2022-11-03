package no.nav.helse.flex.arbeidstaker

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.korrigerSoknad
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.skapSykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.MethodName::class)
class AvbruttSoknadIncidentTest : BaseTestClass() {

    @Autowired
    lateinit var namedParameterJdbcTemplate: NamedParameterJdbcTemplate

    private final val fnr = "123456789"
    private final val basisdato = LocalDate.of(2021, 9, 1)

    @Test
    fun `1 - arbeidstakersøknad opprettes for en lang sykmelding`() {
        val kafkaSoknader = sendSykmelding(
            skapSykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.minusDays(20),
                    tom = basisdato.minusDays(2),
                ),
            )
        )
        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(1)

        val ventPåRecords = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
        val kafkaSoknader = ventPåRecords.tilSoknader()

        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.NY)
        assertThat(kafkaSoknader[0].sendTilGosys).isNull()
        assertThat(kafkaSoknader[0].merknader).isNull()

        val hentetViaRest = hentSoknader(fnr)
        assertThat(hentetViaRest).hasSize(1)
    }

    @Test
    fun `2 - vi merker søknaden med avbrutt feilinfo`() {
        val soknader = hentSoknaderMetadata(fnr)

        namedParameterJdbcTemplate.update(
            """UPDATE SYKEPENGESOKNAD SET AVBRUTT_FEILINFO = true WHERE SYKEPENGESOKNAD_UUID = :sykepengesoknadId""",

            MapSqlParameterSource()
                .addValue("sykepengesoknadId", soknader.first().id)
        )
    }

    @Test
    fun `3 - vi besvarer og sender inn søknaden`() {
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
        assertThat(kafkaSoknader[0].sendTilGosys).isTrue()
        assertThat(kafkaSoknader[0].merknader).isEqualTo(listOf("AVBRUTT_FEILINFO"))

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    fun `4 - vi korrigerer og sender inn søknaden`() {
        flexSyketilfelleMockRestServiceServer?.reset()
        val soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.SENDT }.id
        korrigerSoknad(soknadId, fnr)
        mockFlexSyketilfelleArbeidsgiverperiode(andreKorrigerteRessurser = soknadId)
        val soknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first { it.korrigerer == soknadId }.id,
            fnr = fnr
        )

        val sendtSoknad = SoknadBesvarer(rSSykepengesoknad = soknad, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(kafkaSoknader).hasSize(1)
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.SENDT)
        assertThat(kafkaSoknader[0].sendTilGosys).isTrue()
        assertThat(kafkaSoknader[0].merknader).isEqualTo(listOf("AVBRUTT_FEILINFO"))

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }
}
