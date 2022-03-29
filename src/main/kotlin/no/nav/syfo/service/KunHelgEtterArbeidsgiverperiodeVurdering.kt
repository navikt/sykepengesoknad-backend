package no.nav.syfo.service

import no.nav.syfo.domain.Arbeidsgiverperiode
import no.nav.syfo.domain.Mottaker
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.juridiskvurdering.JuridiskVurdering
import no.nav.syfo.juridiskvurdering.SporingType.organisasjonsnummer
import no.nav.syfo.juridiskvurdering.SporingType.soknad
import no.nav.syfo.juridiskvurdering.SporingType.sykmelding
import no.nav.syfo.juridiskvurdering.Utfall.VILKAR_IKKE_OPPFYLT
import no.nav.syfo.service.MottakerAvSoknadService.MottakerOgVurdering
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

fun MottakerAvSoknadService.kunHelgEtterArbeidsgiverperiodeVurdering(
    mottakerResultat: MottakerOgVurdering,
    sykepengesoknad: Sykepengesoknad
): Mottaker? {
    if (mottakerResultat.mottaker.tilNav() &&
        mottakerResultat.arbeidsgiverperiode != null &&
        mottakerResultat.arbeidsgiverperiode.arbeidsgiverPeriode != null
    ) {

        val sykepengesoknadTom = sykepengesoknad.tom
        val arbeidsgiverperiode: Arbeidsgiverperiode = mottakerResultat.arbeidsgiverperiode
        val arbeidsgiverperiodeTom = mottakerResultat.arbeidsgiverperiode.arbeidsgiverPeriode.tom

        val kunHelgEtterArbeidsgiverperiode =
            arbeidsgiverperiodeTom.erFredag() && sykepengesoknadTom!!.erHelgenEtter(arbeidsgiverperiodeTom)

        if (sykepengesoknad.status == Soknadstatus.SENDT && kunHelgEtterArbeidsgiverperiode) {
            val vurdering = JuridiskVurdering(
                fodselsnummer = sykepengesoknad.fnr,
                sporing = hashMapOf(soknad to listOf(sykepengesoknad.id))
                    .also { map ->
                        sykepengesoknad.sykmeldingId?.let {
                            map[sykmelding] = listOf(it)
                        }
                        sykepengesoknad.arbeidsgiverOrgnummer?.let {
                            map[organisasjonsnummer] = listOf(it)
                        }
                    },
                input = hashMapOf<String, Any>(
                    "versjon" to LocalDate.of(2022, 2, 1),

                ).also { map ->
                    sykepengesoknadTom?.let {
                        map["sykepengesoknadTom"] = it
                    }
                    arbeidsgiverperiode.arbeidsgiverPeriode?.let {
                        map["arbeidsgiverperiode"] = it
                    }
                },
                output = mapOf(
                    "kunHelgEtterArbeidsgiverperiode" to kunHelgEtterArbeidsgiverperiode,
                    "versjon" to LocalDate.of(2022, 2, 1),
                ),
                lovverk = "folketrygdloven",
                paragraf = "8-11",
                bokstav = null,
                ledd = null,
                punktum = null,
                lovverksversjon = LocalDate.of(1997, 5, 1),
                utfall = VILKAR_IKKE_OPPFYLT
            )
            juridiskVurderingKafkaProducer.produserMelding(vurdering)
        }

        if (kunHelgEtterArbeidsgiverperiode) {
            metrikk.arbeidsgiverperiodeMedHelg(true)
            return Mottaker.ARBEIDSGIVER
        }
    }
    return null
}

private fun Mottaker.tilNav(): Boolean = when (this) {
    Mottaker.NAV -> true
    Mottaker.ARBEIDSGIVER_OG_NAV -> true
    Mottaker.ARBEIDSGIVER -> false
}

private fun LocalDate.erHelgenEtter(arbeidsgiverperiodeTom: LocalDate): Boolean {
    val nesteLørdag = arbeidsgiverperiodeTom.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
    val nesteSøndag = arbeidsgiverperiodeTom.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))

    return this == nesteLørdag || this == nesteSøndag
}

private fun LocalDate.erFredag(): Boolean = this.dayOfWeek == DayOfWeek.FRIDAY
