package no.nav.helse.flex.behandlingsdager

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING
import no.nav.helse.flex.testdata.behandingsdager
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class BehandlingsdagerFeatureSwitchTest : BaseTestClass() {
    private val fnr = "12345678900"

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
                sykmeldingsperioder = behandingsdager(),
            ),
        )
        val soknader = hentSoknaderMetadata(fnr)

        val soknad1 = hentSoknad(soknader[0].id, fnr)
        assertThat(soknad1.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                "ANSVARSERKLARING",
                "ENKELTSTAENDE_BEHANDLINGSDAGER_0",
                "FERIE_V2",
                "ARBEID_UTENFOR_NORGE",
                "ANDRE_INNTEKTSKILDER",
                "TIL_SLUTT",
            ),
        )
        assertThat(
            soknad1.sporsmal!!.first {
                it.tag == ANSVARSERKLARING
            }.sporsmalstekst,
        ).isEqualTo(
            "Jeg vet at jeg kan miste retten til sykepenger hvis opplysningene jeg gir ikke er riktige eller " +
                "fullstendige. Jeg vet også at NAV kan holde igjen eller kreve tilbake penger, og at å gi feil " +
                "opplysninger kan være straffbart.",
        )
    }

    @Test
    @Order(2)
    fun `oppretter søknad med featureswitch av`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = behandingsdager(),
            ),
        )
        val soknader = hentSoknaderMetadata(fnr)

        val soknad1 = hentSoknad(soknader[0].id, fnr)
        assertThat(soknad1.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                "ANSVARSERKLARING",
                "ENKELTSTAENDE_BEHANDLINGSDAGER_0",
                "FERIE_V2",
                "ARBEID_UTENFOR_NORGE",
                "ANDRE_INNTEKTSKILDER",
                "VAER_KLAR_OVER_AT",
                "BEKREFT_OPPLYSNINGER",
            ),
        )
        assertThat(
            soknad1.sporsmal!!.first {
                it.tag == ANSVARSERKLARING
            }.sporsmalstekst,
        ).isEqualTo(
            "Jeg vet at jeg kan miste retten til sykepenger hvis opplysningene jeg gir ikke er riktige eller " +
                "fullstendige. Jeg vet også at NAV kan holde igjen eller kreve tilbake penger, og at å gi feil " +
                "opplysninger kan være straffbart.",
        )
    }
}
