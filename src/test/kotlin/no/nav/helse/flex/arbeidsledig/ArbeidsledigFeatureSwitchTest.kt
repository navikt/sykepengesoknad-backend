package no.nav.helse.flex.arbeidsledig

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ArbeidsledigFeatureSwitchTest : BaseTestClass() {
    private val fnr = "123456789"

    @AfterEach
    fun teardown() {
        databaseReset.resetDatabase()
        fakeUnleash.resetAll()
        fakeUnleash.disable(UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL)
    }

    val tidligereArbeidsgiverOrgnummer = "gamlejobben"

    @Test
    @Order(1)
    fun `oppretter søknad med featureswitch på`() {
        fakeUnleash.resetAll()
        fakeUnleash.enable(UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL)
        sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                fnr = fnr,
                tidligereArbeidsgiverOrgnummer = tidligereArbeidsgiverOrgnummer,
            ),
        )
        val soknader = hentSoknaderMetadata(fnr)

        val soknad1 = hentSoknad(soknader[0].id, fnr)
        assertThat(soknad1.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                "ANSVARSERKLARING",
                "FRISKMELDT",
                "ARBEID_UTENFOR_NORGE",
                "ANDRE_INNTEKTSKILDER",
                "ARBEIDSLEDIG_UTLAND",
                "TIL_SLUTT",
            ),
        )
    }

    @Test
    @Order(2)
    fun `oppretter søknad med featureswitch av`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                fnr = fnr,
                tidligereArbeidsgiverOrgnummer = tidligereArbeidsgiverOrgnummer,
            ),
        )
        val soknader = hentSoknaderMetadata(fnr)

        val soknad1 = hentSoknad(soknader[0].id, fnr)
        assertThat(soknad1.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                "ANSVARSERKLARING",
                "FRISKMELDT",
                "ARBEID_UTENFOR_NORGE",
                "ANDRE_INNTEKTSKILDER",
                "ARBEIDSLEDIG_UTLAND",
                "VAER_KLAR_OVER_AT",
                "BEKREFT_OPPLYSNINGER",
            ),
        )
    }
}
