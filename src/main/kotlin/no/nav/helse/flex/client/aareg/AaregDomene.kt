package no.nav.helse.flex.client.aareg

import java.time.LocalDate

data class ArbeidsforholdRequest(
    val arbeidstakerId: String,
    val arbeidsforholdtyper: List<String>,
    val arbeidsforholdstatuser: List<String>,
)

data class Arbeidsforhold(
    val type: Kodeverksentitet,
    val arbeidstaker: Arbeidstaker,
    val arbeidssted: Arbeidssted,
    val opplysningspliktig: Opplysningspliktig,
    val ansettelsesperiode: Ansettelsesperiode,
)

data class Ansettelsesperiode(
    val startdato: LocalDate,
    val sluttdato: LocalDate? = null,
)

data class Kodeverksentitet(
    val kode: String,
    val beskrivelse: String,
)

data class Arbeidstaker(
    val identer: List<Ident>,
)

data class Arbeidssted(
    val type: String,
    val identer: List<Ident>,
)

data class Opplysningspliktig(
    val type: String,
    val identer: List<Ident>,
)

data class Ident(
    val type: String,
    val ident: String,
    val gjeldende: Boolean? = null,
)
