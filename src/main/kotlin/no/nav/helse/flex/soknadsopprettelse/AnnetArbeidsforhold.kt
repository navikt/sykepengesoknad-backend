package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.rest.SoknadMetadata
import no.nav.helse.flex.soknadsopprettelse.sporsmal.andreInntektskilderArbeidsledig
import no.nav.helse.flex.soknadsopprettelse.sporsmal.ansvarserklaringSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.arbeidUtenforNorge
import no.nav.helse.flex.soknadsopprettelse.sporsmal.bekreftOpplysningerSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.friskmeldingSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.permisjonSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.utdanningsSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.utenlandsoppholdArbeidsledigAnnetSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.vaerKlarOverAt
import java.time.Instant

fun settOppSoknadAnnetArbeidsforhold(
    soknadMetadata: SoknadMetadata,
    erForsteSoknadISykeforlop: Boolean
): Sykepengesoknad {
    val gradertReisetilskudd = soknadMetadata.soknadstype == Soknadstype.GRADERT_REISETILSKUDD

    val sporsmal = mutableListOf(
        ansvarserklaringSporsmal(),
        andreInntektskilderArbeidsledig(soknadMetadata.fom, soknadMetadata.tom),
        friskmeldingSporsmal(soknadMetadata.fom, soknadMetadata.tom),
        permisjonSporsmal(soknadMetadata.fom, soknadMetadata.tom),
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
    }

    return Sykepengesoknad(
        id = soknadMetadata.id,
        soknadstype = if (gradertReisetilskudd) Soknadstype.GRADERT_REISETILSKUDD else Soknadstype.ANNET_ARBEIDSFORHOLD,
        arbeidssituasjon = Arbeidssituasjon.ANNET,
        fnr = soknadMetadata.fnr,
        status = soknadMetadata.status,
        fom = soknadMetadata.fom,
        tom = soknadMetadata.tom,
        opprettet = Instant.now(),
        sykmeldingId = soknadMetadata.sykmeldingId,
        sykmeldingSkrevet = soknadMetadata.sykmeldingSkrevet,
        startSykeforlop = soknadMetadata.startSykeforlop,
        soknadPerioder = soknadMetadata.sykmeldingsperioder,
        sporsmal = sporsmal,
        egenmeldtSykmelding = soknadMetadata.egenmeldtSykmelding,
        merknaderFraSykmelding = soknadMetadata.merknader,
    )
}
