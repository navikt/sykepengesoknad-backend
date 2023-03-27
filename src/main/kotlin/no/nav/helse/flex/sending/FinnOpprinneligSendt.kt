package no.nav.helse.flex.sending

import no.nav.helse.flex.repository.SykepengesoknadDbRecord
import no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger.overlap
import java.time.Instant

fun List<SykepengesoknadDbRecord>.finnOpprinneligSendt(soknad: SykepengesoknadDbRecord): Instant? {
    if (soknad.fom == null || soknad.tom == null) {
        return null
    }
    return this.asSequence()
        .filter { it.sykepengesoknadUuid != soknad.id }
        .filter { it.fom != null && it.tom != null }
        .filter { it.sendt != null }
        .filter { it.soknadstype == soknad.soknadstype }
        .filter { it.arbeidssituasjon == soknad.arbeidssituasjon }
        .filter { it.arbeidsgiverOrgnummer == soknad.arbeidsgiverOrgnummer }.toList()
        .filter { (it.fom!!..it.tom!!).overlap(soknad.fom..soknad.tom) }
        .sortedBy { it.sendt }
        .map { it.sendt }
        .firstOrNull()
}
