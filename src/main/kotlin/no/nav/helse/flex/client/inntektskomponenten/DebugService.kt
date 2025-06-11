package no.nav.helse.flex.client.inntektskomponenten

import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.YearMonth
import java.util.concurrent.TimeUnit

@Service
class DebugService(
    private val inntektskomponentenClient: InntektskomponentenClient,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
) {
    private val log = logger()

    @Scheduled(initialDelay = 3, fixedDelay = 3600, timeUnit = TimeUnit.MINUTES)
    fun debugInntekt() {
        val fnr = sykepengesoknadRepository.findBySykepengesoknadUuid("9150a8a7-20f6-31e8-9def-a4123665ba95")!!.fnr
        val fom = YearMonth.of(2025, 2)
        val tom = YearMonth.of(2025, 5)
        val inntekter = inntektskomponentenClient.hentInntekter(fnr, fom, tom)

        inntekter.arbeidsInntektMaaned.forEach {
            val inntektListe = it.arbeidsInntektInformasjon.inntektListe
            val arbeidsforholdListe = it.arbeidsInntektInformasjon.arbeidsforholdListe

            inntektListe.forEach {
                log.info("Hentet inntekter.aktoerType ${it.virksomhet.aktoerType}")
            }

            arbeidsforholdListe.forEach {
                log.info("Hentet arbeidsgiver.aktoerType ${it.arbeidsgiver.aktoerType}")
            }
        }
    }
}
