package no.nav.syfo.controller.mapper

import no.nav.syfo.mock.MockSoknadSelvstendigeOgFrilansere
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
