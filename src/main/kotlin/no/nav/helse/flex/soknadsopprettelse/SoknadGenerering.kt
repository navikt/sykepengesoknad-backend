package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad

fun erForsteSoknadTilArbeidsgiverIForlop(
    eksisterendeSoknader: List<Sykepengesoknad>,
    sykepengesoknad: Sykepengesoknad
): Boolean {
    return finnTidligereSoknaderMedSammeArbeidssituasjon(eksisterendeSoknader, sykepengesoknad)
        // Sjekker om det finnes en tidligere søknad med samme startdato for sykeforløp.
        .none { it.startSykeforlop == sykepengesoknad.startSykeforlop }
}

fun harBlittStiltUtlandsSporsmal(
    eksisterendeSoknader: List<Sykepengesoknad>,
    sykepengesoknad: Sykepengesoknad
): Boolean {
    return finnTidligereSoknaderMedSammeArbeidssituasjon(eksisterendeSoknader, sykepengesoknad)
        // Finn søkander med samme startdato for sykeforløp og sjekk om de har stilt spørsmål om utenlandsopphold.
        .filter { it.startSykeforlop == sykepengesoknad.startSykeforlop }
        .any { it -> it.sporsmal.any { it.tag == UTENLANDSK_SYKMELDING_BOSTED } }
}

// Returnerer en liste med søknader med tidligere 'fom' og samme arbeidssituasjon som søknaden det sammenlignes med.
// Om det er arbeidssituasjon ARBEIDSTAKER med søknadstype BEHANDLINGSDAGER, GRADERT_REISETILSKUD eller ARBEIDSTAKER
// sjekkes det at arbeidsgiver er den samme.
private fun finnTidligereSoknaderMedSammeArbeidssituasjon(
    eksisterendeSoknader: List<Sykepengesoknad>,
    sykepengesoknad: Sykepengesoknad
): Sequence<Sykepengesoknad> {
    return eksisterendeSoknader
        .asSequence()
        .filter { it.fom != null && it.fom.isBefore(sykepengesoknad.fom) }
        .filter { it.sykmeldingId != null && it.startSykeforlop != null }
        .filter { it.arbeidssituasjon == sykepengesoknad.arbeidssituasjon }
        .filter {
            if (sykepengesoknad.arbeidssituasjon == Arbeidssituasjon.ARBEIDSTAKER && sykepengesoknad.arbeidsgiverOrgnummer != null) {
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
}
