package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.utenlandsksykmelding.utenlandskSykmeldingSporsmal
import no.nav.helse.flex.yrkesskade.YrkesskadeSporsmalGrunnlag
import java.time.LocalDate

fun settOppSoknadArbeidsledig(
    fom: LocalDate,
    tom: LocalDate,
    soknadstype: Soknadstype,
    utenlandskSykmelding: Boolean,
    yrkesskade: YrkesskadeSporsmalGrunnlag,
    harTidligereUtenlandskSpm: Boolean,
    erForsteSoknadISykeforlop: Boolean,
    ghostInntekter: List<KjentInntektskilde>,
): List<Sporsmal> {
    val erGradertReisetilskudd = soknadstype == Soknadstype.GRADERT_REISETILSKUDD

    return mutableListOf<Sporsmal>()
        .apply {
            add(ansvarserklaringSporsmal())

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

            add(friskmeldingSporsmal(fom, tom))
            add(oppholdUtenforEOSSporsmal(fom, tom))
            add(tilSlutt())

            if (erForsteSoknadISykeforlop) {
                add(arbeidUtenforNorge())
            }
            addAll(yrkesskade.yrkeskadeSporsmal())

            if (utenlandskSykmelding && (erForsteSoknadISykeforlop || !harTidligereUtenlandskSpm)) {
                addAll(utenlandskSykmeldingSporsmal(fom, tom))
            }
            if (erGradertReisetilskudd) {
                add(brukteReisetilskuddetSpørsmål())
            }
        }.toList()
}
