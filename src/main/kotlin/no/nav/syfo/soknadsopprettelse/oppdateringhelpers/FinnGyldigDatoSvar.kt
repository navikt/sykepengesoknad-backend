package no.nav.syfo.soknadsopprettelse.oppdateringhelpers

import no.nav.syfo.domain.Svartype
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.util.DatoUtil
import no.nav.syfo.util.parseGyldigDato
import java.time.LocalDate

fun Sykepengesoknad.finnGyldigDatoSvar(hovedtag: String, undertag: String, relevantSvarVerdi: String = "JA"): LocalDate? {
    val hovedsporsmal = this.getSporsmalMedTag(hovedtag)
    if (hovedsporsmal.svartype != Svartype.JA_NEI) {
        throw RuntimeException("Hovedspørsmål skal være type JA_NEI $hovedsporsmal")
    }
    if (hovedsporsmal.forsteSvar != relevantSvarVerdi) {
        return null
    }
    val undersporsmal = this.getSporsmalMedTag(undertag)
    if (undersporsmal.svartype != Svartype.DATO) {
        throw RuntimeException("Undersporsmal skal være av svartype dato $undersporsmal")
    }
    val gyldigDato = parseGyldigDato(undersporsmal.forsteSvar)
    return if (gyldigDato != null && DatoUtil.datoErInnenforMinMax(gyldigDato, undersporsmal.min, undersporsmal.max))
        gyldigDato
    else
        null
}
