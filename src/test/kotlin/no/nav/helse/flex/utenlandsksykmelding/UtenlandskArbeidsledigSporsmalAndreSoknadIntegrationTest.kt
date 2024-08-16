package no.nav.helse.flex.utenlandsksykmelding

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.syfo.model.sykmelding.arbeidsgiver.UtenlandskSykmeldingAGDTO
import org.amshove.kluent.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class UtenlandskArbeidsledigSporsmalAndreSoknadIntegrationTest : FellesTestOppsett() {
    private final val fnr = "12454578474"
    private final val basisdato = LocalDate.of(2021, 9, 1)

    @BeforeAll
    fun konfigurerUnleash() {
        fakeUnleash.resetAll()
    }

    @Test
    @Order(1)
    fun `Søknad 1 opprettes`() {
        val kafkaSoknader =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = basisdato,
                            tom = basisdato.plusDays(15),
                        ),
                    utenlandskSykemelding = null,
                    arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                ),
                oppfolgingsdato = basisdato,
            )

        kafkaSoknader[0].utenlandskSykmelding!!.shouldBeFalse()
        kafkaSoknader[0].startSyketilfelle!!.toString() shouldBeEqualTo "2021-09-01"
    }

    @Test
    @Order(2)
    fun `Besvarer og sender inn søknad 1`() {
        val soknaden = hentSoknader(fnr).first()

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "FRISKMELDT", svar = "JA")
            .besvarSporsmal(tag = "ARBEID_UTENFOR_NORGE", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER", svar = "NEI")
            .besvarSporsmal(tag = "OPPHOLD_UTENFOR_EOS", svar = "NEI")
            .oppsummering()
            .sendSoknad()

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(kafkaSoknader).hasSize(1)
    }

    @Test
    @Order(3)
    fun `Søknad 2 opprettes for utelnlandsk sykmelding `() {
        val kafkaSoknader =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = basisdato.plusDays(16),
                            tom = basisdato.plusDays(30),
                        ),
                    utenlandskSykemelding = UtenlandskSykmeldingAGDTO("Granka"),
                    arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                ),
                oppfolgingsdato = basisdato,
            )

        kafkaSoknader[0].utenlandskSykmelding!!.shouldBeTrue()
        kafkaSoknader[0].startSyketilfelle!!.toString() shouldBeEqualTo "2021-09-01"
    }

    @Test
    @Order(4)
    fun `Besvarer og sender inn søknad 2 som nå har utenlandsk sm sporsmål`() {
        val soknaden = hentSoknader(fnr).first { it.status == RSSoknadstatus.NY }

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "FRISKMELDT", svar = "JA")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER", svar = "NEI")
            .besvarSporsmal(tag = "OPPHOLD_UTENFOR_EOS", svar = "NEI")
            .besvarSporsmal(tag = "UTENLANDSK_SYKMELDING_BOSTED", svar = "NEI")
            .besvarSporsmal(tag = "UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE", svar = "NEI")
            .besvarSporsmal(tag = "UTENLANDSK_SYKMELDING_TRYGD_UTENFOR_NORGE", svar = "NEI")
            .oppsummering()
            .sendSoknad()

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(kafkaSoknader).hasSize(1)
    }
}
