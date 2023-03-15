package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.sporsmal.andreInntektskilderArbeidsledig
import no.nav.helse.flex.soknadsopprettelse.sporsmal.ansvarserklaringSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.arbeidUtenforNorge
import no.nav.helse.flex.soknadsopprettelse.sporsmal.bekreftOpplysningerSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.friskmeldingSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.permisjonSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.utenlandsksykmelding.utenlandskSykmeldingSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.utenlandsoppholdArbeidsledigAnnetSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.vaerKlarOverAt

fun settOppSoknadAnnetArbeidsforhold(
    sykepengesoknad: Sykepengesoknad,
    erForsteSoknadISykeforlop: Boolean,
    harTidligereUtenlandskSpm: Boolean,
    utenlandskSporsmalEnablet: Boolean
): List<Sporsmal> {
    val gradertReisetilskudd = sykepengesoknad.soknadstype == Soknadstype.GRADERT_REISETILSKUDD

    return mutableListOf(
        ansvarserklaringSporsmal(reisetilskudd = gradertReisetilskudd),
        andreInntektskilderArbeidsledig(sykepengesoknad.fom!!, sykepengesoknad.tom!!),
        friskmeldingSporsmal(sykepengesoknad.fom, sykepengesoknad.tom),
        permisjonSporsmal(sykepengesoknad.fom, sykepengesoknad.tom),
        utenlandsoppholdArbeidsledigAnnetSporsmal(sykepengesoknad.fom, sykepengesoknad.tom),
        vaerKlarOverAt(gradertReisetilskudd = gradertReisetilskudd),
        bekreftOpplysningerSporsmal()
    ).also {
        if (erForsteSoknadISykeforlop) {
            it.add(arbeidUtenforNorge())
        }
        if (sykepengesoknad.utenlandskSykmelding) {
            if (erForsteSoknadISykeforlop || !harTidligereUtenlandskSpm) {
                if (utenlandskSporsmalEnablet) {
                    it.addAll(utenlandskSykmeldingSporsmal(sykepengesoknad))
                }
            }
        }
        if (gradertReisetilskudd) {
            it.add(brukteReisetilskuddetSpørsmål())
        }
    }.toList()
}
