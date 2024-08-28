package no.nav.helse.flex.arbeidstaker

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.juridiskvurdering.Utfall
import no.nav.helse.flex.korrigerSoknad
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.oppdaterSporsmalMedResult
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.testutil.jsonTilHashMap
import no.nav.helse.flex.tilJuridiskVurdering
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import org.amshove.kluent.`should be equal to`
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.MethodName::class)
class KorrektFaktiskGradMappesFraSvarTilPeriodeTest : FellesTestOppsett() {
    private val fnr = "12345678900"

    @BeforeAll
    fun konfigurerUnleash() {
        fakeUnleash.resetAll()
    }

    @Test
    fun `1 - vi oppretter en arbeidstakersoknad`() {
        // Opprett søknad
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    mutableListOf(
                        SykmeldingsperiodeAGDTO(
                            fom = (LocalDate.of(2018, 1, 13)),
                            tom = (LocalDate.of(2018, 1, 14)),
                            gradert = GradertDTO(grad = 100, reisetilskudd = false),
                            type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                            aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                            behandlingsdager = null,
                            innspillTilArbeidsgiver = null,
                            reisetilskudd = false,
                        ),
                        SykmeldingsperiodeAGDTO(
                            fom = (LocalDate.of(2018, 1, 15)),
                            tom = (LocalDate.of(2018, 1, 29)),
                            gradert = GradertDTO(grad = 70, reisetilskudd = false),
                            type = PeriodetypeDTO.GRADERT,
                            aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                            behandlingsdager = null,
                            innspillTilArbeidsgiver = null,
                            reisetilskudd = false,
                        ),
                        // Viktig med shuffle da sorteringen ved opprettelse er fiksen.
                    ).also { it.shuffle() },
            ),
        )
    }

    @Test
    fun `3 - vi svarer på sporsmalene og sender den inn`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(TILBAKE_I_ARBEID, "NEI")
            .besvarSporsmal(FERIE_V2, "NEI")
            .besvarSporsmal(PERMISJON_V2, "NEI")
            .besvarSporsmal(OPPHOLD_UTENFOR_EOS, "NEI")
            .besvarSporsmal("JOBBET_DU_GRADERT_1", "JA", false)
            .besvarSporsmal("HVOR_MANGE_TIMER_PER_UKE_1", "37,5", false)
            .besvarSporsmal("HVOR_MYE_PROSENT_1", "CHECKED", false)
            .besvarSporsmal("HVOR_MYE_PROSENT_VERDI_1", "49")
            .besvarSporsmal("ARBEID_UNDERVEIS_100_PROSENT_0", "NEI")
            .besvarSporsmal("HVOR_MYE_PROSENT_0", "CHECKED")
            .besvarSporsmal(ANDRE_INNTEKTSKILDER_V2, "NEI")
            .oppsummering()
            .sendSoknad()
    }

    @Test
    fun `4 - vi sjekker at faktisk grad er hentet ut korrekt`() {
        val soknaden = hentSoknaderMetadata(fnr).first()

        assertThat(soknaden.status).isEqualTo(RSSoknadstatus.SENDT)

        val soknadPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        assertThat(soknadPaKafka.soknadsperioder!!.map { Pair(it.faktiskGrad, it.sykmeldingsgrad) }).isEqualTo(
            listOf(
                Pair(null, 100),
                Pair(49, 70),
            ),
        )
    }

    @Test
    fun `5 - vi sjekker at faktisk grad er riktig på jurdisk vurdering på kafka`() {
        val vurdering =
            juridiskVurderingKafkaConsumer
                .ventPåRecords(antall = 2)
                .tilJuridiskVurdering()
                .first { it.paragraf == "8-13" }
        vurdering.eventName `should be equal to` "subsumsjon"
        vurdering.utfall `should be equal to` Utfall.VILKAR_BEREGNET
        vurdering.input `should be equal to`
            """
            {
              "fravar": [],
              "versjon": "2022-02-01",
              "arbeidUnderveis": [
                {
                  "tag": "ARBEID_UNDERVEIS_100_PROSENT_0",
                  "svar": [
                    "NEI"
                  ],
                  "undersporsmal": []
                },
                {
                  "tag": "JOBBET_DU_GRADERT_1",
                  "svar": [
                    "JA"
                  ],
                  "undersporsmal": [
                    {
                      "tag": "HVOR_MANGE_TIMER_PER_UKE_1",
                      "svar": [
                        "37,5"
                      ],
                      "undersporsmal": []
                    },
                    {
                      "tag": "HVOR_MYE_HAR_DU_JOBBET_1",
                      "svar": [],
                      "undersporsmal": [
                        {
                          "tag": "HVOR_MYE_PROSENT_1",
                          "svar": [
                            "CHECKED"
                          ],
                          "undersporsmal": [
                            {
                              "tag": "HVOR_MYE_PROSENT_VERDI_1",
                              "svar": [
                                "49"
                              ],
                              "undersporsmal": []
                            }
                          ]
                        },
                        {
                          "tag": "HVOR_MYE_TIMER_1",
                          "svar": [],
                          "undersporsmal": []
                        }
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent().jsonTilHashMap()
        vurdering.output `should be equal to`
            """
{
  "perioder": [
    {
      "fom": "2018-01-13",
      "tom": "2018-01-14",
      "faktiskGrad": null
    },
    {
      "fom": "2018-01-15",
      "tom": "2018-01-29",
      "faktiskGrad": 49
    }
  ],
  "versjon": "2022-02-01"
}            
        """.jsonTilHashMap()
    }

    @Test
    fun `6 - vi svarer at vi ar sykmeldt mer enn 70 prosent med timer i frontend, men det beregnes til max 70`() {
        flexSyketilfelleMockRestServiceServer.reset()
        val soknaden = hentSoknader(fnr = fnr).first { it.status == RSSoknadstatus.SENDT }
        mockFlexSyketilfelleArbeidsgiverperiode(andreKorrigerteRessurser = soknaden.id)

        val korrigerendeSoknad = korrigerSoknad(soknaden.id, fnr)

        SoknadBesvarer(rSSykepengesoknad = korrigerendeSoknad, mockMvc = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal("JOBBET_DU_GRADERT_1", "JA", false)
            .besvarSporsmal("HVOR_MANGE_TIMER_PER_UKE_1", "37", false)
            .besvarSporsmal("HVOR_MYE_PROSENT_1", null, false)
            .besvarSporsmal("HVOR_MYE_TIMER_1", "CHECKED", false)
            .besvarSporsmal("HVOR_MYE_TIMER_VERDI_1", "5")
            .besvarSporsmal("ARBEID_UNDERVEIS_100_PROSENT_0", "NEI")
            .besvarSporsmal("HVOR_MYE_PROSENT_0", "CHECKED")
            .oppsummering()
            .sendSoknad()

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
        val soknadPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        assertThat(soknadPaKafka.soknadsperioder!!.map { Pair(it.faktiskGrad, it.sykmeldingsgrad) }).isEqualTo(
            listOf(
                Pair(null, 100),
                Pair(30, 70),
            ),
        )
    }

    @Test
    fun `7 - vi svarer at vi var sykmeldt mindre enn 70 prosent med timer i frontend`() {
        flexSyketilfelleMockRestServiceServer.reset()
        val soknaden = hentSoknader(fnr = fnr).first { it.status == RSSoknadstatus.SENDT }
        mockFlexSyketilfelleArbeidsgiverperiode(andreKorrigerteRessurser = soknaden.id)

        val korrigerendeSoknad = korrigerSoknad(soknaden.id, fnr)

        SoknadBesvarer(rSSykepengesoknad = korrigerendeSoknad, mockMvc = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal("JOBBET_DU_GRADERT_1", "JA", false)
            .besvarSporsmal("HVOR_MANGE_TIMER_PER_UKE_1", "37", false)
            .besvarSporsmal("HVOR_MYE_PROSENT_1", null, false)
            .besvarSporsmal("HVOR_MYE_TIMER_1", "CHECKED", false)
            .besvarSporsmal("HVOR_MYE_TIMER_VERDI_1", "70")
            .besvarSporsmal("ARBEID_UNDERVEIS_100_PROSENT_0", "NEI")
            .besvarSporsmal("HVOR_MYE_PROSENT_0", "CHECKED")
            .oppsummering()
            .sendSoknad()

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
        val soknadPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        assertThat(soknadPaKafka.soknadsperioder!!.map { Pair(it.faktiskGrad, it.sykmeldingsgrad) }).isEqualTo(
            listOf(
                Pair(null, 100),
                Pair(86, 70),
            ),
        )
    }

    @Test
    fun `8 - vi svarer at vi var sykmeldt mindre enn 70 prosent med prosent i frontend`() {
        flexSyketilfelleMockRestServiceServer.reset()
        val soknaden = hentSoknader(fnr = fnr).first { it.status == RSSoknadstatus.SENDT }
        mockFlexSyketilfelleArbeidsgiverperiode(andreKorrigerteRessurser = soknaden.id)

        val korrigerendeSoknad = korrigerSoknad(soknaden.id, fnr)

        SoknadBesvarer(rSSykepengesoknad = korrigerendeSoknad, mockMvc = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal("JOBBET_DU_GRADERT_1", "JA", false)
            .besvarSporsmal("HVOR_MANGE_TIMER_PER_UKE_1", "37", false)
            .besvarSporsmal("HVOR_MYE_PROSENT_1", "CHECKED", false)
            .besvarSporsmal("HVOR_MYE_TIMER_1", null, false)
            .besvarSporsmal("HVOR_MYE_PROSENT_VERDI_1", "89")
            .besvarSporsmal("ARBEID_UNDERVEIS_100_PROSENT_0", "NEI")
            .besvarSporsmal("HVOR_MYE_PROSENT_0", "CHECKED")
            .oppsummering()
            .sendSoknad()

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
        val soknadPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        assertThat(soknadPaKafka.soknadsperioder!!.map { Pair(it.faktiskGrad, it.sykmeldingsgrad) }).isEqualTo(
            listOf(
                Pair(null, 100),
                Pair(89, 70),
            ),
        )
    }

    @Test
    fun `9 - vi kan ikke svare at vi var sykmeldt mer enn 70 prosent med prosent i frontend`() {
        flexSyketilfelleMockRestServiceServer.reset()
        val soknaden = hentSoknader(fnr = fnr).first { it.status == RSSoknadstatus.SENDT }
        mockFlexSyketilfelleArbeidsgiverperiode(andreKorrigerteRessurser = soknaden.id)

        val korrigerendeSoknad = korrigerSoknad(soknaden.id, fnr)

        val besvarer =
            SoknadBesvarer(rSSykepengesoknad = korrigerendeSoknad, mockMvc = this, fnr = fnr)
                .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
                .besvarSporsmal("JOBBET_DU_GRADERT_1", "JA", false)
                .besvarSporsmal("HVOR_MANGE_TIMER_PER_UKE_1", "37", false)
                .besvarSporsmal("HVOR_MYE_PROSENT_1", "CHECKED", false)
                .besvarSporsmal("HVOR_MYE_TIMER_1", null, false)
                .besvarSporsmal("HVOR_MYE_PROSENT_VERDI_1", "11", false)

        val json =
            oppdaterSporsmalMedResult(fnr, besvarer.finnHovedsporsmal("JOBBET_DU_GRADERT_1"), soknadsId = korrigerendeSoknad.id)
                .andExpect(MockMvcResultMatchers.status().isBadRequest).andReturn().response.contentAsString
        assertThat(json).isEqualTo("""{"reason":"SPORSMALETS_SVAR_VALIDERER_IKKE"}""")
    }
}
