package no.nav.helse.flex.service

import no.nav.helse.flex.domain.Arbeidsgiverperiode
import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.juridiskvurdering.JuridiskVurdering
import no.nav.helse.flex.juridiskvurdering.SporingType.ORGANISASJONSNUMMER
import no.nav.helse.flex.juridiskvurdering.SporingType.SOKNAD
import no.nav.helse.flex.juridiskvurdering.SporingType.SYKMELDING
import no.nav.helse.flex.juridiskvurdering.Utfall.VILKAR_IKKE_OPPFYLT
import no.nav.helse.flex.service.MottakerAvSoknadService.MottakerOgVurdering
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

fun MottakerAvSoknadService.kunHelgEtterArbeidsgiverperiodeVurdering(
    mottakerResultat: MottakerOgVurdering,
    sykepengesoknad: Sykepengesoknad,
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
            val vurdering =
                JuridiskVurdering(
                    fodselsnummer = sykepengesoknad.fnr,
                    sporing =
                        hashMapOf(SOKNAD to listOf(sykepengesoknad.id))
                            .also { map ->
                                sykepengesoknad.sykmeldingId?.let {
                                    map[SYKMELDING] = listOf(it)
                                }
                                sykepengesoknad.arbeidsgiverOrgnummer?.let {
                                    map[ORGANISASJONSNUMMER] = listOf(it)
                                }
                            },
                    input =
                        hashMapOf<String, Any>(
                            "versjon" to LocalDate.of(2022, 2, 1),
                        ).also { map ->
                            sykepengesoknadTom?.let {
                                map["sykepengesoknadTom"] = it
                            }
                            arbeidsgiverperiode.arbeidsgiverPeriode?.let {
                                map["arbeidsgiverperiode"] = it
                            }
                        },
                    output =
                        mapOf(
                            "kunHelgEtterArbeidsgiverperiode" to kunHelgEtterArbeidsgiverperiode,
                            "versjon" to LocalDate.of(2022, 2, 1),
                        ),
                    lovverk = "folketrygdloven",
                    paragraf = "8-11",
                    bokstav = null,
                    ledd = null,
                    punktum = null,
                    lovverksversjon = LocalDate.of(1997, 5, 1),
                    utfall = VILKAR_IKKE_OPPFYLT,
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

private fun Mottaker.tilNav(): Boolean =
    when (this) {
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
