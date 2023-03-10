package no.nav.helse.flex.soknadsopprettelse.sporsmal.utenlandsksykmelding

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Svartype.*
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.Visningskriterie.JA
import no.nav.helse.flex.soknadsopprettelse.*
import java.time.format.DateTimeFormatter

fun borDuINorge(sykepengesoknad: Sykepengesoknad): Sporsmal =
    Sporsmal(
        tag = UTENLANDSK_SYKMELDING_BOSTED,
        sporsmalstekst = "Bor du i utlandet?",
        undertekst = null,
        svartype = JA_NEI,
        min = null,
        max = null,
        pavirkerAndreSporsmal = false,
        kriterieForVisningAvUndersporsmal = JA,
        svar = emptyList(),
        undersporsmal = listOf(
            Sporsmal(
                tag = UTENLANDSK_SYKMELDING_CO,
                sporsmalstekst = "C/O",
                undertekst = null,
                svartype = FRITEKST,
                min = null,
                max = null,
                pavirkerAndreSporsmal = false,
                kriterieForVisningAvUndersporsmal = null,
                svar = emptyList(),
                undersporsmal = emptyList()
            ),
            Sporsmal(
                tag = UTENLANDSK_SYKMELDING_VEGNAVN,
                sporsmalstekst = "Vegnavn og husnummer, evt. postboks",
                undertekst = null,
                svartype = FRITEKST,
                min = "1",
                max = null,
                pavirkerAndreSporsmal = false,
                kriterieForVisningAvUndersporsmal = null,
                svar = emptyList(),
                undersporsmal = emptyList()
            ),
            Sporsmal(
                tag = UTENLANDSK_SYKMELDING_BYGNING,
                sporsmalstekst = "Bygning",
                undertekst = null,
                svartype = FRITEKST,
                min = null,
                max = null,
                pavirkerAndreSporsmal = false,
                kriterieForVisningAvUndersporsmal = null,
                svar = emptyList(),
                undersporsmal = emptyList()
            ),
            Sporsmal(
                tag = UTENLANDSK_SYKMELDING_BY,
                sporsmalstekst = "By / stedsnavn",
                undertekst = null,
                svartype = FRITEKST,
                min = null,
                max = null,
                pavirkerAndreSporsmal = false,
                kriterieForVisningAvUndersporsmal = null,
                svar = emptyList(),
                undersporsmal = emptyList()
            ),
            Sporsmal(
                tag = UTENLANDSK_SYKMELDING_REGION,
                sporsmalstekst = "Region",
                undertekst = null,
                svartype = FRITEKST,
                min = null,
                max = null,
                pavirkerAndreSporsmal = false,
                kriterieForVisningAvUndersporsmal = null,
                svar = emptyList(),
                undersporsmal = emptyList()
            ),
            Sporsmal(
                tag = UTENLANDSK_SYKMELDING_LAND,
                sporsmalstekst = "Land",
                undertekst = null,
                svartype = FRITEKST,
                min = "1",
                max = null,
                pavirkerAndreSporsmal = false,
                kriterieForVisningAvUndersporsmal = null,
                svar = emptyList(),
                undersporsmal = emptyList()
            ),
            Sporsmal(
                tag = UTENLANDSK_SYKMELDING_TELEFONNUMMER,
                sporsmalstekst = "Telefonnummer",
                undertekst = null,
                svartype = FRITEKST,
                min = "1",
                max = null,
                pavirkerAndreSporsmal = false,
                kriterieForVisningAvUndersporsmal = null,
                svar = emptyList(),
                undersporsmal = emptyList()
            ),
            Sporsmal(
                tag = UTENLANDSK_SYKMELDING_GYLDIGHET_ADRESSE,
                sporsmalstekst = "I hvilken dato skal denne adressen brukes?",
                undertekst = "Du velger selv hvor lenge adressen skal være gyldig, maksimalt 1 år. Etter 1 år må du endre eller forlenge adressen.",
                svartype = Svartype.PERIODER,
                min = sykepengesoknad.fom!!.minusYears(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
                max = sykepengesoknad.tom!!.plusYears(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
                pavirkerAndreSporsmal = false,
                kriterieForVisningAvUndersporsmal = null,
                svar = emptyList(),
                undersporsmal = emptyList()
            )
        )
    )
