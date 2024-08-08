package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Soknadstype.GRADERT_REISETILSKUDD
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype.DATO
import no.nav.helse.flex.domain.Svartype.JA_NEI
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.Visningskriterie.JA
import no.nav.helse.flex.soknadsopprettelse.sporsmal.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmArbeidUtenforNorge
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmOppholdUtenforEos
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmOppholdUtenforNorge
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmOppholdstillatelse
import no.nav.helse.flex.soknadsopprettelse.sporsmal.utenlandsksykmelding.utenlandskSykmeldingSporsmal
import no.nav.helse.flex.soknadsopprettelse.undersporsmal.jobbetDuUndersporsmal
import no.nav.helse.flex.util.DatoUtil.formatterPeriode
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

interface MedlemskapSporsmalTag

enum class LovMeSporsmalTag : MedlemskapSporsmalTag {
    OPPHOLDSTILATELSE,
    ARBEID_UTENFOR_NORGE,
    OPPHOLD_UTENFOR_NORGE,
    OPPHOLD_UTENFOR_EØS_OMRÅDE,
}

enum class SykepengesoknadSporsmalTag : MedlemskapSporsmalTag {
    ARBEID_UTENFOR_NORGE,
}

fun settOppSoknadArbeidstaker(
    soknadOptions: SettOppSoknadOptions,
    andreKjenteArbeidsforhold: List<ArbeidsforholdFraInntektskomponenten>,
): List<Sporsmal> {
    val (sykepengesoknad, erForsteSoknadISykeforlop, harTidligereUtenlandskSpm, yrkesskade, medlemskapTags) = soknadOptions
    val erGradertReisetilskudd = sykepengesoknad.soknadstype == GRADERT_REISETILSKUDD
    return mutableListOf<Sporsmal>().apply {
        add(ansvarserklaringSporsmal())
        add(
            if (erGradertReisetilskudd) {
                tilbakeIFulltArbeidGradertReisetilskuddSporsmal(sykepengesoknad)
            } else {
                tilbakeIFulltArbeidSporsmal(sykepengesoknad)
            },
        )
        add(ferieSporsmal(sykepengesoknad.fom!!, sykepengesoknad.tom!!))
        add(permisjonSporsmal(sykepengesoknad.fom, sykepengesoknad.tom))
        add(oppholdUtenforEOSSporsmal(sykepengesoknad.fom, sykepengesoknad.tom))
        add(tilSlutt())
        addAll(yrkesskade.yrkeskadeSporsmal())

        if (sykepengesoknad.utenlandskSykmelding && (erForsteSoknadISykeforlop || !harTidligereUtenlandskSpm)) {
            addAll(utenlandskSykmeldingSporsmal(sykepengesoknad))
        }

        add(andreInntektskilderArbeidstakerV2(sykepengesoknad.arbeidsgiverNavn!!, andreKjenteArbeidsforhold.map { it.navn }))
        addAll(jobbetDuIPeriodenSporsmal(sykepengesoknad.soknadPerioder!!, sykepengesoknad.arbeidsgiverNavn))

        if (erGradertReisetilskudd) {
            add(brukteReisetilskuddetSpørsmål())
        }

        addAll(
            medlemskapTags!!.map {
                when (it) {
                    LovMeSporsmalTag.OPPHOLDSTILATELSE ->
                        lagSporsmalOmOppholdstillatelse(
                            sykepengesoknad.tom,
                            soknadOptions.kjentOppholdstillatelse,
                        )
                    LovMeSporsmalTag.ARBEID_UTENFOR_NORGE -> lagSporsmalOmArbeidUtenforNorge(sykepengesoknad.tom)
                    LovMeSporsmalTag.OPPHOLD_UTENFOR_NORGE -> lagSporsmalOmOppholdUtenforNorge(sykepengesoknad.tom)
                    LovMeSporsmalTag.OPPHOLD_UTENFOR_EØS_OMRÅDE -> lagSporsmalOmOppholdUtenforEos(sykepengesoknad.tom)
                    SykepengesoknadSporsmalTag.ARBEID_UTENFOR_NORGE -> arbeidUtenforNorge()
                    else -> {
                        throw RuntimeException("Ukjent MedlemskapSporsmalTag: $it.")
                    }
                }
            },
        )
    }
}

private fun tilbakeIFulltArbeidSporsmal(soknadMetadata: Sykepengesoknad): Sporsmal {
    return Sporsmal(
        tag = TILBAKE_I_ARBEID,
        sporsmalstekst = "Var du tilbake i fullt arbeid hos ${soknadMetadata.arbeidsgiverNavn} i løpet av perioden ${
            formatterPeriode(
                soknadMetadata.fom!!,
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
                    min = soknadMetadata.fom.format(ISO_LOCAL_DATE),
                    max = soknadMetadata.tom.format(ISO_LOCAL_DATE),
                ),
            ),
    )
}

fun jobbetDuIPeriodenSporsmal(
    soknadsperioder: List<Soknadsperiode>,
    arbeidsgiverNavn: String,
): List<Sporsmal> {
    return soknadsperioder
        .lastIndex.downTo(0)
        .reversed()
        .map { index ->
            val periode = soknadsperioder[index]
            if (periode.grad == 100) {
                jobbetDu100Prosent(periode, arbeidsgiverNavn, index)
            } else {
                jobbetDuGradert(periode, arbeidsgiverNavn, index)
            }
        }
}

private fun jobbetDu100Prosent(
    periode: Soknadsperiode,
    arbeidsgiver: String,
    index: Int,
): Sporsmal {
    return Sporsmal(
        tag = ARBEID_UNDERVEIS_100_PROSENT + index,
        sporsmalstekst = "I perioden ${
            formatterPeriode(
                periode.fom,
                periode.tom,
            )
        } var du 100 % sykmeldt fra $arbeidsgiver. Jobbet du noe hos $arbeidsgiver i denne perioden?",
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal =
            jobbetDuUndersporsmal(
                periode = periode,
                minProsent = 1,
                index = index,
                arbeidsgiverNavn = arbeidsgiver,
            ),
    )
}
