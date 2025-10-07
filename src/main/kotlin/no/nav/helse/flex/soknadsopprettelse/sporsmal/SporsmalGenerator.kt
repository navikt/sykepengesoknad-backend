package no.nav.helse.flex.soknadsopprettelse.sporsmal

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.arbeidsgiverperiode.harDagerNAVSkalBetaleFor
import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.domain.*
import no.nav.helse.flex.domain.Arbeidssituasjon.*
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.frisktilarbeid.FriskTilArbeidRepository
import no.nav.helse.flex.frisktilarbeid.tilPeriode
import no.nav.helse.flex.logger
import no.nav.helse.flex.medlemskap.KjentOppholdstillatelse
import no.nav.helse.flex.medlemskap.MedlemskapVurderingClient
import no.nav.helse.flex.medlemskap.MedlemskapVurderingRequest
import no.nav.helse.flex.medlemskap.MedlemskapVurderingResponse
import no.nav.helse.flex.medlemskap.MedlemskapVurderingSporsmal
import no.nav.helse.flex.medlemskap.MedlemskapVurderingSvarType
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadDbRecord
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.service.SykepengegrunnlagForNaeringsdrivende
import no.nav.helse.flex.service.SykepengegrunnlagNaeringsdrivende
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.aaregdata.AaregDataHenting
import no.nav.helse.flex.soknadsopprettelse.aaregdata.ArbeidsforholdFraAAreg
import no.nav.helse.flex.soknadsopprettelse.frisktilarbeid.settOppSykepengesoknadFriskmeldtTilArbeidsformidling
import no.nav.helse.flex.unleash.UnleashToggles
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString
import no.nav.helse.flex.yrkesskade.YrkesskadeIndikatorer
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.optionals.getOrElse

@Service
@Transactional
class SporsmalGenerator(
    private val identService: IdentService,
    private val arbeidsforholdFraInntektskomponentenHenting: ArbeidsforholdFraInntektskomponentenHenting,
    private val sykepengegrunnlagForNaeringsdrivende: SykepengegrunnlagForNaeringsdrivende,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val yrkesskadeIndikatorer: YrkesskadeIndikatorer,
    private val medlemskapVurderingClient: MedlemskapVurderingClient,
    private val environmentToggles: EnvironmentToggles,
    private val unleashToggles: UnleashToggles,
    private val friskTilArbeidRepository: FriskTilArbeidRepository,
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
        val sykepengegrunnlag =
            if (listOf(BARNEPASSER, FISKER, JORDBRUKER, NAERINGSDRIVENDE).contains(soknad.arbeidssituasjon)) {
                sykepengegrunnlagForNaeringsdrivende.beregnSykepengegrunnlag(soknad)
            } else {
                null
            }

        val sporsmalOgAndreKjenteArbeidsforhold =
            lagSykepengesoknadSporsmal(
                soknad = soknad,
                eksisterendeSoknader = eksisterendeSoknader,
                identer = identer,
                sykepengegrunnlag = sykepengegrunnlag,
            )
        sykepengesoknadDAO.byttUtSporsmal(soknad.copy(sporsmal = sporsmalOgAndreKjenteArbeidsforhold.sporsmal))

        sykepengesoknadRepository.findBySykepengesoknadUuid(id)?.let {
            sykepengesoknadRepository.save(
                it
                    .leggTilSykepengegrunnlagNaringsdrivende(sykepengegrunnlag)
                    .copy(
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

    private fun SykepengesoknadDbRecord.leggTilSykepengegrunnlagNaringsdrivende(
        sykepengegrunnlag: SykepengegrunnlagNaeringsdrivende? = null,
    ): SykepengesoknadDbRecord {
        if (!unleashToggles.sigrunPaaKafkaEnabled(this.fnr)) {
            return this
        }
        val selvstendigNaringsdrivendeInfo: SelvstendigNaringsdrivendeInfo? =
            this.selvstendigNaringsdrivende?.let { naringsdrivendeString ->
                objectMapper.readValue(naringsdrivendeString)
            }
        return this.copy(
            selvstendigNaringsdrivende =
                selvstendigNaringsdrivendeInfo
                    ?.copy(
                        sykepengegrunnlagNaeringsdrivende = sykepengegrunnlag,
                    )?.serialisertTilString(),
        )
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
        sykepengegrunnlag: SykepengegrunnlagNaeringsdrivende? = null,
    ): SporsmalOgAndreKjenteArbeidsforhold {
        val erForsteSoknadISykeforlop = erForsteSoknadTilArbeidsgiverIForlop(eksisterendeSoknader, soknad)
        val erEnkeltstaendeBehandlingsdagSoknad = soknad.soknadstype == Soknadstype.BEHANDLINGSDAGER
        val harTidligereUtenlandskSpm = harBlittStiltUtlandsSporsmal(eksisterendeSoknader, soknad)
        val yrkesskadeSporsmalGrunnlag =
            yrkesskadeIndikatorer.hentYrkesskadeSporsmalGrunnlag(
                soknad = soknad,
                identer = identer,
                sykmeldingId = soknad.sykmeldingId,
                erForsteSoknadISykeforlop = erForsteSoknadISykeforlop,
            )

        fun tilkommenInntektGrunnlagHenting(): List<ArbeidsforholdFraAAreg>? {
            if (soknad.soknadstype != Soknadstype.ARBEIDSTAKERE) {
                return null
            }
            if (unleashToggles.tilkommenInntektEnabled(soknad.fnr)) {
                log.info("Tilkommen inntekt toggle enabled for soknad ${soknad.id}")

                return if (soknad.harDagerNAVSkalBetaleFor(eksisterendeSoknader)) {
                    log.info("Leter etter nye arbeidsforhold for soknad ${soknad.id} siden søknaden er estimert utenfor agp")
                    aaregDataHenting.hentNyeArbeidsforhold(
                        fnr = soknad.fnr,
                        sykepengesoknad = soknad,
                        eksisterendeSoknader = eksisterendeSoknader,
                    )
                } else {
                    log.info(
                        "Leter ikke etter nye arbeidsforhold for person med soknad ${soknad.id} siden søknaden " +
                            "er estimert helt innenfor agp",
                    )
                    null
                }
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
        if (soknad.soknadstype == Soknadstype.FRISKMELDT_TIL_ARBEIDSFORMIDLING) {
            val vedtakId =
                soknad.friskTilArbeidVedtakId
                    ?: throw RuntimeException("Frisk til arbeid vedtak id mangler for søknad ${soknad.id}")
            val friskTilArbeidVedtak =
                friskTilArbeidRepository
                    .findById(vedtakId)
                    .getOrElse { throw RuntimeException("Fant ikke frisk til arbeid vedtak med id $vedtakId") }
            return settOppSykepengesoknadFriskmeldtTilArbeidsformidling(
                soknadOptions,
                friskTilArbeidVedtak.tilPeriode(),
            ).tilSporsmalOgAndreKjenteArbeidsforhold()
        }

        return when (soknad.arbeidssituasjon) {
            ARBEIDSTAKER -> {
                val andreKjenteArbeidsforhold =
                    arbeidsforholdFraInntektskomponentenHenting.hentArbeidsforhold(
                        fnr = soknad.fnr,
                        arbeidsgiverOrgnummer = soknad.arbeidsgiverOrgnummer!!,
                        startSykeforlop = soknad.startSykeforlop!!,
                    )

                val medlemskapSporsmalResultat = lagMedlemsskapSporsmalResultat(eksisterendeSoknader, soknad)
                val arbeidstakerSporsmal =
                    settOppSoknadArbeidstaker(
                        soknadOptions =
                            soknadOptions.copy(
                                medlemskapSporsmalTags = medlemskapSporsmalResultat?.medlemskapSporsmalTags,
                                kjentOppholdstillatelse = medlemskapSporsmalResultat?.kjentOppholdstillatelse,
                            ),
                        andreKjenteArbeidsforholdFraInntektskomponenten = andreKjenteArbeidsforhold,
                    )
                if (arbeidstakerSporsmal.any { it.tag.startsWith(NYTT_ARBEIDSFORHOLD_UNDERVEIS) }) {
                    log.info("Skapte tilkommen inntekt spørsmål for søknad ${soknad.id}")
                }
                SporsmalOgAndreKjenteArbeidsforhold(
                    sporsmal = arbeidstakerSporsmal,
                    andreKjenteArbeidsforhold = andreKjenteArbeidsforhold,
                    arbeidsforholdFraAAreg = soknadOptions.arbeidsforholdoversiktResponse,
                )
            }

            else -> {
                when (soknad.arbeidssituasjon) {
                    FISKER,
                    JORDBRUKER,
                    BARNEPASSER,
                    NAERINGSDRIVENDE,
                    FRILANSER,
                    -> {
                        settOppSoknadSelvstendigOgFrilanser(soknadOptions, sykepengegrunnlag)
                    }

                    ARBEIDSLEDIG -> settOppSoknadArbeidsledig(soknadOptions)
                    ANNET -> settOppSoknadAnnetArbeidsforhold(soknadOptions)

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

    private fun lagMedlemsskapSporsmalResultat(
        eksisterendeSoknader: List<Sykepengesoknad>,
        soknad: Sykepengesoknad,
    ): MedlemskapSporsmalResultat? {
        // Medlemskapspørsmål skal kun stilles i én første søknad i et sykeforløp, uavhengig av arbeidsgiver.
        if (!skalHaSporsmalOmMedlemskap(eksisterendeSoknader, soknad)) {
            return MedlemskapSporsmalResultat(medlemskapSporsmalTags = emptyList())
        }

        // Stiller spørsmål om ARBEID_UTENFOR_NORGE hvis det ikke blir returnert en medlemskapvurdering fra LovMe.
        val medlemskapVurderingResponse =
            hentMedlemskapVurdering(soknad)
                ?: return MedlemskapSporsmalResultat(medlemskapSporsmalTags = listOf(SykepengesoknadSporsmalTag.ARBEID_UTENFOR_NORGE))

        val (svar, sporsmal, kjentOppholdstillatelse) = medlemskapVurderingResponse

        val medlemskapSporsmalTags =
            when {
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

        return MedlemskapSporsmalResultat(
            medlemskapSporsmalTags = medlemskapSporsmalTags,
            kjentOppholdstillatelse = kjentOppholdstillatelse,
        )
    }

    private fun hentMedlemskapVurdering(soknad: Sykepengesoknad): MedlemskapVurderingResponse? =
        runCatching {
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

data class MedlemskapSporsmalResultat(
    val medlemskapSporsmalTags: List<MedlemskapSporsmalTag>,
    val kjentOppholdstillatelse: KjentOppholdstillatelse? = null,
)
