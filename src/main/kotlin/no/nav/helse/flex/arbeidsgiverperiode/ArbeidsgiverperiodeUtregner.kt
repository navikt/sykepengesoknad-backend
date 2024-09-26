package no.nav.helse.flex.arbeidsgiverperiode

import no.nav.helse.flex.arbeidsgiverperiode.domain.Arbeidsgiverperiode
import no.nav.helse.flex.arbeidsgiverperiode.domain.PeriodeDTO
import no.nav.helse.flex.arbeidsgiverperiode.domain.Syketilfellebit
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.parseEgenmeldingsdagerFraSykmelding
import java.time.LocalDate

fun beregnArbeidsgiverperiode(
    biter: List<Syketilfellebit>,
    soknad: Sykepengesoknad,
): Arbeidsgiverperiode? {
    val genererOppfolgingstilfelle =
        genererOppfolgingstilfelle(
            biter = biter,
            grense = soknad.tom!!.atStartOfDay(),
            startSyketilfelle = soknad.startSykeforlop,
        )
    return genererOppfolgingstilfelle
        ?.lastOrNull {
            if (it.sisteSykedagEllerFeriedag == null) {
                return@lastOrNull it.oppbruktArbeidsgvierperiode()
            }
            it.sisteSykedagEllerFeriedag.plusDays(16).isEqualOrAfter(soknad.forsteDagISoknad())
        }
        ?.let {
            Arbeidsgiverperiode(
                it.dagerAvArbeidsgiverperiode,
                it.oppbruktArbeidsgvierperiode(),
                it.arbeidsgiverperiode().let { p -> PeriodeDTO(p.first, p.second) },
            )
        }
}

private fun Sykepengesoknad.forsteDagISoknad(): LocalDate {
    return egenmeldingsdagerFraSykmelding.parseEgenmeldingsdagerFraSykmelding()?.minOrNull()
        ?: fom!!
}
