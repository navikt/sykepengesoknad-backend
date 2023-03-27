package no.nav.helse.flex.sending

import no.nav.helse.flex.repository.SykepengesoknadDbRecord
import no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger.overlap
import java.time.Instant

fun List<SykepengesoknadDbRecord>.finnOpprinneligSendt(korrigerer: String): Instant? {
    val opprinnelig = this.firstOrNull { it.sykepengesoknadUuid == korrigerer }
        ?: throw RuntimeException("Forventa å finne søknad med id $korrigerer")

    if (opprinnelig.korrigerer != null) {
        return finnOpprinneligSendt(opprinnelig.korrigerer)
    }

    return opprinnelig.sendt
}

fun List<SykepengesoknadDbRecord>.finnOpprinneligSendt(soknad: SykepengesoknadDbRecord): Instant? {
    if (soknad.fom == null || soknad.tom == null) {
        return null
    }
    this.asSequence()
        .filter { it.sykepengesoknadUuid != soknad.id }
        .filter { it.fom != null && it.tom != null }
        .filter { it.sendt != null }
        .filter { it.soknadstype == soknad.soknadstype }
        .filter { it.arbeidssituasjon == soknad.arbeidssituasjon }
        .filter { it.arbeidsgiverOrgnummer == soknad.arbeidsgiverOrgnummer }.toList()
        .filter { (it.fom!!..it.tom!!).overlap(soknad.fom..soknad.tom) }
        .sortedBy { it.sendt }
        .firstOrNull()?.let {
            if (it.korrigerer != null) {
                return finnOpprinneligSendt(it.korrigerer)
            }
            return it.sendt
        }

    return null
}
