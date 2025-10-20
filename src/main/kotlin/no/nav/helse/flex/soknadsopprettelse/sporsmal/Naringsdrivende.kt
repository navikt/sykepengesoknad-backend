package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.service.SykepengegrunnlagNaeringsdrivende
import java.time.LocalDate
import kotlin.text.toInt

internal fun finnNaringsdrivendeTidligstDato(
    soknad: Sykepengesoknad,
    sykepengegrunnlagNaeringsdrivende: SykepengegrunnlagNaeringsdrivende?,
): LocalDate {
    val forsteFerdiglignetAar = sykepengegrunnlagNaeringsdrivende?.inntekter?.map { it.inntektsaar.toInt() }?.minBy { it }
    return if (forsteFerdiglignetAar != null) {
        LocalDate.of(forsteFerdiglignetAar, 1, 1)
    } else {
        LocalDate.of(soknad.startSykeforlop?.minusYears(5)?.year ?: LocalDate.now().year, 1, 1)
    }
}
