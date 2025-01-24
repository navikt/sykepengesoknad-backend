package no.nav.helse.flex.client.aareg

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

data class ArbeidsforholdRequest(
    val arbeidstakerId: String,
    val arbeidsforholdtyper: List<String>,
    val arbeidsforholdstatuser: List<String>,
)

data class Arbeidsforhold(
    val id: String? = null,
    val type: Kodeverksentitet,
    val arbeidstaker: Arbeidstaker,
    val arbeidssted: Arbeidssted,
    val opplysningspliktig: Opplysningspliktig,
    val ansettelsesperiode: Ansettelsesperiode,
    val ansettelsesdetaljer: List<Ansettelsesdetaljer>,
    val opprettet: LocalDateTime,
)

data class Ansettelsesdetaljer(
    val type: String,
    val arbeidstidsordning: Kodeverksentitet? = null,
    val ansettelsesform: Kodeverksentitet? = null,
    val yrke: Kodeverksentitet,
    val antallTimerPrUke: Double? = null,
    val avtaltStillingsprosent: Double? = null,
    val sisteStillingsprosentendring: String? = null,
    val sisteLoennsendring: String? = null,
    val rapporteringsmaaneder: Rapporteringsmaaneder,
)

data class Rapporteringsmaaneder(
    val fra: YearMonth,
    val til: YearMonth? = null,
)

data class Ansettelsesperiode(
    val startdato: LocalDate,
    val sluttdato: LocalDate? = null,
    val sluttaarsak: Kodeverksentitet? = null,
    val varsling: Kodeverksentitet? = null,
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
