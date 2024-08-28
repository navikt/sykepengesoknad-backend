package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.client.inntektskomponenten.PensjongivendeInntektClient
import no.nav.helse.flex.arbeidsgiverperiode.erUtenforArbeidsgiverPeriode
import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.logger
import no.nav.helse.flex.medlemskap.MedlemskapVurderingClient
import no.nav.helse.flex.medlemskap.MedlemskapVurderingRepository
import no.nav.helse.flex.medlemskap.MedlemskapVurderingRequest
import no.nav.helse.flex.medlemskap.MedlemskapVurderingResponse
import no.nav.helse.flex.medlemskap.MedlemskapVurderingSporsmal
import no.nav.helse.flex.medlemskap.MedlemskapVurderingSvarType
import no.nav.helse.flex.medlemskap.hentKjentOppholdstillatelse
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.service.SykepengegrunnlagService
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.unleash.UnleashToggles
import no.nav.helse.flex.yrkesskade.YrkesskadeIndikatorer
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class SporsmalGenerator(
    private val identService: IdentService,
    private val arbeidsforholdFraInntektskomponentenHenting: ArbeidsforholdFraInntektskomponentenHenting,
    private val sykepengegrunnlagService: SykepengegrunnlagService,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val yrkesskadeIndikatorer: YrkesskadeIndikatorer,
    private val medlemskapVurderingClient: MedlemskapVurderingClient,
    private val environmentToggles: EnvironmentToggles,
    private val unleashToggles: UnleashToggles,
    private val medlemskapVurderingRepository: MedlemskapVurderingRepository,
    private val aaregDataHenting: AaregDataHenting,
) {
    private val log = logger()

    data class SporsmalOgAndreKjenteArbeidsforhold(
        val sporsmal: List<Sporsmal>,
        val andreKjenteArbeidsforhold: List<ArbeidsforholdFraInntektskomponenten>? = null,
        val arbeidsforholdFraAAreg: List<ArbeidsforholdFraAAreg>? = null,
    )

    fun lagSporsmalPaSoknad(id: String) {
        val soknad = sykepengesoknadDAO.finnSykepengesoknad(id)
        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(soknad.fnr)
        val eksisterendeSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer).filterNot { it.id == soknad.id }

        val sporsmalOgAndreKjenteArbeidsforhold =
            lagSykepengesoknadSporsmal(
                soknad = soknad,
                eksisterendeSoknader = eksisterendeSoknader,
                identer = identer,
            )
        sykepengesoknadDAO.byttUtSporsmal(soknad.copy(sporsmal = sporsmalOgAndreKjenteArbeidsforhold.sporsmal))

        sykepengesoknadRepository.findBySykepengesoknadUuid(id)?.let {
            sykepengesoknadRepository.save(
                it.copy(
                    inntektskilderDataFraInntektskomponenten =
                        sporsmalOgAndreKjenteArbeidsforhold
                            .andreKjenteArbeidsforhold
                            ?.serialisertTilString(),
                    arbeidsforholdFraAareg =
                        sporsmalOgAndreKjenteArbeidsforhold
                            .arbeidsforholdFraAAreg
                            ?.serialisertTilString(),
                ),
            )
        }
    }

    private fun List<Sporsmal>.tilSporsmalOgAndreKjenteArbeidsforhold(): SporsmalOgAndreKjenteArbeidsforhold =
        SporsmalOgAndreKjenteArbeidsforhold(
            sporsmal = this,
            andreKjenteArbeidsforhold = null,
        )

    private fun lagSykepengesoknadSporsmal(
        soknad: Sykepengesoknad,
        eksisterendeSoknader: List<Sykepengesoknad>,
        identer: FolkeregisterIdenter,
    ): SporsmalOgAndreKjenteArbeidsforhold {
        val erForsteSoknadISykeforlop = erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad)
        val erEnkeltstaendeBehandlingsdagSoknad = soknad.soknadstype == Soknadstype.BEHANDLINGSDAGER
        val harTidligereUtenlandskSpm = harBlittStiltUtlandsSporsmal(eksisterendeSoknader, soknad)
        val yrkesskadeSporsmalGrunnlag =
            yrkesskadeIndikatorer.hentYrkesskadeSporsmalGrunnlag(
                identer = identer,
                sykmeldingId = soknad.sykmeldingId,
                erForsteSoknadISykeforlop = erForsteSoknadISykeforlop,
            )

        fun tilkommenInntektGrunnlagHenting(): List<ArbeidsforholdFraAAreg>? {
            if (soknad.soknadstype != Soknadstype.ARBEIDSTAKERE) {
                return null
            }
            if (unleashToggles.tilkommenInntektEnabled(soknad.fnr)) {
                log.info("Tilkommen inntekt toggle enabled")

                val erUtenforAGP = soknad.erUtenforArbeidsgiverPeriode(eksisterendeSoknader)
                if (!erUtenforAGP) {
                    return null
                }

                return aaregDataHenting.hentNyeArbeidsforhold(
                    fnr = soknad.fnr,
                    arbeidsgiverOrgnummer = soknad.arbeidsgiverOrgnummer!!,
                    startSykeforlop = soknad.startSykeforlop!!,
                    sykepengesoknadId = soknad.id,
                    soknadTom = soknad.tom!!,
                )
            }
            return null
        }

        val soknadOptions =
            SettOppSoknadOptions(
                sykepengesoknad = soknad,
                erForsteSoknadISykeforlop = erForsteSoknadISykeforlop,
                harTidligereUtenlandskSpm = harTidligereUtenlandskSpm,
                yrkesskade = yrkesskadeSporsmalGrunnlag,
                arbeidsforholdoversiktResponse = tilkommenInntektGrunnlagHenting(),
                eksisterendeSoknader = eksisterendeSoknader,
            )

        if (erEnkeltstaendeBehandlingsdagSoknad) {
            return settOppSykepengesoknadBehandlingsdager(soknadOptions).tilSporsmalOgAndreKjenteArbeidsforhold()
        }

        if (soknad.soknadstype == Soknadstype.REISETILSKUDD) {
            return skapReisetilskuddsoknad(
                soknadOptions,
            ).tilSporsmalOgAndreKjenteArbeidsforhold()
        }

        return when (soknad.arbeidssituasjon) {
            Arbeidssituasjon.ARBEIDSTAKER -> {
                val andreKjenteArbeidsforhold =
                    arbeidsforholdFraInntektskomponentenHenting.hentArbeidsforhold(
                        fnr = soknad.fnr,
                        arbeidsgiverOrgnummer = soknad.arbeidsgiverOrgnummer!!,
                        startSykeforlop = soknad.startSykeforlop!!,
                    )

                val arbeidstakerSporsmal =
                    settOppSoknadArbeidstaker(
                        soknadOptions =
                            soknadOptions.copy(
                                medlemskapSporsmalTags = lagMedlemsskapSporsmalTags(eksisterendeSoknader, soknad),
                                // TODO: Refaktorer hentOppholdstillatelse() ut av lagMedlemsskapSporsmalTags()
                                //  sånn at vi slipper å gå til databasen rett etter vi har hentet
                                //  og lagret medlemskapsvurdering.
                                kjentOppholdstillatelse =
                                    medlemskapVurderingRepository.findBySykepengesoknadIdAndFomAndTom(
                                        sykepengesoknadId = soknad.id,
                                        // fom og tom vil ikke være tomme for Arbeidssiduasjon.ARBEIDSTAKER.
                                        fom = soknad.fom!!,
                                        tom = soknad.tom!!,
                                    )?.hentKjentOppholdstillatelse(),
                            ),
                        andreKjenteArbeidsforholdFraInntektskomponenten = andreKjenteArbeidsforhold,
                    )

                SporsmalOgAndreKjenteArbeidsforhold(
                    sporsmal = arbeidstakerSporsmal,
                    andreKjenteArbeidsforhold = andreKjenteArbeidsforhold,
                    arbeidsforholdFraAAreg = soknadOptions.arbeidsforholdoversiktResponse,
                )
            }

            else -> {
                when (soknad.arbeidssituasjon) {
                    Arbeidssituasjon.FISKER,
                    Arbeidssituasjon.JORDBRUKER,
                    Arbeidssituasjon.NAERINGSDRIVENDE,
                    Arbeidssituasjon.FRILANSER,
                    -> {
                        val sykepengegrunnlag = sykepengegrunnlagService.sykepengegrunnlagNaeringsdrivende(soknad)
                        settOppSoknadSelvstendigOgFrilanser(soknadOptions, sykepengegrunnlag)
                    }

                    Arbeidssituasjon.ARBEIDSLEDIG -> settOppSoknadArbeidsledig(soknadOptions)
                    Arbeidssituasjon.ANNET -> settOppSoknadAnnetArbeidsforhold(soknadOptions)

                    else -> {
                        throw RuntimeException(
                            "Arbeidssituasjon ${soknad.arbeidssituasjon} for sykepengesøknad ${soknad.id} er ukjent. " +
                                "Kan ikke generere spørsmål.",
                        )
                    }
                }.tilSporsmalOgAndreKjenteArbeidsforhold()
            }
        }
    }

    private fun lagMedlemsskapSporsmalTags(
        eksisterendeSoknader: List<Sykepengesoknad>,
        soknad: Sykepengesoknad,
    ): List<MedlemskapSporsmalTag> {
        // Medlemskapspørsmal skal kun stilles i én første søknad i et sykeforløp, uavhengig av arbeidsgiver.
        if (!skalHaSporsmalOmMedlemskap(eksisterendeSoknader, soknad)) {
            return emptyList()
        }

        // Stiller spørsmål om ARBEID_UTENFOR_NORGE hvis det ikke blir returnert en medlemskapvurdering fra LovMe.
        val medlemskapVurdering =
            hentMedlemskapVurdering(soknad)
                ?: return listOf(SykepengesoknadSporsmalTag.ARBEID_UTENFOR_NORGE)

        val (svar, sporsmal) = medlemskapVurdering

        return when {
            // Det skal ikke stilles medlemskapspørsmål hvis det er et avklart medlemskapsforhold.
            svar in
                listOf(
                    MedlemskapVurderingSvarType.JA,
                    MedlemskapVurderingSvarType.NEI,
                )
            -> emptyList()

            // LovMe kan returnerer UAVKLART uten tilhørende spørsmål når det ikke er et scenario de har implementert
            // på sin side. Blir også brukt hvis LovMe anser tidligere svar (på spørsmål i søkand) som fortsatt gyldige.
            // I disse tilfellene stiller vi det "gamle" spørsmålet om Arbeid Utenfor Norge.
            svar == MedlemskapVurderingSvarType.UAVKLART && sporsmal.isEmpty() -> {
                log.info("Medlemskapvurdering er UAVKLART for søknad ${soknad.id}, men LovMe returnerte ingen spørsmål.")
                listOf(SykepengesoknadSporsmalTag.ARBEID_UTENFOR_NORGE)
            }

            //  LovMe kan returnerer UAVKLART med tilhørende spørsmål.
            else -> {
                log.info("Medlemskapvurdering er UAVKLART med spørsmål for søknad ${soknad.id}.")
                sporsmal.map {
                    when (it) {
                        MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE -> LovMeSporsmalTag.OPPHOLDSTILATELSE
                        MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE -> LovMeSporsmalTag.ARBEID_UTENFOR_NORGE
                        MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_NORGE -> LovMeSporsmalTag.OPPHOLD_UTENFOR_NORGE
                        MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_EØS_OMRÅDE -> LovMeSporsmalTag.OPPHOLD_UTENFOR_EØS_OMRÅDE
                    }
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
                    sykepengesoknadId = soknad.id,
                ),
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
                    e,
                )
            }
        }.getOrNull()
    }
}
