package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Soknadstype.GRADERT_REISETILSKUDD
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.medlemskap.KjentOppholdstillatelse
import no.nav.helse.flex.soknadsopprettelse.aaregdata.ArbeidsforholdFraAAreg
import no.nav.helse.flex.soknadsopprettelse.sporsmal.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmArbeidUtenforNorge
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmOppholdUtenforEos
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmOppholdUtenforNorge
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmOppholdstillatelse
import no.nav.helse.flex.soknadsopprettelse.sporsmal.utenlandsksykmelding.utenlandskSykmeldingSporsmal
import no.nav.helse.flex.yrkesskade.YrkesskadeSporsmalGrunnlag
import java.time.LocalDate

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
    fom: LocalDate,
    tom: LocalDate,
    soknadstype: Soknadstype,
    arbeidsgiverNavn: String?,
    soknadPerioder: List<Soknadsperiode>,
    utenlandskSykmelding: Boolean,
    yrkesskade: YrkesskadeSporsmalGrunnlag,
    nyeArbeidsforholdFraAAreg: List<ArbeidsforholdFraAAreg>,
    kjentOppholdstillatelse: KjentOppholdstillatelse?,
    medlemskapSporsmalTags: List<MedlemskapSporsmalTag>,
    harTidligereUtenlandskSpm: Boolean,
    erForsteSoknadISykeforlop: Boolean,
    ghostInntekter: List<KjentInntektskilde>,
): List<Sporsmal> {
    val erGradertReisetilskudd = soknadstype == GRADERT_REISETILSKUDD
    return mutableListOf<Sporsmal>().apply {
        add(ansvarserklaringSporsmal())
        add(
            if (erGradertReisetilskudd) {
                tilbakeIFulltArbeidGradertReisetilskuddSporsmal(fom, tom)
            } else {
                tilbakeIFulltArbeidSporsmal(arbeidsgiverNavn, fom, tom)
            },
        )
        add(ferieSporsmal(fom, tom))
        add(permisjonSporsmal(fom, tom))
        add(oppholdUtenforEOSSporsmal(fom, tom))
        add(tilSlutt())
        addAll(yrkesskade.yrkeskadeSporsmal())

        if (utenlandskSykmelding && (erForsteSoknadISykeforlop || !harTidligereUtenlandskSpm)) {
            addAll(utenlandskSykmeldingSporsmal(fom, tom))
        }
        addAll(
            nyttArbeidsforholdSporsmal(
                nyeArbeidsforhold = nyeArbeidsforholdFraAAreg,
                fom = fom,
                tom = tom,
            ),
        )

        if (ghostInntekter.isNotEmpty()) {
            add(
                flereInntektskilderGhost(
                    andreKjenteInntektskilder = ghostInntekter,
                    fom = fom,
                    tom = tom,
                ),
            )
        } else {
            add(andreInntektskilderV2())
        }

        addAll(jobbetDuIPeriodenSporsmal(soknadPerioder, arbeidsgiverNavn))

        if (erGradertReisetilskudd) {
            add(brukteReisetilskuddetSpørsmål())
        }

        addAll(
            medlemskapSporsmalTags.map {
                when (it) {
                    LovMeSporsmalTag.OPPHOLDSTILATELSE -> {
                        lagSporsmalOmOppholdstillatelse(
                            fom,
                            tom,
                            kjentOppholdstillatelse,
                        )
                    }

                    LovMeSporsmalTag.ARBEID_UTENFOR_NORGE -> {
                        lagSporsmalOmArbeidUtenforNorge(fom, tom)
                    }

                    LovMeSporsmalTag.OPPHOLD_UTENFOR_NORGE -> {
                        lagSporsmalOmOppholdUtenforNorge(fom, tom)
                    }

                    LovMeSporsmalTag.OPPHOLD_UTENFOR_EØS_OMRÅDE -> {
                        lagSporsmalOmOppholdUtenforEos(fom, tom)
                    }

                    SykepengesoknadSporsmalTag.ARBEID_UTENFOR_NORGE -> {
                        arbeidUtenforNorge()
                    }

                    else -> {
                        throw RuntimeException("Ukjent MedlemskapSporsmalTag: $it.")
                    }
                }
            },
        )
    }
}
