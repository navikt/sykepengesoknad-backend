package no.nav.helse.flex.oppholdutland

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.korrigerSoknadMedResult
import no.nav.helse.flex.opprettUtlandssoknad
import no.nav.helse.flex.sendSoknadMedResult
import no.nav.helse.flex.soknadsopprettelse.ARBEIDSGIVER
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER_UTLAND
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER_UTLAND_INFO
import no.nav.helse.flex.soknadsopprettelse.FERIE
import no.nav.helse.flex.soknadsopprettelse.LAND
import no.nav.helse.flex.soknadsopprettelse.PERIODEUTLAND
import no.nav.helse.flex.soknadsopprettelse.SYKMELDINGSGRAD
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
class OppholdUtlandIntegrationTest : BaseTestClass() {
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
                PERIODEUTLAND,
                LAND,
                ARBEIDSGIVER,
                BEKREFT_OPPLYSNINGER_UTLAND_INFO,
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

        assertThat(soknad.getSporsmalMedTag(BEKREFT_OPPLYSNINGER_UTLAND_INFO).undertekst).isEqualTo(
            """
            <ul>
                <li>Jeg har avklart med legen at reisen ikke vil forlenge sykefraværet</li>
                <li>Reisen hindrer ikke planlagt behandling eller avtaler med NAV</li>
            </ul>
            """.trimIndent(),
        )
        SoknadBesvarer(soknad, this, fnr)
            .besvarSporsmal(ARBEIDSGIVER, svar = "JA", ferdigBesvart = false)
            .besvarSporsmal(SYKMELDINGSGRAD, "JA", ferdigBesvart = false)
            .besvarSporsmal(FERIE, "JA", mutert = true)

        val soknadEtter =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        assertThat(soknadEtter.getSporsmalMedTag(BEKREFT_OPPLYSNINGER_UTLAND_INFO).undertekst).isEqualTo(
            """
            <ul>
                <li>Jeg har avklart med legen at reisen ikke vil forlenge sykefraværet</li>
                <li>Reisen hindrer ikke planlagt behandling eller avtaler med NAV</li>
            <li>Reisen er avklart med arbeidsgiveren min</li></ul>
            """.trimIndent(),
        )

        SoknadBesvarer(soknadEtter, this, fnr)
            .besvarSporsmal(FERIE, svar = "NEI", ferdigBesvart = false)
            .besvarSporsmal(ARBEIDSGIVER, svar = "NEI", mutert = true)
    }

    @Test
    fun `08 - Vi bekrefter opplysninger`() {
        val soknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        SoknadBesvarer(soknad, this, fnr)
            .besvarSporsmal(BEKREFT_OPPLYSNINGER_UTLAND, svar = "CHECKED")

        val soknadEtter =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        assertThat(soknadEtter.getSporsmalMedTag(BEKREFT_OPPLYSNINGER_UTLAND).svar.map { it.verdi }).isEqualTo(
            listOf("CHECKED"),
        )
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
