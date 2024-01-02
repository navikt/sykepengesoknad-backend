package no.nav.helse.flex.soknadsopprettelse.splitt

import java.time.LocalDate

class Tidsenhet(
    val fom: LocalDate,
    val tom: LocalDate,
)

class SykmeldingTidsenheter(
    val ferdigsplittet: MutableList<Tidsenhet>,
    val splittbar: MutableList<Tidsenhet>,
)
