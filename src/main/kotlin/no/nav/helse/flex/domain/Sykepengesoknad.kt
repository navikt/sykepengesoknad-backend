package no.nav.helse.flex.domain

import no.nav.helse.flex.inntektsopplysninger.InntektsopplysningerDokumentType
import no.nav.helse.flex.medlemskap.KjentOppholdstillatelse
import no.nav.helse.flex.soknadsopprettelse.ArbeidsforholdFraInntektskomponenten
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate

data class Sykepengesoknad(
    val id: String,
    val fnr: String,
    val soknadstype: Soknadstype,
    val status: Soknadstatus,
    val opprettet: Instant?,
    val avbruttDato: LocalDate? = null,
    val sendtNav: Instant? = null,
    val korrigerer: String? = null,
    val korrigertAv: String? = null,
    val sporsmal: List<Sporsmal>,
    val opprinnelse: Opprinnelse = Opprinnelse.SYKEPENGESOKNAD_BACKEND,
    val avsendertype: Avsendertype? = null,
    val sykmeldingId: String?,
    val aktivertDato: LocalDate? = null,
    val fom: LocalDate?,
    val tom: LocalDate?,
    val startSykeforlop: LocalDate?,
    val sykmeldingSkrevet: Instant?,
    val sykmeldingSignaturDato: Instant? = null,
    val soknadPerioder: List<Soknadsperiode>?,
    val sendtArbeidsgiver: Instant? = null,
    val arbeidsgiverOrgnummer: String? = null,
    val arbeidsgiverNavn: String? = null,
    val arbeidssituasjon: Arbeidssituasjon?,
    val egenmeldtSykmelding: Boolean? = null,
    val merknaderFraSykmelding: List<Merknad>? = null,
    val opprettetAvInntektsmelding: Boolean = false,
    val sendt: Instant? = null,
    val utenlandskSykmelding: Boolean,
    val klippet: Boolean = false,
    val egenmeldingsdagerFraSykmelding: String?,
    val inntektskilderDataFraInntektskomponenten: List<ArbeidsforholdFraInntektskomponenten>? = null,
    val korrigeringsfristUtlopt: Boolean? = null,
    val forstegangssoknad: Boolean?,
    val tidligereArbeidsgiverOrgnummer: String? = null,
    val inntektsopplysningerNyKvittering: Boolean? = null,
    val inntektsopplysningerInnsendingId: String? = null,
    val inntektsopplysningerInnsendingDokumenter: List<InntektsopplysningerDokumentType>? = null,
    val fiskerBlad: FiskerBlad? = null,
    val kjentOppholdstillatelse: KjentOppholdstillatelse? = null,
) : Serializable {
    fun alleSporsmalOgUndersporsmal(): List<Sporsmal> {
        return sporsmal.flatten()
    }

    fun getSporsmalMedTag(tag: String): Sporsmal {
        return getSporsmalMedTagOrNull(tag)
            ?: throw RuntimeException("Søknaden inneholder ikke spørsmål med tag: $tag")
    }

    fun getSporsmalMedTagOrNull(tag: String): Sporsmal? {
        return sporsmal.flatten().firstOrNull { s -> s.tag == tag }
    }

    private fun addHovedsporsmalHjelper(
        nyttSporsmal: Sporsmal,
        etterHovedsporsmal: Sporsmal?,
    ): List<Sporsmal> =
        if (sporsmal.none { s -> s.tag == nyttSporsmal.tag }) {
            val spm = sporsmal.toMutableList()
            etterHovedsporsmal
                ?.let {
                    spm.forEachIndexed { index, sporsmal ->
                        if (sporsmal.tag == it.tag) {
                            return@let index
                        }
                    }
                    return@let null
                }
                ?.also { spm.add(it + 1, nyttSporsmal) }
                ?: spm.add(nyttSporsmal)
            spm.toList()
        } else {
            sporsmal
        }

    private fun fjernSporsmalHjelper(tag: String): List<Sporsmal> = fjernSporsmalHjelper(tag, sporsmal)

    private fun fjernSporsmalHjelper(
        tag: String,
        sporsmal: List<Sporsmal>,
    ): List<Sporsmal> =
        sporsmal
            .filterNot { it.tag == tag }
            .map { it.copy(undersporsmal = fjernSporsmalHjelper(tag, it.undersporsmal)) }

    private fun replaceSporsmalHjelper(nyttSporsmal: Sporsmal): List<Sporsmal> = replaceSporsmalHjelper(nyttSporsmal, sporsmal)

    private fun replaceSporsmalHjelper(
        nyttSporsmal: Sporsmal,
        sporsmal: List<Sporsmal>,
    ): List<Sporsmal> =
        sporsmal.map { spm ->
            if (nyttSporsmal.tag == spm.tag) {
                nyttSporsmal
            } else {
                spm.copy(undersporsmal = replaceSporsmalHjelper(nyttSporsmal, spm.undersporsmal))
            }
        }

    private fun replaceSporsmalHjelper(nyttSporsmal: List<Sporsmal>): List<Sporsmal> = replaceSporsmalHjelper(nyttSporsmal, sporsmal)

    private fun replaceSporsmalHjelper(
        nyttSporsmal: List<Sporsmal>,
        sporsmal: List<Sporsmal>,
    ): List<Sporsmal> =
        sporsmal.map { spm ->
            nyttSporsmal.find { it.tag == spm.tag }
                ?: spm.copy(undersporsmal = replaceSporsmalHjelper(nyttSporsmal, spm.undersporsmal))
        }

    fun replaceSporsmal(nyttSporsmal: Sporsmal): Sykepengesoknad = copy(sporsmal = replaceSporsmalHjelper(nyttSporsmal))

    @Deprecated("Denne håndterer ikke underspørsmål så bra hvis de er underspørsmål til hovedspørsmål som også byttes")
    fun replaceSporsmal(nyttSporsmal: List<Sporsmal>): Sykepengesoknad = copy(sporsmal = replaceSporsmalHjelper(nyttSporsmal))

    fun addHovedsporsmal(
        nyttSporsmal: Sporsmal,
        etterHovedsporsmal: Sporsmal?,
    ): Sykepengesoknad = copy(sporsmal = addHovedsporsmalHjelper(nyttSporsmal, etterHovedsporsmal))

    fun fjernSporsmal(tag: String): Sykepengesoknad = copy(sporsmal = fjernSporsmalHjelper(tag))
}

fun List<Sporsmal>.flatten(): List<Sporsmal> =
    flatMap {
        mutableListOf(it).apply {
            addAll(it.undersporsmal.flatten())
        }
    }
