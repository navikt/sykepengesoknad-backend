package no.nav.helse.flex.soknadsopprettelse.sporsmal.utenlandsksykmelding

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.*

fun lønnetArbeidUtenforNorge(): Sporsmal = Sporsmal(
    tag = UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE,
    sporsmalstekst = "Utfører du lønnet arbeid utenfor Norge?",
    undertekst = null,
    svartype = Svartype.JA_NEI,
    min = null,
    max = null,
    pavirkerAndreSporsmal = false,
    kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
    svar = emptyList(),
    undersporsmal = listOf(
        Sporsmal(
            tag = UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE_FRITEKST,
            sporsmalstekst = "Oppgi nærmere opplysninger om arbeid/virksomhet utenfor Norge",
            undertekst = "(f. eks. navn på arbeidsgivere og nærmere informasjon om din yrkesaktivitet i utlandet)\n",
            svartype = Svartype.FRITEKST,
            min = "1",
            max = null,
            pavirkerAndreSporsmal = false,
            kriterieForVisningAvUndersporsmal = null,
            svar = emptyList(),
            undersporsmal = emptyList()
        )
    )
)
