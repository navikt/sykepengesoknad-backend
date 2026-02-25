package no.nav.helse.flex.domain.sykmelding

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Periode
import java.time.LocalDate

sealed interface BrukersituasjonDto {
    val yrkesgruppe: Yrkesgruppe
    val arbeidssituasjon: ArbeidssituasjonDto
}

enum class Yrkesgruppe {
    ARBEIDSTAKER,
    NARINGSDRIVENDE,
    FRILANSER,
    ARBEIDSLEDIG,
    FISKER_LOTT_OG_HYRE,
    UKJENT,
}

sealed interface ArbeidssituasjonDto {
    val name: String
}

enum class ArbeidstakerArbeidssituasjonDto : ArbeidssituasjonDto {
    ARBEIDSTAKER,
    FISKER_HYRE,
}

enum class NaringsdrivendeArbeidssituasjonDto : ArbeidssituasjonDto {
    NARINGSDRIVENDE,
    JORDBRUKER,
    FISKER_LOTT,
}

enum class FrilanserArbeidssituasjonDto : ArbeidssituasjonDto {
    FRILANSER,
}

enum class ArbeidsledigArbeidssituasjonDto : ArbeidssituasjonDto {
    ARBEIDSLEDIG,
    PERMITTERT,
}

enum class FiskerLottOgHyreArbeidssituasjonDto : ArbeidssituasjonDto {
    FISKER_LOTT_OG_HYRE,
}

enum class UkjentYrkesgruppeArbeidssituasjonDto : ArbeidssituasjonDto {
    ANNET,
    UTDATERT,
}

data class ArbeidstakerDto(
    val arbeidsgiver: ArbeidsgiverDto,
    val harRiktigNarmesteLeder: Boolean,
    val egenmeldingsdager: List<LocalDate>,
    override val arbeidssituasjon: ArbeidstakerArbeidssituasjonDto,
    val fiskerSituasjon: FiskerSituasjonDto? = null,
) : BrukersituasjonDto {
    override val yrkesgruppe: Yrkesgruppe = Yrkesgruppe.ARBEIDSTAKER

    init {
        if (arbeidssituasjon == ArbeidstakerArbeidssituasjonDto.FISKER_HYRE) {
            requireNotNull(fiskerSituasjon)
        }
    }
}

data class NaringsdrivendeDto(
    val sykForSykmeldingPerioder: List<SykForSykmeldingPeriodeDto>,
    val harForsikringForste16Dager: Boolean = false,
    override val arbeidssituasjon: NaringsdrivendeArbeidssituasjonDto,
    val fiskerSituasjon: FiskerSituasjonDto? = null,
) : BrukersituasjonDto {
    override val yrkesgruppe: Yrkesgruppe = Yrkesgruppe.NARINGSDRIVENDE

    init {
        if (arbeidssituasjon == NaringsdrivendeArbeidssituasjonDto.FISKER_LOTT) {
            requireNotNull(fiskerSituasjon)
        }
    }
}

data class FrilanserDto(
    val sykForSykmeldingPerioder: List<SykForSykmeldingPeriodeDto>,
    val harForsikringForste16Dager: Boolean? = null,
    override val arbeidssituasjon: FrilanserArbeidssituasjonDto,
) : BrukersituasjonDto {
    override val yrkesgruppe: Yrkesgruppe = Yrkesgruppe.FRILANSER
}

data class ArbeidsledigDto(
    val tidligereArbeidsgiver: TidligereArbeidsgiverDto?,
    override val arbeidssituasjon: ArbeidsledigArbeidssituasjonDto,
) : BrukersituasjonDto {
    override val yrkesgruppe: Yrkesgruppe = Yrkesgruppe.ARBEIDSLEDIG
}

data class FiskerLottOgHyreDto(
    val arbeidsgiver: ArbeidsgiverDto,
    val harRiktigNarmesteLeder: Boolean,
    val egenmeldingsdager: List<LocalDate>,
    override val arbeidssituasjon: FiskerLottOgHyreArbeidssituasjonDto,
    val fiskerSituasjon: FiskerSituasjonDto,
) : BrukersituasjonDto {
    override val yrkesgruppe: Yrkesgruppe = Yrkesgruppe.FISKER_LOTT_OG_HYRE
}

data class UkjentYrkesgruppeDto(
    override val arbeidssituasjon: UkjentYrkesgruppeArbeidssituasjonDto,
    val antattArbeidssituasjon: ArbeidssituasjonDto? = null,
) : BrukersituasjonDto {
    override val yrkesgruppe: Yrkesgruppe = Yrkesgruppe.UKJENT

    init {
        if (arbeidssituasjon == UkjentYrkesgruppeArbeidssituasjonDto.UTDATERT) {
            requireNotNull(antattArbeidssituasjon)
            require(antattArbeidssituasjon != UkjentYrkesgruppeArbeidssituasjonDto.UTDATERT)
        }
    }
}

data class FiskerSituasjonDto(
    val blad: FiskerBladDto,
)

data class ArbeidsgiverDto(
    val orgnummer: String,
    val juridiskOrgnummer: String,
    val orgnavn: String,
)

data class TidligereArbeidsgiverDto(
    val orgnummer: String,
    val orgnavn: String,
)

data class SykForSykmeldingPeriodeDto(
    val fom: LocalDate,
    val tom: LocalDate,
)

enum class FiskerBladDto {
    A,
    B,
}
