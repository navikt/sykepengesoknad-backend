package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.client.ereg.EregClient
import no.nav.helse.flex.client.inntektskomponenten.ArbeidsInntektMaaned
import no.nav.helse.flex.client.inntektskomponenten.InntektskomponentenClient
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.YearMonth

@Component
class AndreArbeidsforholdHenting(
    val inntektskomponentenClient: InntektskomponentenClient,
    val eregClient: EregClient,
) {
    fun hentArbeidsforhold(
        fnr: String,
        arbeidsgiverOrgnummer: String,
        startSykeforlop: LocalDate,
    ): List<ArbeidsforholdFraInntektskomponenten> {
        val sykmeldingOrgnummer = arbeidsgiverOrgnummer

        val hentedeInntekter =
            inntektskomponentenClient.hentInntekter(
                fnr,
                fom = startSykeforlop.yearMonth().minusMonths(3),
                tom = startSykeforlop.yearMonth(),
            ).arbeidsInntektMaaned

        fun ArbeidsInntektMaaned.orgnumreForManed(): Set<String> {
            val inntekterOrgnummer =
                this.arbeidsInntektInformasjon.inntektListe
                    .filter { it.inntektType == "LOENNSINNTEKT" }
                    .filter { it.virksomhet.aktoerType == "ORGANISASJON" }
                    .map { it.virksomhet.identifikator }
                    .toSet()

            return inntekterOrgnummer
        }

        val alleMånedersOrgnr = hentedeInntekter.flatMap { it.orgnumreForManed() }.toSet()

        fun ArbeidsInntektMaaned.frilansOrgnumere(): Set<String> {
            val frilansArbeidsforholdOrgnumre =
                this.arbeidsInntektInformasjon.arbeidsforholdListe
                    .filter { it.arbeidsforholdstype == "frilanserOppdragstakerHonorarPersonerMm" }
                    .map { it.arbeidsgiver.identifikator }
                    .toSet()

            val ikkeFrilansForhold =
                this.arbeidsInntektInformasjon.arbeidsforholdListe
                    .filter { it.arbeidsforholdstype == "ordinaertArbeidsforhold" }
                    .map { it.arbeidsgiver.identifikator }
                    .toSet()

            return frilansArbeidsforholdOrgnumre.subtract(ikkeFrilansForhold)
        }

        val frilansOrgrunmrene = hentedeInntekter.flatMap { it.frilansOrgnumere() }.toSet()
        return alleMånedersOrgnr
            .filter { it != sykmeldingOrgnummer }
            .map(tilArbeidsforholdFraInntektskomponenten(frilansOrgrunmrene))
    }

    private fun tilArbeidsforholdFraInntektskomponenten(frilansOrgnummer: Set<String>) =
        fun(orgnr: String): ArbeidsforholdFraInntektskomponenten {
            val hentBedrift = eregClient.hentBedrift(orgnr)
            return ArbeidsforholdFraInntektskomponenten(
                orgnummer = orgnr,
                navn = hentBedrift.navn.navnelinje1.prettyOrgnavn(),
                arbeidsforholdstype =
                    if (frilansOrgnummer.contains(
                            orgnr,
                        )
                    ) {
                        Arbeidsforholdstype.FRILANSER
                    } else {
                        Arbeidsforholdstype.ARBEIDSTAKER
                    },
            )
        }
}

fun LocalDate.yearMonth(): YearMonth = YearMonth.from(this)

enum class Arbeidsforholdstype {
    FRILANSER,
    ARBEIDSTAKER,
}

data class ArbeidsforholdFraInntektskomponenten(
    val orgnummer: String,
    val navn: String,
    val arbeidsforholdstype: Arbeidsforholdstype,
)
