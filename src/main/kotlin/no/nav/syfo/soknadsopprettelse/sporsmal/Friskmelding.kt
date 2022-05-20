package no.nav.syfo.soknadsopprettelse.sporsmal

import no.nav.syfo.domain.*
import no.nav.syfo.soknadsopprettelse.*
import no.nav.syfo.soknadsopprettelse.oppdateringhelpers.finnGyldigDatoSvar
import no.nav.syfo.util.DatoUtil
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun oppdaterMedSvarPaFriskmeldingSporsmal(sykepengesoknad: Sykepengesoknad): Sykepengesoknad {
    val friskmeldtDato = sykepengesoknad.finnGyldigDatoSvar(FRISKMELDT, FRISKMELDT_START, relevantSvarVerdi = "NEI")

    val sporsmal = sykepengesoknad
        .sporsmal.asSequence()
        .filterNot { (_, tag) -> tag == UTDANNING }
        .filterNot { (_, tag) -> tag == ARBEIDSLEDIG_UTLAND }
        .filterNot { (_, tag) -> tag == ANDRE_INNTEKTSKILDER }
        .filterNot { (_, tag) -> tag == PERMISJON_V2 }
        .toMutableList()

    if (friskmeldtDato == null || friskmeldtDato.isAfter(sykepengesoknad.fom)) {
        val oppdatertTom =
            if (friskmeldtDato == null)
                sykepengesoknad.tom
            else
                friskmeldtDato.minusDays(1)

        sporsmal.add(utenlandsoppholdArbeidsledigAnnetSporsmal(sykepengesoknad.fom!!, oppdatertTom!!))
        sporsmal.add(utdanningsSporsmal(sykepengesoknad.fom, oppdatertTom))
        sporsmal.add(andreInntektskilderArbeidsledig(sykepengesoknad.fom, oppdatertTom))
        if (sykepengesoknad.arbeidssituasjon == Arbeidssituasjon.ANNET) {
            sporsmal.add(permisjonSporsmal(sykepengesoknad.fom, oppdatertTom))
        }
    }

    return sykepengesoknad.copy(sporsmal = sporsmal)
}

fun friskmeldingSporsmal(fom: LocalDate, tom: LocalDate): Sporsmal =
    Sporsmal(
        tag = FRISKMELDT,
        sporsmalstekst = "Brukte du hele sykmeldingen fram til ${DatoUtil.formatterDato(tom)}?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.NEI,
        undersporsmal = listOf(
            Sporsmal(
                tag = FRISKMELDT_START,
                sporsmalstekst = "Fra hvilken dato trengte du ikke lenger sykmeldingen?",
                svartype = Svartype.DATO,
                min = fom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                max = tom.format(DateTimeFormatter.ISO_LOCAL_DATE)
            )
        )
    )
