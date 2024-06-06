package no.nav.helse.flex.arbeidsledig

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import org.amshove.kluent.`should be true`
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@TestMethodOrder(MethodOrderer.MethodName::class)
class ArbeidsledigIntegrationTest : FellesTestOppsett() {
    companion object {
        const val FNR = "123456789"
        const val TIDLIGERE_ARBEIDSGIVER_ORGNR = "gamlejobben"
    }

    @Test
    fun `01 - Opprett arbeidsledigsøknad`() {
        val soknader =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                    fnr = FNR,
                    tidligereArbeidsgiverOrgnummer = TIDLIGERE_ARBEIDSGIVER_ORGNR,
                ),
            )

        assertThat(soknader).hasSize(1)
        with(soknader.last()) {
            assertThat(type).isEqualTo(SoknadstypeDTO.ARBEIDSLEDIG)
            assertThat(tidligereArbeidsgiverOrgnummer).isEqualTo(TIDLIGERE_ARBEIDSGIVER_ORGNR)
        }
    }

    @Test
    fun `02 - Sjekk at søknaden inneholder alle nødvendige spørsmål`() {
        val soknader = hentSoknader(FNR)
        assertThat(soknader).hasSize(1)

        val soknaden = soknader.first()
        assertThat(soknaden.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                FRISKMELDT,
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER,
                ARBEIDSLEDIG_UTLAND,
                TIL_SLUTT,
            ),
        )
    }

    @Test
    fun `03 - Sjekk at svar på ansvarserklæringa muterer ikke søknaden`() {
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(FNR).first().id,
                fnr = FNR,
            )

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = FNR)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .also {
                assertThat(it.muterteSoknaden).isFalse()
            }
    }

    @Test
    fun `04 - vi svarer at vi ble friskmeldt midt i søknadsperioden - Det muterer søknaden`() {
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(FNR).first().id,
                fnr = FNR,
            )

        assertThat(soknaden.sporsmal!!.first { it.tag == ANDRE_INNTEKTSKILDER }.sporsmalstekst)
            .isEqualTo(
                "Har du hatt inntekt mens du har vært sykmeldt i perioden 1. - 15. februar 2020?",
            )
        assertThat(soknaden.sporsmal!!.first { it.tag == ARBEIDSLEDIG_UTLAND }.sporsmalstekst)
            .isEqualTo(
                "Var du på reise utenfor EU/EØS mens du var sykmeldt 1. - 15. februar 2020?",
            )

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = FNR)
            .besvarSporsmal(FRISKMELDT, "NEI", false)
            .besvarSporsmal(
                FRISKMELDT_START,
                LocalDate.of(2020, 2, 5).format(DateTimeFormatter.ISO_LOCAL_DATE),
                mutert = true,
            )
            .also {
                assertThat(it.muterteSoknaden).isTrue()

                assertThat(it.rSSykepengesoknad.sporsmal!!.first { it.tag == ANDRE_INNTEKTSKILDER }.sporsmalstekst)
                    .isEqualTo(
                        "Har du hatt inntekt mens du har vært sykmeldt i perioden 1. - 4. februar 2020?",
                    )

                assertThat(it.rSSykepengesoknad.sporsmal!!.first { it.tag == ARBEIDSLEDIG_UTLAND }.sporsmalstekst)
                    .isEqualTo(
                        "Var du på reise utenfor EU/EØS mens du var sykmeldt 1. - 4. februar 2020?",
                    )
            }
    }

    @Test
    fun `05 - unødvendige spørsmål forsvinner når man blir friskmeldt første dag i søknadsperioden`() {
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(FNR).first().id,
                fnr = FNR,
            )

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = FNR)
            .besvarSporsmal(
                FRISKMELDT_START,
                LocalDate.of(2020, 2, 1).format(DateTimeFormatter.ISO_LOCAL_DATE),
                mutert = true,
            )
            .also {
                assertThat(it.rSSykepengesoknad.sporsmal!!.map { it.tag }).isEqualTo(
                    listOf(
                        ANSVARSERKLARING,
                        FRISKMELDT,
                        ARBEID_UTENFOR_NORGE,
                        TIL_SLUTT,
                    ),
                )
            }
    }

    @Test
    fun `06 - unødvendige spørsmål kommer tilbake når man svarer at man ikke ble friskmeldt første dagen likevel`() {
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(FNR).first().id,
                fnr = FNR,
            )

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = FNR)
            .besvarSporsmal(
                FRISKMELDT_START,
                LocalDate.of(2020, 2, 4).format(DateTimeFormatter.ISO_LOCAL_DATE),
                mutert = true,
            )
            .also {
                assertThat(it.rSSykepengesoknad.sporsmal!!.map { it.tag }).isEqualTo(
                    listOf(
                        ANSVARSERKLARING,
                        FRISKMELDT,
                        ARBEID_UTENFOR_NORGE,
                        ANDRE_INNTEKTSKILDER,
                        ARBEIDSLEDIG_UTLAND,
                        TIL_SLUTT,
                    ),
                )
            }
    }

    @Test
    fun `07 - vi kan ikke sende inn søknaden før alle spørsmål er besvart`() {
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(FNR).first().id,
                fnr = FNR,
            )
        sendSoknadMedResult(FNR, soknaden.id).andExpect(((MockMvcResultMatchers.status().isBadRequest)))
    }

    @Test
    fun `08 - Svar på alle spørsmål`() {
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(FNR).first().id,
                fnr = FNR,
            )

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = FNR)
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "JA")
            .besvarSporsmal(ANDRE_INNTEKTSKILDER, "NEI")
            .besvarSporsmal(ARBEIDSLEDIG_UTLAND, "JA", ferdigBesvart = false)
            .besvarSporsmal(UTLANDSOPPHOLD_SOKT_SYKEPENGER, "JA", ferdigBesvart = false)
            .besvarSporsmal(
                UTLAND_NAR,
                svar =
                    "{\"fom\":\"${
                        LocalDate.of(2020, 2, 1).format(DateTimeFormatter.ISO_LOCAL_DATE)}\"," +
                        "\"tom\":\"${LocalDate.of(2020, 2, 3).format(DateTimeFormatter.ISO_LOCAL_DATE)}\"}",
            )
            .besvarSporsmal(TIL_SLUTT, "Skal si ifra om noe endrer seg", ferdigBesvart = false)
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
    }

    @Test
    fun `09 - Kast feil dersom spørsmål id ikke finnes i søknaden`() {
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(FNR).first().id,
                fnr = FNR,
            )

        val json =
            oppdaterSporsmalMedResult(FNR, soknaden.sporsmal!![0].copy(id = "FEILID"), soknadsId = soknaden.id)
                .andExpect(MockMvcResultMatchers.status().isBadRequest).andReturn().response.contentAsString
        assertThat(json).isEqualTo("""{"reason":"SPORSMAL_FINNES_IKKE_I_SOKNAD"}""")
    }

    @Test
    fun `10 - Send inn søknaden - Den får da status sendt og blir publisert på kafka`() {
        sendSoknad(FNR, hentSoknaderMetadata(FNR).first().id)

        val soknaden = hentSoknaderMetadata(FNR).first()
        assertThat(soknaden.status).isEqualTo(RSSoknadstatus.SENDT)

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        assertThat(soknader).hasSize(1)
        with(soknader.last()) {
            assertThat(type).isEqualTo(SoknadstypeDTO.ARBEIDSLEDIG)
            assertThat(status).isEqualTo(SoknadsstatusDTO.SENDT)
            assertThat(permitteringer).hasSize(0)
            assertThat(friskmeldt).isEqualTo(LocalDate.of(2020, 2, 4))
            arbeidUtenforNorge!!.`should be true`()
            assertThat(tidligereArbeidsgiverOrgnummer).isEqualTo(TIDLIGERE_ARBEIDSGIVER_ORGNR)
            assertThat(fravar!![0].fom).isEqualTo(LocalDate.of(2020, 2, 1))
            assertThat(fravar!![0].tom).isEqualTo(LocalDate.of(2020, 2, 3))
        }
    }

    @Test
    fun `11 - vi kan ikke besvare spørsmål på en søknad som er sendt`() {
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(FNR).first().id,
                fnr = FNR,
            )

        assertThat(soknaden.status).isEqualTo(RSSoknadstatus.SENDT)

        val json =
            oppdaterSporsmalMedResult(FNR, soknaden.sporsmal!![0], soknadsId = soknaden.id)
                .andExpect(MockMvcResultMatchers.status().isBadRequest).andReturn().response.contentAsString
        assertThat(json).isEqualTo("""{"reason":"FEIL_STATUS_FOR_OPPDATER_SPORSMAL"}""")
    }

    @Test
    fun `12 - Det virker å hente metadata med det snart (høsten 2023) utdaterte acr claimet`() {
        val soknadMetadataResponse = hentSoknaderMetadataCustomAcr(FNR, "Level4")

        assertThat(soknadMetadataResponse).isEqualTo("200")
    }

    @Test
    fun `13 - Det virker ikke å hente metadata med et ugyldig acr claim`() {
        val soknadMetadataResponse = hentSoknaderMetadataCustomAcr(FNR, "doNotLetMeIn")

        assertThat(soknadMetadataResponse).isEqualTo("401")
    }

    @Test
    fun `14 - Det virker å hente metadata med det nye acr claimet`() {
        val soknadMetadataResponse = hentSoknaderMetadataCustomAcr(FNR, "idporten-loa-high")

        assertThat(soknadMetadataResponse).isEqualTo("200")
    }
}
