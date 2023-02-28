package no.nav.helse.flex.service

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.mock.opprettNySoknad
import no.nav.helse.flex.soknadsopprettelse.hentTidligsteFomForSykmelding
import no.nav.helse.flex.util.tilOsloInstant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.Collections.emptyList

class TidligsteFomForSykmeldingTest {

    @Test
    fun `test at vi finner den eldste fom i en sykmelding`() {
        val soknad = Sykepengesoknad(
            fnr = "fnr",
            startSykeforlop = LocalDate.now(),
            fom = LocalDate.now().minusDays(34),
            tom = LocalDate.now(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            sykmeldingId = "id1",
            sykmeldingSkrevet = LocalDateTime.now().minusMonths(4).tilOsloInstant(),
            soknadPerioder = emptyList(),
            id = UUID.randomUUID().toString(),
            status = Soknadstatus.NY,
            opprettet = Instant.now(),
            sporsmal = kotlin.collections.emptyList(),
            utenlandskSykmelding = false
        )

        val ingenEksisterendeSoknader = hentTidligsteFomForSykmelding(soknad, emptyList())

        assertThat(ingenEksisterendeSoknader).isEqualTo(soknad.fom)

        val eksisterende1 = opprettNySoknad().copy(sykmeldingId = soknad.sykmeldingId, fom = LocalDate.now().minusDays(993))
        val eksisterende2 = opprettNySoknad().copy(sykmeldingId = soknad.sykmeldingId, fom = LocalDate.now().minusDays(5000))
        val eksisterende3 = opprettNySoknad().copy(sykmeldingId = "id annen", fom = LocalDate.now().minusDays(6004))

        val medEksisterendeSoknader = hentTidligsteFomForSykmelding(soknad, listOf(eksisterende1, eksisterende2, eksisterende3).shuffled())
        assertThat(medEksisterendeSoknader).isEqualTo(eksisterende2.fom)
    }
}
