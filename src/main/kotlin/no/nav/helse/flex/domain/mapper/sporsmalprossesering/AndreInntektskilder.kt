package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildetypeDTO
import java.util.*

fun hentInntektListe(soknad: Sykepengesoknad): List<InntektskildeDTO> {
    val andreinntektsporsmal =
        soknad.getSporsmalMedTagOrNull(ANDRE_INNTEKTSKILDER) ?: soknad.getSporsmalMedTagOrNull(ANDRE_INNTEKTSKILDER_V2)

    return if ("JA" == andreinntektsporsmal?.forsteSvar) {
        andreinntektsporsmal.undersporsmal[0].undersporsmal
            .filter { it.svar.isNotEmpty() }
            .map { sporsmal ->

                if (sporsmal.undersporsmal.firstOrNull()?.tag?.endsWith(ER_DU_SYKMELDT) == true) {
                    InntektskildeDTO(
                        type = mapSporsmalTilInntektskildetype(sporsmal),
                        sykmeldt = if (sporsmal.undersporsmal.isEmpty()) {
                            null
                        } else {
                            "JA" == sporsmal.undersporsmal[0].forsteSvar
                        }
                    )
                } else {
                    // TODO: Send svar på andre inntektskilder selvstendig videre på kafka
                    InntektskildeDTO(
                        type = mapSporsmalTilInntektskildetype(sporsmal),
                        sykmeldt = null
                    )
                }
            }
    } else {
        Collections.emptyList()
    }
}

private fun mapSporsmalTilInntektskildetype(sporsmal: Sporsmal): InntektskildetypeDTO {
    return when (sporsmal.tag) {
        INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD -> InntektskildetypeDTO.ANDRE_ARBEIDSFORHOLD
        INNTEKTSKILDE_SELVSTENDIG -> InntektskildetypeDTO.SELVSTENDIG_NARINGSDRIVENDE
        INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA -> InntektskildetypeDTO.SELVSTENDIG_NARINGSDRIVENDE_DAGMAMMA
        INNTEKTSKILDE_JORDBRUKER -> InntektskildetypeDTO.JORDBRUKER_FISKER_REINDRIFTSUTOVER
        INNTEKTSKILDE_FRILANSER -> InntektskildetypeDTO.FRILANSER
        INNTEKTSKILDE_STYREVERV -> InntektskildetypeDTO.STYREVERV
        INNTEKTSKILDE_ANNET -> InntektskildetypeDTO.ANNET
        INNTEKTSKILDE_FOSTERHJEM -> InntektskildetypeDTO.FOSTERHJEMGODTGJORELSE
        INNTEKTSKILDE_OMSORGSLONN -> InntektskildetypeDTO.OMSORGSLONN
        INNTEKTSKILDE_ARBEIDSFORHOLD -> InntektskildetypeDTO.ARBEIDSFORHOLD
        INNTEKTSKILDE_FRILANSER_SELVSTENDIG -> InntektskildetypeDTO.FRILANSER_SELVSTENDIG
        else -> throw RuntimeException("Inntektskildetype " + sporsmal.tag + " finnes ikke i DTO")
    }
}
