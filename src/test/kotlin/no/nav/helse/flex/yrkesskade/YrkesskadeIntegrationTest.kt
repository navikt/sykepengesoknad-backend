package no.nav.helse.flex.yrkesskade

import no.nav.helse.flex.*
import no.nav.helse.flex.mockdispatcher.FNR_MED_YRKESSKADE
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.util.serialisertTilString
import no.nav.helse.flex.yrkesskade.SykmeldingYrkesskadeConsumer.ReceivedSykmelding
import no.nav.helse.flex.yrkesskade.SykmeldingYrkesskadeConsumer.ReceivedSykmelding.Sykmelding
import no.nav.helse.flex.yrkesskade.SykmeldingYrkesskadeConsumer.ReceivedSykmelding.Sykmelding.MedisinskVurdering
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.UUID

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class YrkesskadeIntegrationTest : BaseTestClass() {

    @Autowired
    private lateinit var yrkesskadeSykmeldingRepository: YrkesskadeSykmeldingRepository

    private final val basisdato = LocalDate.of(2021, 9, 1)

    private val sykmeldingIdMedYrkesskade = UUID.randomUUID().toString()
    val fnr1 = "54334523"

    @Test
    @Order(1)
    fun `Sykmeldingene kommer inn`() {
        kafkaProducer.send(
            ProducerRecord(
                listOf(SYKMELDING_OK_TOPIC, SYKMELDING_MANUELL_TOPIC).random(),
                UUID.randomUUID().toString(),
                ReceivedSykmelding(
                    Sykmelding(MedisinskVurdering(yrkesskade = false))
                ).serialisertTilString()
            )
        )

        kafkaProducer.send(
            ProducerRecord(
                listOf(SYKMELDING_OK_TOPIC, SYKMELDING_MANUELL_TOPIC).random(),
                sykmeldingIdMedYrkesskade,
                ReceivedSykmelding(
                    Sykmelding(MedisinskVurdering(yrkesskade = true))
                ).serialisertTilString()
            )
        )

        await().until {
            yrkesskadeSykmeldingRepository.count() == 1L
        }
    }

    @Test
    @Order(2)
    fun `Arbeidstakersøknad for sykmelding med yrkesskade opprettes med yrkesskadespørsmål i seg i førstegangssoknaden`() {
        val kafkaSoknader = sendSykmelding(
            sykmeldingKafkaMessage(
                sykmeldingId = sykmeldingIdMedYrkesskade,
                fnr = fnr1,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato,
                    tom = basisdato.plusDays(35)
                )
            ),
            forventaSoknader = 2

        )

        kafkaSoknader.first().sporsmal!!.any { it.tag == "YRKESSKADE" }.`should be true`()
        kafkaSoknader.last().sporsmal!!.any { it.tag == "YRKESSKADE" }.`should be false`()
    }

    @Test
    @Order(3)
    fun `Svarer ja på spørsmålet om yrkesskade`() {
        mockFlexSyketilfelleArbeidsgiverperiode()

        val soknaden = hentSoknader(fnr1).first()

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr1)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
            .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
            .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
            .besvarSporsmal(tag = "UTLAND_V2", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UTENFOR_NORGE", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_0", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
            .besvarSporsmal(tag = "YRKESSKADE", svar = "JA", ferdigBesvart = false)
            .besvarSporsmal(tag = "YRKESSKADE_SAMMENHENG", svar = "JA")
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .sendSoknad()

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.first().yrkesskade!!.`should be true`()
    }

    @Test
    @Order(4)
    fun `Arbeidstakersøknad for sykmelding uten yrkesskade men mulig yrkesskade i infotryd opprettes med yrkesskadespørsmål i seg i førstegangssoknaden`() {
        val kafkaSoknader = sendSykmelding(
            sykmeldingKafkaMessage(
                sykmeldingId = UUID.randomUUID().toString(),
                fnr = FNR_MED_YRKESSKADE,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato,
                    tom = basisdato.plusDays(35)
                )
            ),
            forventaSoknader = 2

        )

        kafkaSoknader.first().sporsmal!!.any { it.tag == "YRKESSKADE" }.`should be true`()
        kafkaSoknader.last().sporsmal!!.any { it.tag == "YRKESSKADE" }.`should be false`()
    }

    @Test
    @Order(5)
    fun `Svarer nei på spørsmålet om yrkesskade`() {
        mockFlexSyketilfelleArbeidsgiverperiode()

        val soknaden = hentSoknader(FNR_MED_YRKESSKADE).first()

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = FNR_MED_YRKESSKADE)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
            .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
            .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
            .besvarSporsmal(tag = "UTLAND_V2", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UTENFOR_NORGE", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_0", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
            .besvarSporsmal(tag = "YRKESSKADE", svar = "NEI")
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .sendSoknad()

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.first().yrkesskade!!.`should be false`()
    }

    @Test
    @Order(6)
    fun `4 juridiske vurderinger`() {
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 4)
    }
}
