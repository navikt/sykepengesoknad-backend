package no.nav.syfo.soknadsopprettelse.oppdateringhelpers

import no.nav.syfo.domain.Soknadsperiode
import no.nav.syfo.domain.Sykepengesoknad
import java.time.LocalDate

fun Sykepengesoknad.skapOppdaterteSoknadsperioder(oppdatertFomDato: LocalDate?): List<Soknadsperiode> {
    return if (oppdatertFomDato == null)
        this.soknadPerioder!!
    else
        this.soknadPerioder!!
            .filter { (fom) -> fom.isBefore(oppdatertFomDato) }
            .map { soknadsperiode ->
                if (soknadsperiode.tom.isBefore(oppdatertFomDato))
                    soknadsperiode
                else
                    Soknadsperiode(
                        fom = soknadsperiode.fom,
                        tom = oppdatertFomDato.minusDays(1),
                        grad = soknadsperiode.grad,
                        sykmeldingstype = soknadsperiode.sykmeldingstype
                    )
            }
}
