package no.nav.helse.flex.service

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.mock.opprettNySoknad
import no.nav.helse.flex.mock.opprettNySoknadMock
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.util.tilOsloInstant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.*

class SlettGamleUtkastServiceTest : BaseTestClass() {
    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var slettGamleUtkastService: SlettGamleUtkastService

    @BeforeEach
    @AfterEach
    fun setUp() {
        databaseReset.resetDatabase()
    }

    @Test
    fun `tom database inkluderer ingen utkast å slette`() {
        val antall = slettGamleUtkastService.slettGamleUtkast()
        assertThat(antall).isEqualTo(0)
    }

    @Test
    fun `Et gammel arbeidstakersøknad utkast blir slettet`() {
        val soknad =
            opprettNySoknad().copy(
                status = Soknadstatus.UTKAST_TIL_KORRIGERING,
                tom = LocalDate.now().minusDays(7),
                opprettet = LocalDate.now().minusDays(7).atStartOfDay().tilOsloInstant(),
            )
        sykepengesoknadDAO.lagreSykepengesoknad(soknad)

        val soknad2 = soknad.copy(status = Soknadstatus.SENDT, id = UUID.randomUUID().toString())
        sykepengesoknadDAO.lagreSykepengesoknad(soknad2)

        assertThat(
            sykepengesoknadDAO.finnSykepengesoknaderForSykmelding(
                soknad.sykmeldingId!!,
            ),
        ).hasSize(2)

        val antall = slettGamleUtkastService.slettGamleUtkast()
        assertThat(antall).isEqualTo(1)

        assertThat(
            sykepengesoknadDAO.finnSykepengesoknaderForSykmelding(
                soknad.sykmeldingId!!,
            ),
        ).hasSize(1)

        assertThat(sykepengesoknadDAO.finnSykepengesoknad(soknad2.id).status).isEqualTo(Soknadstatus.SENDT)
    }

    @Test
    fun `Sletter ikke utkast yngre enn 7 dager`() {
        @Suppress("DEPRECATION")
        val soknad =
            opprettNySoknadMock().copy(
                status = Soknadstatus.UTKAST_TIL_KORRIGERING,
                tom = LocalDate.now().minusDays(6),
                opprettet = LocalDate.now().minusDays(6).atStartOfDay().tilOsloInstant(),
            )
        sykepengesoknadDAO.lagreSykepengesoknad(soknad)

        assertThat(
            sykepengesoknadDAO.finnSykepengesoknaderForSykmelding(
                soknad.sykmeldingId!!,
            ),
        ).hasSize(1)

        val antall = slettGamleUtkastService.slettGamleUtkast()
        assertThat(antall).isEqualTo(0)

        assertThat(
            sykepengesoknadDAO.finnSykepengesoknaderForSykmelding(
                soknad.sykmeldingId!!,
            ),
        ).hasSize(1)

        assertThat(sykepengesoknadDAO.finnSykepengesoknad(soknad.id).status).isEqualTo(Soknadstatus.UTKAST_TIL_KORRIGERING)
    }
}
