package no.nav.helse.flex.arbeidsgiverperiode

import no.nav.helse.flex.arbeidsgiverperiode.domain.Oppfolgingstilfelle
import no.nav.helse.flex.arbeidsgiverperiode.domain.Syketilfelledag
import no.nav.helse.flex.arbeidsgiverperiode.domain.Tag
import java.time.LocalDate

fun grupperIOppfolgingstilfeller(tidslinje: List<Syketilfelledag>): List<Oppfolgingstilfelle> {
    val oppfolgingstilfelleListe = ArrayList<Oppfolgingstilfelle>()
    var gjeldendeSyketilfelledagListe = ArrayList<Syketilfelledag>()
    var ikkeSykedagerSidenForrigeSykedag = 0
    var dagerAvArbeidsgiverperiode = 0
    var behandlingsdager = 0
    var sisteDagIArbeidsgiverperiode: Syketilfelledag? = null
    var sisteSykedagEllerFeriedagIOppfolgingstilfelle: LocalDate? = null

    tidslinje.forEach {
        when {
            it.erArbeidsdag() -> {
                ikkeSykedagerSidenForrigeSykedag++
            }

            it.erFeriedag() -> {
                sisteSykedagEllerFeriedagIOppfolgingstilfelle = it.dag
                if (ikkeSykedagerSidenForrigeSykedag > 0) {
                    // Vi teller kun feriedager her hvis man har vært tilbake på jobb
                    ikkeSykedagerSidenForrigeSykedag++
                }
                if (dagerAvArbeidsgiverperiode in 1..15) {
                    dagerAvArbeidsgiverperiode++
                    sisteDagIArbeidsgiverperiode = it
                }
            }
            else -> { // Er syk
                sisteSykedagEllerFeriedagIOppfolgingstilfelle = it.dag
                gjeldendeSyketilfelledagListe.add(it)
                ikkeSykedagerSidenForrigeSykedag = 0

                if (it.erBehandlingsdag()) {
                    behandlingsdager++
                }
                dagerAvArbeidsgiverperiode++

                if (dagerAvArbeidsgiverperiode <= 16) {
                    sisteDagIArbeidsgiverperiode = it
                }
            }
        }

        if (ikkeSykedagerSidenForrigeSykedag >= 16 && gjeldendeSyketilfelledagListe.isNotEmpty()) {
            val nyttOppfolgingstilfelle =
                Oppfolgingstilfelle(
                    tidslinje = gjeldendeSyketilfelledagListe,
                    sisteDagIArbeidsgiverperiode = sisteDagIArbeidsgiverperiode ?: it,
                    dagerAvArbeidsgiverperiode = dagerAvArbeidsgiverperiode,
                    behandlingsdager = behandlingsdager,
                    sisteSykedagEllerFeriedag = sisteSykedagEllerFeriedagIOppfolgingstilfelle,
                )
            oppfolgingstilfelleListe.add(nyttOppfolgingstilfelle)

            // Resett variabler
            gjeldendeSyketilfelledagListe = ArrayList()
            ikkeSykedagerSidenForrigeSykedag = 0
            dagerAvArbeidsgiverperiode = 0
            behandlingsdager = 0
            sisteDagIArbeidsgiverperiode = null
            sisteSykedagEllerFeriedagIOppfolgingstilfelle = null
        }
    }

    if (gjeldendeSyketilfelledagListe.isNotEmpty()) {
        val sisteOppfolgingstilfelle =
            Oppfolgingstilfelle(
                tidslinje = gjeldendeSyketilfelledagListe,
                sisteDagIArbeidsgiverperiode = sisteDagIArbeidsgiverperiode ?: tidslinje.last(),
                dagerAvArbeidsgiverperiode = dagerAvArbeidsgiverperiode,
                behandlingsdager = behandlingsdager,
                sisteSykedagEllerFeriedag = sisteSykedagEllerFeriedagIOppfolgingstilfelle,
            )
        oppfolgingstilfelleListe.add(sisteOppfolgingstilfelle)
    }

    return oppfolgingstilfelleListe
}

private fun Syketilfelledag.erBehandlingsdag() =
    prioritertSyketilfellebit
        ?.tags
        ?.toList()
        ?.let { it in (Tag.SYKEPENGESOKNAD and Tag.BEHANDLINGSDAG) }
        ?: false

fun Syketilfelledag.erArbeidsdag() =
    prioritertSyketilfellebit
        ?.tags
        ?.toList()
        ?.let {
            it in (
                (Tag.SYKMELDING and Tag.PERIODE and Tag.FULL_AKTIVITET)
                    or
                    (Tag.SYKEPENGESOKNAD and Tag.ARBEID_GJENNOPPTATT)
                    or
                    (Tag.SYKEPENGESOKNAD and Tag.BEHANDLINGSDAGER)
            )
        }
        ?: true

fun Syketilfelledag.erFeriedag() =
    prioritertSyketilfellebit
        ?.tags
        ?.toList()
        ?.let { it in (Tag.SYKEPENGESOKNAD and (Tag.FERIE or Tag.PERMISJON)) }
        ?: false

fun Syketilfelledag.erSendt() =
    prioritertSyketilfellebit
        ?.tags
        ?.toList()
        ?.let { it in (Tag.SENDT or Tag.BEKREFTET) }
        ?: false
