package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Arbeidssituasjon.*
import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype.DATO
import no.nav.helse.flex.domain.Svartype.JA_NEI
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.Sykmeldingstype
import no.nav.helse.flex.domain.Visningskriterie.JA
import no.nav.helse.flex.service.SykepengegrunnlagNaeringsdrivende
import no.nav.helse.flex.soknadsopprettelse.sporsmal.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.utenlandsksykmelding.utenlandskSykmeldingSporsmal
import no.nav.helse.flex.soknadsopprettelse.undersporsmal.jobbetDuUndersporsmal
import no.nav.helse.flex.util.DatoUtil.formatterDato
import no.nav.helse.flex.util.DatoUtil.formatterPeriode
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

fun settOppSoknadSelvstendigOgFrilanser(
    opts: SettOppSoknadOptions,
    sykepengegrunnlagNaeringsdrivende: SykepengegrunnlagNaeringsdrivende? = null,
): List<Sporsmal> {
    val (sykepengesoknad, erForsteSoknadISykeforlop, harTidligereUtenlandskSpm, yrkesskade) = opts
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
            add(andreInntektskilderSelvstendigOgFrilanser(sykepengesoknad.arbeidssituasjon!!))
            add(oppholdUtenforEOSSporsmal(sykepengesoknad.fom!!, sykepengesoknad.tom!!))
            add(tilSlutt())
            addAll(
                jobbetDuIPeriodenSporsmalSelvstendigFrilanser(
                    sykepengesoknad.soknadPerioder!!,
                    sykepengesoknad.arbeidssituasjon,
                ),
            )

            if (erForsteSoknadISykeforlop) {
                add(arbeidUtenforNorge())

                if (listOf(NAERINGSDRIVENDE, FISKER, JORDBRUKER).contains(sykepengesoknad.arbeidssituasjon)) {
                    add(fravaerForSykmeldingSporsmal(sykepengesoknad))
                    add(lagSporsmalOmInntektsopplyninger(sykepengesoknad, sykepengegrunnlagNaeringsdrivende))
                }
            }
            addAll(yrkesskade.yrkeskadeSporsmal())

            if (sykepengesoknad.utenlandskSykmelding && (erForsteSoknadISykeforlop || !harTidligereUtenlandskSpm)) {
                addAll(utenlandskSykmeldingSporsmal(sykepengesoknad))
            }
            if (erGradertReisetilskudd) {
                add(brukteReisetilskuddetSpørsmål())
            }
        }.toList()
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
                jobbetDu100Prosent(periode, arbeidssituasjon, index)
            } else {
                jobbetDuGradert(periode, arbeidssituasjon, index)
            }
        }

private fun jobbetDu100Prosent(
    periode: Soknadsperiode,
    arbeidssituasjon: Arbeidssituasjon,
    index: Int,
): Sporsmal =
    Sporsmal(
        tag = ARBEID_UNDERVEIS_100_PROSENT + index,
        sporsmalstekst = "I perioden ${
            formatterPeriode(
                periode.fom,
                periode.tom,
            )
        } var du 100% sykmeldt som $arbeidssituasjon. Jobbet du noe i denne perioden?",
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal = jobbetDuUndersporsmal(periode, 1, index),
    )

fun jobbetDuGradert(
    periode: Soknadsperiode,
    arbeidssituasjon: Arbeidssituasjon,
    index: Int,
): Sporsmal =
    Sporsmal(
        tag = JOBBET_DU_GRADERT + index,
        sporsmalstekst = "Sykmeldingen sier du kunne jobbe ${100 - periode.grad} % som $arbeidssituasjon. Jobbet du mer enn det?",
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal = jobbetDuGradertUndersporsmal(periode, 100 + 1 - periode.grad, index),
    )

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
        sporsmalstekst = "Var du borte fra jobb i fire uker eller mer rett før du ble sykmeldt ${soknadMetadata.fom}?",
        svartype = JA_NEI,
        undertekst = "Gjelder sammenhengende ferie eller annet fravær gjennom alle fire ukene. Har du jobbet underveis, kan du svare nei. ",
    )
