package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.service.SykepengegrunnlagNaeringsdrivende
import no.nav.helse.flex.service.finnFoersteAarISykepengegrunnlaget
import java.time.LocalDate

internal fun finnNaringsdrivendeTidligstDato(
    startSykeforlop: LocalDate?,
    sykepengegrunnlagNaeringsdrivende: SykepengegrunnlagNaeringsdrivende?,
): LocalDate {
    val forsteAarISykepengegrunnlaget = sykepengegrunnlagNaeringsdrivende?.inntekter?.finnFoersteAarISykepengegrunnlaget()
    return if (forsteAarISykepengegrunnlaget != null) {
        LocalDate.of(forsteAarISykepengegrunnlaget, 1, 1)
    } else {
        LocalDate.of(startSykeforlop?.minusYears(5)?.year ?: LocalDate.now().year, 1, 1)
    }
}
