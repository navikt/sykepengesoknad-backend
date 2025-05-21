package no.nav.helse.flex.service

import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.domain.Arbeidsgiverperiode
import no.nav.helse.flex.domain.Arbeidssituasjon.ARBEIDSTAKER
import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.Mottaker.ARBEIDSGIVER
import no.nav.helse.flex.domain.Mottaker.ARBEIDSGIVER_OG_NAV
import no.nav.helse.flex.domain.Mottaker.NAV
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype.*
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.forskuttering.ForskutteringRepository
import no.nav.helse.flex.juridiskvurdering.JuridiskVurdering
import no.nav.helse.flex.juridiskvurdering.JuridiskVurderingKafkaProducer
import no.nav.helse.flex.juridiskvurdering.SporingType.ORGANISASJONSNUMMER
import no.nav.helse.flex.juridiskvurdering.SporingType.SOKNAD
import no.nav.helse.flex.juridiskvurdering.SporingType.SYKMELDING
import no.nav.helse.flex.juridiskvurdering.Utfall
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.util.isBeforeOrEqual
import org.apache.commons.lang3.StringUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(rollbackFor = [Throwable::class])
class MottakerAvSoknadService(
    val sykepengesoknadDAO: SykepengesoknadDAO,
    val flexSyketilfelleClient: FlexSyketilfelleClient,
    val identService: IdentService,
    val juridiskVurderingKafkaProducer: JuridiskVurderingKafkaProducer,
    val forskutteringRepository: ForskutteringRepository,
) {
    val log = logger()

    fun finnMottakerAvSoknad(
        sykepengesoknad: Sykepengesoknad,
        identer: FolkeregisterIdenter,
        sykmelding: SykmeldingKafkaMessage? = null,
    ): Mottaker =
        when (sykepengesoknad.soknadstype) {
            SELVSTENDIGE_OG_FRILANSERE,
            OPPHOLD_UTLAND,
            ARBEIDSLEDIG,
            ANNET_ARBEIDSFORHOLD,
            REISETILSKUDD,
            FRISKMELDT_TIL_ARBEIDSFORMIDLING,
            -> NAV

            BEHANDLINGSDAGER,
            GRADERT_REISETILSKUDD,
            ->
                when (sykepengesoknad.arbeidssituasjon) {
                    ARBEIDSTAKER -> mottakerAvSoknadForArbeidstaker(sykepengesoknad, identer, null)
                    else -> NAV
                }

            ARBEIDSTAKERE -> mottakerAvSoknadForArbeidstaker(sykepengesoknad, identer, sykmelding)
        }

    private fun mottakerAvSoknadForArbeidstaker(
        sykepengesoknad: Sykepengesoknad,
        identer: FolkeregisterIdenter,
        sykmelding: SykmeldingKafkaMessage?,
    ): Mottaker {
        val mottakerAvKorrigertSoknad = mottakerAvKorrigertSoknad(sykepengesoknad)

        if (mottakerAvKorrigertSoknad == ARBEIDSGIVER_OG_NAV) {
            return ARBEIDSGIVER_OG_NAV
        }

        val mottakerResultat =
            beregnMottakerAvSoknadForArbeidstakerOgBehandlingsdager(sykepengesoknad, identer, sykmelding)
                .also {
                    it.vurdering.forEach { jv ->
                        juridiskVurderingKafkaProducer.produserMelding(jv)
                    }
                }

        if (mottakerAvKorrigertSoknad != null && mottakerAvKorrigertSoknad != mottakerResultat.mottaker) {
            return ARBEIDSGIVER_OG_NAV
        }

        kunHelgEtterArbeidsgiverperiodeVurdering(mottakerResultat, sykepengesoknad)?.let { return it }

        return mottakerResultat.mottaker
    }

    fun arbeidsgiverForskutterer(sykepengesoknad: Sykepengesoknad): Boolean {
        if (sykepengesoknad.arbeidsgiverOrgnummer == null) {
            return false
        }
        if (StringUtils.isEmpty(sykepengesoknad.arbeidsgiverOrgnummer)) {
            return false
        }

        val forskuttering =
            forskutteringRepository
                .finnForskuttering(
                    brukerFnr = sykepengesoknad.fnr,
                    orgnummer = sykepengesoknad.arbeidsgiverOrgnummer,
                )?.arbeidsgiverForskutterer

        if (forskuttering == null) {
            // Ukjent telles som forskuttering
            return true
        }
        return forskuttering
    }

    class MottakerOgVurdering(
        val mottaker: Mottaker,
        val arbeidsgiverperiode: Arbeidsgiverperiode?,
        val vurdering: List<JuridiskVurdering>,
    )

    private fun beregnMottakerAvSoknadForArbeidstakerOgBehandlingsdager(
        sykepengesoknad: Sykepengesoknad,
        identer: FolkeregisterIdenter,
        sykmelding: SykmeldingKafkaMessage?,
    ): MottakerOgVurdering {
        val arbeidsgiverperiode =
            flexSyketilfelleClient.beregnArbeidsgiverperiode(
                soknad = sykepengesoknad,
                sykmelding = sykmelding,
                identer = identer,
                forelopig = sykepengesoknad.status != Soknadstatus.SENDT,
            )

        if (arbeidsgiverperiode == null) {
            // Innenfor ag perioden
            return MottakerOgVurdering(
                mottaker = ARBEIDSGIVER,
                arbeidsgiverperiode = null,
                vurdering =
                    listOfNotNull(
                        skapJuridiskVurderingDagerArbeidsgiver(
                            utfall = Utfall.VILKAR_OPPFYLT,
                            sykepengesoknad = sykepengesoknad,
                        ),
                        skapJuridiskVurderingNavBetaler(
                            utfall = Utfall.VILKAR_IKKE_OPPFYLT,
                            sykepengesoknad = sykepengesoknad,
                        ),
                    ),
            )
        }

        val sykepengesoknadFom = sykepengesoknad.fom
        val sykepengesoknadTom = sykepengesoknad.tom
        val arbeidsgiverperiodeTom = arbeidsgiverperiode.arbeidsgiverPeriode!!.tom
        val oppbruktArbeidsgiverperiode = arbeidsgiverperiode.oppbruktArbeidsgiverperiode

        if (!oppbruktArbeidsgiverperiode || sykepengesoknadTom!!.isBeforeOrEqual(arbeidsgiverperiodeTom)) {
            // Ikke oppbrukt agperiode / innenfor ag perioden
            return MottakerOgVurdering(
                mottaker = ARBEIDSGIVER,
                arbeidsgiverperiode = arbeidsgiverperiode,
                vurdering =
                    listOfNotNull(
                        skapJuridiskVurderingDagerArbeidsgiver(
                            utfall = Utfall.VILKAR_IKKE_OPPFYLT,
                            sykepengesoknad = sykepengesoknad,
                            arbeidsgiverperiode = arbeidsgiverperiode,
                        ),
                        skapJuridiskVurderingNavBetaler(
                            utfall = Utfall.VILKAR_OPPFYLT,
                            sykepengesoknad = sykepengesoknad,
                            arbeidsgiverperiode = arbeidsgiverperiode,
                        ),
                    ),
            )
        }

        // Oppbrukt arbeidsgiverperiode

        if (sykepengesoknadFom!!.isBeforeOrEqual(arbeidsgiverperiodeTom)) {
            // Både innenfor og utafor
            return MottakerOgVurdering(
                mottaker = ARBEIDSGIVER_OG_NAV,
                arbeidsgiverperiode = arbeidsgiverperiode,
                vurdering =
                    listOfNotNull(
                        skapJuridiskVurderingNavBetaler(
                            utfall = Utfall.VILKAR_OPPFYLT,
                            sykepengesoknad = sykepengesoknad,
                            arbeidsgiverperiode = arbeidsgiverperiode,
                            periode = Periode(fom = sykepengesoknadFom, tom = arbeidsgiverperiodeTom),
                        ),
                        skapJuridiskVurderingDagerArbeidsgiver(
                            utfall = Utfall.VILKAR_IKKE_OPPFYLT,
                            sykepengesoknad = sykepengesoknad,
                            arbeidsgiverperiode = arbeidsgiverperiode,
                            periode = Periode(fom = sykepengesoknadFom, tom = arbeidsgiverperiodeTom),
                        ),
                        skapJuridiskVurderingNavBetaler(
                            utfall = Utfall.VILKAR_IKKE_OPPFYLT,
                            sykepengesoknad = sykepengesoknad,
                            arbeidsgiverperiode = arbeidsgiverperiode,
                            periode = Periode(fom = arbeidsgiverperiodeTom.plusDays(1), tom = sykepengesoknadTom),
                        ),
                        skapJuridiskVurderingDagerArbeidsgiver(
                            utfall = Utfall.VILKAR_OPPFYLT,
                            sykepengesoknad = sykepengesoknad,
                            arbeidsgiverperiode = arbeidsgiverperiode,
                            periode = Periode(fom = arbeidsgiverperiodeTom.plusDays(1), tom = sykepengesoknadTom),
                        ),
                    ),
            )
        }

        if (
            sykepengesoknad.arbeidssituasjon == ARBEIDSTAKER &&
            arbeidsgiverForskutterer(sykepengesoknad)
        ) {
            // Utenfor arbeidgiverperiode og arbeidsgiver forskutter
            return MottakerOgVurdering(
                mottaker = ARBEIDSGIVER_OG_NAV,
                arbeidsgiverperiode = arbeidsgiverperiode,
                vurdering =
                    listOfNotNull(
                        skapJuridiskVurderingDagerArbeidsgiver(
                            utfall = Utfall.VILKAR_OPPFYLT,
                            sykepengesoknad = sykepengesoknad,
                            arbeidsgiverperiode = arbeidsgiverperiode,
                        ),
                        skapJuridiskVurderingNavBetaler(
                            utfall = Utfall.VILKAR_IKKE_OPPFYLT,
                            sykepengesoknad = sykepengesoknad,
                            arbeidsgiverperiode = arbeidsgiverperiode,
                        ),
                    ),
            )
        }

        // Utenfor arbeidgiverperiode
        return MottakerOgVurdering(
            mottaker = NAV,
            arbeidsgiverperiode = arbeidsgiverperiode,
            vurdering =
                listOfNotNull(
                    skapJuridiskVurderingNavBetaler(
                        utfall = Utfall.VILKAR_OPPFYLT,
                        sykepengesoknad = sykepengesoknad,
                        arbeidsgiverperiode = arbeidsgiverperiode,
                    ),
                    skapJuridiskVurderingDagerArbeidsgiver(
                        utfall = Utfall.VILKAR_IKKE_OPPFYLT,
                        sykepengesoknad = sykepengesoknad,
                        arbeidsgiverperiode = arbeidsgiverperiode,
                    ),
                ),
        )
    }

    fun skapJuridiskVurderingDagerArbeidsgiver(
        utfall: Utfall,
        sykepengesoknad: Sykepengesoknad,
        arbeidsgiverperiode: Arbeidsgiverperiode = Arbeidsgiverperiode(),
        periode: Periode? = null,
    ): JuridiskVurdering? =
        skapJuridiskVurdering(
            utfall = utfall,
            sykepengesoknad = sykepengesoknad,
            arbeidsgiverperiode = arbeidsgiverperiode,
            periode = periode,
            paragraf = "8-17",
            ledd = 1,
            bokstav = "a",
            punktum = null,
        )

    fun skapJuridiskVurderingNavBetaler(
        utfall: Utfall,
        sykepengesoknad: Sykepengesoknad,
        arbeidsgiverperiode: Arbeidsgiverperiode = Arbeidsgiverperiode(),
        periode: Periode? = null,
    ): JuridiskVurdering? =
        skapJuridiskVurdering(
            utfall = utfall,
            sykepengesoknad = sykepengesoknad,
            arbeidsgiverperiode = arbeidsgiverperiode,
            periode = periode,
            paragraf = "8-17",
            ledd = 1,
            bokstav = "b",
            punktum = null,
        )

    fun skapJuridiskVurdering(
        utfall: Utfall,
        sykepengesoknad: Sykepengesoknad,
        arbeidsgiverperiode: Arbeidsgiverperiode,
        paragraf: String,
        periode: Periode? = null,
        ledd: Int? = null,
        bokstav: String? = null,
        punktum: Int? = null,
    ): JuridiskVurdering? {
        if (sykepengesoknad.status != Soknadstatus.SENDT) {
            return null
        }

        return JuridiskVurdering(
            fodselsnummer = sykepengesoknad.fnr,
            sporing =
                hashMapOf(SOKNAD to listOf(sykepengesoknad.id))
                    .also { map ->
                        sykepengesoknad.sykmeldingId?.let {
                            map[SYKMELDING] = listOf(it)
                        }
                        sykepengesoknad.arbeidsgiverOrgnummer?.let {
                            map[ORGANISASJONSNUMMER] = listOf(it)
                        }
                    },
            input =
                hashMapOf<String, Any>(
                    "oppbruktArbeidsgiverperiode" to arbeidsgiverperiode.oppbruktArbeidsgiverperiode,
                    "versjon" to LocalDate.of(2022, 2, 1),
                ).also { map ->
                    sykepengesoknad.tom?.let {
                        map["sykepengesoknadTom"] = it
                    }
                    sykepengesoknad.fom?.let {
                        map["sykepengesoknadFom"] = it
                    }
                    arbeidsgiverperiode.arbeidsgiverPeriode?.let {
                        map["arbeidsgiverperiode"] = it
                    }
                },
            output =
                mapOf(
                    "periode" to (periode ?: Periode(fom = sykepengesoknad.fom!!, tom = sykepengesoknad.tom!!)),
                    "versjon" to LocalDate.of(2022, 2, 1),
                ),
            lovverk = "folketrygdloven",
            paragraf = paragraf,
            ledd = ledd,
            bokstav = bokstav,
            punktum = punktum,
            lovverksversjon = LocalDate.of(2018, 1, 1),
            utfall = utfall,
        )
    }

    private fun mottakerAvKorrigertSoknad(sykepengesoknad: Sykepengesoknad): Mottaker? =
        sykepengesoknad.korrigerer?.let { korrigertSoknadUuid ->
            sykepengesoknadDAO.finnMottakerAvSoknad(korrigertSoknadUuid) ?: run {
                log.error("Finner ikke mottaker for en korrigert søknad")
                throw RuntimeException("Finner ikke mottaker for en korrigert søknad")
            }
        }
}
