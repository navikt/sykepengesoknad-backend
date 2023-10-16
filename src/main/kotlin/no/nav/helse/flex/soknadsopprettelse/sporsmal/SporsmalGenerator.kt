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
        val erMedlemskapAvklart: Boolean,
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

                val medlemskapSporsmal = lagMedlemskapSporsmal(eksisterendeSoknader, soknad)

                val arbeidstakerSporsmal = settOppSoknadArbeidstaker(
                    opts = opts,
                    andreKjenteArbeidsforhold = andreKjenteArbeidsforhold.map { it.navn },
                    stillSporsmalOmArbeidUtenforNorge = !medlemskapSporsmal.erMedlemskapAvklart
                )

                SporsmalOgAndreKjenteArbeidsforhold(
                    sporsmal = arbeidstakerSporsmal + medlemskapSporsmal.sporsmal,
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

    // TODO: Refaktorer denne metoden så vi ikke trenger å returnerer MedlemskapSporsmal over alt.
    private fun lagMedlemskapSporsmal(
        eksisterendeSoknader: List<Sykepengesoknad>,
        soknad: Sykepengesoknad
    ): MedlemskapSporsmal {
        // Medlemskapsspørsmål skal kun stilles i søknader som er den første søknaden i et sykeforløp. Dersom det blir
        // sendt inn en tilbakedatert sykemdling vil det resuletere i en søknad med en tidligere dato for
        // startSyketilfelle. Den vil da bli tolket som en førstegangssøknad som skal ha medlemskapsspørsmål. Det
        // resulterer i to søknader med medlemskapspørsmål i samme syketilfelle. Det er mulig å vurdere en
        // implementasjon som fjerner medlemskapsspørsmålene fra den opprinnelige førstegangssøknaden, men det
        // forutsetter at søknaden ikke er sendt inn av brukeren, og at vi er i stand til å knytte den til samme
        // syketilfelle, til tross for at den søknaden fortstatt her opprinnelig startSyketilfelle.
        if (erForsteSoknadIForlop(eksisterendeSoknader, soknad)) {
            val medlemskapVurdering = try {
                medlemskapVurderingClient.hentMedlemskapVurdering(
                    MedlemskapVurderingRequest(
                        // Bruker 'fnr' fra sykepengesøknaden, ikke liste over identer siden det ikke støttes av LovMe.
                        fnr = soknad.fnr,
                        fom = soknad.fom!!,
                        tom = soknad.tom!!,
                        sykepengesoknadId = soknad.id
                    )
                )
            } catch (e: Exception) {
                // Vi kaster exception i prod, men logger bare og returnerer tom liste i DEV siden det er såpass
                // mange brukere som ikke finnes i PDL, som igjen gjør at LovMe ikke får gjort oppslag.
                if (environmentToggles.isProduction()) {
                    throw e
                } else {
                    log.warn(
                        "Henting av medlemskapvurdering feilet for søknad ${soknad.id}, men vi er i DEV og gjør " +
                            "ikke noe med det annet enn å returnere en tom liste med spørsmål.",
                        e
                    )
                    return MedlemskapSporsmal(
                        erMedlemskapAvklart = false,
                        sporsmal = emptyList()
                    )
                }
            }

            log.info(
                "Hentet medlemskapvurdering for søknad ${soknad.id} med svar ${medlemskapVurdering.svar} og " +
                    "${medlemskapVurdering.sporsmal.size} sporsmal."
            )

            if (medlemskapVurdering.svar == MedlemskapVurderingSvarType.UAVKLART) {
                // TODO: Fjern når LovMe har implementert alle scenario sånn at de vil returnere spørsmål ved UAVKLART.
                if (medlemskapVurdering.sporsmal.isEmpty()) {
                    log.warn(
                        "Medlemskapvurdering er UAVKLART for søknad ${soknad.id}, men LovMe returnerte ingen " +
                            "spørsmål om medlemskap å stille bruker."
                    )
                    return MedlemskapSporsmal(
                        erMedlemskapAvklart = false,
                        sporsmal = emptyList()
                    )
                }

                // TODO: Fjern feature-toggle før prodsetting.
                if (medlemskapToggle.stillMedlemskapSporsmal(soknad.fnr)) {
                    val medlemskapSporsmal = mutableListOf<Sporsmal>()
                    medlemskapVurdering.sporsmal.forEach {
                        when (it) {
                            MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE -> medlemskapSporsmal.add(
                                lagSporsmalOmOppholdstillatelse()
                            )

                            MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE -> medlemskapSporsmal.add(
                                lagSporsmalOmArbeidUtenforNorge()
                            )

                            MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_NORGE -> medlemskapSporsmal.add(
                                lagSporsmalOmOppholdUtenforNorge()
                            )

                            MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_EØS_OMRÅDE -> medlemskapSporsmal.add(
                                lagSporsmalOmOppholdUtenforEos()
                            )
                        }
                    }
                    return MedlemskapSporsmal(
                        erMedlemskapAvklart = true,
                        sporsmal = medlemskapSporsmal
                    )
                } else {
                    log.info(
                        "Medlemskapvurdering er UAVKLART for søknad ${soknad.id}, men medlemskapToggle svarte 'false' " +
                            "så det stilles ingen spørsmål om medlemskap til bruker."
                    )
                    return MedlemskapSporsmal(
                        erMedlemskapAvklart = false,
                        sporsmal = emptyList()
                    )
                }
            } else {
                // MedlemskapVurdering er JA eller NEI.
                return MedlemskapSporsmal(
                    erMedlemskapAvklart = true,
                    sporsmal = emptyList()
                )
            }
        }
        return MedlemskapSporsmal(
            // Det er ikke første søknad i forløp, så svarer med at medlemskap er avklart.
            erMedlemskapAvklart = true,
            sporsmal = emptyList()
        )
    }
}

data class SettOppSoknadOpts(
    val sykepengesoknad: Sykepengesoknad,
    val erForsteSoknadISykeforlop: Boolean,
    val harTidligereUtenlandskSpm: Boolean,
    val yrkesskade: YrkesskadeSporsmalGrunnlag
)
