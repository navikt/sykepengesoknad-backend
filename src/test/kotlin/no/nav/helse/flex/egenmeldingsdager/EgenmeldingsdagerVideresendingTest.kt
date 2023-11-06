package no.nav.helse.flex.egenmeldingsdager

import no.nav.helse.flex.*
import no.nav.helse.flex.aktivering.AktiveringJob
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.util.serialisertTilString
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.SvartypeDTO
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class EgenmeldingsdagerVideresendingTest : BaseTestClass() {

    @Autowired
    private lateinit var aktiveringJob: AktiveringJob

    private val fnr = "12345678900"
    private val basisdato = LocalDate.of(2099, 9, 1)
    private val sykmeldingKafkaMessage = sykmeldingKafkaMessage(
        fnr = fnr,
        sykmeldingsperioder = heltSykmeldt(
            fom = basisdato,
            tom = basisdato.plusDays(10)

        )
    ).let {
        it.copy(
            event = it.event.copy(
                erSvarOppdatering = true,
                sporsmals = it.event.sporsmals!!.plus(
                    SporsmalOgSvarDTO(
                        tekst = "Brukte du egenmeldingsdager før sykmeldinga?",
                        shortName = ShortNameDTO.EGENMELDINGSDAGER,
                        svartype = SvartypeDTO.DAGER,
                        svar = basisdato.minusDays(3).datesUntil(basisdato).toList().serialisertTilString()
                    )
                )
            )
        )
    }

    @Test
    @Order(1)
    fun `Arbeidstakersøknad opprettes`() {
        mockFlexSyketilfelleSykeforloep(
            sykmeldingKafkaMessage.sykmelding.id,
            sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.minOf { it.fom }
        )
        kafkaProducer.send(
            ProducerRecord(
                SYKMELDINGSENDT_TOPIC,
                sykmeldingKafkaMessage.sykmelding.id,
                sykmeldingKafkaMessage.serialisertTilString()
            )
        )
        val kafkaSoknad = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        kafkaSoknad.status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        kafkaSoknad.egenmeldingsdagerFraSykmelding shouldBeEqualTo listOf(
            basisdato.minusDays(3),
            basisdato.minusDays(2),
            basisdato.minusDays(1)
        )
    }

    @Test
    @Order(2)
    fun `Vi aktiverer søknaden`() {
        aktiveringJob.bestillAktivering(now = basisdato.plusDays(16))
        val kafkaSoknad = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        kafkaSoknad.status shouldBeEqualTo SoknadsstatusDTO.NY
        kafkaSoknad.egenmeldingsdagerFraSykmelding shouldBeEqualTo listOf(
            basisdato.minusDays(3),
            basisdato.minusDays(2),
            basisdato.minusDays(1)
        )
    }

    @Test
    @Order(3)
    fun `Vi besvarer og sender inn søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
            fnr = fnr
        )

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
            .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
            .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
            .besvarSporsmal(tag = "UTLAND_V2", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_0", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .sendSoknad()

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
        val kafkaSoknad = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        kafkaSoknad.status shouldBeEqualTo SoknadsstatusDTO.SENDT
        kafkaSoknad.egenmeldingsdagerFraSykmelding shouldBeEqualTo listOf(
            basisdato.minusDays(3),
            basisdato.minusDays(2),
            basisdato.minusDays(1)
        )
    }
}
