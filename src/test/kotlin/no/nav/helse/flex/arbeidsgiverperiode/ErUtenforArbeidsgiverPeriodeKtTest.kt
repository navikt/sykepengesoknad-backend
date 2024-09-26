package no.nav.helse.flex.arbeidsgiverperiode

import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.mock.opprettNySoknad
import org.amshove.kluent.`should be false`
import org.amshove.kluent.`should be true`
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ErUtenforArbeidsgiverPeriodeKtTest {
    @Test
    fun `tolv dager er innenfor agp`() {
        val kortSoknad =
            opprettSendtSoknad(
                fom = LocalDate.of(2022, 7, 1),
                tom = LocalDate.of(2022, 7, 12),
            )

        kortSoknad.erUtenforArbeidsgiverPeriode(emptyList()).`should be false`()
    }

    @Test
    fun `seksten dager er innenfor agp`() {
        val kortSoknad =
            opprettSendtSoknad(
                fom = LocalDate.of(2022, 7, 1),
                tom = LocalDate.of(2022, 7, 16),
            )

        kortSoknad.erUtenforArbeidsgiverPeriode(emptyList()).`should be false`()
    }

    @Test
    fun `sytten dager er utenfor agp`() {
        val soknad =
            opprettSendtSoknad(
                fom = LocalDate.of(2022, 7, 1),
                tom = LocalDate.of(2022, 7, 17),
            )

        soknad.erUtenforArbeidsgiverPeriode(emptyList()).`should be true`()
    }

    @Test
    fun `tjue dager er utenfor agp`() {
        val soknad =
            opprettSendtSoknad(
                fom = LocalDate.of(2022, 7, 1),
                tom = LocalDate.of(2022, 7, 20),
            )

        soknad.erUtenforArbeidsgiverPeriode(emptyList()).`should be true`()
    }
}

fun opprettSendtSoknad(
    fom: LocalDate,
    tom: LocalDate,
) = opprettNySoknad().copy(
    fom = fom,
    tom = tom,
    status = Soknadstatus.SENDT,
)
