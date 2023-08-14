package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.logger
import no.nav.helse.flex.medlemskap.MedlemskapVurderingClient
import no.nav.helse.flex.medlemskap.MedlemskapVurderingRequest
import no.nav.helse.flex.medlemskap.MedlemskapVurderingSporsmal
import no.nav.helse.flex.medlemskap.MedlemskapVurderingSvarType
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmArbeidUtenforNorge
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmOppholdstillatelse
import no.nav.helse.flex.yrkesskade.YrkesskadeIndikatorer
import no.nav.helse.flex.yrkesskade.YrkesskadeSporsmalGrunnlag
import org.springframework.beans.factory.annotation.Value
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

    @Value("\${ENABLE_MEDLEMSKAP}") var stillMedlemskapSporsmal: Boolean
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
                val medlemskapSporsmal = lagMedlemskapSporsmal(eksisterendeSoknader, soknad)

                SporsmalOgAndreKjenteArbeidsforhold(
                    sporsmal = arbeidstakerSporsmal + medlemskapSporsmal,
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
    ): MutableList<Sporsmal> {
        val medlemskapSporsmal = mutableListOf<Sporsmal>()

        if (!harBlittStiltMedlemskapSporsmal(eksisterendeSoknader, soknad)) {
            val medlemskapVurdering = medlemskapVurderingClient.hentMedlemskapVurdering(
                MedlemskapVurderingRequest(
                    // Bruker 'fnr' fra sykepengesøknaden, ikke liste over identer siden det ikke støttes av LovMe enda.
                    fnr = soknad.fnr,
                    fom = soknad.fom!!,
                    tom = soknad.tom!!,
                    sykepengesoknadId = soknad.id
                )
            )

            log.info("Hentet medlemskapvurdering for søknad ${soknad.id} med svar ${medlemskapVurdering.svar}.")
            if (medlemskapVurdering.svar == MedlemskapVurderingSvarType.UAVKLART) {
                // TODO: Fjern feature-toggle når all funksjonallitet er implementert.
                if (stillMedlemskapSporsmal) {
                    medlemskapVurdering.sporsmal.forEach {
                        when (it) {
                            MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE -> medlemskapSporsmal.add(
                                lagSporsmalOmOppholdstillatelse()
                            )

                            MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE -> medlemskapSporsmal.add(
                                lagSporsmalOmArbeidUtenforNorge()
                            )
                            // TODO: Implementer resterende spørsmål.
                            else -> {
                                log.warn("Ikke implementert medlemskapsspørsmål ${it.name}. Lager ikke brukerspørsmål.")
                            }
                        }
                    }
                } else {
                    log.info(
                        "Medlemskapvurdering er UAVKLART for søknad ${soknad.id}, men stiller ikke spørsmål i PROD."
                    )
                }
            }
        }
        // Det skal bare gjøres medlemskapvurdering en gang per sykeforløp for arbeidstakersøknader.
        return medlemskapSporsmal
    }
}

data class SettOppSoknadOpts(
    val sykepengesoknad: Sykepengesoknad,
    val erForsteSoknadISykeforlop: Boolean,
    val harTidligereUtenlandskSpm: Boolean,
    val yrkesskade: YrkesskadeSporsmalGrunnlag
)
