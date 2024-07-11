package no.nav.helse.flex.oppholdutland

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.korrigerSoknadMedResult
import no.nav.helse.flex.opprettUtlandssoknad
import no.nav.helse.flex.sendSoknadMedResult
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@TestMethodOrder(MethodOrderer.MethodName::class)
class OppholdUtlandIntegrationTest : FellesTestOppsett() {
    final val fnr = "123456789"

    @Test
    fun `01 - utlandssøknad opprettes`() {
        opprettUtlandssoknad(fnr)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0)

        assertThat(hentSoknaderMetadata(fnr)).hasSize(1)
    }

    @Test
    fun `02 - utlandssøknad opprettes ikke når det allerede finnes en`() {
        opprettUtlandssoknad(fnr)

        assertThat(hentSoknaderMetadata(fnr)).hasSize(1)
    }

    @Test
    fun `03 - søknaden har forventa spørsmål`() {
        val soknader = hentSoknaderMetadata(fnr)
        assertThat(soknader).hasSize(1)

        val soknad =
            hentSoknad(
                soknadId = soknader.first().id,
                fnr = fnr,
            )
        assertThat(soknad.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                LAND,
                PERIODEUTLAND,
                ARBEIDSGIVER,
                TIL_SLUTT,
            ),
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
        assertThat(soknadEtter.getSporsmalMedTag("LAND").svar.map { it.verdi }).isEqualTo(
            listOf(
                "Syden",
                "Kina",
            ),
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
            .besvarSporsmal(TIL_SLUTT, "svar", ferdigBesvart = false)
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")

        val soknadEtter =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )

        SoknadBesvarer(soknadEtter, this, fnr)
            .besvarSporsmal(FERIE, svar = "NEI", ferdigBesvart = false)
            .besvarSporsmal(ARBEIDSGIVER, svar = "NEI", ferdigBesvart = false)
            .besvarSporsmal(TIL_SLUTT, "svar", ferdigBesvart = false)
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
    }

    @Test
    fun `08 - Vi bekrefter opplysninger`() {
        val soknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        SoknadBesvarer(soknad, this, fnr)
            .besvarSporsmal(TIL_SLUTT, svar = "svar", ferdigBesvart = false)
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, svar = "CHECKED")

        val soknadEtter =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        assertThat(soknadEtter.sporsmal?.last()?.undersporsmal?.get(0)?.tag).isEqualTo("BEKREFT_OPPLYSNINGER")
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
        assertThat(soknadPaKafka.type).isEqualTo(SoknadstypeDTO.OPPHOLD_UTLAND)
        assertThat(soknadPaKafka.status).isEqualTo(SoknadsstatusDTO.SENDT)
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
        assertThat(hentSoknaderMetadata(fnr)).hasSize(1)

        opprettUtlandssoknad(fnr)

        assertThat(hentSoknaderMetadata(fnr)).hasSize(2)
    }
}
