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
                {"sigrunInntekt":{"inntekter":[{"aar":"2023","verdi":1067008},{"aar":"2022","verdi":1129745},{"aar":"2021","verdi":1184422}],"g-verdier":[{"aar":"2021","verdi":104716},{"aar":"2022","verdi":109784},{"aar":"2023","verdi":116239}],"g-sykmelding":124028,"beregnet":{"snitt":871798,"p25":930210,"m25":558126}}}
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
