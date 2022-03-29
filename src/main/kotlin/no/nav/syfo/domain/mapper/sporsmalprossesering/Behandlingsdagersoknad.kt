package no.nav.syfo.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildetypeDTO
import no.nav.syfo.domain.Sporsmal
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.soknadsopprettelse.ENKELTSTAENDE_BEHANDLINGSDAGER_UKE
import no.nav.syfo.soknadsopprettelse.HVILKE_ANDRE_INNTEKTSKILDER
import no.nav.syfo.soknadsopprettelse.INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD
import no.nav.syfo.soknadsopprettelse.INNTEKTSKILDE_ANNET
import no.nav.syfo.soknadsopprettelse.INNTEKTSKILDE_FRILANSER
import no.nav.syfo.soknadsopprettelse.INNTEKTSKILDE_JORDBRUKER
import no.nav.syfo.soknadsopprettelse.INNTEKTSKILDE_SELVSTENDIG
import no.nav.syfo.soknadsopprettelse.INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*

fun Sykepengesoknad.hentBehandlingsdager(): List<LocalDate> {

    return this.alleSporsmalOgUndersporsmal()
        .filter { it.tag.startsWith(ENKELTSTAENDE_BEHANDLINGSDAGER_UKE) }
        .filter { it.svar.size == 1 }
        .mapNotNull {
            try {
                LocalDate.parse(it.svar.first().verdi, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (e: DateTimeParseException) {
                null
            }
        }
}

fun hentInntektListeBehandlingsdager(soknad: Sykepengesoknad): List<InntektskildeDTO> {
    val andreinntektsporsmal = soknad.getSporsmalMedTagOrNull(HVILKE_ANDRE_INNTEKTSKILDER)
    return if ("JA" == andreinntektsporsmal?.forsteSvar)
        andreinntektsporsmal.undersporsmal[0].undersporsmal
            .filter { it.svar.isNotEmpty() }
            .map {
                InntektskildeDTO(
                    mapSporsmalTilInntektskildetype(it),
                    "JA" == it.undersporsmal[0].forsteSvar
                )
            }
    else
        Collections.emptyList()
}

private fun mapSporsmalTilInntektskildetype(sporsmal: Sporsmal): InntektskildetypeDTO? {
    return when (sporsmal.tag) {
        INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD -> InntektskildetypeDTO.ANDRE_ARBEIDSFORHOLD
        INNTEKTSKILDE_SELVSTENDIG -> InntektskildetypeDTO.SELVSTENDIG_NARINGSDRIVENDE
        INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA -> InntektskildetypeDTO.SELVSTENDIG_NARINGSDRIVENDE_DAGMAMMA
        INNTEKTSKILDE_JORDBRUKER -> InntektskildetypeDTO.JORDBRUKER_FISKER_REINDRIFTSUTOVER
        INNTEKTSKILDE_FRILANSER -> InntektskildetypeDTO.FRILANSER
        INNTEKTSKILDE_ANNET -> InntektskildetypeDTO.ANNET
        else -> null
    }
}
