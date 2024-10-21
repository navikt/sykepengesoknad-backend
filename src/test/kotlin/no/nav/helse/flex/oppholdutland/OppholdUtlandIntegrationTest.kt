package no.nav.helse.flex.oppholdutland

import no.nav.helse.flex.*
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testutil.SoknadBesvarer
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@TestMethodOrder(MethodOrderer.MethodName::class)
class OppholdUtlandIntegrationTest : FellesTestOppsett() {
    final val fnr = "123456789"

    private fun verifiserKafkaSoknader() {
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().let { kafkaSoknader ->
            kafkaSoknader.size `should be equal to` 1
            kafkaSoknader.first().type `should be equal to` SoknadstypeDTO.OPPHOLD_UTLAND
            kafkaSoknader.first().status `should be equal to` SoknadsstatusDTO.NY
        }
    }

    @Test
    fun `01 - utlandssøknad opprettes`() {
        opprettUtlandssoknad(fnr)
        verifiserKafkaSoknader()

        hentSoknader(fnr).size `should be equal to` 1
    }

    @Test
    fun `02 - utlandssøknad opprettes ikke når det allerede finnes en`() {
        opprettUtlandssoknad(fnr)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0)
        hentSoknaderMetadata(fnr).size `should be equal to` 1
    }

    @Test
    fun `03 - søknaden har forventa spørsmål`() {
        val soknader = hentSoknaderMetadata(fnr)
        soknader.size `should be equal to` 1

        val soknad =
            hentSoknad(
                soknadId = soknader.first().id,
                fnr = fnr,
            )
        soknad.sporsmal!!.map { it.tag } `should be equal to`
            listOf(
                LAND,
                PERIODEUTLAND,
                ARBEIDSGIVER,
                AVKLARING_I_FORBINDELSE_MED_REISE,
                TIL_SLUTT,
            )
    }

    @Test
    fun `04 - Vi får 400 dersom vi forsøker å sende en søknad med valideringsfeil`() {
        val soknader = hentSoknaderMetadata(fnr)

        sendSoknadMedResult(fnr, soknader[0].id).andExpect((MockMvcResultMatchers.status().isBadRequest))
    }

    @Test
    fun `05 - Vi besvarer land spørsmålet`() {
        val soknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        SoknadBesvarer(soknad, this, fnr)
            .besvarSporsmal(LAND, svarListe = listOf("Syden", "Kina"))

        val soknadEtter =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        soknadEtter.getSporsmalMedTag("LAND").svar.map { it.verdi } `should be equal to`
            listOf(
                "Syden",
                "Kina",
            )
    }

    @Test
    fun `06 - Vi besvarer periode og arbeidsgiver spørsmålene`() {
        val soknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )

        SoknadBesvarer(soknad, this, fnr)
            .besvarSporsmal(
                PERIODEUTLAND,
                svar = "{\"fom\":\"${
                    LocalDate.now().minusMonths(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
                }\",\"tom\":\"${LocalDate.now().minusWeeks(1).format(DateTimeFormatter.ISO_LOCAL_DATE)}\"}",
            )
            .besvarSporsmal(ARBEIDSGIVER, svar = "NEI")
    }

    @Test
    fun `07 - Bekreft teksten endrer seg hvis vi får arbeirdsgiver`() {
        val soknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )

        SoknadBesvarer(soknad, this, fnr)
            .besvarSporsmal(ARBEIDSGIVER, svar = "JA", ferdigBesvart = false)
            .besvarSporsmal(SYKMELDINGSGRAD, "JA", ferdigBesvart = false)
            .besvarSporsmal(FERIE, "JA", mutert = true, ferdigBesvart = false)
            .besvarSporsmal(AVKLART_MED_ARBEIDSGIVER_ELLER_NAV, svar = "NEI", ferdigBesvart = false)
            .besvarSporsmal(AVKLART_MED_SYKMELDER, svar = "JA")
            .oppsummering()

        val soknadEtter =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )

        SoknadBesvarer(soknadEtter, this, fnr)
            .besvarSporsmal(FERIE, svar = "NEI", ferdigBesvart = false)
            .besvarSporsmal(ARBEIDSGIVER, svar = "NEI", ferdigBesvart = false)
            .oppsummering()
    }

    @Test
    fun `09 - Vi sender søknaden`() {
        val soknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )

        SoknadBesvarer(soknad, this, fnr).sendSoknad()

        val soknadPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        soknadPaKafka.type `should be equal to` SoknadstypeDTO.OPPHOLD_UTLAND
        soknadPaKafka.status `should be equal to` SoknadsstatusDTO.SENDT
    }

    @Test
    fun `10 - Vi kan ikke korrigere utandssoknaden`() {
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        korrigerSoknadMedResult(soknaden.id, fnr).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andReturn()
    }

    @Test
    fun `11 - utlandssøknad opprettes når det allerede finnes en som er sendt`() {
        hentSoknaderMetadata(fnr).size `should be equal to` 1

        opprettUtlandssoknad(fnr)
        verifiserKafkaSoknader()

        hentSoknaderMetadata(fnr).size `should be equal to` 2
    }

    @Test
    fun `12 - utlandssøknad som avbrytes blir publisert på kafka, og ikke slettet fra db`() {
        hentSoknaderMetadata(fnr).let {
            it.size `should be equal to` 2
            avbrytSoknad(it.last().id, fnr)
            sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().let { kafkaSoknader ->
                kafkaSoknader.size `should be equal to` 1
                kafkaSoknader.first().type `should be equal to` SoknadstypeDTO.OPPHOLD_UTLAND
                kafkaSoknader.first().status `should be equal to` SoknadsstatusDTO.AVBRUTT
            }
        }

        hentSoknaderMetadata(fnr).size `should be equal to` 2
    }

    @Test
    fun `13 - utlandssøknad kan gjenåpnes`() {
        hentSoknaderMetadata(fnr).let {
            it.size `should be equal to` 2
            gjenapneSoknad(it.last().id, fnr)
            sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().let { kafkaSoknader ->
                kafkaSoknader.size `should be equal to` 1
                kafkaSoknader.first().type `should be equal to` SoknadstypeDTO.OPPHOLD_UTLAND
                kafkaSoknader.first().status `should be equal to` SoknadsstatusDTO.NY
            }
        }
        hentSoknaderMetadata(fnr).size `should be equal to` 2
    }
}
