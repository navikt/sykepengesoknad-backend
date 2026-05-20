package no.nav.helse.flex.fakes

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.repository.normaliser
import no.nav.helse.flex.testutil.lagSoknad
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class SykepengesoknadRepositoryFakeTest {
    private val repository = SykepengesoknadRepositoryFake()

    @Test
    fun `finner soknader for sykmeldingUuid`() {
        val sykmeldingUuid = UUID.randomUUID().toString()
        val annenSykmeldingUuid = UUID.randomUUID().toString()

        lagreSoknad(sykmeldingUuid = sykmeldingUuid, fnr = "11111111111", arbeidsgiver = 1)
        lagreSoknad(sykmeldingUuid = sykmeldingUuid, fnr = "22222222222", arbeidsgiver = 2)
        lagreSoknad(sykmeldingUuid = annenSykmeldingUuid, fnr = "33333333333", arbeidsgiver = 3)

        val funnet = repository.findBySykmeldingUuid(sykmeldingUuid)

        funnet.size `should be equal to` 2
        funnet.all { it.sykmeldingUuid == sykmeldingUuid } `should be equal to` true
    }

    @Test
    fun `returnerer tom liste nar sykmeldingUuid ikke finnes`() {
        val funnet = repository.findBySykmeldingUuid(UUID.randomUUID().toString())

        funnet.isEmpty() `should be equal to` true
    }

    private fun lagreSoknad(
        sykmeldingUuid: String,
        fnr: String,
        arbeidsgiver: Int,
    ) {
        repository.lagreSoknad(
            lagSoknad(
                fnr = fnr,
                arbeidsgiver = arbeidsgiver,
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 31),
                startSykeforlop = LocalDate.of(2024, 1, 1),
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
                status = Soknadstatus.NY,
                sykmeldingId = sykmeldingUuid,
            ).normaliser().soknad,
        )
    }
}
