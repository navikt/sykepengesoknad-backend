package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.yrkesskade.YrkesskadeIndikatorer
import no.nav.helse.flex.soknadsopprettelse.AndreArbeidsforholdHenting
import no.nav.helse.flex.soknadsopprettelse.erForsteSoknadTilArbeidsgiverIForlop
import no.nav.helse.flex.soknadsopprettelse.hentTidligsteFomForSykmelding
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadAnnetArbeidsforhold
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadArbeidsledig
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadArbeidstaker
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadSelvstendigOgFrilanser
import no.nav.helse.flex.soknadsopprettelse.settOppSykepengesoknadBehandlingsdager
import no.nav.helse.flex.soknadsopprettelse.skapReisetilskuddsoknad
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
@Transactional
class SporsmalGenerator(
    private val identService: IdentService,
    private val andreArbeidsforholdHenting: AndreArbeidsforholdHenting,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val yrkesskadeIndikatorer: YrkesskadeIndikatorer,

    @Value("\${UTENLANDSK_SPORSMAL_ENABLET:false}")
    private val utenlandskSporsmalEnablet: Boolean,

    @Value("\${EGENMELDING_SYKMELDING_FOM}") private val egenmeldingSykmeldingFom: String

) {

    var egenmeldingSykmeldingFomDate = OffsetDateTime.parse(egenmeldingSykmeldingFom)
    fun lagSporsmalPaSoknad(id: String) {
        val soknad = sykepengesoknadDAO.finnSykepengesoknad(id)

        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(soknad.fnr)

        val andreSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer).filterNot { it.id == soknad.id }

        val sporsmal = genererSykepengesoknadSporsmal(soknad, andreSoknader, identer)

        sykepengesoknadDAO.byttUtSporsmal(soknad.copy(sporsmal = sporsmal))
    }

    private fun genererSykepengesoknadSporsmal(
        soknad: Sykepengesoknad,
        eksisterendeSoknader: List<Sykepengesoknad>,
        identer: FolkeregisterIdenter
    ): List<Sporsmal> {
        val tidligsteFomForSykmelding = hentTidligsteFomForSykmelding(soknad, eksisterendeSoknader)
        val erForsteSoknadISykeforlop = erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad)

        val erEnkeltstaendeBehandlingsdagSoknad = soknad.soknadstype == Soknadstype.BEHANDLINGSDAGER

        val egenmeldingISykmeldingen = egenmeldingSykmeldingFomDate.isBefore(
            soknad.opprettet!!.atOffset(
                ZoneOffset.UTC
            )
        )
        if (erEnkeltstaendeBehandlingsdagSoknad) {
            return settOppSykepengesoknadBehandlingsdager(
                soknadMetadata = soknad,
                erForsteSoknadISykeforlop = erForsteSoknadISykeforlop,
                tidligsteFomForSykmelding = tidligsteFomForSykmelding,
                egenmeldingISykmeldingen = egenmeldingISykmeldingen
            )
        }

        val erReisetilskudd = soknad.soknadstype == Soknadstype.REISETILSKUDD
        if (erReisetilskudd) {
            return skapReisetilskuddsoknad(
                soknad
            )
        }

        val harTidligereUtenlandskSpm = harBlittStiltUtlandsSporsmal(eksisterendeSoknader, soknad)

        return when (soknad.arbeidssituasjon) {
            Arbeidssituasjon.ARBEIDSTAKER -> {
                settOppSoknadArbeidstaker(
                    sykepengesoknad = soknad,
                    erForsteSoknadISykeforlop = erForsteSoknadISykeforlop,
                    egenmeldingISykmeldingen = egenmeldingISykmeldingen,
                    tidligsteFomForSykmelding = tidligsteFomForSykmelding,
                    andreKjenteArbeidsforhold = andreArbeidsforholdHenting.hentArbeidsforhold(
                        fnr = soknad.fnr,
                        arbeidsgiverOrgnummer = soknad.arbeidsgiverOrgnummer!!,
                        startSykeforlop = soknad.startSykeforlop!!
                    ),
                    utenlandskSporsmalEnablet = utenlandskSporsmalEnablet,
                    harTidligereUtenlandskSpm = harTidligereUtenlandskSpm,
                    yrkesskade = erForsteSoknadISykeforlop && yrkesskadeIndikatorer.harYrkesskadeIndikatorer(identer)
                )
            }

            Arbeidssituasjon.NAERINGSDRIVENDE, Arbeidssituasjon.FRILANSER -> settOppSoknadSelvstendigOgFrilanser(
                sykepengesoknad = soknad,
                erForsteSoknadISykeforlop = erForsteSoknadISykeforlop,
                utenlandskSporsmalEnablet = utenlandskSporsmalEnablet,
                harTidligereUtenlandskSpm = harTidligereUtenlandskSpm,
                yrkesskade = erForsteSoknadISykeforlop && yrkesskadeIndikatorer.harYrkesskadeIndikatorer(identer)

            )

            Arbeidssituasjon.ARBEIDSLEDIG -> settOppSoknadArbeidsledig(
                sykepengesoknad = soknad,
                erForsteSoknadISykeforlop = erForsteSoknadISykeforlop,
                utenlandskSporsmalEnablet = utenlandskSporsmalEnablet,
                harTidligereUtenlandskSpm = harTidligereUtenlandskSpm,
                yrkesskade = erForsteSoknadISykeforlop && yrkesskadeIndikatorer.harYrkesskadeIndikatorer(identer)

            )

            Arbeidssituasjon.ANNET -> settOppSoknadAnnetArbeidsforhold(
                sykepengesoknad = soknad,
                erForsteSoknadISykeforlop = erForsteSoknadISykeforlop,
                utenlandskSporsmalEnablet = utenlandskSporsmalEnablet,
                harTidligereUtenlandskSpm = harTidligereUtenlandskSpm,
                yrkesskade = erForsteSoknadISykeforlop && yrkesskadeIndikatorer.harYrkesskadeIndikatorer(identer)
            )

            else -> {
                throw RuntimeException("Skal ikke ende her")
            }
        }
    }
}
