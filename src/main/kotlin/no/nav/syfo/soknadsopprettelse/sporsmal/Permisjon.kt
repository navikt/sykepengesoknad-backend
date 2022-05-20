package no.nav.syfo.soknadsopprettelse.sporsmal

import no.nav.syfo.domain.Sporsmal
import no.nav.syfo.domain.Svartype
import no.nav.syfo.domain.Visningskriterie
import no.nav.syfo.soknadsopprettelse.PERMISJON_NAR_V2
import no.nav.syfo.soknadsopprettelse.PERMISJON_V2
import no.nav.syfo.util.DatoUtil
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun permisjonSporsmal(fom: LocalDate, tom: LocalDate): Sporsmal {
    return Sporsmal(
        tag = PERMISJON_V2,
        sporsmalstekst = "Tok du permisjon mens du var sykmeldt ${DatoUtil.formatterPeriode(fom, tom)}?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal = listOf(
            Sporsmal(
                tag = PERMISJON_NAR_V2,
                sporsmalstekst = "Når tok du permisjon?",
                svartype = Svartype.PERIODER,
                min = fom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                max = tom.format(DateTimeFormatter.ISO_LOCAL_DATE)
            )
        )
    )
}
