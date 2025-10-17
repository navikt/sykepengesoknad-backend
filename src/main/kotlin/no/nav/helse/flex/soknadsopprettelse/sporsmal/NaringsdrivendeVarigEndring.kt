package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.service.SykepengegrunnlagNaeringsdrivende
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.util.DatoUtil.formatterDato
import java.time.format.DateTimeFormatter

fun lagSporsmalOmNaringsdrivendeVarigEndring(
    soknad: Sykepengesoknad,
    sykepengegrunnlagNaeringsdrivende: SykepengegrunnlagNaeringsdrivende?,
): Sporsmal {
    val tidligstDato = finnNaringsdrivendeTidligstDato(soknad, sykepengegrunnlagNaeringsdrivende)

    return Sporsmal(
        tag = NARINGSDRIVENDE_VARIG_ENDRING,
        sporsmalstekst =
            "Har det skjedd en varig endring i arbeidssituasjonen din mellom ${formatterDato(tidligstDato)} og frem " +
                "til du ble sykmeldt ${formatterDato(soknad.fom!!)}?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = NARINGSDRIVENDE_VARIG_ENDRING_TYPE,
                    sporsmalstekst = "Hvilken varig endring har skjedd?",
                    undertekst = "Du kan velge et eller flere alternativer",
                    svartype = Svartype.CHECKBOX_GRUPPE,
                    undersporsmal =
                        listOf(
                            Sporsmal(
                                tag = NARINGSDRIVENDE_VARIG_ENDRING_TYPE_NY_VIRKSOMHET,
                                sporsmalstekst = "Opprettet en ny virksomhet",
                                svartype = Svartype.CHECKBOX,
                            ),
                            Sporsmal(
                                tag = NARINGSDRIVENDE_VARIG_ENDRING_TYPE_AVVIKLET_VIRKSOMHET,
                                sporsmalstekst = "Avviklet en virksomhet",
                                svartype = Svartype.CHECKBOX,
                            ),
                            Sporsmal(
                                tag = NARINGSDRIVENDE_VARIG_ENDRING_TYPE_JOBBET_MINDRE,
                                sporsmalstekst = "Jobbet mindre i en virksomhet",
                                svartype = Svartype.CHECKBOX,
                            ),
                            Sporsmal(
                                tag = NARINGSDRIVENDE_VARIG_ENDRING_TYPE_JOBBET_MER,
                                sporsmalstekst = "Jobbet mer i en virksomhet",
                                svartype = Svartype.CHECKBOX,
                            ),
                            Sporsmal(
                                tag = NARINGSDRIVENDE_VARIG_ENDRING_TYPE_LAGT_OM,
                                sporsmalstekst = "Lagt om en virksomhet",
                                svartype = Svartype.CHECKBOX,
                            ),
                            Sporsmal(
                                tag = NARINGSDRIVENDE_VARIG_ENDRING_TYPE_KUNDEGRUNNLAG,
                                sporsmalstekst = "Endret kundegrunnlag",
                                svartype = Svartype.CHECKBOX,
                            ),
                            Sporsmal(
                                tag = NARINGSDRIVENDE_VARIG_ENDRING_TYPE_ANNET,
                                sporsmalstekst = "Annen endring",
                                svartype = Svartype.CHECKBOX,
                            ),
                        ),
                ),
                Sporsmal(
                    tag = NARINGSDRIVENDE_VARIG_ENDRING_DATO,
                    sporsmalstekst = "Når skjedde endringen?",
                    svartype = Svartype.AAR_MAANED,
                    min = tidligstDato.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    max = soknad.fom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                ),
            ),
    )
}
