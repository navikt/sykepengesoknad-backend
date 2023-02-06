package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.soknadsopprettelse.AndreArbeidsforholdHenting
import no.nav.helse.flex.soknadsopprettelse.erForsteSoknadTilArbeidsgiverIForlop
import no.nav.helse.flex.soknadsopprettelse.hentTidligsteFomForSykmelding
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadAnnetArbeidsforhold
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadArbeidsledig
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadArbeidstaker
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadSelvstendigOgFrilanser
import no.nav.helse.flex.soknadsopprettelse.settOppSykepengesoknadBehandlingsdager
import no.nav.helse.flex.soknadsopprettelse.skapReisetilskuddsoknad
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class SporsmalGenerator(
    private val identService: IdentService,
    private val andreArbeidsforholdHenting: AndreArbeidsforholdHenting,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
) {
    fun lagSporsmalPaSoknad(id: String) {
        val soknad = sykepengesoknadDAO.finnSykepengesoknad(id)

        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(soknad.fnr)

        val andreSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer).filterNot { it.id == soknad.id }

        val sporsmal = genererSykepengesoknadSporsmal(soknad, andreSoknader)

        sykepengesoknadDAO.byttUtSporsmal(soknad.copy(sporsmal = sporsmal))
    }

    private fun genererSykepengesoknadSporsmal(
        soknad: Sykepengesoknad,
        eksisterendeSoknader: List<Sykepengesoknad>,
    ): List<Sporsmal> {

        val tidligsteFomForSykmelding = hentTidligsteFomForSykmelding(soknad, eksisterendeSoknader)
        val erForsteSoknadISykeforlop = erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad)

        val erEnkeltstaendeBehandlingsdagSoknad = soknad.soknadstype == Soknadstype.BEHANDLINGSDAGER

        if (erEnkeltstaendeBehandlingsdagSoknad) {
            return settOppSykepengesoknadBehandlingsdager(
                soknad,
                erForsteSoknadISykeforlop,
                tidligsteFomForSykmelding
            )
        }

        val erReisetilskudd = soknad.soknadstype == Soknadstype.REISETILSKUDD
        if (erReisetilskudd) {
            return skapReisetilskuddsoknad(
                soknad
            )
        }

        return when (soknad.arbeidssituasjon) {
            Arbeidssituasjon.ARBEIDSTAKER -> {
                settOppSoknadArbeidstaker(
                    sykepengesoknad = soknad,
                    erForsteSoknadISykeforlop = erForsteSoknadISykeforlop,
                    tidligsteFomForSykmelding = tidligsteFomForSykmelding,
                    andreKjenteArbeidsforhold = andreArbeidsforholdHenting.hentArbeidsforhold(
                        fnr = soknad.fnr,
                        arbeidsgiverOrgnummer = soknad.arbeidsgiverOrgnummer!!,
                        startSykeforlop = soknad.startSykeforlop!!
                    )
                )
            }

            Arbeidssituasjon.NAERINGSDRIVENDE, Arbeidssituasjon.FRILANSER -> settOppSoknadSelvstendigOgFrilanser(
                soknad,
                erForsteSoknadISykeforlop
            )

            Arbeidssituasjon.ARBEIDSLEDIG -> settOppSoknadArbeidsledig(soknad, erForsteSoknadISykeforlop)
            Arbeidssituasjon.ANNET -> settOppSoknadAnnetArbeidsforhold(soknad, erForsteSoknadISykeforlop)
            else -> {
                throw RuntimeException("Skal ikke ende her")
            }
        }
    }
}
