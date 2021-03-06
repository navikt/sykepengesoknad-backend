package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Arbeidssituasjon.*
import no.nav.helse.flex.domain.Opprinnelse
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype.*
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.Visningskriterie.CHECKED
import no.nav.helse.flex.domain.Visningskriterie.JA
import no.nav.helse.flex.domain.rest.SoknadMetadata
import no.nav.helse.flex.soknadsopprettelse.sporsmal.ansvarserklaringSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.bekreftOpplysningerSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.vaerKlarOverAtReisetilskudd
import no.nav.helse.flex.util.DatoUtil.formatterPeriode
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

fun skapReisetilskuddsoknad(
    soknadMetadata: SoknadMetadata
): Sykepengesoknad {

    return Sykepengesoknad(
        id = soknadMetadata.id,
        fnr = soknadMetadata.fnr,
        sykmeldingId = soknadMetadata.sykmeldingId,
        status = soknadMetadata.status,
        fom = soknadMetadata.fom,
        tom = soknadMetadata.tom,
        opprettet = Instant.now(),
        startSykeforlop = soknadMetadata.startSykeforlop,
        sykmeldingSkrevet = soknadMetadata.sykmeldingSkrevet,
        arbeidsgiverOrgnummer = soknadMetadata.arbeidsgiverOrgnummer,
        arbeidsgiverNavn = soknadMetadata.arbeidsgiverNavn,
        soknadPerioder = soknadMetadata.sykmeldingsperioder,
        opprinnelse = Opprinnelse.SYFOSOKNAD,
        soknadstype = Soknadstype.REISETILSKUDD,
        arbeidssituasjon = soknadMetadata.arbeidssituasjon,
        egenmeldtSykmelding = soknadMetadata.egenmeldtSykmelding,
        merknaderFraSykmelding = soknadMetadata.merknader,

        sporsmal = mutableListOf(
            ansvarserklaringSporsmal(reisetilskudd = true),
            vaerKlarOverAtReisetilskudd(),
            bekreftOpplysningerSporsmal()
        ).also {
            it.addAll(
                reisetilskuddSporsmal(soknadMetadata.fom, soknadMetadata.tom, soknadMetadata.arbeidssituasjon)
            )
        }.toList()
    )
}

fun reisetilskuddSporsmal(
    fom: LocalDate,
    tom: LocalDate,
    arbeidssituasjon: Arbeidssituasjon,
): List<Sporsmal> {
    val formattertPeriode = formatterPeriode(
        fom,
        tom
    )
    return mutableListOf(
        transportTilDagligSp??rsm??l(),
        reiseMedBilSp??rsm??l(formattertPeriode, fom, tom),
        kvitteringSporsmal(formattertPeriode),
    ).also { list ->
        if (arrayOf(ARBEIDSLEDIG, ARBEIDSTAKER, ANNET).any { it == arbeidssituasjon }) {
            list.add(arbeidsgiverLeggerUtSp??rsm??l())
        }
    }
}

fun kvitteringSporsmal(formattertPeriode: String) = Sporsmal(
    tag = KVITTERINGER,
    svartype = KVITTERING,
    sporsmalstekst = "Last opp kvitteringer for reiser til og fra jobben mellom $formattertPeriode.",
)

fun reiseMedBilSp??rsm??l(
    formattertPeriode: String,
    fom: LocalDate,
    tom: LocalDate
) = Sporsmal(
    tag = REISE_MED_BIL,
    svartype = JA_NEI,
    sporsmalstekst = "Reiste du med egen bil, leiebil eller kollega til jobben mellom $formattertPeriode?",
    kriterieForVisningAvUndersporsmal = JA,
    undersporsmal = listOf(
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
            undersporsmal = listOf(
                Sporsmal(
                    tag = BIL_BOMPENGER_BELOP,
                    svartype = BELOP,
                    min = "0",
                    sporsmalstekst = "Hvor mye betalte du i bompenger mellom hjemmet ditt og jobben?",
                    undertekst = "kr",
                )
            )
        ),
        Sporsmal(
            tag = KM_HJEM_JOBB,
            min = "0",
            sporsmalstekst = "Hvor mange kilometer er kj??returen mellom hjemmet ditt og jobben ??n vei?",
            undertekst = "km",
            svartype = KILOMETER,
        )
    )
)

fun transportTilDagligSp??rsm??l() = Sporsmal(
    tag = TRANSPORT_TIL_DAGLIG,
    svartype = JA_NEI,
    sporsmalstekst = "Brukte du bil eller offentlig transport til og fra jobben f??r du ble sykmeldt?",
    kriterieForVisningAvUndersporsmal = JA,
    undersporsmal = listOf(
        Sporsmal(
            tag = TYPE_TRANSPORT,
            svartype = CHECKBOX_GRUPPE,
            sporsmalstekst = "Hva slags type transport brukte du?",
            undersporsmal = listOf(
                Sporsmal(
                    tag = OFFENTLIG_TRANSPORT_TIL_DAGLIG,
                    sporsmalstekst = "Offentlig transport",
                    svartype = CHECKBOX,
                    kriterieForVisningAvUndersporsmal = CHECKED,
                    undersporsmal = listOf(
                        offentligTransportBel??pSp??rsm??l()
                    )
                ),
                Sporsmal(
                    tag = BIL_TIL_DAGLIG,
                    sporsmalstekst = "Bil",
                    svartype = CHECKBOX,
                )
            )

        )
    )
)

fun offentligTransportBel??pSp??rsm??l() = Sporsmal(
    tag = OFFENTLIG_TRANSPORT_BELOP,
    min = "0",
    sporsmalstekst = "Hvor mye betaler du vanligvis i m??neden for offentlig transport?",
    undertekst = "kr",
    svartype = BELOP,
)

fun arbeidsgiverLeggerUtSp??rsm??l() = Sporsmal(
    tag = UTBETALING,
    svartype = JA_NEI,
    sporsmalstekst = "Legger arbeidsgiveren din ut for reisene?",
)

fun brukteReisetilskuddetSp??rsm??l() = Sporsmal(
    tag = BRUKTE_REISETILSKUDDET,
    sporsmalstekst = "Hadde du ekstra reiseutgifter mens du var sykmeldt?",
    svartype = JA_NEI,
    pavirkerAndreSporsmal = true,
)
