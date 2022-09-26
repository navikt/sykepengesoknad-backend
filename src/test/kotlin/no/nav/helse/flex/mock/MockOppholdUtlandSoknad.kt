package no.nav.helse.flex.mock

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.ARBEIDSGIVER
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER_UTLAND
import no.nav.helse.flex.soknadsopprettelse.FERIE
import no.nav.helse.flex.soknadsopprettelse.LAND
import no.nav.helse.flex.soknadsopprettelse.PERIODEUTLAND
import no.nav.helse.flex.soknadsopprettelse.SYKMELDINGSGRAD
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadOppholdUtland
import no.nav.helse.flex.testutil.besvarsporsmal
import java.time.LocalDateTime.now
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

fun mockUtlandssoknad(): Sykepengesoknad {
    return leggSvarPaSoknad(settOppSoknadOppholdUtland("fnr-7454630"), "NEI")
}

private fun leggSvarPaSoknad(sykepengesoknad: Sykepengesoknad, feriesvar: String): Sykepengesoknad {
    return sykepengesoknad
        .perioder()
        .arbeidsgiver(feriesvar)
        .besvarsporsmal(LAND, "England")
        .besvarsporsmal(BEKREFT_OPPLYSNINGER_UTLAND, "CHECKED")
}

private fun Sykepengesoknad.arbeidsgiver(feriesvarverdi: String): Sykepengesoknad {
    return besvarsporsmal(ARBEIDSGIVER, "JA")
        .besvarsporsmal(SYKMELDINGSGRAD, "JA")
        .besvarsporsmal(FERIE, feriesvarverdi)
}

private fun Sykepengesoknad.perioder(): Sykepengesoknad {
    return besvarsporsmal(PERIODEUTLAND, "{\"fom\":\"" + now().plusDays(4).format(ISO_LOCAL_DATE) + "\",\"tom\":\"" + now().plusMonths(1).format(ISO_LOCAL_DATE) + "\"}")
}
