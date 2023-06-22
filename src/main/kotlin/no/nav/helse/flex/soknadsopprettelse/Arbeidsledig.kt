package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.utenlandsksykmelding.utenlandskSykmeldingSporsmal

fun settOppSoknadArbeidsledig(
    opts: SettOppSoknadOpts
): List<Sporsmal> {
    val (sykepengesoknad, erForsteSoknadISykeforlop, harTidligereUtenlandskSpm, yrkesskade) = opts

    val gradertReisetilskudd = sykepengesoknad.soknadstype == Soknadstype.GRADERT_REISETILSKUDD

    return mutableListOf(
        ansvarserklaringSporsmal(reisetilskudd = gradertReisetilskudd),
        andreInntektskilderArbeidsledig(sykepengesoknad.fom!!, sykepengesoknad.tom!!),
        friskmeldingSporsmal(sykepengesoknad.fom, sykepengesoknad.tom),
        utenlandsoppholdArbeidsledigAnnetSporsmal(sykepengesoknad.fom, sykepengesoknad.tom),
        vaerKlarOverAt(gradertReisetilskudd = gradertReisetilskudd),
        bekreftOpplysningerSporsmal()
    ).also {
        if (erForsteSoknadISykeforlop) {
            it.add(arbeidUtenforNorge())
        }
        if (yrkesskade) {
            it.add(yrkesskadeSporsmal())
        }
        if (sykepengesoknad.utenlandskSykmelding) {
            if (erForsteSoknadISykeforlop || !harTidligereUtenlandskSpm) {
                it.addAll(utenlandskSykmeldingSporsmal(sykepengesoknad))
            }
        }
        if (gradertReisetilskudd) {
            it.add(brukteReisetilskuddetSpørsmål())
        }
    }.toList()
}
