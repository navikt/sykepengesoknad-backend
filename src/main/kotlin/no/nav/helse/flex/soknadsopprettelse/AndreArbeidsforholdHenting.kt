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
    val eregClient: EregClient
) {

    fun hentArbeidsforhold(fnr: String, arbeidsgiverOrgnummer: String, startSykeforlop: LocalDate): List<String> {
        val sykmeldingOrgnummer = arbeidsgiverOrgnummer

        val hentInntekter = inntektskomponentenClient
            .hentInntekter(
                fnr,
                fom = startSykeforlop.yearMonth().minusMonths(3),
                tom = startSykeforlop.yearMonth()
            )

        fun ArbeidsInntektMaaned.orgnumreForManed(): Set<String> {
            val frilansArbeidsforholdOrgnumre = this.arbeidsInntektInformasjon.arbeidsforholdListe
                .filter { it.arbeidsforholdstype == "frilanserOppdragstakerHonorarPersonerMm" }
                .map { it.arbeidsgiver.identifikator }
                .toSet()

            val inntekterOrgnummer = this.arbeidsInntektInformasjon.inntektListe
                .filter { it.inntektType == "LOENNSINNTEKT" }
                .filter { it.virksomhet.aktoerType == "ORGANISASJON" }
                .map { it.virksomhet.identifikator }
                .toSet()
                .subtract(frilansArbeidsforholdOrgnumre)

            return inntekterOrgnummer.subtract(frilansArbeidsforholdOrgnumre)
        }

        val alleMånedersOrgnr = hentInntekter.arbeidsInntektMaaned.flatMap { it.orgnumreForManed() }.toSet()

        return alleMånedersOrgnr
            .filter { it != sykmeldingOrgnummer }
            .map { eregClient.hentBedrift(it) }
            .map { it.navn.navnelinje1 }
            .map { it.prettyOrgnavn() }
    }
}

fun LocalDate.yearMonth() = YearMonth.from(this)
