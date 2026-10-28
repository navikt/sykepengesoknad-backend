package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.*
import no.nav.helse.flex.domain.Svartype.DATO
import no.nav.helse.flex.domain.Svartype.JA_NEI
import no.nav.helse.flex.domain.Visningskriterie.JA
import no.nav.helse.flex.service.SykepengegrunnlagNaeringsdrivende
import no.nav.helse.flex.soknadsopprettelse.sporsmal.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.utenlandsksykmelding.utenlandskSykmeldingSporsmal
import no.nav.helse.flex.util.formatterDato
import no.nav.helse.flex.util.formatterPeriode
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

fun settOppSoknadSelvstendigOgFrilanser(
    sykepengesoknad: Sykepengesoknad,
    sykepengegrunnlagNaeringsdrivende: SykepengegrunnlagNaeringsdrivende? = null,
    harTidligereUtenlandskSpm: Boolean,
    erForsteSoknadISykeforlop: Boolean,
    ghostInntekter: List<KjentInntektskilde>,
): List<Sporsmal> {
    val erGradertReisetilskudd = sykepengesoknad.soknadstype == Soknadstype.GRADERT_REISETILSKUDD

    return mutableListOf<Sporsmal>()
        .apply {
            add(ansvarserklaringSporsmal())
            add(
                if (erGradertReisetilskudd) {
                    tilbakeIFulltArbeidGradertReisetilskuddSporsmal(sykepengesoknad)
                } else {
                    tilbakeIFulltArbeidSporsmal(sykepengesoknad)
                },
            )
            if (ghostInntekter.isNotEmpty()) {
                add(
                    flereInntektskilderGhost(
                        andreKjenteInntektskilder = ghostInntekter,
                        soknadsperiode =
                            Soknadsperiode(
                                fom = sykepengesoknad.fom!!,
                                tom = sykepengesoknad.tom!!,
                                grad = 0,
                                sykmeldingstype = null,
                            ),
                    ),
                )
            } else {
                add(andreInntektskilderV2())
            }
            add(oppholdUtenforEOSSporsmal(sykepengesoknad.fom!!, sykepengesoknad.tom!!))
            add(tilSlutt())
            addAll(
                jobbetDuIPeriodenSporsmalSelvstendigFrilanser(
                    sykepengesoknad.soknadPerioder!!,
                    sykepengesoknad.arbeidssituasjon!!,
                ),
            )
            if (sykepengesoknad.erTilsvarendeSelvstendig()) {
                addAll(
                    fellesLottOgSelvstendigSporsmal(
                        fom = sykepengesoknad.fom,
                        tom = sykepengesoknad.tom,
                        startSykeforlop = sykepengesoknad.startSykeforlop!!,
                        erForsteSoknadISykeforlop = erForsteSoknadISykeforlop,
                        sykepengegrunnlagNaeringsdrivende = sykepengegrunnlagNaeringsdrivende,
                        gradert = sykepengesoknad.harNaringsdrivendeGradertPeriodeEllerOppgittArbeidUnderveis(),
                        erFiskerMedLott = sykepengesoknad.erFiskerMedLott(),
                        erTilsvarendeSelvstendig = sykepengesoknad.erTilsvarendeSelvstendig(),
                    ),
                )
                if (erForsteSoknadISykeforlop) {
                    add(fravaerForSykmeldingSporsmal(sykepengesoknad)) // TODO fjerne
                }
            } else if (erForsteSoknadISykeforlop) {
                add(arbeidUtenforNorge())
            }

            if (sykepengesoknad.utenlandskSykmelding && (erForsteSoknadISykeforlop || !harTidligereUtenlandskSpm)) {
                addAll(utenlandskSykmeldingSporsmal(sykepengesoknad))
            }
            if (erGradertReisetilskudd) {
                add(brukteReisetilskuddetSpørsmål())
            }
        }.toList()
}

fun fellesLottOgSelvstendigSporsmal(
    fom: LocalDate,
    tom: LocalDate,
    startSykeforlop: LocalDate,
    erForsteSoknadISykeforlop: Boolean,
    sykepengegrunnlagNaeringsdrivende: SykepengegrunnlagNaeringsdrivende?,
    gradert: Boolean,
    erFiskerMedLott: Boolean,
    erTilsvarendeSelvstendig: Boolean,
): List<Sporsmal> =
    mutableListOf<Sporsmal>().apply {
        opprettholdtInntektSporsmal(
            fom = fom,
            tom = tom,
            gradert = gradert,
            erFiskerMedLott = erFiskerMedLott,
            erTilsvarendeSelvstendig = erTilsvarendeSelvstendig,
        )?.let { add(it) }

        if (erForsteSoknadISykeforlop) {
            add(naringsdrivendeOppholdIUtlandet(fom))
            add(lagSporsmalOmNaringsdrivendeVirksomhetenAvviklet(fom))
            if (sykepengegrunnlagNaeringsdrivende?.harFunnetInntektFoerSykepengegrunnlaget != true) {
                add(
                    lagSporsmalOmNaringsdrivendeNyIArbeidslivet(
                        fom = fom,
                        startSykeforlop = startSykeforlop,
                        sykepengegrunnlagNaeringsdrivende = sykepengegrunnlagNaeringsdrivende,
                    ),
                )
            }
            add(
                lagSporsmalOmNaringsdrivendeVarigEndring(
                    fom = fom,
                    startSykeforlop = startSykeforlop,
                    sykepengegrunnlagNaeringsdrivende = sykepengegrunnlagNaeringsdrivende,
                ),
            )
        }
    }

fun jobbetDuIPeriodenSporsmalSelvstendigFrilanser(
    soknadsperioder: List<Soknadsperiode>,
    arbeidssituasjon: Arbeidssituasjon,
): List<Sporsmal> =
    soknadsperioder
        .filter { it.sykmeldingstype == Sykmeldingstype.GRADERT || it.sykmeldingstype == Sykmeldingstype.AKTIVITET_IKKE_MULIG }
        .lastIndex
        .downTo(0)
        .reversed()
        .map { index ->
            val periode = soknadsperioder[index]
            if (periode.grad == 100) {
                jobbetDu100ProsentSelvstendigFrilanser(periode, arbeidssituasjon, index)
            } else {
                jobbetDuGradertSelvstendigFrilanser(periode, arbeidssituasjon, index)
            }
        }

private fun tilbakeIFulltArbeidSporsmal(soknadMetadata: Sykepengesoknad): Sporsmal =
    Sporsmal(
        tag = TILBAKE_I_ARBEID,
        sporsmalstekst = "Var du tilbake i fullt arbeid som ${soknadMetadata.arbeidssituasjon} før sykmeldingsperioden utløp ${
            formatterDato(
                soknadMetadata.tom!!,
            )
        }?",
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = TILBAKE_NAR,
                    sporsmalstekst = "Når begynte du å jobbe igjen?",
                    svartype = DATO,
                    min = soknadMetadata.fom!!.format(ISO_LOCAL_DATE),
                    max = soknadMetadata.tom.format(ISO_LOCAL_DATE),
                ),
            ),
    )

private fun fravaerForSykmeldingSporsmal(soknadMetadata: Sykepengesoknad): Sporsmal =
    Sporsmal(
        tag = FRAVAR_FOR_SYKMELDINGEN_V2,
        sporsmalstekst = "Var du borte fra jobb i fire uker eller mer rett før du ble sykmeldt ${
            soknadMetadata.startSykeforlop?.let {
                formatterDato(
                    it,
                )
            }
        }?",
        svartype = JA_NEI,
        undertekst = "Gjelder sammenhengende fravær gjennom alle fire ukene.",
    )

private fun naringsdrivendeOppholdIUtlandet(fom: LocalDate): Sporsmal =
    Sporsmal(
        tag = NARINGSDRIVENDE_OPPHOLD_I_UTLANDET,
        sporsmalstekst = "Har du vært i utlandet i løpet av de siste 12 månedene før du ble sykmeldt ${formatterDato(fom)}?",
        svartype = JA_NEI,
        undertekst = "Du kan se bort ifra opphold som varte kortere enn 5 uker.",
    )

fun naringsdrivendeOpprettholdtInntekt100Prosent(
    fom: LocalDate,
    tom: LocalDate,
): Sporsmal =
    Sporsmal(
        tag = NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT,
        sporsmalstekst = "Hadde du inntekt i virksomheten din selv om du var 100 % sykmeldt og ikke jobbet selv?",
        undertekst = "For eksempel inntekter fra utleie eller arbeid utført av en vikar som gir et overskudd.",
        svartype = JA_NEI,
        // Disse er med for å få muteringen til å fungere
        min = fom.format(ISO_LOCAL_DATE),
        max = tom.format(ISO_LOCAL_DATE),
    )

fun naringsdrivendeOpprettholdtInntektGradert(
    fom: LocalDate,
    tom: LocalDate,
): Sporsmal =
    Sporsmal(
        tag = NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT_GRADERT,
        sporsmalstekst = "Hadde du inntekt i virksomheten din mens du var sykmeldt ${formatterPeriode(
            fom,
            tom,
        )}, som ikke var et resultat av at du selv jobbet?",
        undertekst = "For eksempel inntekter fra utleie eller arbeid utført av en vikar som gir et overskudd.",
        svartype = JA_NEI,
        // Disse er med for å få muteringen til å fungere
        min = fom.format(ISO_LOCAL_DATE),
        max = tom.format(ISO_LOCAL_DATE),
    )

fun Sykepengesoknad.harNaringsdrivendeGradertPeriodeEllerOppgittArbeidUnderveis() =
    this.harGradertPeriode() || this.harOppgittArbeidUnderveisVed100Prosent()

fun opprettholdtInntektSporsmal(
    fom: LocalDate,
    tom: LocalDate,
    gradert: Boolean,
    erFiskerMedLott: Boolean,
    erTilsvarendeSelvstendig: Boolean,
): Sporsmal? =
    when {
        erFiskerMedLott ->
            if (gradert) {
                fiskerLottOpprettholdtInntektGradert(fom, tom)
            } else {
                fiskerLottOpprettholdtInntekt100Prosent(fom, tom)
            }

        erTilsvarendeSelvstendig ->
            if (gradert) {
                naringsdrivendeOpprettholdtInntektGradert(fom, tom)
            } else {
                naringsdrivendeOpprettholdtInntekt100Prosent(fom, tom)
            }

        else -> null
    }

private fun Sykepengesoknad.harGradertPeriode(): Boolean = this.soknadPerioder!!.any { it.grad != 100 }

private fun Sykepengesoknad.harOppgittArbeidUnderveisVed100Prosent(): Boolean =
    this.sporsmal.any { it.tag.startsWith(ARBEID_UNDERVEIS_100_PROSENT) && it.forsteSvar == "JA" }
