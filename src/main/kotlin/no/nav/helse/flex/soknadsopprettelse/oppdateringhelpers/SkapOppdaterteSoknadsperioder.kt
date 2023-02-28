package no.nav.helse.flex.soknadsopprettelse.oppdateringhelpers

import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Sykepengesoknad
import java.time.LocalDate

fun Sykepengesoknad.skapOppdaterteSoknadsperioder(oppdatertFomDato: LocalDate?): List<Soknadsperiode> {
    return if (oppdatertFomDato == null) {
        this.soknadPerioder!!
    } else {
        this.soknadPerioder!!
            .filter { (fom) -> fom.isBefore(oppdatertFomDato) }
            .map { soknadsperiode ->
                if (soknadsperiode.tom.isBefore(oppdatertFomDato)) {
                    soknadsperiode
                } else {
                    Soknadsperiode(
                        fom = soknadsperiode.fom,
                        tom = oppdatertFomDato.minusDays(1),
                        grad = soknadsperiode.grad,
                        sykmeldingstype = soknadsperiode.sykmeldingstype
                    )
                }
            }
    }
}
