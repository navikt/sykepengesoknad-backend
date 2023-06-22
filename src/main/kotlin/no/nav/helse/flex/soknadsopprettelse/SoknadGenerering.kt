package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad

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

fun harBlittStiltUtlandsSporsmal(
    eksisterendeSoknader: List<Sykepengesoknad>,
    sykepengesoknad: Sykepengesoknad
): Boolean {
    return eksisterendeSoknader
        .asSequence()
        .filter { it.fom != null && it.fom.isBefore(sykepengesoknad.fom) }
        .filter { it.sykmeldingId != null }
        .filter { it.startSykeforlop != null }
        .filter { it.arbeidssituasjon == sykepengesoknad.arbeidssituasjon }
        .filter {
            if (sykepengesoknad.arbeidssituasjon == Arbeidssituasjon.ARBEIDSTAKER) {
                sykepengesoknad.arbeidsgiverOrgnummer?.let { orgnr ->
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
        .filter { it.startSykeforlop == sykepengesoknad.startSykeforlop }
        .any { sok -> sok.sporsmal.any { it.tag == UTENLANDSK_SYKMELDING_BOSTED } }
}
