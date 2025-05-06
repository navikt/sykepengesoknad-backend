package no.nav.helse.flex.soknadsopprettelse.sporsmal.utenlandsksykmelding

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad

fun utenlandskSykmeldingSporsmal(sykepengesoknad: Sykepengesoknad): List<Sporsmal> =
    listOf(
        borDuINorge(sykepengesoknad),
        trygdUtenforNorge(),
        lønnetArbeidUtenforNorge(),
    )
