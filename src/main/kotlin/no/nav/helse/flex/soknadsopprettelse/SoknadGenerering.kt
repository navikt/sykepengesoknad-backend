package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad

fun erForsteSoknadTilArbeidsgiverIForlop(
    eksisterendeSoknader: List<Sykepengesoknad>,
    sykepengesoknad: Sykepengesoknad,
): Boolean =
    eksisterendeSoknader
        .asSequence()
        .finnTidligereSoknaderMedSammeArbeidssituasjon(sykepengesoknad)
        .harSammeArbeidsgiver(sykepengesoknad)
        // Sjekker om det finnes en tidligere søknad med samme startdato for sykeforløp.
        .none { it.startSykeforlop == sykepengesoknad.startSykeforlop }

fun harBlittStiltUtlandsSporsmal(
    eksisterendeSoknader: List<Sykepengesoknad>,
    sykepengesoknad: Sykepengesoknad,
): Boolean =
    eksisterendeSoknader
        .asSequence()
        .finnTidligereSoknaderMedSammeArbeidssituasjon(sykepengesoknad)
        .harSammeArbeidsgiver(sykepengesoknad)
        // Finn søknader med samme startdato for sykeforløp og sjekk om de har stilt spørsmål om utenlandsopphold.
        .filter { it.startSykeforlop == sykepengesoknad.startSykeforlop }
        .any { it -> it.sporsmal.any { it.tag == UTENLANDSK_SYKMELDING_BOSTED } }

fun skalHaSporsmalOmMedlemskap(
    eksisterendeSoknader: List<Sykepengesoknad>,
    sykepengesoknad: Sykepengesoknad,
): Boolean =
    // Returnerer 'true' hvis det er første søknad til arbeidsgiver i sykeforlløpet og andre aktive søknader i samme
    // sykeforløp ikke allerede har medlemskapsspørsmål.
    erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, sykepengesoknad) &&
        eksisterendeSoknader
            .asSequence()
            .filterNot {
                listOf(
                    Soknadstatus.AVBRUTT,
                    Soknadstatus.UTGATT,
                    Soknadstatus.SLETTET,
                ).contains(it.status)
            }.filter { it.startSykeforlop == sykepengesoknad.startSykeforlop }
            .none { soknad ->
                soknad.sporsmal.any {
                    it.tag in
                        listOf(
                            MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE,
                            MEDLEMSKAP_OPPHOLD_UTENFOR_EOS,
                            MEDLEMSKAP_OPPHOLDSTILLATELSE,
                            MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE,
                            ARBEID_UTENFOR_NORGE,
                        )
                }
            }

// Finner søknader med samme arbeidssituasjon som, men med 'fom' FØR søknaden det sammenlignes med.
private fun Sequence<Sykepengesoknad>.finnTidligereSoknaderMedSammeArbeidssituasjon(
    sykepengesoknad: Sykepengesoknad,
): Sequence<Sykepengesoknad> =
    this
        .filter { it.fom != null && it.fom.isBefore(sykepengesoknad.fom) }
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
                    Soknadstype.GRADERT_REISETILSKUDD,
                ).contains(it.soknadstype)
            ) {
                return@filter it.arbeidsgiverOrgnummer == sykepengesoknad.arbeidsgiverOrgnummer
            }
        }
        true
    }

private fun soknadHarArbeidsgiver(sykepengesoknad: Sykepengesoknad) =
    sykepengesoknad.arbeidssituasjon == Arbeidssituasjon.ARBEIDSTAKER && sykepengesoknad.arbeidsgiverOrgnummer != null
