package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_I_ARBEID
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_NAR
import no.nav.helse.flex.util.DatoUtil
import java.time.format.DateTimeFormatter

fun tilbakeIFulltArbeidGradertReisetilskuddSporsmal(soknadMetadata: Sykepengesoknad): Sporsmal =
    Sporsmal(
        tag = TILBAKE_I_ARBEID,
        sporsmalstekst = "Var du tilbake i ditt vanlige arbeid uten ekstra reiseutgifter før ${
            DatoUtil.formatterDatoUtenÅr(
                soknadMetadata.tom!!.plusDays(1),
            )
        }?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = TILBAKE_NAR,
                    sporsmalstekst = "Når var du tilbake?",
                    svartype = Svartype.DATO,
                    min = soknadMetadata.fom!!.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    max = soknadMetadata.tom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                ),
            ),
    )
