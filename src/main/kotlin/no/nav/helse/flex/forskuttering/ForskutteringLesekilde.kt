package no.nav.helse.flex.forskuttering

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

enum class ForskutteringLesekilde { GAMMEL, ESYFO }

@Component
class ForskutteringLeser(
    private val gammel: ForskutteringRepository,
    private val esyfo: ForskutteringEsyfoRepository?,
    meterRegistry: MeterRegistry,
    @Value("\${FORSKUTTERING_LESEKILDE:GAMMEL}") kilde: String,
    @Value("\${FORSKUTTERING_ESYFO_CONSUMER_AKTIV:false}") consumerAktiv: Boolean,
) {
    val lesekilde = ForskutteringLesekilde.valueOf(kilde)
    private val aktivKilde = AtomicInteger(if (lesekilde == ForskutteringLesekilde.ESYFO) 1 else 0)

    init {
        require(lesekilde != ForskutteringLesekilde.ESYFO || consumerAktiv) {
            "ESYFO-lesing krever aktiv ESYFO-consumer"
        }
        require(lesekilde != ForskutteringLesekilde.ESYFO || esyfo != null) {
            "ESYFO-lesing krever ESYFO-repository"
        }
        meterRegistry.gauge("forskuttering_esyfo_lesekilde_aktiv", aktivKilde)
    }

    fun finnForskuttering(
        brukerFnr: String,
        orgnummer: String,
    ): Boolean? =
        when (lesekilde) {
            ForskutteringLesekilde.GAMMEL -> gammel.finnForskuttering(brukerFnr, orgnummer)?.arbeidsgiverForskutterer
            ForskutteringLesekilde.ESYFO -> checkNotNull(esyfo).finnForskuttering(brukerFnr, orgnummer)?.arbeidsgiverForskutterer
        }
}
