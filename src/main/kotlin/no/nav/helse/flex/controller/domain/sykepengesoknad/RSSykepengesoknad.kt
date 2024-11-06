package no.nav.helse.flex.controller.domain.sykepengesoknad

import no.nav.helse.flex.medlemskap.KjentOppholdstillatelse
import no.nav.helse.flex.soknadsopprettelse.ArbeidsforholdFraInntektskomponenten
import java.time.LocalDate
import java.time.LocalDateTime

data class RSSykepengesoknad(
    val id: String,
    val sykmeldingId: String? = null,
    val soknadstype: RSSoknadstype,
    val status: RSSoknadstatus?,
    val fom: LocalDate? = null,
    val tom: LocalDate? = null,
    val opprettetDato: LocalDate?,
    val sendtTilNAVDato: LocalDateTime? = null,
    val sendtTilArbeidsgiverDato: LocalDateTime? = null,
    val avbruttDato: LocalDate? = null,
    val startSykeforlop: LocalDate? = null,
    val sykmeldingUtskrevet: LocalDate? = null,
    val arbeidsgiver: RSArbeidsgiver? = null,
    val korrigerer: String? = null,
    val korrigertAv: String? = null,
    val arbeidssituasjon: RSArbeidssituasjon? = null,
    val soknadPerioder: List<RSSoknadsperiode>? = null,
    val sporsmal: List<RSSporsmal>?,
    val egenmeldtSykmelding: Boolean?,
    val merknaderFraSykmelding: List<RSMerknad>?,
    val opprettetAvInntektsmelding: Boolean,
    val utenlandskSykmelding: Boolean,
    val klippet: Boolean,
    val inntektskilderDataFraInntektskomponenten: List<ArbeidsforholdFraInntektskomponenten>?,
    val inntektsopplysningerNyKvittering: Boolean? = null,
    val inntektsopplysningerInnsendingId: String? = null,
    val inntektsopplysningerInnsendingDokumenter: List<String>? = null,
    val korrigeringsfristUtlopt: Boolean?,
    val forstegangssoknad: Boolean?,
    val kjentOppholdstillatelse: KjentOppholdstillatelse? = null,
    val julesoknad: Boolean?,
) {
    fun alleSporsmalOgUndersporsmal(): List<RSSporsmal> {
        return sporsmal?.flatten()?.toList() ?: emptyList()
    }

    fun getSporsmalMedTag(tag: String): RSSporsmal {
        return getSporsmalMedTagOrNull(tag)
            ?: throw RuntimeException("Søknaden inneholder ikke spørsmål med tag: $tag")
    }

    fun getSporsmalMedTagOrNull(tag: String): RSSporsmal? {
        return sporsmal?.flatten()?.firstOrNull { s -> s.tag == tag }
    }
}

fun List<RSSporsmal>.flatten(): List<RSSporsmal> =
    flatMap {
        mutableListOf(it).apply {
            addAll(it.undersporsmal.flatten())
        }
    }
