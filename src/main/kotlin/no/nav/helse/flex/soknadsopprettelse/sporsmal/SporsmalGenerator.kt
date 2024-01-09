package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.logger
import no.nav.helse.flex.medlemskap.MedlemskapVurderingClient
import no.nav.helse.flex.medlemskap.MedlemskapVurderingRequest
import no.nav.helse.flex.medlemskap.MedlemskapVurderingResponse
import no.nav.helse.flex.medlemskap.MedlemskapVurderingSporsmal
import no.nav.helse.flex.medlemskap.MedlemskapVurderingSvarType
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.unleash.UnleashToggles
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
    private val medlemskapVurderingClient: MedlemskapVurderingClient,
    private val environmentToggles: EnvironmentToggles,
    private val unleashToggles: UnleashToggles,
) {
    private val log = logger()

    data class SporsmalOgAndreKjenteArbeidsforhold(
        val sporsmal: List<Sporsmal>,
        val andreKjenteArbeidsforhold: List<ArbeidsforholdFraInntektskomponenten>? = null,
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

        sporsmalOgAndreKjenteArbeidsforhold.andreKjenteArbeidsforhold?.let {
            sykepengesoknadDAO.lagreInntektskilderDataFraInntektskomponenten(soknad.id, it)
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
        val nyttTilSluttSpmToggle = unleashToggles.nyttTilSluttSporsmal(soknad.fnr)
        val yrkesskadeSporsmalGrunnlag =
            yrkesskadeIndikatorer.hentYrkesskadeSporsmalGrunnlag(
                identer = identer,
                sykmeldingId = soknad.sykmeldingId,
                erForsteSoknadISykeforlop = erForsteSoknadISykeforlop,
            )

        val soknadOptions =
            SettOppSoknadOptions(
                sykepengesoknad = soknad,
                erForsteSoknadISykeforlop = erForsteSoknadISykeforlop,
                harTidligereUtenlandskSpm = harTidligereUtenlandskSpm,
                yrkesskade = yrkesskadeSporsmalGrunnlag,
                kjenteInntektskilderEnabled = unleashToggles.stillKjenteInntektskilderSporsmal(soknad.fnr),
                naringsdrivendeInntektsopplysningerEnabled = unleashToggles.naringsdrivendeInntektsopplysninger(soknad.fnr),
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
                    andreArbeidsforholdHenting.hentArbeidsforhold(
                        fnr = soknad.fnr,
                        arbeidsgiverOrgnummer = soknad.arbeidsgiverOrgnummer!!,
                        startSykeforlop = soknad.startSykeforlop!!,
                    )

                val arbeidstakerSporsmal =
                    settOppSoknadArbeidstaker(
                        soknadOptions =
                            soknadOptions.copy(
                                medlemskapSporsmalTags = lagMedlemsskapSporsmalTags(eksisterendeSoknader, soknad),
                            ),
                        andreKjenteArbeidsforhold = andreKjenteArbeidsforhold.map { it.navn },
                    )

                SporsmalOgAndreKjenteArbeidsforhold(
                    sporsmal = arbeidstakerSporsmal,
                    andreKjenteArbeidsforhold = andreKjenteArbeidsforhold,
                )
            }

            else -> {
                when (soknad.arbeidssituasjon) {
                    Arbeidssituasjon.NAERINGSDRIVENDE,
                    Arbeidssituasjon.FRILANSER,
                    -> settOppSoknadSelvstendigOgFrilanser(soknadOptions)

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
        // Medlemskapspørsmal skal kun stilles i den første søknaden i et sykeforløp, uavhengig av arbeidsgiver.
        if (!erForsteSoknadIForlop(eksisterendeSoknader, soknad)) {
            return emptyList()
        }

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

            // TODO: Fjern denne når toggle er slått på for alle brukere.
            !unleashToggles.stillMedlemskapSporsmal(soknad.fnr) -> {
                log.info(
                    "Medlemskapvurdering er UAVKLART for søknad ${soknad.id}, men medlemskapToggle svarte " +
                        "'false' så det stilles ingen spørsmål i søknaden .",
                )
                listOf(SykepengesoknadSporsmalTag.ARBEID_UTENFOR_NORGE)
            }

            else -> {
                log.info(
                    "Medlemskapvurdering er UAVKLART for søknad ${soknad.id}, medlemskapToggle svarte 'true' så det " +
                        "stilles spørsmål i søknaden .",
                )
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
