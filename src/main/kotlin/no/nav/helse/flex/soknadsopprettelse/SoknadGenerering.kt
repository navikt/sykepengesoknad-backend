package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import java.time.LocalDate

fun erForsteSoknadTilArbeidsgiverIForlop(
    eksisterendeSoknader: List<Sykepengesoknad>,
    soknadMetadata: Sykepengesoknad
): Boolean {
    return eksisterendeSoknader
        .asSequence()
        .filter { it.fom != null && it.fom.isBefore(soknadMetadata.fom) }
        .filter { it.sykmeldingId != null }
        .filter { it.startSykeforlop != null }
        .filter { it.arbeidssituasjon == soknadMetadata.arbeidssituasjon }
        .filter {
            if (soknadMetadata.arbeidssituasjon == Arbeidssituasjon.ARBEIDSTAKER) {
                soknadMetadata.arbeidsgiverOrgnummer?.let { orgnr ->
                    if (it.soknadstype == Soknadstype.ARBEIDSTAKERE) {
                        return@filter it.arbeidsgiverOrgnummer == orgnr
                    } else if (it.soknadstype == Soknadstype.BEHANDLINGSDAGER) {
                        return@filter it.arbeidsgiverOrgnummer == orgnr
                    }
                    false
                }
            }
            true
        }
        .none { it.startSykeforlop == soknadMetadata.startSykeforlop }
}

fun hentTidligsteFomForSykmelding(
    soknadMetadata: Sykepengesoknad,
    eksisterendeSoknader: List<Sykepengesoknad>
): LocalDate {
    return eksisterendeSoknader
        .filter { it.sykmeldingId == soknadMetadata.sykmeldingId }
        .map { it.fom!! }
        .toMutableList()
        .also { it.add(soknadMetadata.fom!!) }
        .minOrNull()!!
}
