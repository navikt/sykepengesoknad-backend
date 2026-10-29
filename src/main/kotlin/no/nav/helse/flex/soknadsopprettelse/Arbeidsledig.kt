package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.sporsmal.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.utenlandsksykmelding.utenlandskSykmeldingSporsmal
import no.nav.helse.flex.yrkesskade.YrkesskadeSporsmalGrunnlag

fun settOppSoknadArbeidsledig(
    sykepengesoknad: Sykepengesoknad,
    yrkesskade: YrkesskadeSporsmalGrunnlag,
    harTidligereUtenlandskSpm: Boolean,
    erForsteSoknadISykeforlop: Boolean,
    ghostInntekter: List<KjentInntektskilde>,
): List<Sporsmal> {
    val erGradertReisetilskudd = sykepengesoknad.soknadstype == Soknadstype.GRADERT_REISETILSKUDD

    return mutableListOf<Sporsmal>()
        .apply {
            add(ansvarserklaringSporsmal())

            if (ghostInntekter.isNotEmpty()) {
                add(
                    flereInntektskilderGhost(
                        andreKjenteInntektskilder = ghostInntekter,
                        fom = sykepengesoknad.fom!!,
                        tom = sykepengesoknad.tom!!,
                    ),
                )
            } else {
                add(andreInntektskilderV2())
            }

            add(friskmeldingSporsmal(sykepengesoknad.fom!!, sykepengesoknad.tom!!))
            add(oppholdUtenforEOSSporsmal(sykepengesoknad.fom, sykepengesoknad.tom))
            add(tilSlutt())

            if (erForsteSoknadISykeforlop) {
                add(arbeidUtenforNorge())
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
