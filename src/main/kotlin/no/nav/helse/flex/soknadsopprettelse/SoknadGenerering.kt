package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad

fun erForsteSoknadTilArbeidsgiverIForlop(
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
                    // TODO: Ta med GRADERT_REISETILSKUDD.
                    if (it.soknadstype == Soknadstype.ARBEIDSTAKERE) {
                        return@filter it.arbeidsgiverOrgnummer == orgnr
                    } else if (it.soknadstype == Soknadstype.BEHANDLINGSDAGER) {
                        return@filter it.arbeidsgiverOrgnummer == orgnr
                    }
                    // TODO: Spiller ingen rolle om denne er true eller false siden den bare returnerer ut av 'let'.
                    false
                }
            }
            true
        }
        .none { it.startSykeforlop == sykepengesoknad.startSykeforlop }
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
                    // TODO: Ta med GRADERT_REISETILSKUDD.
                    if (it.soknadstype == Soknadstype.ARBEIDSTAKERE) {
                        return@filter it.arbeidsgiverOrgnummer == orgnr
                    } else if (it.soknadstype == Soknadstype.BEHANDLINGSDAGER) {
                        return@filter it.arbeidsgiverOrgnummer == orgnr
                    }
                    // TODO: Spiller ingen rolle om denne er true eller false siden den bare returnerer ut av 'let'.
                    false
                }
            }
            true
        }
        .filter { it.startSykeforlop == sykepengesoknad.startSykeforlop }
        .any { sok -> sok.sporsmal.any { it.tag == UTENLANDSK_SYKMELDING_BOSTED } }
}
