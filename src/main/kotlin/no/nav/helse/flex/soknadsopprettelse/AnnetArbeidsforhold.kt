package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.utenlandsksykmelding.utenlandskSykmeldingSporsmal

fun settOppSoknadAnnetArbeidsforhold(opts: SettOppSoknadOptions): List<Sporsmal> {
    val (sykepengesoknad, erForsteSoknadISykeforlop, harTidligereUtenlandskSpm, yrkesskade) = opts
    val erGradertReisetilskudd = sykepengesoknad.soknadstype == Soknadstype.GRADERT_REISETILSKUDD

    return mutableListOf<Sporsmal>()
        .apply {
            add(ansvarserklaringSporsmal())
            add(andreInntektskilderArbeidsledig(sykepengesoknad.fom!!, sykepengesoknad.tom!!))
            add(friskmeldingSporsmal(sykepengesoknad.fom, sykepengesoknad.tom))
            add(permisjonSporsmal(sykepengesoknad.fom, sykepengesoknad.tom))
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
