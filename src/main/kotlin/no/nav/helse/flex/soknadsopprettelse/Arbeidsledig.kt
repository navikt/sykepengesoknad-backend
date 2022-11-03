package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.sporsmal.andreInntektskilderArbeidsledig
import no.nav.helse.flex.soknadsopprettelse.sporsmal.ansvarserklaringSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.arbeidUtenforNorge
import no.nav.helse.flex.soknadsopprettelse.sporsmal.bekreftOpplysningerSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.friskmeldingSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.utdanningsSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.utenlandsoppholdArbeidsledigAnnetSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.vaerKlarOverAt

fun settOppSoknadArbeidsledig(soknadMetadata: Sykepengesoknad, erForsteSoknadISykeforlop: Boolean): List<Sporsmal> {
    val gradertReisetilskudd = soknadMetadata.soknadstype == Soknadstype.GRADERT_REISETILSKUDD

    return mutableListOf(
        ansvarserklaringSporsmal(reisetilskudd = gradertReisetilskudd),
        andreInntektskilderArbeidsledig(soknadMetadata.fom!!, soknadMetadata.tom!!),
        friskmeldingSporsmal(soknadMetadata.fom, soknadMetadata.tom),
        utenlandsoppholdArbeidsledigAnnetSporsmal(soknadMetadata.fom, soknadMetadata.tom),
        utdanningsSporsmal(soknadMetadata.fom, soknadMetadata.tom),
        vaerKlarOverAt(gradertReisetilskudd = gradertReisetilskudd),
        bekreftOpplysningerSporsmal()
    ).also {
        if (erForsteSoknadISykeforlop) {
            it.add(arbeidUtenforNorge())
        }
        if (gradertReisetilskudd) {
            it.add(brukteReisetilskuddetSpørsmål())
        }
    }.toList()
}
