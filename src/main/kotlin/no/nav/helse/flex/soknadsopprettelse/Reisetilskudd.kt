package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Arbeidssituasjon.*
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype.*
import no.nav.helse.flex.domain.Visningskriterie.CHECKED
import no.nav.helse.flex.domain.Visningskriterie.JA
import no.nav.helse.flex.soknadsopprettelse.sporsmal.*
import no.nav.helse.flex.util.DatoUtil.formatterPeriode
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

fun skapReisetilskuddsoknad(opts: SettOppSoknadOptions): List<Sporsmal> =
    mutableListOf(
        ansvarserklaringSporsmal(),
    ).also {
        it.addAll(
            reisetilskuddSporsmal(
                opts.sykepengesoknad.fom!!,
                opts.sykepengesoknad.tom!!,
                opts.sykepengesoknad.arbeidssituasjon!!,
            ),
        )
        it.addAll(opts.yrkesskade.yrkeskadeSporsmal())
        it.add(tilSlutt())
    }.toList()

fun reisetilskuddSporsmal(
    fom: LocalDate,
    tom: LocalDate,
    arbeidssituasjon: Arbeidssituasjon,
): List<Sporsmal> {
    val formattertPeriode =
        formatterPeriode(
            fom,
            tom,
        )
    return mutableListOf(
        transportTilDagligSpørsmål(),
        reiseMedBilSpørsmål(formattertPeriode, fom, tom),
        kvitteringSporsmal(formattertPeriode),
    ).also { list ->
        if (arrayOf(ARBEIDSLEDIG, ARBEIDSTAKER, ANNET).any { it == arbeidssituasjon }) {
            list.add(arbeidsgiverLeggerUtSpørsmål())
        }
    }
}

fun kvitteringSporsmal(formattertPeriode: String) =
    Sporsmal(
        tag = KVITTERINGER,
        svartype = KVITTERING,
        sporsmalstekst = "Last opp kvitteringer for reiser til og fra jobben mellom $formattertPeriode.",
    )

fun reiseMedBilSpørsmål(
    formattertPeriode: String,
    fom: LocalDate,
    tom: LocalDate,
) = Sporsmal(
    tag = REISE_MED_BIL,
    svartype = JA_NEI,
    sporsmalstekst = "Reiste du med egen bil, leiebil eller kollega til jobben mellom $formattertPeriode?",
    kriterieForVisningAvUndersporsmal = JA,
    undersporsmal =
        listOf(
            Sporsmal(
                tag = BIL_DATOER,
                svartype = DATOER,
                min = fom.format(ISO_LOCAL_DATE),
                max = tom.format(ISO_LOCAL_DATE),
                sporsmalstekst = "Hvilke dager reiste du med bil i perioden $formattertPeriode?",
            ),
            Sporsmal(
                tag = BIL_BOMPENGER,
                svartype = JA_NEI,
                sporsmalstekst = "Hadde du utgifter til bompenger?",
                kriterieForVisningAvUndersporsmal = JA,
                undersporsmal =
                    listOf(
                        Sporsmal(
                            tag = BIL_BOMPENGER_BELOP,
                            svartype = BELOP,
                            min = "0",
                            sporsmalstekst = "Hvor mye betalte du i bompenger mellom hjemmet ditt og jobben?",
                        ),
                    ),
            ),
            Sporsmal(
                tag = KM_HJEM_JOBB,
                min = "0",
                sporsmalstekst = "Hvor mange kilometer er kjøreturen mellom hjemmet ditt og jobben én vei?",
                svartype = KILOMETER,
            ),
        ),
)

fun transportTilDagligSpørsmål() =
    Sporsmal(
        tag = TRANSPORT_TIL_DAGLIG,
        svartype = JA_NEI,
        sporsmalstekst = "Brukte du bil eller offentlig transport til og fra jobben før du ble sykmeldt?",
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = TYPE_TRANSPORT,
                    svartype = CHECKBOX_GRUPPE,
                    sporsmalstekst = "Hva slags type transport brukte du?",
                    undersporsmal =
                        listOf(
                            Sporsmal(
                                tag = OFFENTLIG_TRANSPORT_TIL_DAGLIG,
                                sporsmalstekst = "Offentlig transport",
                                svartype = CHECKBOX,
                                kriterieForVisningAvUndersporsmal = CHECKED,
                                undersporsmal =
                                    listOf(
                                        offentligTransportBeløpSpørsmål(),
                                    ),
                            ),
                            Sporsmal(
                                tag = BIL_TIL_DAGLIG,
                                sporsmalstekst = "Bil",
                                svartype = CHECKBOX,
                            ),
                        ),
                ),
            ),
    )

fun offentligTransportBeløpSpørsmål() =
    Sporsmal(
        tag = OFFENTLIG_TRANSPORT_BELOP,
        min = "0",
        sporsmalstekst = "Hvor mye betaler du vanligvis i måneden for offentlig transport?",
        svartype = BELOP,
    )

fun arbeidsgiverLeggerUtSpørsmål() =
    Sporsmal(
        tag = UTBETALING,
        svartype = JA_NEI,
        sporsmalstekst = "Legger arbeidsgiveren din ut for reisene?",
    )

fun brukteReisetilskuddetSpørsmål() =
    Sporsmal(
        tag = BRUKTE_REISETILSKUDDET,
        sporsmalstekst = "Hadde du ekstra reiseutgifter mens du var sykmeldt?",
        svartype = JA_NEI,
    )
