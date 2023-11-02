package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.logger
import no.nav.helse.flex.medlemskap.MedlemskapToggle
import no.nav.helse.flex.medlemskap.MedlemskapVurderingClient
import no.nav.helse.flex.medlemskap.MedlemskapVurderingRequest
import no.nav.helse.flex.medlemskap.MedlemskapVurderingResponse
import no.nav.helse.flex.medlemskap.MedlemskapVurderingSporsmal
import no.nav.helse.flex.medlemskap.MedlemskapVurderingSvarType
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmArbeidUtenforNorge
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmOppholdUtenforEos
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmOppholdUtenforNorge
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmOppholdstillatelse
import no.nav.helse.flex.yrkesskade.YrkesskadeIndikatorer
import no.nav.helse.flex.yrkesskade.YrkesskadeSporsmalGrunnlag
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class SporsmalGenerator(
    private val identService: IdentService,
    private val andreArbeidsforholdHenting: AndreArbeidsforholdHenting,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val yrkesskadeIndikatorer: YrkesskadeIndikatorer,
    private val medlemskapVurderingClient: MedlemskapVurderingClient,
    private val environmentToggles: EnvironmentToggles,
    private val medlemskapToggle: MedlemskapToggle
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

    data class MedlemskapSporsmal(
        val stillSporsmalOmArbeidUtenforNorge: Boolean,
        val sporsmal: List<Sporsmal>
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
        val harTidligereUtenlandskSpm = harBlittStiltUtlandsSporsmal(eksisterendeSoknader, soknad)
        val yrkesskadeSporsmalGrunnlag = yrkesskadeIndikatorer.hentYrkesskadeSporsmalGrunnlag(
            identer,
            soknad.sykmeldingId,
            erForsteSoknadISykeforlop
        )

        val opts = SettOppSoknadOpts(
            sykepengesoknad = soknad,
            erForsteSoknadISykeforlop = erForsteSoknadISykeforlop,
            harTidligereUtenlandskSpm = harTidligereUtenlandskSpm,
            yrkesskade = yrkesskadeSporsmalGrunnlag
        )

        if (erEnkeltstaendeBehandlingsdagSoknad) {
            return settOppSykepengesoknadBehandlingsdager(opts).tilSporsmalOgAndreKjenteArbeidsforhold()
        }

        if (soknad.soknadstype == Soknadstype.REISETILSKUDD) {
            return skapReisetilskuddsoknad(opts).tilSporsmalOgAndreKjenteArbeidsforhold()
        }

        return when (soknad.arbeidssituasjon) {
            Arbeidssituasjon.ARBEIDSTAKER -> {
                val andreKjenteArbeidsforhold = andreArbeidsforholdHenting.hentArbeidsforhold(
                    fnr = soknad.fnr,
                    arbeidsgiverOrgnummer = soknad.arbeidsgiverOrgnummer!!,
                    startSykeforlop = soknad.startSykeforlop!!
                )

                val arbeidstakerSporsmal = settOppSoknadArbeidstaker(
                    opts = opts,
                    andreKjenteArbeidsforhold = andreKjenteArbeidsforhold.map { it.navn }
                )

                SporsmalOgAndreKjenteArbeidsforhold(
                    // TODO: Flytt opprettelse av de faktisk medlemskapspørsmålene til Arbeidstakere.
                    sporsmal = arbeidstakerSporsmal + lagMedlemskapSporsmal(eksisterendeSoknader, soknad),
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
                        throw RuntimeException(
                            "Arbeidssituasjon ${soknad.arbeidssituasjon} for sykepengesøknad ${soknad.id} er ukjent. " +
                                "Kan ikke generere spørsmål."
                        )
                    }
                }.tilSporsmalOgAndreKjenteArbeidsforhold()
            }
        }
    }

    private fun lagMedlemskapSporsmal(
        eksisterendeSoknader: List<Sykepengesoknad>,
        soknad: Sykepengesoknad
    ): List<Sporsmal> {
        // Medlemskapspørsmal skal kun stilles i den første søknaden i et sykeforløp, uavhengig av arbeidsgiver.
        if (!erForsteSoknadIForlop(eksisterendeSoknader, soknad)) {
            return emptyList()
        }

        val medlemskapVurdering = hentMedlemskapVurdering(soknad)
            ?: return listOf(arbeidUtenforNorge())

        val (svar, sporsmal) = medlemskapVurdering

        return when {
            // Det skal ikke stilles medlemskapspørsmål hvis det er et avklart medlemskapsforhold.
            svar in listOf(MedlemskapVurderingSvarType.JA, MedlemskapVurderingSvarType.NEI) -> emptyList()

            svar == MedlemskapVurderingSvarType.UAVKLART && sporsmal.isEmpty() -> {
                log.info("Medlemskapvurdering er UAVKLART for søknad ${soknad.id}, men LovMe returnerte ingen spørsmål.")
                listOf(arbeidUtenforNorge())
            }

            !medlemskapToggle.stillMedlemskapSporsmal(soknad.fnr) -> {
                log.info("Medlemskapvurdering er UAVKLART for søknad ${soknad.id}, men medlemskapToggle svarte 'false'.")
                listOf(arbeidUtenforNorge())
            }

            else -> sporsmal.map {
                when (it) {
                    MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE -> lagSporsmalOmOppholdstillatelse()
                    MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE -> lagSporsmalOmArbeidUtenforNorge()
                    MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_NORGE -> lagSporsmalOmOppholdUtenforNorge()
                    MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_EØS_OMRÅDE -> lagSporsmalOmOppholdUtenforEos()
                }
            }
        }
    }

    private fun hentMedlemskapVurdering(soknad: Sykepengesoknad): MedlemskapVurderingResponse? {
        return runCatching {
            medlemskapVurderingClient.hentMedlemskapVurdering(
                MedlemskapVurderingRequest(
                    // Bruker 'fnr' fra sykepengesøknaden, ikke liste over identer siden det ikke støttes av LovMe.
                    fnr = soknad.fnr,
                    fom = soknad.fom ?: error("'fom' kan ikke være null."),
                    tom = soknad.tom ?: error("'tom' kan ikke være null."),
                    sykepengesoknadId = soknad.id
                )
            )
        }.onFailure { e ->
            // Vi kaster exception i PROD, men logger bare og returnerer tom liste i DEV siden det er såpass
            // mange brukere som ikke finnes i PDL, som igjen gjør at LovMe ikke får gjort oppslag.
            if (environmentToggles.isProduction()) {
                throw e
            } else {
                log.warn(
                    "Henting av medlemskapvurdering feilet for søknad ${soknad.id}, men vi er i DEV og gjør ikke" +
                        "noe med det annet enn å returnere en tom liste med spørsmål.",
                    e
                )
            }
        }.getOrNull()
    }
}

data class SettOppSoknadOpts(
    val sykepengesoknad: Sykepengesoknad,
    val erForsteSoknadISykeforlop: Boolean,
    val harTidligereUtenlandskSpm: Boolean,
    val yrkesskade: YrkesskadeSporsmalGrunnlag
)
