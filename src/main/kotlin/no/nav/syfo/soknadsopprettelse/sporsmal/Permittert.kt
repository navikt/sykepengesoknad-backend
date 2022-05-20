package no.nav.syfo.soknadsopprettelse.sporsmal

import no.nav.syfo.domain.Sporsmal
import no.nav.syfo.domain.Svartype
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.Visningskriterie
import no.nav.syfo.domain.rest.SoknadMetadata
import no.nav.syfo.soknadsopprettelse.PERMITTERT_NAA
import no.nav.syfo.soknadsopprettelse.PERMITTERT_NAA_NAR
import no.nav.syfo.soknadsopprettelse.PERMITTERT_PERIODE
import no.nav.syfo.soknadsopprettelse.PERMITTERT_PERIODE_NAR
import no.nav.syfo.util.DatoUtil
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun permittertNaaSporsmal(soknadMetadata: SoknadMetadata): Sporsmal {
    return Sporsmal(
        tag = PERMITTERT_NAA,
        sporsmalstekst = "Var du permittert av arbeidsgiveren din da du ble sykmeldt ${DatoUtil.formatterDato(soknadMetadata.fom)}?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal = listOf(
            Sporsmal(
                tag = PERMITTERT_NAA_NAR,
                sporsmalstekst = "Velg første dag i permitteringen",
                svartype = Svartype.DATO,
                min = LocalDate.of(2020, 2, 1).format(DateTimeFormatter.ISO_LOCAL_DATE),
                max = soknadMetadata.tom.format(DateTimeFormatter.ISO_LOCAL_DATE)
            )
        )
    )
}

fun permittertPeriodeSporsmal(fom: LocalDate): Sporsmal {

    val fomPeriode = fom.minusMonths(1)
    val tomPeriode = fom
    return Sporsmal(
        tag = PERMITTERT_PERIODE,
        sporsmalstekst = "Har du vært permittert av arbeidsgiveren din i mer enn 14 sammenhengende dager mellom ${DatoUtil.formatterPeriode(fomPeriode, tomPeriode)}?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal = listOf(
            Sporsmal(
                tag = PERMITTERT_PERIODE_NAR,
                svartype = Svartype.PERIODER,
                min = fomPeriode.format(DateTimeFormatter.ISO_LOCAL_DATE),
                max = tomPeriode.format(DateTimeFormatter.ISO_LOCAL_DATE)
            )
        )
    )
}

fun oppdaterMedSvarPaaPermittertNaa(sykepengesoknad: Sykepengesoknad): Sykepengesoknad {
    sykepengesoknad.getSporsmalMedTagOrNull(PERMITTERT_NAA)?.let { permittertNaaSporsmaal ->
        val oppdaterteSporsmal = sykepengesoknad
            .sporsmal.asSequence()
            .filterNot { (_, tag) -> tag == PERMITTERT_PERIODE }
            .toMutableList()

        if (permittertNaaSporsmaal.forsteSvar != "JA") {
            oppdaterteSporsmal.add(permittertPeriodeSporsmal(sykepengesoknad.fom!!))
        }

        return sykepengesoknad.copy(sporsmal = oppdaterteSporsmal)
    }
    return sykepengesoknad
}
