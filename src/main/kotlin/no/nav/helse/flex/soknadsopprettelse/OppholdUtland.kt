package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Soknadstatus.NY
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Svartype.CHECKBOX_PANEL
import no.nav.helse.flex.domain.Svartype.IKKE_RELEVANT
import no.nav.helse.flex.domain.Svartype.JA_NEI
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.Visningskriterie.JA
import java.time.Instant
import java.time.LocalDate.now
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import java.util.UUID.randomUUID

fun Sporsmal.plasseringSporsmalUtland(): Int {
    return when (this.tag) {
        PERIODEUTLAND -> -10
        LAND -> -9
        ARBEIDSGIVER -> -8
        BEKREFT_OPPLYSNINGER_UTLAND_INFO -> -7
        else -> 0
    }
}

fun settOppSoknadOppholdUtland(fnr: String): Sykepengesoknad {
    val sporsmal = listOf(
        periodeSporsmal(),
        landSporsmal(),
        arbeidsgiverSporsmal(),
        bekreftSporsmal(false)
    )

    return Sykepengesoknad(
        id = randomUUID().toString(),
        fnr = fnr,
        status = NY,
        opprettet = Instant.now(),
        sporsmal = sporsmal,
        soknadstype = Soknadstype.OPPHOLD_UTLAND,
        arbeidssituasjon = null,
        fom = null,
        tom = null,
        soknadPerioder = emptyList(),
        startSykeforlop = null,
        sykmeldingSkrevet = null,
        sykmeldingSignaturDato = null,
        sykmeldingId = null,
        egenmeldtSykmelding = null,
        merknaderFraSykmelding = null,
        utenlandskSykmelding = false,
        egenmeldingsdagerFraSykmelding = null
    )
}

private fun arbeidsgiverSporsmal(): Sporsmal {
    return Sporsmal(
        tag = ARBEIDSGIVER,
        sporsmalstekst = "Har du arbeidsgiver?",
        svartype = JA_NEI,
        pavirkerAndreSporsmal = true,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal = listOf(
            Sporsmal(
                tag = SYKMELDINGSGRAD,
                sporsmalstekst = "Er du 100 % sykmeldt?",
                svartype = JA_NEI
            ),
            Sporsmal(
                tag = FERIE,
                sporsmalstekst = "Har du avtalt med arbeidsgiveren din at du skal ta ut feriedager i hele perioden?",
                svartype = JA_NEI
            )
        )
    )
}

private fun periodeSporsmal(): Sporsmal {
    return Sporsmal(
        tag = PERIODEUTLAND,
        sporsmalstekst = "Når skal du reise?",
        svartype = Svartype.PERIODER,
        min = now().minusMonths(3).format(ISO_LOCAL_DATE),
        max = now().plusMonths(6).format(ISO_LOCAL_DATE)
    )
}

private fun landSporsmal(): Sporsmal {
    return Sporsmal(
        tag = LAND,
        sporsmalstekst = "Hvilket land skal du reise til?",
        svartype = Svartype.LAND,
        max = "50"
    )
}

fun bekreftSporsmal(harArbeidsgiver: Boolean): Sporsmal {
    return Sporsmal(
        tag = BEKREFT_OPPLYSNINGER_UTLAND_INFO,
        sporsmalstekst = "Før du reiser ber vi deg bekrefte:",
        svartype = IKKE_RELEVANT,
        undersporsmal = listOf(
            Sporsmal(
                tag = BEKREFT_OPPLYSNINGER_UTLAND,
                sporsmalstekst =
                if (harArbeidsgiver) {
                    "Jeg bekrefter de tre punktene ovenfor"
                } else {
                    "Jeg bekrefter de to punktene ovenfor"
                },
                svartype = CHECKBOX_PANEL
            )
        ),
        undertekst = "<ul>\n" +
            "    <li>Jeg har avklart med legen at reisen ikke vil forlenge sykefraværet</li>\n" +
            "    <li>Reisen hindrer ikke planlagt behandling eller avtaler med NAV</li>\n" +
            (if (harArbeidsgiver) "<li>Reisen er avklart med arbeidsgiveren min</li>" else "") +
            "</ul>"
    )
}
