package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.rest.SoknadMetadata
import java.time.LocalDate

fun genererSykepengesoknadFraMetadata(
    soknadMetadata: SoknadMetadata,
    eksisterendeSoknader: List<Sykepengesoknad>
): Sykepengesoknad {

    val tidligsteFomForSykmelding = hentTidligsteFomForSykmelding(soknadMetadata, eksisterendeSoknader)
    val erForsteSoknadISykeforlop = erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknadMetadata)

    val erEnkeltstaendeBehandlingsdagSoknad = soknadMetadata.soknadstype == Soknadstype.BEHANDLINGSDAGER

    if (erEnkeltstaendeBehandlingsdagSoknad) {
        return settOppSykepengesoknadBehandlingsdager(
            soknadMetadata,
            erForsteSoknadISykeforlop,
            tidligsteFomForSykmelding
        ).fjernSporsmalHvisFremtidig()
    }

    val erReisetilskudd = soknadMetadata.soknadstype == Soknadstype.REISETILSKUDD
    if (erReisetilskudd) {
        return skapReisetilskuddsoknad(
            soknadMetadata
        ).fjernSporsmalHvisFremtidig()
    }

    return when (soknadMetadata.arbeidssituasjon) {
        Arbeidssituasjon.ARBEIDSTAKER -> {

            settOppSoknadArbeidstaker(
                soknadMetadata = soknadMetadata,
                erForsteSoknadISykeforlop = erForsteSoknadISykeforlop,
                tidligsteFomForSykmelding = tidligsteFomForSykmelding,
            )
        }
        Arbeidssituasjon.NAERINGSDRIVENDE, Arbeidssituasjon.FRILANSER -> settOppSoknadSelvstendigOgFrilanser(
            soknadMetadata,
            erForsteSoknadISykeforlop
        )
        Arbeidssituasjon.ARBEIDSLEDIG -> settOppSoknadArbeidsledig(soknadMetadata, erForsteSoknadISykeforlop)
        Arbeidssituasjon.ANNET -> settOppSoknadAnnetArbeidsforhold(soknadMetadata, erForsteSoknadISykeforlop)
    }.fjernSporsmalHvisFremtidig()
}

fun Sykepengesoknad.fjernSporsmalHvisFremtidig(): Sykepengesoknad {
    if (status == no.nav.helse.flex.domain.Soknadstatus.FREMTIDIG) {
        return this.copy(sporsmal = emptyList())
    }
    return this
}

private fun erForsteSoknadTilArbeidsgiverIForlop(
    eksisterendeSoknader: List<Sykepengesoknad>,
    soknadMetadata: SoknadMetadata
): Boolean {
    return eksisterendeSoknader
        .asSequence()
        .filter { it.fom != null && it.fom.isBefore(soknadMetadata.fom) }
        .filter { it.sykmeldingId != null }
        .filter { it.startSykeforlop != null }
        .filter { it.arbeidssituasjon == soknadMetadata.arbeidssituasjon }
        .filter {
            if (soknadMetadata.arbeidssituasjon == Arbeidssituasjon.ARBEIDSTAKER) {
                soknadMetadata.arbeidsgiverOrgnummer?.let { orgnr ->
                    if (it.soknadstype == Soknadstype.ARBEIDSTAKERE) {
                        return@filter it.arbeidsgiverOrgnummer == orgnr
                    } else if (it.soknadstype == Soknadstype.BEHANDLINGSDAGER) {
                        return@filter it.arbeidsgiverOrgnummer == orgnr
                    }
                    false
                }
            }
            true
        }
        .none { it.startSykeforlop == soknadMetadata.startSykeforlop }
}

fun hentTidligsteFomForSykmelding(
    soknadMetadata: SoknadMetadata,
    eksisterendeSoknader: List<Sykepengesoknad>
): LocalDate {

    return eksisterendeSoknader
        .filter { it.sykmeldingId == soknadMetadata.sykmeldingId }
        .map { it.fom!! }
        .toMutableList()
        .also { it.add(soknadMetadata.fom) }
        .minOrNull()!!
}
