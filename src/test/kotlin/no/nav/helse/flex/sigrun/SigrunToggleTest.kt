package no.nav.helse.flex.sigrun

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.flatten
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
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

        hentSoknader("87654321234").first().sporsmal!!.flatten().first {
            it.tag == "INNTEKTSOPPLYSNINGER_VARIG_ENDRING_25_PROSENT"
        }.metadata `should be equal to`
            objectMapper.readTree(
                """
                {
                    "inntekt-2023" : 1067008,
                    "inntekt-2022" : 1129745,
                    "inntekt-2021" : 1184422,
                    "g-2021" : 104716,
                    "g-2022" : 109784,
                    "g-2023" : 116239,
                    "g-sykmelding" : 124028,
                    "beregnet-snitt" : 871798,
                    "fastsatt-sykepengegrunnlag" : 744168,
                    "beregnet-p25" : 930210,
                    "beregnet-m25" : 558126
                  }
                """.trimIndent(),
            )
    }

    @Test
    fun `metadata på spørsmål om varigendring må være null når toggle er på`() {
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

        hentSoknader("87654321234").first().sporsmal!!.flatten().first {
            it.tag == "INNTEKTSOPPLYSNINGER_VARIG_ENDRING_25_PROSENT"
        }.metadata.toString() `should be equal to` "null"
    }
}
