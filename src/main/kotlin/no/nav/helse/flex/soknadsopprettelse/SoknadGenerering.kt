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
        .filter { it.sykmeldingId != null && it.startSykeforlop != null }
        .filter { it.arbeidssituasjon == sykepengesoknad.arbeidssituasjon }
        .filter {
            if (harArbeidsgiver(sykepengesoknad)) {
                // BEHANDLINGSDAGER og GRADERT_REISETILSKUDD behandles som ARBEIDSTAKER.
                if (listOf(
                        Soknadstype.ARBEIDSTAKERE,
                        Soknadstype.BEHANDLINGSDAGER,
                        Soknadstype.GRADERT_REISETILSKUDD
                    ).contains(it.soknadstype)
                ) {
                    return@filter it.arbeidsgiverOrgnummer == sykepengesoknad.arbeidsgiverOrgnummer
                }
            }
            true
        }
        // Sjekker om det finnes en tidligere søknad med samme startdato for sykeforløp.
        .none { it.startSykeforlop == sykepengesoknad.startSykeforlop }
}

fun harBlittStiltUtlandsSporsmal(
    eksisterendeSoknader: List<Sykepengesoknad>,
    sykepengesoknad: Sykepengesoknad
): Boolean {
    return eksisterendeSoknader
        .asSequence()
        .filter { it.fom != null && it.fom.isBefore(sykepengesoknad.fom) }
        .filter { it.sykmeldingId != null && it.startSykeforlop != null }
        .filter { it.arbeidssituasjon == sykepengesoknad.arbeidssituasjon }
        .filter {
            if (harArbeidsgiver(sykepengesoknad)) {
                // BEHANDLINGSDAGER og GRADERT_REISETILSKUDD behandles som ARBEIDSTAKER.
                if (listOf(
                        Soknadstype.ARBEIDSTAKERE,
                        Soknadstype.BEHANDLINGSDAGER,
                        Soknadstype.GRADERT_REISETILSKUDD
                    ).contains(it.soknadstype)
                ) {
                    return@filter it.arbeidsgiverOrgnummer == sykepengesoknad.arbeidsgiverOrgnummer
                }
            }
            true
        }
        // Finn søkander med samme startdato for sykeforløp og sjekk om de har stilt spørsmål om utenlandsopphold.
        .filter { it.startSykeforlop == sykepengesoknad.startSykeforlop }
        .any { sok -> sok.sporsmal.any { it.tag == UTENLANDSK_SYKMELDING_BOSTED } }
}

private fun harArbeidsgiver(sykepengesoknad: Sykepengesoknad) =
    sykepengesoknad.arbeidssituasjon == Arbeidssituasjon.ARBEIDSTAKER && sykepengesoknad.arbeidsgiverOrgnummer != null
