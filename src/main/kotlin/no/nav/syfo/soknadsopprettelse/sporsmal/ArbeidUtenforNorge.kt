package no.nav.syfo.soknadsopprettelse.sporsmal

import no.nav.syfo.domain.Sporsmal
import no.nav.syfo.domain.Svartype
import no.nav.syfo.soknadsopprettelse.ARBEID_UTENFOR_NORGE

fun arbeidUtenforNorge(): Sporsmal =
    Sporsmal(
        tag = ARBEID_UTENFOR_NORGE,
        sporsmalstekst = "Har du arbeidet i utlandet i løpet av de siste 12 månedene?",
        svartype = Svartype.JA_NEI
    )
