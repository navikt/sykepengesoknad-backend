package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.util.isBeforeOrEqual

fun erForsteSoknadTilArbeidsgiverIForlop(
    eksisterendeSoknader: List<Sykepengesoknad>,
    sykepengesoknad: Sykepengesoknad
): Boolean {
    return eksisterendeSoknader.asSequence()
        .finnTidligereSoknaderMedSammeArbeidssituasjon(sykepengesoknad)
        .harSammeArbeidsgiver(sykepengesoknad)
        // Sjekker om det finnes en tidligere søknad med samme startdato for sykeforløp.
        .none { it.startSykeforlop == sykepengesoknad.startSykeforlop }
}

fun erForsteSoknadIForlop(
    eksisterendeSoknader: List<Sykepengesoknad>,
    sykepengesoknad: Sykepengesoknad
): Boolean {
    return eksisterendeSoknader.asSequence()
        // Finner søknader med samme arbeidssituasjon som, men med 'fom' FØR eller LIK søknaden det sammenlignes med.
        .filter { it.fom != null && it.fom.isBeforeOrEqual(sykepengesoknad.fom!!) }
        .filter { it.sykmeldingId != null && it.startSykeforlop != null }
        // Spørsmål om medlemskap vil bare bli stilt i for søknader med arbeidssituasjon.ARBEIDSTAKER, men det ingen
        // gevinst i å eksplitt sjekke på det her.
        .filter { it.arbeidssituasjon == sykepengesoknad.arbeidssituasjon }
        // Sjekker om det finnes en tidligere søknad med samme startdato for sykeforløp.
        .none { it.startSykeforlop == sykepengesoknad.startSykeforlop }
}

fun harBlittStiltUtlandsSporsmal(
    eksisterendeSoknader: List<Sykepengesoknad>,
    sykepengesoknad: Sykepengesoknad
): Boolean {
    return eksisterendeSoknader.asSequence()
        .finnTidligereSoknaderMedSammeArbeidssituasjon(sykepengesoknad)
        .harSammeArbeidsgiver(sykepengesoknad)
        // Finn søknader med samme startdato for sykeforløp og sjekk om de har stilt spørsmål om utenlandsopphold.
        .filter { it.startSykeforlop == sykepengesoknad.startSykeforlop }
        .any { it -> it.sporsmal.any { it.tag == UTENLANDSK_SYKMELDING_BOSTED } }
}

// Finner søknader med samme arbeidssituasjon som, men med 'fom' FØR søknaden det sammenlignes med.
private fun Sequence<Sykepengesoknad>.finnTidligereSoknaderMedSammeArbeidssituasjon(sykepengesoknad: Sykepengesoknad): Sequence<Sykepengesoknad> =
    this.filter { it.fom != null && it.fom.isBefore(sykepengesoknad.fom) }
        .filter { it.sykmeldingId != null && it.startSykeforlop != null }
        .filter { it.arbeidssituasjon == sykepengesoknad.arbeidssituasjon }

// Om det er arbeidssituasjon ARBEIDSTAKER med søknadstype BEHANDLINGSDAGER, GRADERT_REISETILSKUD eller ARBEIDSTAKER
// sjekkes det at arbeidsgiver er den samme.
private fun Sequence<Sykepengesoknad>.harSammeArbeidsgiver(sykepengesoknad: Sykepengesoknad): Sequence<Sykepengesoknad> =
    this.filter {
        if (soknadHarArbeidsgiver(sykepengesoknad)) {
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

private fun soknadHarArbeidsgiver(sykepengesoknad: Sykepengesoknad) =
    sykepengesoknad.arbeidssituasjon == Arbeidssituasjon.ARBEIDSTAKER && sykepengesoknad.arbeidsgiverOrgnummer != null
