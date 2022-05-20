package no.nav.syfo.soknadsopprettelse

import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.rest.SoknadMetadata
import no.nav.syfo.soknadsopprettelse.sporsmal.andreInntektskilderArbeidsledig
import no.nav.syfo.soknadsopprettelse.sporsmal.ansvarserklaringSporsmal
import no.nav.syfo.soknadsopprettelse.sporsmal.arbeidUtenforNorge
import no.nav.syfo.soknadsopprettelse.sporsmal.bekreftOpplysningerSporsmal
import no.nav.syfo.soknadsopprettelse.sporsmal.friskmeldingSporsmal
import no.nav.syfo.soknadsopprettelse.sporsmal.permisjonSporsmal
import no.nav.syfo.soknadsopprettelse.sporsmal.permittertNaaSporsmal
import no.nav.syfo.soknadsopprettelse.sporsmal.permittertPeriodeSporsmal
import no.nav.syfo.soknadsopprettelse.sporsmal.utdanningsSporsmal
import no.nav.syfo.soknadsopprettelse.sporsmal.utenlandsoppholdArbeidsledigAnnetSporsmal
import no.nav.syfo.soknadsopprettelse.sporsmal.vaerKlarOverAt
import java.time.Instant
import java.time.LocalDateTime.now

fun settOppSoknadAnnetArbeidsforhold(
    soknadMetadata: SoknadMetadata,
    erForsteSoknadISykeforlop: Boolean
): Sykepengesoknad {
    val gradertReisetilskudd = soknadMetadata.soknadstype == Soknadstype.GRADERT_REISETILSKUDD

    val sporsmal = mutableListOf(
        ansvarserklaringSporsmal(reisetilskudd = gradertReisetilskudd),
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
            it.add(permittertNaaSporsmal(soknadMetadata))
            it.add(permittertPeriodeSporsmal(soknadMetadata.fom))
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
