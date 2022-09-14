package no.nav.helse.flex.forskuttering

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.forskuttering.domain.Forskuttering
import no.nav.helse.flex.forskuttering.domain.NarmesteLederLeesah
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.OBJECT_MAPPER
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class OppdateringAvForskuttering(
    val forskutteringRepository: ForskutteringRepository,
) {

    val log = logger()

    fun behandleMeldingFraKafka(meldingString: String) {
        val narmesteLederLeesah = meldingString.tilNarmesteLederLeesah()
        val narmesteLeder = forskutteringRepository.findByNarmesteLederId(narmesteLederLeesah.narmesteLederId)

        if (narmesteLeder != null) {
            forskutteringRepository.save(narmesteLederLeesah.tilNarmesteLeder(id = narmesteLeder.id))
            log.info("Oppdatert narmesteleder med id ${narmesteLederLeesah.narmesteLederId}")
        } else {
            forskutteringRepository.save(narmesteLederLeesah.tilNarmesteLeder(id = null))
            log.info("Lagret narmesteleder med id ${narmesteLederLeesah.narmesteLederId}")
        }
    }

    fun String.tilNarmesteLederLeesah(): NarmesteLederLeesah = OBJECT_MAPPER.readValue(this)
}

private fun NarmesteLederLeesah.tilNarmesteLeder(id: String?): Forskuttering = Forskuttering(
    id = id,
    narmesteLederId = narmesteLederId,
    brukerFnr = fnr,
    orgnummer = orgnummer,
    aktivFom = aktivFom,
    aktivTom = aktivTom,
    arbeidsgiverForskutterer = arbeidsgiverForskutterer,
    timestamp = timestamp.toInstant(),
    oppdatert = Instant.now()
)
