package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.soknadsopprettelse.ARBEID_UTENFOR_NORGE

fun arbeidUtenforNorge(): Sporsmal =
    Sporsmal(
        tag = ARBEID_UTENFOR_NORGE,
        sporsmalstekst = "Har du arbeidet i utlandet i løpet av de siste 12 månedene?",
        svartype = Svartype.JA_NEI,
    )
