package no.nav.helse.flex.arbeidsledig

import io.kotest.matchers.shouldBe
import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
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

        soknader.size shouldBe 1
        with(soknader.last()) {
            type shouldBe SoknadstypeDTO.ARBEIDSLEDIG
            tidligereArbeidsgiverOrgnummer shouldBe TIDLIGERE_ARBEIDSGIVER_ORGNR
        }
    }

    @Test
    fun `02 - Sjekk at søknaden inneholder alle nødvendige spørsmål`() {
        val soknader = hentSoknader(FNR)
        soknader.size shouldBe 1

        val soknaden = soknader.first()
        soknaden.sporsmal!!.map { it.tag } shouldBe
            listOf(
                ANSVARSERKLARING,
                FRISKMELDT,
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER_V2,
                OPPHOLD_UTENFOR_EOS,
                TIL_SLUTT,
            )
    }

    @Test
    fun `03 - Sjekk at svar på ansvarserklæringa muterer ikke søknaden`() {
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(FNR).first().id,
                fnr = FNR,
            )

        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = FNR)
            .besvarSporsmal(tag = ANSVARSERKLARING, svar = "CHECKED", mutert = false)
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

        soknaden.getSporsmalMedTag(ANDRE_INNTEKTSKILDER_V2).sporsmalstekst shouldBe
            "Har du annen inntekt eller oppdrag?"
        soknaden.getSporsmalMedTag(OPPHOLD_UTENFOR_EOS).sporsmalstekst shouldBe
            "Var du på reise utenfor EU/EØS og Storbritannia mens du var sykmeldt 1. - 15. februar 2020?"

        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = FNR)
            .besvarSporsmal(FRISKMELDT, "NEI", false)
            .besvarSporsmal(
                FRISKMELDT_START,
                LocalDate.of(2020, 2, 5).format(DateTimeFormatter.ISO_LOCAL_DATE),
                mutert = true,
            ).also { soknadBesvarer ->
                soknadBesvarer.muterteSoknaden shouldBe true

                soknadBesvarer.rSSykepengesoknad.getSporsmalMedTag(OPPHOLD_UTENFOR_EOS).sporsmalstekst shouldBe
                    "Var du på reise utenfor EU/EØS og Storbritannia mens du var sykmeldt 1. - 4. februar 2020?"
            }
    }

    @Test
    fun `05 - unødvendige spørsmål forsvinner når man blir friskmeldt første dag i søknadsperioden`() {
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(FNR).first().id,
                fnr = FNR,
            )

        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = FNR)
            .besvarSporsmal(
                FRISKMELDT_START,
                LocalDate.of(2020, 2, 1).format(DateTimeFormatter.ISO_LOCAL_DATE),
                mutert = true,
            ).also { soknadBesvarer ->
                soknadBesvarer.rSSykepengesoknad.sporsmal!!.map { it.tag } shouldBe
                    listOf(
                        ANSVARSERKLARING,
                        FRISKMELDT,
                        ARBEID_UTENFOR_NORGE,
                        TIL_SLUTT,
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

        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = FNR)
            .besvarSporsmal(
                FRISKMELDT_START,
                LocalDate.of(2020, 2, 4).format(DateTimeFormatter.ISO_LOCAL_DATE),
                mutert = true,
            ).also { soknadBesvarer ->
                soknadBesvarer.rSSykepengesoknad.sporsmal!!.map { it.tag } shouldBe
                    listOf(
                        ANSVARSERKLARING,
                        FRISKMELDT,
                        ARBEID_UTENFOR_NORGE,
                        ANDRE_INNTEKTSKILDER_V2,
                        OPPHOLD_UTENFOR_EOS,
                        TIL_SLUTT,
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

        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = FNR)
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "JA")
            .besvarSporsmal(ANDRE_INNTEKTSKILDER_V2, "NEI")
            .besvarSporsmal(OPPHOLD_UTENFOR_EOS, "JA", ferdigBesvart = false)
            .besvarSporsmal(
                OPPHOLD_UTENFOR_EOS_NAR,
                svar =
                    "{\"fom\":\"${
                        LocalDate.of(2020, 2, 1).format(DateTimeFormatter.ISO_LOCAL_DATE)}\"," +
                        "\"tom\":\"${LocalDate.of(2020, 2, 3).format(DateTimeFormatter.ISO_LOCAL_DATE)}\"}",
            ).oppsummering()
    }

    @Test
    fun `09 - Kast feil dersom spørsmål id ikke finnes i søknaden`() {
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(FNR).first().id,
                fnr = FNR,
            )

        oppdaterSporsmalMedResult(FNR, soknaden.sporsmal!![0].copy(id = "FEILID"), soknadsId = soknaden.id)
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andReturn()
            .response.contentAsString
            .also {
                it shouldBe """{"reason":"SPORSMAL_FINNES_IKKE_I_SOKNAD"}"""
            }
    }

    @Test
    fun `10 - Send inn søknaden - Den får da status sendt og blir publisert på kafka`() {
        sendSoknad(FNR, hentSoknaderMetadata(FNR).first().id)

        val soknaden = hentSoknaderMetadata(FNR).first()
        soknaden.status shouldBe RSSoknadstatus.SENDT

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2).tilSoknader()
        soknader.size shouldBe 2
        with(soknader.first()) {
            type shouldBe SoknadstypeDTO.ARBEIDSLEDIG
            status shouldBe SoknadsstatusDTO.SENDT
            permitteringer?.size shouldBe 0
            friskmeldt shouldBe LocalDate.of(2020, 2, 4)
            arbeidUtenforNorge!! shouldBe true
            tidligereArbeidsgiverOrgnummer shouldBe TIDLIGERE_ARBEIDSGIVER_ORGNR
            fravar!![0].fom shouldBe LocalDate.of(2020, 2, 1)
            fravar!![0].tom shouldBe LocalDate.of(2020, 2, 3)
        }
        with(soknader.last()) {
            type shouldBe SoknadstypeDTO.OPPHOLD_UTLAND
            status shouldBe SoknadsstatusDTO.NY
        }
    }

    @Test
    fun `11 - vi kan ikke besvare spørsmål på en søknad som er sendt`() {
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(FNR).first().id,
                fnr = FNR,
            )

        soknaden.status shouldBe RSSoknadstatus.SENDT

        oppdaterSporsmalMedResult(FNR, soknaden.sporsmal!![0], soknadsId = soknaden.id)
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andReturn()
            .response.contentAsString
            .also {
                it shouldBe """{"reason":"FEIL_STATUS_FOR_OPPDATER_SPORSMAL"}"""
            }
    }

    @Test
    fun `12 - Det virker å hente metadata med det snart (høsten 2023) utdaterte acr claimet`() {
        val soknadMetadataResponse = hentSoknaderMetadataCustomAcr(FNR, "Level4")
        soknadMetadataResponse shouldBe "200"
    }

    @Test
    fun `13 - Det virker ikke å hente metadata med et ugyldig acr claim`() {
        val soknadMetadataResponse = hentSoknaderMetadataCustomAcr(FNR, "doNotLetMeIn")
        soknadMetadataResponse shouldBe "401"
    }

    @Test
    fun `14 - Det virker å hente metadata med det nye acr claimet`() {
        val soknadMetadataResponse = hentSoknaderMetadataCustomAcr(FNR, "idporten-loa-high")
        soknadMetadataResponse shouldBe "200"
    }
}
