package no.nav.helse.flex.arbeidstaker

import no.nav.helse.flex.*
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING
import no.nav.helse.flex.soknadsopprettelse.sporsmal.UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.syfo.model.Merknad
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ArbeidstakerFeatureSwitchTest : BaseTestClass() {

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    private val fnr = "12345678900"
    private val basisdato = LocalDate.of(2021, 9, 1)
    private val oppfolgingsdato = basisdato.minusDays(20)

    @AfterEach
    fun teardown() {
        databaseReset.resetDatabase()
        fakeUnleash.resetAll()
        fakeUnleash.disable(UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL)
    }

    @Test
    @Order(1)
    fun `oppretter søknad med featureswitch på`() {
        fakeUnleash.resetAll()
        fakeUnleash.enable(UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL)
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.minusDays(2),
                    tom = basisdato.plusDays(5)

                ),
                merknader = listOf(Merknad(type = "UGYLDIG_TILBAKEDATERING", beskrivelse = "Hey"))
            ),
            oppfolgingsdato = oppfolgingsdato,

            forventaSoknader = 1
        )
        val soknader = hentSoknaderMetadata(fnr)

        val soknad1 = hentSoknad(soknader[0].id, fnr)
        assertThat(soknad1.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                "ANSVARSERKLARING",
                "TILBAKE_I_ARBEID",
                "FERIE_V2",
                "PERMISJON_V2",
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                "ANDRE_INNTEKTSKILDER_V2",
                "UTLAND_V2",
                "TIL_SLUTT"
            )
        )
        assertThat(soknad1.sporsmal!!.first { it.tag == ANSVARSERKLARING }.sporsmalstekst).isEqualTo("Jeg vet at jeg kan miste retten til sykepenger hvis opplysningene jeg gir ikke er riktige eller fullstendige. Jeg vet også at NAV kan holde igjen eller kreve tilbake penger, og at å gi feil opplysninger kan være straffbart.")
    }

    @Test
    @Order(2)
    fun `oppretter søknad med featureswitch av`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.minusDays(2),
                    tom = basisdato.plusDays(5)

                ),
                merknader = listOf(Merknad(type = "UGYLDIG_TILBAKEDATERING", beskrivelse = "Hey"))
            ),
            oppfolgingsdato = oppfolgingsdato,

            forventaSoknader = 1
        )
        val soknader = hentSoknaderMetadata(fnr)

        val soknad1 = hentSoknad(soknader[0].id, fnr)
        assertThat(soknad1.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                "ANSVARSERKLARING",
                "TILBAKE_I_ARBEID",
                "FERIE_V2",
                "PERMISJON_V2",
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                "ANDRE_INNTEKTSKILDER_V2",
                "UTLAND_V2",
                "VAER_KLAR_OVER_AT",
                "BEKREFT_OPPLYSNINGER"
            )
        )
        assertThat(soknad1.sporsmal!!.first { it.tag == ANSVARSERKLARING }.sporsmalstekst).isEqualTo("Jeg vet at jeg kan miste retten til sykepenger hvis opplysningene jeg gir ikke er riktige eller fullstendige. Jeg vet også at NAV kan holde igjen eller kreve tilbake penger, og at å gi feil opplysninger kan være straffbart.")
    }
}
