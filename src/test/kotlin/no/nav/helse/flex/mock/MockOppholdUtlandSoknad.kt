package no.nav.helse.flex.mock

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.ARBEIDSGIVER
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER_UTLAND
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER_UTLAND_INFO
import no.nav.helse.flex.soknadsopprettelse.FERIE
import no.nav.helse.flex.soknadsopprettelse.LAND
import no.nav.helse.flex.soknadsopprettelse.PERIODEUTLAND
import no.nav.helse.flex.soknadsopprettelse.SYKMELDINGSGRAD
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadOppholdUtland
import java.time.LocalDateTime.now
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import java.util.Arrays.asList
import java.util.Collections.emptyList

fun mockUtlandssoknad(): Sykepengesoknad {
    return leggSvarPaSoknad(settOppSoknadOppholdUtland("fnr-7454630"), "NEI")
}

fun leggSvarPaSoknad(sykepengesoknad: Sykepengesoknad, feriesvar: String): Sykepengesoknad {
    return sykepengesoknad.replaceSporsmal(perioder(sykepengesoknad))
        .replaceSporsmal(land(sykepengesoknad))
        .replaceSporsmal(arbeidsgiver(sykepengesoknad, feriesvar))
        .replaceSporsmal(bekreft(sykepengesoknad))
}

private fun bekreft(sykepengesoknad: Sykepengesoknad): Sporsmal {
    return sykepengesoknad.getSporsmalMedTag(BEKREFT_OPPLYSNINGER_UTLAND_INFO).toBuilder()
        .svar(emptyList())
        .undersporsmal(
            listOf(
                sykepengesoknad.getSporsmalMedTag(BEKREFT_OPPLYSNINGER_UTLAND).toBuilder()
                    .svar(listOf(Svar(null, "CHECKED", null)))
                    .undersporsmal(emptyList())
                    .build()
            )
        )
        .build()
}

private fun arbeidsgiver(sykepengesoknad: Sykepengesoknad, feriesvarverdi: String): Sporsmal {
    return sykepengesoknad.getSporsmalMedTag(ARBEIDSGIVER).toBuilder()
        .svar(listOf(Svar(null, "JA", null)))
        .undersporsmal(
            asList(
                sykepengesoknad.getSporsmalMedTag(SYKMELDINGSGRAD).toBuilder()
                    .svar(listOf(Svar(null, "JA", null)))
                    .undersporsmal(emptyList())
                    .build(),
                sykepengesoknad.getSporsmalMedTag(FERIE).toBuilder()
                    .svar(listOf(Svar(null, feriesvarverdi, null)))
                    .undersporsmal(emptyList())
                    .build()
            )
        )
        .build()
}

private fun land(sykepengesoknad: Sykepengesoknad): Sporsmal {
    return sykepengesoknad.getSporsmalMedTag(LAND).toBuilder()
        .svar(listOf(Svar(null, "England", null)))
        .undersporsmal(emptyList())
        .build()
}

private fun perioder(sykepengesoknad: Sykepengesoknad): Sporsmal {
    return sykepengesoknad.getSporsmalMedTag(PERIODEUTLAND).toBuilder()
        .svar(
            listOf(
                Svar(
                    null,
                    "{\"fom\":\"" + now().plusDays(4).format(ISO_LOCAL_DATE) +
                        "\",\"tom\":\"" + now().plusMonths(1).format(ISO_LOCAL_DATE) + "\"}",
                    null
                )
            )
        )
        .undersporsmal(emptyList())
        .build()
}
