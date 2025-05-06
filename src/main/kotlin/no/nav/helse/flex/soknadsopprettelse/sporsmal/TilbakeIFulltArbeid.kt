package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype.DATO
import no.nav.helse.flex.domain.Svartype.JA_NEI
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.Visningskriterie.JA
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_I_ARBEID
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_NAR
import no.nav.helse.flex.util.DatoUtil.formatterPeriode
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

fun tilbakeIFulltArbeidSporsmal(sykepengesoknad: Sykepengesoknad): Sporsmal =
    Sporsmal(
        tag = TILBAKE_I_ARBEID,
        sporsmalstekst = "Var du tilbake i fullt arbeid hos ${sykepengesoknad.arbeidsgiverNavn} i løpet av perioden ${
            formatterPeriode(
                sykepengesoknad.fom!!,
                sykepengesoknad.tom!!,
            )
        }?",
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = TILBAKE_NAR,
                    sporsmalstekst = "Når begynte du å jobbe igjen?",
                    svartype = DATO,
                    min = sykepengesoknad.fom.format(ISO_LOCAL_DATE),
                    max = sykepengesoknad.tom.format(ISO_LOCAL_DATE),
                ),
            ),
    )
