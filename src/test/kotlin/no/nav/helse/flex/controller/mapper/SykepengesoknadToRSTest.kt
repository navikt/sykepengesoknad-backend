package no.nav.helse.flex.controller.mapper

import no.nav.helse.flex.mock.MockSoknadSelvstendigeOgFrilansere
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SykepengesoknadToRSTest {

    @Test
    fun mappingTaklerAtSykmeldingSkrevetErNull() {

        val sykepengesoknadSelvstendigFrilanser =
            MockSoknadSelvstendigeOgFrilansere(null).opprettNySoknad().copy(sykmeldingSkrevet = null)

        val (id) = sykepengesoknadSelvstendigFrilanser.tilRSSykepengesoknad()

        assertThat(sykepengesoknadSelvstendigFrilanser.id).isEqualTo(id)
    }
}
