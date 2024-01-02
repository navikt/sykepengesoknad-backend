package no.nav.helse.flex.util

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_I_ARBEID
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_NAR
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Component
class Metrikk(private val registry: MeterRegistry) {
    fun utelattSykmeldingFraSoknadOpprettelse(grunn: String) {
        registry.counter("sykmelding_utelatt_opprettelse", Tags.of("grunn", grunn)).increment()
    }

    val mottattSykmelding = registry.counter("mottatt_sykmelding_counter")

    fun julesoknadOpprettet() {
        registry.counter("syfosoknad_julesoknad", Tags.of("type", "info")).increment()
    }

    fun julesoknadAktivertNlEndret() {
        registry.counter("syfosoknad_julesoknad_aktivert_nl_endret", Tags.of("type", "info")).increment()
    }

    fun dodsfallMottatt() {
        registry.counter("syfosoknad_dodsmelding_mottatt", Tags.of("type", "info")).increment()
    }

    fun arbeidsgiverperiodeMedHelg(oppbruktArbeidsgiverperiode: Boolean) {
        registry.counter(
            "syfosoknad_soknad_med_helg_etterkant_av_arbeidsgiverperiode",
            Tags.of(
                "type",
                "info",
                "oppbruktArbeidsgiverperiode",
                oppbruktArbeidsgiverperiode.toString(),
            ),
        ).increment()
    }

    fun personHendelseMottatt() {
        registry.counter("syfosoknad_personhendelse_mottatt", Tags.of("type", "info")).increment()
    }

    fun tellSoknadOpprettet(soknadstype: Soknadstype) {
        registry.counter(
            "syfosoknad_soknad_opprettet",
            Tags.of(
                "type",
                "info",
                "soknadstype",
                soknadstype.name,
            ),
        )
            .increment()
    }

    fun soknadAvbrutt(soknadstype: Soknadstype) {
        registry.counter(
            "syfosoknad_soknad_avbrutt",
            Tags.of("type", "info", "soknadstype", soknadstype.name),
        ).increment()
    }

    fun tellSoknadSendt(soknadstype: Soknadstype) {
        registry.counter(
            "syfosoknad_soknad_sendt",
            Tags.of("type", "info", "soknadstype", soknadstype.name),
        ).increment()
    }

    fun tellUtkastTilKorrigeringOpprettet(soknadstype: Soknadstype) {
        registry.counter(
            "syfosoknad_utkast_til_korrigering_opprettet",
            Tags.of("type", "info", "soknadstype", soknadstype.name),
        ).increment()
    }

    fun utkastTilKorrigeringAvbrutt() {
        registry.counter("syfosoknad_utkast_til_korrigering_avbrutt", Tags.of("type", "info")).increment()
    }

    fun tellInnsendingFeilet(type: String?) {
        registry.counter("syfosoknad_innsending_feilet", Tags.of("type", "error", "soknadstype", type)).increment()
    }

    fun ettersending(mottaker: String?) {
        registry.counter("syfosoknad_ettersending", Tags.of("type", "info", "mottaker", mottaker)).increment()
    }

    fun tellSoknadMedSparteDagsverk() {
        registry.counter("syfosoknad_med_sparte_dagsverk", Tags.of("type", "info")).increment()
    }

    fun antallDagsverkSpart(
        type: String?,
        antallDagsverk: Double,
    ) {
        registry.counter("syfosoknad_dagsverk_spart", Tags.of("type", type)).increment(antallDagsverk)
    }

    fun tellSoknaderKunSendtTilArbeidsgiver() {
        registry.counter("syfosoknad_kun_arbeidsgiver", Tags.of("type", "info")).increment()
    }

    fun tellForsteSoknadISyketilfelle(soknadstype: String?) {
        registry.counter(
            "syfosoknad_syketilfelle_forstegangssoknad",
            Tags.of(
                "type",
                "info",
                "soknadstype",
                soknadstype,
            ),
        )
            .increment()
    }

    fun tellForlengelseSoknadISyketilfelle(soknadstype: String?) {
        registry.counter(
            "syfosoknad_syketilfelle_forlengelsesoknad",
            Tags.of(
                "type",
                "info",
                "soknadstype",
                soknadstype,
            ),
        )
            .increment()
    }

    fun tellDagerFraAktiveringTilInnsending(
        soknadstype: String?,
        dager: Long,
    ) {
        registry.counter(
            "syfosoknad_dager_fra_aktivering_til_innsending",
            Tags.of(
                "type",
                "info",
                "soknadstype",
                soknadstype,
                "dager",
                dager.toString(),
            ),
        )
            .increment()
    }

    fun prosesserSelvstendigSoknad(
        soknad: Sykepengesoknad,
        sykepengesoknadDTO: SykepengesoknadDTO,
    ) {
        if (soknad.status != Soknadstatus.SENDT) {
            return
        }
        if (sykepengesoknadDTO.harRedusertVenteperiode == true) {
            registry.counter("syfosoknad_anmodningsvedtak6_redusert_venteperiode", Tags.of("type", "info")).increment()
        }
        if (soknad.egenmeldtSykmelding == true) {
            registry.counter("syfosoknad_anmodningsvedtak6_egenmeldt", Tags.of("type", "info")).increment()
            soknad.getSporsmalMedTagOrNull(TILBAKE_I_ARBEID)
                ?.let {
                    if (it.forsteSvar == "JA") {
                        val tilbake =
                            LocalDate.parse(
                                soknad.getSporsmalMedTagOrNull(TILBAKE_NAR)?.forsteSvar!!,
                                DateTimeFormatter.ISO_DATE,
                            )
                        val dager = ChronoUnit.DAYS.between(tilbake, soknad.tom!!)
                        registry.counter(
                            "syfosoknad_anmodningsvedtak6_tilbake_i_arbeid",
                            Tags.of("type", "info", "dager", dager.toString()),
                        ).increment()
                    }
                }
            if (soknad.sporsmal.filter { sporsmal -> sporsmal.tag.matches("^JOBBET_DU_(100|GRADERT).*".toRegex()) }
                    .any { it.forsteSvar == "JA" }.apply {}
            ) {
                registry.counter("syfosoknad_anmodningsvedtak6_jobbet_du_noe", Tags.of("type", "info")).increment()
            }
        }
    }

    fun tellAntallFriskmeldteDagerForArbeidsledige(antallFriskmeldteDager: Long) {
        registry.counter(
            "syfosoknad_friskmeldte_dager_for_arbeidsledige",
            Tags.of(
                "type",
                "info",
                "dager",
                antallFriskmeldteDager.toString(),
            ),
        )
            .increment()
    }
}
