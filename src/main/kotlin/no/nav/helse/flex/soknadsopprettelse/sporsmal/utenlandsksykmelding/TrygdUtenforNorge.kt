package no.nav.helse.flex.soknadsopprettelse.sporsmal.utenlandsksykmelding

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.*

fun trygdUtenforNorge() =
    Sporsmal(
        tag = UTENLANDSK_SYKMELDING_TRYGD_UTENFOR_NORGE,
        sporsmalstekst = "Har du mottatt sykepenger eller lignende i andre EØS-land i løpet av de siste tre årene?",
        undertekst = null,
        svartype = Svartype.JA_NEI,
        min = null,
        max = null,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        svar = emptyList(),
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = UTENLANDSK_SYKMELDING_TRYGD_HVILKET_LAND,
                    sporsmalstekst = "I hvilket land?",
                    undertekst = null,
                    svartype = Svartype.LAND,
                    min = null,
                    max = null,
                    kriterieForVisningAvUndersporsmal = null,
                    svar = emptyList(),
                    undersporsmal = emptyList(),
                ),
            ),
    )
