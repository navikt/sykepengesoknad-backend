package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.AndreArbeidsforholdHenting
import no.nav.helse.flex.soknadsopprettelse.erForsteSoknadTilArbeidsgiverIForlop
import no.nav.helse.flex.soknadsopprettelse.hentTidligsteFomForSykmelding
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadAnnetArbeidsforhold
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadArbeidsledig
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadArbeidstaker
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadSelvstendigOgFrilanser
import no.nav.helse.flex.soknadsopprettelse.settOppSykepengesoknadBehandlingsdager
import no.nav.helse.flex.soknadsopprettelse.skapReisetilskuddsoknad
import no.nav.helse.flex.yrkesskade.YrkesskadeIndikatorer
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
        val yrkesskade = erForsteSoknadISykeforlop && yrkesskadeIndikatorer.harYrkesskadeIndikatorer(identer, soknad.sykmeldingId)

        if (erEnkeltstaendeBehandlingsdagSoknad) {
            return settOppSykepengesoknadBehandlingsdager(
                soknadMetadata = soknad,
                erForsteSoknadISykeforlop = erForsteSoknadISykeforlop,
                tidligsteFomForSykmelding = tidligsteFomForSykmelding,
                egenmeldingISykmeldingen = egenmeldingISykmeldingen,
                yrkesskade = yrkesskade
            )
        }

        val erReisetilskudd = soknad.soknadstype == Soknadstype.REISETILSKUDD
        if (erReisetilskudd) {
            return skapReisetilskuddsoknad(
                soknadMetadata = soknad,
                yrkesskade = yrkesskade
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
                    harTidligereUtenlandskSpm = harTidligereUtenlandskSpm,
                    yrkesskade = yrkesskade
                )
            }

            Arbeidssituasjon.NAERINGSDRIVENDE, Arbeidssituasjon.FRILANSER -> settOppSoknadSelvstendigOgFrilanser(
                sykepengesoknad = soknad,
                erForsteSoknadISykeforlop = erForsteSoknadISykeforlop,
                harTidligereUtenlandskSpm = harTidligereUtenlandskSpm,
                yrkesskade = yrkesskade

            )

            Arbeidssituasjon.ARBEIDSLEDIG -> settOppSoknadArbeidsledig(
                sykepengesoknad = soknad,
                erForsteSoknadISykeforlop = erForsteSoknadISykeforlop,
                harTidligereUtenlandskSpm = harTidligereUtenlandskSpm,
                yrkesskade = yrkesskade

            )

            Arbeidssituasjon.ANNET -> settOppSoknadAnnetArbeidsforhold(
                sykepengesoknad = soknad,
                erForsteSoknadISykeforlop = erForsteSoknadISykeforlop,
                harTidligereUtenlandskSpm = harTidligereUtenlandskSpm,
                yrkesskade = yrkesskade
            )

            else -> {
                throw RuntimeException("Skal ikke ende her")
            }
        }
    }
}
