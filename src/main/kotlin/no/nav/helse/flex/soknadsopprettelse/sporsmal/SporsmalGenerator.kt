package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.logger
import no.nav.helse.flex.medlemskap.MedlemskapVurderingClient
import no.nav.helse.flex.medlemskap.MedlemskapVurderingRequest
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.yrkesskade.YrkesskadeIndikatorer
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class SporsmalGenerator(
    private val identService: IdentService,
    private val andreArbeidsforholdHenting: AndreArbeidsforholdHenting,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val yrkesskadeIndikatorer: YrkesskadeIndikatorer,
    private val medlemskapVurderingClient: MedlemskapVurderingClient

) {

    private val log = logger()

    fun lagSporsmalPaSoknad(id: String) {
        val soknad = sykepengesoknadDAO.finnSykepengesoknad(id)

        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(soknad.fnr)

        val andreSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer).filterNot { it.id == soknad.id }

        val sporsmalOgAndreKjenteArbeidsforhold = genererSykepengesoknadSporsmal(soknad, andreSoknader, identer)

        sykepengesoknadDAO.byttUtSporsmal(soknad.copy(sporsmal = sporsmalOgAndreKjenteArbeidsforhold.sporsmal))

        sporsmalOgAndreKjenteArbeidsforhold.andreKjenteArbeidsforhold?.let {
            sykepengesoknadDAO.lagreInntektskilderDataFraInntektskomponenten(soknad.id, it)
        }
    }

    data class SporsmalOgAndreKjenteArbeidsforhold(
        val sporsmal: List<Sporsmal>,
        val andreKjenteArbeidsforhold: List<ArbeidsforholdFraInntektskomponenten>? = null
    )

    fun List<Sporsmal>.tilSporsmalOgAndreKjenteArbeidsforhold(): SporsmalOgAndreKjenteArbeidsforhold =
        SporsmalOgAndreKjenteArbeidsforhold(
            sporsmal = this,
            andreKjenteArbeidsforhold = null
        )

    private fun genererSykepengesoknadSporsmal(
        soknad: Sykepengesoknad,
        eksisterendeSoknader: List<Sykepengesoknad>,
        identer: FolkeregisterIdenter
    ): SporsmalOgAndreKjenteArbeidsforhold {
        val erForsteSoknadISykeforlop = erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad)

        val erEnkeltstaendeBehandlingsdagSoknad = soknad.soknadstype == Soknadstype.BEHANDLINGSDAGER

        val yrkesskade =
            erForsteSoknadISykeforlop && yrkesskadeIndikatorer.harYrkesskadeIndikatorer(identer, soknad.sykmeldingId)

        val harTidligereUtenlandskSpm = harBlittStiltUtlandsSporsmal(eksisterendeSoknader, soknad)

        val opts = SettOppSoknadOpts(
            sykepengesoknad = soknad,
            erForsteSoknadISykeforlop = erForsteSoknadISykeforlop,
            harTidligereUtenlandskSpm = harTidligereUtenlandskSpm,
            yrkesskade = yrkesskade
        )

        if (erEnkeltstaendeBehandlingsdagSoknad) {
            return settOppSykepengesoknadBehandlingsdager(
                opts
            ).tilSporsmalOgAndreKjenteArbeidsforhold()
        }

        val erReisetilskudd = soknad.soknadstype == Soknadstype.REISETILSKUDD
        if (erReisetilskudd) {
            return skapReisetilskuddsoknad(
                opts
            ).tilSporsmalOgAndreKjenteArbeidsforhold()
        }

        // Det skal bare gjøres medlemskapvurdering for første søknad i sykeforløpet.
        if (erForsteSoknadISykeforlop) {
            try {
                val hentMedlemskapVurdering = medlemskapVurderingClient.hentMedlemskapVurdering(
                    MedlemskapVurderingRequest(
                        // Bruker fnr fra sykepengesøknaden, ikke identer siden LovMe ikke støtter det enda, og har
                        // planer om å implementere kall mot PDL på sin side.
                        fnr = soknad.fnr,
                        fom = soknad.fom!!,
                        tom = soknad.tom!!,
                        sykepengesoknadId = soknad.id
                    )
                )
                log.info("Hentet medlemskapvurdering for søknad ${soknad.id} med svar ${hentMedlemskapVurdering.svar}")
            } catch (e: Exception) {
                // Catch-all sånn at vi kan samle data uten at det påvirker spørsmålsgenereringen. Data og tid brukt
                // blir lagret i databasen av MedlemskapvurderingClient.
                log.warn("Feilet ved henting av medlemskapvurdering for søknad ${soknad.id}. Gjør ingenting.", e)
            }
        }

        return when (soknad.arbeidssituasjon) {
            Arbeidssituasjon.ARBEIDSTAKER -> {
                val andreKjenteArbeidsforhold = andreArbeidsforholdHenting.hentArbeidsforhold(
                    fnr = soknad.fnr,
                    arbeidsgiverOrgnummer = soknad.arbeidsgiverOrgnummer!!,
                    startSykeforlop = soknad.startSykeforlop!!
                )
                val sporsmal = settOppSoknadArbeidstaker(
                    opts = opts,
                    andreKjenteArbeidsforhold = andreKjenteArbeidsforhold.map { it.navn }

                )
                SporsmalOgAndreKjenteArbeidsforhold(
                    sporsmal = sporsmal,
                    andreKjenteArbeidsforhold = andreKjenteArbeidsforhold
                )
            }

            else -> {
                when (soknad.arbeidssituasjon) {
                    Arbeidssituasjon.NAERINGSDRIVENDE -> settOppSoknadSelvstendigOgFrilanser(opts)
                    Arbeidssituasjon.FRILANSER -> settOppSoknadSelvstendigOgFrilanser(opts)
                    Arbeidssituasjon.ARBEIDSLEDIG -> settOppSoknadArbeidsledig(opts)
                    Arbeidssituasjon.ANNET -> settOppSoknadAnnetArbeidsforhold(opts)

                    else -> {
                        throw RuntimeException("Arbeidssituasjon ${soknad.arbeidssituasjon} for sykepengesøknad ${soknad.id} er ukjent. Kan ikke generere spørsmål.")
                    }
                }.tilSporsmalOgAndreKjenteArbeidsforhold()
            }
        }
    }
}

data class SettOppSoknadOpts(
    val sykepengesoknad: Sykepengesoknad,
    val erForsteSoknadISykeforlop: Boolean,
    val harTidligereUtenlandskSpm: Boolean,
    val yrkesskade: Boolean
)
