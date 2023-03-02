package no.nav.helse.flex.gjenapnesykmelding

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.kafka.consumer.SYKMELDINGBEKREFTET_TOPIC
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.model.sykmeldingstatus.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate
import java.util.*

@TestMethodOrder(MethodOrderer.MethodName::class)
class GjenapneSykmeldingIntegrationMedTombstoneTest : BaseTestClass() {

    private final val fnr = "123456789"

    @BeforeEach
    fun setUp() {
        flexSyketilfelleMockRestServiceServer?.reset()
    }

    @Test
    fun `1 - arbeidsledigsøknader opprettes`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2018, 1, 1),
                    tom = LocalDate.of(2018, 1, 10)
                )
            )
        )

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2018, 1, 1),
                    tom = LocalDate.of(2018, 1, 10)
                )
            )
        )

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(2)
        assertThat(hentetViaRest[0].soknadstype).isEqualTo(RSSoknadstype.ARBEIDSLEDIG)
        assertThat(hentetViaRest[0].status).isEqualTo(RSSoknadstatus.NY)
    }

    @Test
    fun `2 - vi svarer på den ene søknaden`() {
        val rsSykepengesoknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )
        SoknadBesvarer(rSSykepengesoknad = rsSykepengesoknad, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "FRISKMELDT", svar = "JA")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER", svar = "NEI")
            .besvarSporsmal(tag = "ARBEIDSLEDIG_UTLAND", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UTENFOR_NORGE", svar = "NEI")
            .besvarSporsmal(tag = "UTDANNING", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER", svar = "NEI")
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .sendSoknad()

        val soknadPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().last()

        assertThat(soknadPaKafka.status).isEqualTo(SoknadsstatusDTO.SENDT)
    }

    @Test
    fun `3 - vi sender inn tombstone status på den sendte og den ene nye sykmeldingene - Kun den ene blir slettet`() {
        val soknader = hentSoknaderMetadata(fnr)
        assertThat(soknader).hasSize(2)
        val sykmedlingIdTilNy = soknader.find { it.status == RSSoknadstatus.NY }!!.sykmeldingId!!
        val sykmedlingIdTilSendt = soknader.find { it.status == RSSoknadstatus.SENDT }!!.sykmeldingId!!

        // TODO tombstone bedre
        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId = sykmedlingIdTilNy,
            sykmeldingKafkaMessage = null,
            topic = SYKMELDINGBEKREFTET_TOPIC
        )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId = sykmedlingIdTilSendt,
            sykmeldingKafkaMessage = null,
            topic = SYKMELDINGSENDT_TOPIC
        )

        val soknadPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().last()

        val soknaderEtterEvents = hentSoknaderMetadata(fnr)

        assertThat(soknaderEtterEvents).hasSize(1)
        assertThat(soknaderEtterEvents.find { it.status == RSSoknadstatus.SENDT }!!.sykmeldingId).isEqualTo(
            sykmedlingIdTilSendt
        )

        assertThat(soknadPaKafka.status).isEqualTo(SoknadsstatusDTO.SLETTET)
        assertThat(soknadPaKafka.sykmeldingId).isEqualTo(sykmedlingIdTilNy)
    }

    @Test
    fun `4 - en arbeidstakersøknad kan ikke slettes fra kafka`() {
        val kafkaSoknad = sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2018, 1, 1),
                    tom = LocalDate.of(2018, 1, 10)
                )
            )
        )

        val soknader = hentSoknaderMetadata(fnr)
        assertThat(soknader).hasSize(2)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId = kafkaSoknad.first().sykmeldingId!!,
            sykmeldingKafkaMessage = null,
            topic = SYKMELDINGSENDT_TOPIC
        )

        val soknaderEtterApenMelding = hentSoknaderMetadata(fnr)
        assertThat(soknaderEtterApenMelding).hasSize(2)
    }
}
