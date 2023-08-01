package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
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
        // Finn søknader med samme startdato for sykeforløp og sjekk om de har stilt spørsmål om utenlandsopphold.
        .filter { it.startSykeforlop == sykepengesoknad.startSykeforlop }
        .any { it -> it.sporsmal.any { it.tag == UTENLANDSK_SYKMELDING_BOSTED } }
}

fun harBlittStiltMedlemskapSporsmal(
    eksisterendeSoknader: List<Sykepengesoknad>,
    sykepengesoknad: Sykepengesoknad
): Boolean {
    return eksisterendeSoknader
        .asSequence()
        .filter { it.sykmeldingId != null && it.startSykeforlop != null }
        .filter { it.arbeidssituasjon == sykepengesoknad.arbeidssituasjon }
        .filter { it.status !in listOf(Soknadstatus.UTGATT, Soknadstatus.SLETTET, Soknadstatus.AVBRUTT) }
        .filter { it.startSykeforlop == sykepengesoknad.startSykeforlop }
        .any { it ->
            it.sporsmal.any {
                it.tag in listOf(
                    MEDLEMSKAP_OPPHOLDSTILLATELSE,
                    MEDLEMSKAP_OPPHOLD_UTENFOR_EOS,
                    MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE,
                    MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE
                )
            }
        }
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
}

private fun soknadHarArbeidsgiver(sykepengesoknad: Sykepengesoknad) =
    sykepengesoknad.arbeidssituasjon == Arbeidssituasjon.ARBEIDSTAKER && sykepengesoknad.arbeidsgiverOrgnummer != null
