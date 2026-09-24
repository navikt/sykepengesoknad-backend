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
import no.nav.helse.flex.util.isAfterOrEqual
import no.nav.helse.flex.util.isBeforeOrEqual
import no.nav.helse.flex.yrkesskade.YrkesskadeSporsmalGrunnlag
import java.time.LocalDate
import kotlin.collections.orEmpty

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
            filtrerArbeidsforholdISykeforlop(
                arbeidsforholdoversiktResponse = arbeidsforholdoversiktResponse,
                fom = sykepengesoknad.fom,
                tom = sykepengesoknad.tom,
            )

        if (arbeidsforholdoversiktResponse != null) {
            if (heltNyeArbeidsforhold?.isNotEmpty() == true) {
                addAll(
                    nyttArbeidsforholdSporsmal(
                        heltNyeArbeidsforhold.toList(),
                        fom = sykepengesoknad.fom,
                        tom = sykepengesoknad.tom,
                    ),
                )
            }
        }

        val inntekterFraInntektskomponenten =
            andreKjenteArbeidsforholdFraInntektskomponenten.map { inntekt ->
                KjentInntektskilde(
                    navn = inntekt.navn,
                    kilde = Kilde.INNTEKTSKOMPONENTEN,
                    orgnummer = inntekt.orgnummer,
                )
            }

        val arbeidsforholdFraAAreg =
            arbeidsforholdoversiktResponse?.map { arbeidsforhold ->
                KjentInntektskilde(
                    navn = arbeidsforhold.arbeidsstedNavn,
                    kilde = Kilde.AAAREG,
                    orgnummer = arbeidsforhold.arbeidsstedOrgnummer,
                )
            }

        val andreKjenteInntektskilder =
            buildSet {
                addAll(inntekterFraInntektskomponenten)
                addAll(arbeidsforholdFraAAreg.orEmpty())
            }.filterNot { it.orgnummer == sykepengesoknad.arbeidsgiverOrgnummer }
                .filterNot { kjentInntektskilde ->
                    kjentInntektskilde.orgnummer in
                        heltNyeArbeidsforhold?.map { it.arbeidsstedOrgnummer }.orEmpty()
                }

        if (andreKjenteInntektskilder.size > 1) {
            add(
                flereInntektskilderGhost(
                    andreKjenteInntektskilder = andreKjenteInntektskilder,
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
            add(
                andreInntektskilderArbeidstakerV2(
                    andreKjenteInntektskilder = andreKjenteInntektskilder,
                ),
            )
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

fun filtrerArbeidsforholdISykeforlop(
    arbeidsforholdoversiktResponse: List<ArbeidsforholdFraAAreg>?,
    fom: LocalDate,
    tom: LocalDate,
): Set<ArbeidsforholdFraAAreg>? =
    arbeidsforholdoversiktResponse
        ?.filter { it.startdato.isBeforeOrEqual(tom) }
        ?.filter {
            if (it.sluttdato == null) {
                return@filter true
            }
            val afterOrEqual = it.sluttdato.isAfterOrEqual(fom)
            return@filter afterOrEqual
        }?.toSet()

fun jobbetDuIPeriodenSporsmal(
    soknadsperioder: List<Soknadsperiode>,
    arbeidsgiverNavn: String,
): List<Sporsmal> =
    soknadsperioder
        .lastIndex
        .downTo(0)
        .reversed()
        .map { index ->
            val periode = soknadsperioder[index]
            if (periode.grad == 100) {
                jobbetDu100ProsentArbeidstaker(periode, arbeidsgiverNavn, index)
            } else {
                jobbetDuGradertArbeidstaker(periode, arbeidsgiverNavn, index)
            }
        }
