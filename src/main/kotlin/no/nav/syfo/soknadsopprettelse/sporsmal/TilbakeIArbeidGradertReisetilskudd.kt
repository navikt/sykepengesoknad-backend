package no.nav.syfo.soknadsopprettelse.sporsmal

import no.nav.syfo.domain.Sporsmal
import no.nav.syfo.domain.Svartype
import no.nav.syfo.domain.Visningskriterie
import no.nav.syfo.domain.rest.SoknadMetadata
import no.nav.syfo.soknadsopprettelse.TILBAKE_I_ARBEID
import no.nav.syfo.soknadsopprettelse.TILBAKE_NAR
import no.nav.syfo.util.DatoUtil
import java.time.format.DateTimeFormatter

fun tilbakeIFulltArbeidGradertReisetilskuddSporsmal(soknadMetadata: SoknadMetadata): Sporsmal {
    return Sporsmal(
        tag = TILBAKE_I_ARBEID,
        sporsmalstekst = "Var du tilbake i ditt vanlige arbeid uten ekstra reiseutgifter før ${
        DatoUtil.formatterDatoUtenÅr(
            soknadMetadata.tom.plusDays(1)
        )
        }?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        pavirkerAndreSporsmal = true,
        undersporsmal = listOf(
            Sporsmal(
                tag = TILBAKE_NAR,
                sporsmalstekst = "Når var du tilbake?",
                svartype = Svartype.DATO,
                min = soknadMetadata.fom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                max = soknadMetadata.tom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                pavirkerAndreSporsmal = true
            )
        )
    )
}
