package no.nav.helse.flex.oppholdutland

import no.nav.helse.flex.*
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class OppholdUtlandFeatureSwitchTest : BaseTestClass() {
    private val fnr = "123456789"

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
        opprettUtlandssoknad(fnr)
        val soknader = hentSoknaderMetadata(fnr)

        val soknad1 = hentSoknad(soknader[0].id, fnr)
        assertThat(soknad1.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                "PERIODEUTLAND",
                "LAND",
                "ARBEIDSGIVER",
                "TIL_SLUTT",
            ),
        )
    }

    @Test
    @Order(2)
    fun `oppretter søknad med featureswitch av`() {
        opprettUtlandssoknad(fnr)
        val soknader = hentSoknaderMetadata(fnr)

        val soknad1 = hentSoknad(soknader[0].id, fnr)
        assertThat(soknad1.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                "PERIODEUTLAND",
                "LAND",
                "ARBEIDSGIVER",
                "BEKREFT_OPPLYSNINGER_UTLAND_INFO",
            ),
        )
    }
}
