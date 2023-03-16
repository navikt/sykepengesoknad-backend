package no.nav.helse.flex.client.yrkesskade

import java.time.LocalDate

data class HarYsSakerRequest(

    val foedselsnumre: List<String>,

    val fomDato: LocalDate? = null
)

data class HarYsSakerResponse(
    val harYrkesskadeEllerYrkessykdom: HarYsSak,
    val beskrivelser: List<String>,
    val kilde: String = "Infotrygd",
    val kildeperiode: Kildeperiode? = null
)

data class Kildeperiode(val fomDato: LocalDate, val tomDato: LocalDate)

enum class HarYsSak {
    JA,
    NEI,
    MAA_SJEKKES_MANUELT
}

enum class Kilde {
    INFOTRYGD,
    KOMPYS
}
