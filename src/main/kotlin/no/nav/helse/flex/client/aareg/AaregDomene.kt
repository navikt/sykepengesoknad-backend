package no.nav.helse.flex.client.aareg

import java.time.LocalDate

data class Arbeidsforhold(
    val arbeidsgiver: Arbeidsgiver,
    val opplysningspliktig: Opplysningspliktig,
    val ansettelsesperiode: Ansettelsesperiode,
)

data class Ansettelsesperiode(val periode: Periode)

data class Periode(val fom: LocalDate, val tom: LocalDate?)

data class Arbeidsgiver(val type: String, val organisasjonsnummer: String?)

data class Arbeidsgiverinfo(val orgnummer: String, val tomDate: LocalDate?)

data class Gyldighetsperiode(val fom: LocalDate?, val tom: LocalDate?)

data class Opplysningspliktig(val type: String, val organisasjonsnummer: String?)
