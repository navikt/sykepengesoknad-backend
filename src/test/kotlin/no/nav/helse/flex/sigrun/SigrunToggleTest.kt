package no.nav.helse.flex.sigrun

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.flatten
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.medIndex
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.util.flatten
import no.nav.helse.flex.util.objectMapper
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SigrunToggleTest : FellesTestOppsett() {
    @BeforeEach
    fun konfigurerUnleash() {
        databaseReset.resetDatabase()
        fakeUnleash.resetAll()
    }

    @Test
    fun `metadata på spørsmål om varigendring må ha verdi når toggle er på`() {
        fakeUnleash.enable("sykepengesoknad-backend-sigrun")
        val soknader =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                    fnr = "87654321234",
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = LocalDate.now().minusDays(30),
                            tom = LocalDate.now().minusDays(1),
                        ),
                ),
            )

        soknader shouldHaveSize 1

        val soknad = hentSoknader("87654321234").first()
        soknad.sporsmal!!.flatten().first {
            it.tag == "INNTEKTSOPPLYSNINGER_VARIG_ENDRING_25_PROSENT"
        }.metadata `should be equal to`
            objectMapper.readTree(
                """
                {"sigrunInntekt":{"inntekter":[{"aar":"2023","verdi":851781},{"aar":"2022","verdi":872694},{"aar":"2021","verdi":890919}],"g-verdier":[{"aar":"2021","verdi":104716},{"aar":"2022","verdi":109784},{"aar":"2023","verdi":116239}],"g-sykmelding":124028,"beregnet":{"snitt":871798,"p25":1089748,"m25":653849},"original-inntekt":[{"inntektsaar":"2023","pensjonsgivendeInntekt":[{"datoForFastsetting":"2023-07-17","skatteordning":"FASTLAND","loenn":0,"loenn-bare-pensjon":0,"naering":1000000,"fiske-fangst-familiebarnehage":0}],"totalInntekt":1000000},{"inntektsaar":"2022","pensjonsgivendeInntekt":[{"datoForFastsetting":"2022-07-17","skatteordning":"FASTLAND","loenn":0,"loenn-bare-pensjon":0,"naering":1000000,"fiske-fangst-familiebarnehage":0}],"totalInntekt":1000000},{"inntektsaar":"2021","pensjonsgivendeInntekt":[{"datoForFastsetting":"2021-07-17","skatteordning":"FASTLAND","loenn":0,"loenn-bare-pensjon":0,"naering":1000000,"fiske-fangst-familiebarnehage":0}],"totalInntekt":1000000}]}}
                """.trimIndent(),
            )

        soknad.sporsmal!!.flatten().first {
            it.tag == "INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET"
        }.sporsmalstekst `should be equal to` "Er du ny i arbeidslivet etter 1. januar 2021?"
    }

    @Test
    fun `metadata på spørsmål om varigendring må være null når toggle er av`() {
        fakeUnleash.disable("sykepengesoknad-backend-sigrun")
        val soknader =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                    fnr = "87654321234",
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = LocalDate.now().minusDays(30),
                            tom = LocalDate.now().minusDays(1),
                        ),
                ),
            )

        soknader shouldHaveSize 1

        val soknad = hentSoknader("87654321234").first()
        soknad.sporsmal!!.flatten().first {
            it.tag == "INNTEKTSOPPLYSNINGER_VARIG_ENDRING_25_PROSENT"
        }.metadata.toString() `should be equal to` "null"

        soknad.sporsmal!!.flatten().first {
            it.tag == "INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET"
        }.sporsmalstekst `should be equal to` "Er du ny i arbeidslivet etter 1. januar 2019?"
    }

    @Test
    fun `metadata på spørsmål i kafka`() {
        fakeUnleash.enable("sykepengesoknad-backend-sigrun")
        val soknader =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                    fnr = "87654321234",
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = LocalDate.now().minusDays(30),
                            tom = LocalDate.now().minusDays(1),
                        ),
                ),
            )

        soknader shouldHaveSize 1

        val soknad = hentSoknader("87654321234").first()
        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknad, mockMvc = this, fnr = "87654321234")
                .besvarSporsmal(tag = ANSVARSERKLARING, svar = "CHECKED")
                .besvarSporsmal(tag = medIndex(ARBEID_UNDERVEIS_100_PROSENT, 0), svar = "NEI")
                .besvarSporsmal(tag = TILBAKE_I_ARBEID, svar = "NEI")
                .besvarSporsmal(tag = ANDRE_INNTEKTSKILDER, svar = "NEI")
                .besvarSporsmal(tag = OPPHOLD_UTENFOR_EOS, svar = "NEI")
                .besvarSporsmal(tag = ARBEID_UTENFOR_NORGE, svar = "NEI")
                .besvarSporsmal(tag = INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET, svar = null, ferdigBesvart = false)
                .besvarSporsmal(tag = INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET_NEI, svar = "CHECKED", ferdigBesvart = false)
                .besvarSporsmal(tag = INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET, svar = null, ferdigBesvart = false)
                .besvarSporsmal(tag = INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_NEI, svar = "CHECKED", ferdigBesvart = false)
                .besvarSporsmal(tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING, svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE, svar = null, ferdigBesvart = false)
                .besvarSporsmal(
                    tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE_ENDRET_INNSATS,
                    svar = "CHECKED",
                    ferdigBesvart = false,
                )
                .besvarSporsmal(tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_25_PROSENT, svar = "NEI")
                .oppsummering()
                .sendSoknad()
        sendtSoknad.status `should be equal to` RSSoknadstatus.SENDT

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 1)
        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.shouldHaveSize(1)
        kafkaSoknader[0].sporsmal!!.flatten().first { it.tag == INNTEKTSOPPLYSNINGER_VARIG_ENDRING_25_PROSENT }
            .metadata!!.toPrettyString() `should be equal to`
            """
            {
              "sigrunInntekt" : {
                "inntekter" : [ {
                  "aar" : "2023",
                  "verdi" : 851781
                }, {
                  "aar" : "2022",
                  "verdi" : 872694
                }, {
                  "aar" : "2021",
                  "verdi" : 890919
                } ],
                "g-verdier" : [ {
                  "aar" : "2021",
                  "verdi" : 104716
                }, {
                  "aar" : "2022",
                  "verdi" : 109784
                }, {
                  "aar" : "2023",
                  "verdi" : 116239
                } ],
                "g-sykmelding" : 124028,
                "beregnet" : {
                  "snitt" : 871798,
                  "p25" : 1089748,
                  "m25" : 653849
                },
                "original-inntekt" : [ {
                  "inntektsaar" : "2023",
                  "pensjonsgivendeInntekt" : [ {
                    "datoForFastsetting" : "2023-07-17",
                    "skatteordning" : "FASTLAND",
                    "loenn" : 0,
                    "loenn-bare-pensjon" : 0,
                    "naering" : 1000000,
                    "fiske-fangst-familiebarnehage" : 0
                  } ],
                  "totalInntekt" : 1000000
                }, {
                  "inntektsaar" : "2022",
                  "pensjonsgivendeInntekt" : [ {
                    "datoForFastsetting" : "2022-07-17",
                    "skatteordning" : "FASTLAND",
                    "loenn" : 0,
                    "loenn-bare-pensjon" : 0,
                    "naering" : 1000000,
                    "fiske-fangst-familiebarnehage" : 0
                  } ],
                  "totalInntekt" : 1000000
                }, {
                  "inntektsaar" : "2021",
                  "pensjonsgivendeInntekt" : [ {
                    "datoForFastsetting" : "2021-07-17",
                    "skatteordning" : "FASTLAND",
                    "loenn" : 0,
                    "loenn-bare-pensjon" : 0,
                    "naering" : 1000000,
                    "fiske-fangst-familiebarnehage" : 0
                  } ],
                  "totalInntekt" : 1000000
                } ]
              }
            }
            """.trimIndent()
    }
}
