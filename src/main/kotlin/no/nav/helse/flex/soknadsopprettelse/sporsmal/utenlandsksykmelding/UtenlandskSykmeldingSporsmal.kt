package no.nav.helse.flex.soknadsopprettelse.sporsmal.utenlandsksykmelding

import no.nav.helse.flex.domain.Sporsmal
import java.time.LocalDate

fun utenlandskSykmeldingSporsmal(
    fom: LocalDate,
    tom: LocalDate,
): List<Sporsmal> =
    listOf(
        borDuINorge(fom, tom),
        trygdUtenforNorge(),
        lønnetArbeidUtenforNorge(),
    )
