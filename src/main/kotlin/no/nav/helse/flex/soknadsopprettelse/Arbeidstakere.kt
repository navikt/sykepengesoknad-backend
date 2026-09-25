package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Soknadstype.GRADERT_REISETILSKUDD
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.medlemskap.KjentOppholdstillatelse
import no.nav.helse.flex.soknadsopprettelse.aaregdata.ArbeidsforholdFraAAreg
import no.nav.helse.flex.soknadsopprettelse.sporsmal.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmArbeidUtenforNorge
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmOppholdUtenforEos
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmOppholdUtenforNorge
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmOppholdstillatelse
import no.nav.helse.flex.soknadsopprettelse.sporsmal.utenlandsksykmelding.utenlandskSykmeldingSporsmal
import no.nav.helse.flex.yrkesskade.YrkesskadeSporsmalGrunnlag

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
    sykepengesoknad: Sykepengesoknad,
    andreKjenteArbeidsforholdFraInntektskomponenten: List<ArbeidsforholdFraInntektskomponenten>,
    yrkesskade: YrkesskadeSporsmalGrunnlag,
    arbeidsforholdoversiktResponse: List<ArbeidsforholdFraAAreg>?,
    kjentOppholdstillatelse: KjentOppholdstillatelse?,
    medlemskapSporsmalTags: List<MedlemskapSporsmalTag>,
    harTidligereUtenlandskSpm: Boolean,
    erForsteSoknadISykeforlop: Boolean,
): List<Sporsmal> {
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

        val heltNyeArbeidsforhold =
            sjekkNyeArbeidsforhold(
                fom = sykepengesoknad.fom,
                tom = sykepengesoknad.tom,
                arbeidforholdOversikt = arbeidsforholdoversiktResponse,
            )?.toList()

        if (!heltNyeArbeidsforhold.isNullOrEmpty()) {
            addAll(
                nyttArbeidsforholdSporsmal(
                    heltNyeArbeidsforhold.toList(),
                    fom = sykepengesoknad.fom,
                    tom = sykepengesoknad.tom,
                ),
            )
        }

        if (!arbeidsforholdoversiktResponse.isNullOrEmpty() || andreKjenteArbeidsforholdFraInntektskomponenten.isNotEmpty()) {
            val ghostInntekter =
                sjekkGhostInntekter(
                    arbeidsforholdFraInntektskomponenten = andreKjenteArbeidsforholdFraInntektskomponenten,
                    arbeidforholdOversikt = arbeidsforholdoversiktResponse ?: emptyList(),
                    arbeidsgiverOrgnummer = sykepengesoknad.arbeidsgiverOrgnummer,
                    nyeArbeidsforhold = heltNyeArbeidsforhold ?: emptyList(),
                )

            if (ghostInntekter.isNotEmpty()) {
                add(
                    flereInntektskilderGhost(
                        andreKjenteInntektskilder = ghostInntekter,
                        soknadsperiode =
                            Soknadsperiode(
                                fom = sykepengesoknad.fom,
                                tom = sykepengesoknad.tom,
                                grad = 0,
                                sykmeldingstype = null,
                            ),
                    ),
                )
            } else {
                add(andreInntektskilderArbeidstakerV2())
            }
        } else {
            add(andreInntektskilderArbeidstakerV2())
        }

        addAll(jobbetDuIPeriodenSporsmal(sykepengesoknad.soknadPerioder!!, sykepengesoknad.arbeidsgiverNavn!!))

        if (erGradertReisetilskudd) {
            add(brukteReisetilskuddetSpørsmål())
        }

        addAll(
            medlemskapSporsmalTags.map {
                when (it) {
                    LovMeSporsmalTag.OPPHOLDSTILATELSE -> {
                        lagSporsmalOmOppholdstillatelse(
                            sykepengesoknad.fom,
                            sykepengesoknad.tom,
                            kjentOppholdstillatelse,
                        )
                    }

                    LovMeSporsmalTag.ARBEID_UTENFOR_NORGE -> {
                        lagSporsmalOmArbeidUtenforNorge(sykepengesoknad.fom, sykepengesoknad.tom)
                    }

                    LovMeSporsmalTag.OPPHOLD_UTENFOR_NORGE -> {
                        lagSporsmalOmOppholdUtenforNorge(sykepengesoknad.fom, sykepengesoknad.tom)
                    }

                    LovMeSporsmalTag.OPPHOLD_UTENFOR_EØS_OMRÅDE -> {
                        lagSporsmalOmOppholdUtenforEos(sykepengesoknad.fom, sykepengesoknad.tom)
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
