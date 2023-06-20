package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.client.ereg.EregClient
import no.nav.helse.flex.client.inntektskomponenten.ArbeidsInntektMaaned
import no.nav.helse.flex.client.inntektskomponenten.InntektskomponentenClient
import no.nav.helse.flex.client.inntektskomponenten.InntektskomponentenV2Client
import no.nav.helse.flex.logger
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.YearMonth

@Component
class AndreArbeidsforholdHenting(
    val inntektskomponentenClient: InntektskomponentenClient,
    val inntektskomponentenV2Client: InntektskomponentenV2Client,
    val eregClient: EregClient
) {
    private val log = logger()

    fun hentArbeidsforhold(fnr: String, arbeidsgiverOrgnummer: String, startSykeforlop: LocalDate): List<ArbeidsforholdFraInntektskomponenten> {
        val sykmeldingOrgnummer = arbeidsgiverOrgnummer

        val hentInntekter = inntektskomponentenClient
            .hentInntekter(
                fnr,
                fom = startSykeforlop.yearMonth().minusMonths(3),
                tom = startSykeforlop.yearMonth()
            )

        runCatching {
            val hentInntekterV2 = inntektskomponentenV2Client.hentInntekter(
                fnr,
                fom = startSykeforlop.yearMonth().minusMonths(3),
                tom = startSykeforlop.yearMonth()
            )

            require(hentInntekterV2 == hentInntekter)
        }.onFailure { error ->
            log.info("Henting av inntektliste: forskjellig svar", error)
        }.onSuccess {
            log.info("Henting av inntektliste: ok")
        }

        fun ArbeidsInntektMaaned.orgnumreForManed(): Set<String> {
            val inntekterOrgnummer = this.arbeidsInntektInformasjon.inntektListe
                .filter { it.inntektType == "LOENNSINNTEKT" }
                .filter { it.virksomhet.aktoerType == "ORGANISASJON" }
                .map { it.virksomhet.identifikator }
                .toSet()

            return inntekterOrgnummer
        }

        val alleMånedersOrgnr = hentInntekter.arbeidsInntektMaaned.flatMap { it.orgnumreForManed() }.toSet()

        return alleMånedersOrgnr
            .filter { it != sykmeldingOrgnummer }
            .map(fun(orgnr: String): ArbeidsforholdFraInntektskomponenten {
                val hentBedrift = eregClient.hentBedrift(orgnr)
                return ArbeidsforholdFraInntektskomponenten(
                    orgnummer = orgnr,
                    navn = hentBedrift.navn.navnelinje1.prettyOrgnavn(),
                    arbeidsforholdstype = Arbeidsforholdstype.ARBEIDSTAKER
                )
            })
    }
}

fun LocalDate.yearMonth() = YearMonth.from(this)

enum class Arbeidsforholdstype {
    FRILANSER, ARBEIDSTAKER
}

data class ArbeidsforholdFraInntektskomponenten(
    val orgnummer: String,
    val navn: String,
    val arbeidsforholdstype: Arbeidsforholdstype
)
