package no.nav.helse.flex.soknadsopprettelse.oppdateringhelpers

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.util.DatoUtil
import no.nav.helse.flex.util.parseGyldigDato
import java.time.LocalDate

fun Sykepengesoknad.finnGyldigDatoSvar(
    hovedtag: String,
    undertag: String,
    relevantSvarVerdi: String = "JA",
): LocalDate? {
    val hovedsporsmal = this.getSporsmalMedTag(hovedtag)
    if (hovedsporsmal.svartype != no.nav.helse.flex.domain.Svartype.JA_NEI) {
        throw RuntimeException("Hovedspørsmål skal være type JA_NEI $hovedsporsmal")
    }
    if (hovedsporsmal.forsteSvar != relevantSvarVerdi) {
        return null
    }
    val undersporsmal = this.getSporsmalMedTag(undertag)
    if (undersporsmal.svartype != no.nav.helse.flex.domain.Svartype.DATO) {
        throw RuntimeException("Undersporsmal skal være av svartype dato $undersporsmal")
    }
    val gyldigDato = parseGyldigDato(undersporsmal.forsteSvar)
    return if (gyldigDato != null && DatoUtil.datoErInnenforMinMax(gyldigDato, undersporsmal.min, undersporsmal.max)) {
        gyldigDato
    } else {
        null
    }
}
