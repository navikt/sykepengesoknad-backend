package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.*
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.syfo.model.Merknad
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldHaveSize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TilbakedatertSykmeldingIntegrationTest : FellesTestOppsett() {
    private val fnr = "123456789"

    private val sykmeldingid = "1db78df1-d1d7-4dc4-affd-06e1e08066ce"

    @BeforeAll
    fun konfigurerUnleash() {
        fakeUnleash.resetAll()
    }

    @Test
    @Order(1)
    fun `oppretter søknad for sykmelding under behandling `() {
        sendSykmeldingMedMerknad("UNDER_BEHANDLING")

        val records = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        records.first().merknaderFraSykmelding!!.shouldHaveSize(1)
        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(1)
        hentetViaRest.first().merknaderFraSykmelding!!.shouldHaveSize(1)
        hentetViaRest
            .first()
            .merknaderFraSykmelding!!
            .first()
            .type `should be equal to` "UNDER_BEHANDLING"
    }

    @Test
    @Order(2)
    fun `endrer merknaden på sykmeldinga til UGYLDIG_TILBAKEDATERING`() {
        sendSykmeldingMedMerknad("UGYLDIG_TILBAKEDATERING")

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(1)
        hentetViaRest.first().merknaderFraSykmelding!!.shouldHaveSize(1)
        hentetViaRest
            .first()
            .merknaderFraSykmelding!!
            .first()
            .type `should be equal to` "UGYLDIG_TILBAKEDATERING"
    }

    @Test
    @Order(3)
    fun `endrer merknaden på sykmeldinga til ingen merknad`() {
        sendSykmeldingMedMerknad(null)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(1)
        hentetViaRest.first().merknaderFraSykmelding.shouldBeNull()
    }

    @Test
    @Order(4)
    fun `endrer merknaden tilbake sykmeldinga til UNDER_BEHANDLING`() {
        sendSykmeldingMedMerknad("UNDER_BEHANDLING")

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(1)
        hentetViaRest.first().merknaderFraSykmelding!!.shouldHaveSize(1)
        hentetViaRest
            .first()
            .merknaderFraSykmelding!!
            .first()
            .type `should be equal to` "UNDER_BEHANDLING"
    }

    @Test
    @Order(5)
    fun `sender inn søknaden`() {
        val soknaden = hentSoknader(fnr = fnr).first()

        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "FRISKMELDT", svar = "JA")
            .besvarSporsmal(tag = "ARBEID_UTENFOR_NORGE", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER", svar = "NEI")
            .besvarSporsmal(tag = "OPPHOLD_UTENFOR_EOS", svar = "NEI")
            .oppsummering()
            .sendSoknad()

        val kafkasoknad = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        kafkasoknad.merknaderFraSykmelding!!.first().type `should be equal to` "UNDER_BEHANDLING"
    }

    @Test
    @Order(6)
    fun `merknaden oppdateres for søknader som er sendt, men det publiseres ikke på kafka`() {
        sendSykmeldingMedMerknad(null)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(1)
        hentetViaRest.first().merknaderFraSykmelding.shouldBeNull()
    }

    fun sendSykmeldingMedMerknad(merknad: String?) {
        flexSyketilfelleMockRestServiceServer.reset()

        val kafkaMessage =
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                sykmeldingId = sykmeldingid,
                merknader = if (merknad == null) null else listOf(Merknad(type = merknad, beskrivelse = merknad)),
            )
        mockFlexSyketilfelleSykeforloep(kafkaMessage.sykmelding.id)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId = kafkaMessage.sykmelding.id,
            sykmeldingKafkaMessage = kafkaMessage,
            topic = SYKMELDINGSENDT_TOPIC,
        )
    }
}
