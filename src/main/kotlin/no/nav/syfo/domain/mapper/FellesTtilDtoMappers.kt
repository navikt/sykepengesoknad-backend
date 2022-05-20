package no.nav.syfo.domain.mapper

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.nav.helse.flex.sykepengesoknad.kafka.*
import no.nav.syfo.domain.*
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun Avsendertype.tilAvsendertypeDTO(): AvsendertypeDTO {
    return when (this) {
        Avsendertype.SYSTEM -> AvsendertypeDTO.SYSTEM
        Avsendertype.BRUKER -> AvsendertypeDTO.BRUKER
    }
}

fun Arbeidssituasjon.tilArbeidssituasjonDTO(): ArbeidssituasjonDTO {
    return when (this) {
        Arbeidssituasjon.ARBEIDSTAKER -> ArbeidssituasjonDTO.ARBEIDSTAKER
        Arbeidssituasjon.FRILANSER -> ArbeidssituasjonDTO.FRILANSER
        Arbeidssituasjon.NAERINGSDRIVENDE -> ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE
        Arbeidssituasjon.ARBEIDSLEDIG -> ArbeidssituasjonDTO.ARBEIDSLEDIG
        Arbeidssituasjon.ANNET -> ArbeidssituasjonDTO.ANNET
    }
}

fun ArbeidsgiverForskutterer.tilArbeidsgiverForskuttererDTO(): ArbeidsgiverForskuttererDTO {
    return when (this) {
        ArbeidsgiverForskutterer.JA -> ArbeidsgiverForskuttererDTO.JA
        ArbeidsgiverForskutterer.NEI -> ArbeidsgiverForskuttererDTO.NEI
        ArbeidsgiverForskutterer.VET_IKKE -> ArbeidsgiverForskuttererDTO.VET_IKKE
    }
}

fun Mottaker.tilMottakerDTO(): MottakerDTO {
    return when (this) {
        Mottaker.ARBEIDSGIVER_OG_NAV -> MottakerDTO.ARBEIDSGIVER_OG_NAV
        Mottaker.ARBEIDSGIVER -> MottakerDTO.ARBEIDSGIVER
        Mottaker.NAV -> MottakerDTO.NAV
    }
}

fun List<Merknad>?.tilMerknadDTO(): List<MerknadDTO>? =
    this?.map { MerknadDTO(type = it.type, beskrivelse = it.beskrivelse) }

fun Visningskriterie.tilVisningskriteriumDTO(): VisningskriteriumDTO {
    return when (this) {
        Visningskriterie.JA -> VisningskriteriumDTO.JA
        Visningskriterie.NEI -> VisningskriteriumDTO.NEI
        Visningskriterie.CHECKED -> VisningskriteriumDTO.CHECKED
    }
}

fun Soknadstatus.tilSoknadstatusDTO(): SoknadsstatusDTO {
    return when (this) {
        Soknadstatus.NY -> SoknadsstatusDTO.NY
        Soknadstatus.UTKAST_TIL_KORRIGERING -> SoknadsstatusDTO.NY
        Soknadstatus.SENDT -> SoknadsstatusDTO.SENDT
        Soknadstatus.AVBRUTT -> SoknadsstatusDTO.AVBRUTT
        Soknadstatus.FREMTIDIG -> SoknadsstatusDTO.FREMTIDIG
        Soknadstatus.KORRIGERT -> SoknadsstatusDTO.KORRIGERT
        Soknadstatus.SLETTET -> SoknadsstatusDTO.SLETTET
        Soknadstatus.UTGATT -> SoknadsstatusDTO.UTGAATT
    }
}

fun Svartype.tilSvartypeDTO(): SvartypeDTO {
    return when (this) {
        Svartype.JA_NEI -> SvartypeDTO.JA_NEI
        Svartype.CHECKBOX -> SvartypeDTO.CHECKBOX
        Svartype.CHECKBOX_GRUPPE -> SvartypeDTO.CHECKBOX_GRUPPE
        Svartype.CHECKBOX_PANEL -> SvartypeDTO.CHECKBOX_PANEL
        Svartype.DATO -> SvartypeDTO.DATO
        Svartype.FRITEKST -> SvartypeDTO.FRITEKST
        Svartype.PERIODE -> SvartypeDTO.PERIODE
        Svartype.PERIODER -> SvartypeDTO.PERIODER
        Svartype.PROSENT -> SvartypeDTO.PROSENT
        Svartype.RADIO -> SvartypeDTO.RADIO
        Svartype.RADIO_GRUPPE -> SvartypeDTO.RADIO_GRUPPE
        Svartype.RADIO_GRUPPE_TIMER_PROSENT -> SvartypeDTO.RADIO_GRUPPE_TIMER_PROSENT
        Svartype.RADIO_GRUPPE_UKEKALENDER -> SvartypeDTO.RADIO_GRUPPE_UKEKALENDER
        Svartype.TALL -> SvartypeDTO.TALL
        Svartype.TIMER -> SvartypeDTO.TIMER
        Svartype.IKKE_RELEVANT -> SvartypeDTO.IKKE_RELEVANT
        Svartype.INFO_BEHANDLINGSDAGER -> SvartypeDTO.INFO_BEHANDLINGSDAGER
        Svartype.LAND -> SvartypeDTO.LAND
        Svartype.KVITTERING -> SvartypeDTO.KVITTERING
        Svartype.DATOER -> SvartypeDTO.DATOER
        Svartype.BELOP -> SvartypeDTO.BELOP
        Svartype.KILOMETER -> SvartypeDTO.KILOMETER
    }
}

fun Sykmeldingstype.tilSykmeldingstypeDTO(): SykmeldingstypeDTO {
    return when (this) {
        Sykmeldingstype.AKTIVITET_IKKE_MULIG -> SykmeldingstypeDTO.AKTIVITET_IKKE_MULIG
        Sykmeldingstype.GRADERT -> SykmeldingstypeDTO.GRADERT
        Sykmeldingstype.BEHANDLINGSDAGER -> SykmeldingstypeDTO.BEHANDLINGSDAGER
        Sykmeldingstype.AVVENTENDE -> SykmeldingstypeDTO.AVVENTENDE
        Sykmeldingstype.REISETILSKUDD -> SykmeldingstypeDTO.REISETILSKUDD
        Sykmeldingstype.UKJENT -> throw IllegalArgumentException("Ugyldig sykmeldingtype")
    }
}

fun Sporsmal.tilSporsmalDTO(): SporsmalDTO {
    return SporsmalDTO(
        id = this.id,
        tag = this.tag,
        sporsmalstekst = this.sporsmalstekst,
        undertekst = this.undertekst,
        min = this.min,
        max = this.max,
        svartype = this.svartype.tilSvartypeDTO(),
        kriterieForVisningAvUndersporsmal = this.kriterieForVisningAvUndersporsmal?.tilVisningskriteriumDTO(),
        svar = this.svar.map { it.tilSvarDTO() },
        undersporsmal = this.undersporsmal.map { it.tilSporsmalDTO() }
    )
}

fun Svar.tilSvarDTO(): SvarDTO {
    return SvarDTO(this.verdi)
}

fun Soknadstype.tilSoknadstypeDTO(): SoknadstypeDTO {
    return when (this) {
        Soknadstype.ARBEIDSTAKERE -> SoknadstypeDTO.ARBEIDSTAKERE
        Soknadstype.OPPHOLD_UTLAND -> SoknadstypeDTO.OPPHOLD_UTLAND
        Soknadstype.SELVSTENDIGE_OG_FRILANSERE -> SoknadstypeDTO.SELVSTENDIGE_OG_FRILANSERE
        Soknadstype.ANNET_ARBEIDSFORHOLD -> SoknadstypeDTO.ANNET_ARBEIDSFORHOLD
        Soknadstype.REISETILSKUDD -> SoknadstypeDTO.REISETILSKUDD
        Soknadstype.BEHANDLINGSDAGER -> SoknadstypeDTO.BEHANDLINGSDAGER
        Soknadstype.ARBEIDSLEDIG -> SoknadstypeDTO.ARBEIDSLEDIG
        Soknadstype.GRADERT_REISETILSKUDD -> SoknadstypeDTO.GRADERT_REISETILSKUDD
    }
}

private val objectmapper = ObjectMapper().registerModule(JavaTimeModule()).registerKotlinModule()

fun String.getJsonPeriode(): PeriodeDTO {
    try {
        return objectmapper.readValue(this, PeriodeDTO::class.java)
    } catch (e: IOException) {
        return this.getJsonPeriodeFraGammeltFormat()
    }
}

fun String.getJsonPeriodeFraGammeltFormat(): PeriodeDTO {
    data class FomTom(val fom: String? = null, val tom: String? = null)

    fun String?.gammeltFormatTilLocalDate(): LocalDate? {
        this?.let {
            return LocalDate.parse(it, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        }
        return null
    }

    try {
        val fomTom = objectmapper.readValue(this, FomTom::class.java)
        return PeriodeDTO(
            fom = fomTom.fom.gammeltFormatTilLocalDate(),
            tom = fomTom.tom.gammeltFormatTilLocalDate(),
        )
    } catch (e: IOException) {
        throw RuntimeException("Feil ved parsing av periode: $this", e)
    }
}
