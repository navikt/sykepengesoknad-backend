package no.nav.helse.flex.sending

import no.nav.helse.flex.repository.SykepengesoknadDbRecord
import java.time.Instant

fun List<SykepengesoknadDbRecord>.finnOpprinneligSendt(korrigerer: String): Instant {
    val opprinnelig = this.firstOrNull { it.sykepengesoknadUuid == korrigerer }
        ?: throw RuntimeException("Forventa å finne søknad med id $korrigerer")

    if (opprinnelig.korrigerer != null) {
        return finnOpprinneligSendt(opprinnelig.korrigerer)
    }

    return opprinnelig.finnSendtTidspunkt()
}

fun SykepengesoknadDbRecord.finnSendtTidspunkt(): Instant {
    if (sendtArbeidsgiver != null && sendtNav != null) {
        return listOf(sendtArbeidsgiver, sendtNav).minOrNull()!!
    }
    sendtNav?.let { return it }
    sendtArbeidsgiver?.let { return it }
    throw RuntimeException("$sykepengesoknadUuid er korrigert med status $status, men ingen sendt datoer")
}
