package no.nav.helse.flex.service

import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.client.narmesteleder.Forskuttering
import no.nav.helse.flex.client.narmesteleder.NarmesteLederClient
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
import no.nav.helse.flex.forskuttering.ForskutteringRepository
import no.nav.helse.flex.juridiskvurdering.JuridiskVurdering
import no.nav.helse.flex.juridiskvurdering.JuridiskVurderingKafkaProducer
import no.nav.helse.flex.juridiskvurdering.SporingType.organisasjonsnummer
import no.nav.helse.flex.juridiskvurdering.SporingType.soknad
import no.nav.helse.flex.juridiskvurdering.SporingType.sykmelding
import no.nav.helse.flex.juridiskvurdering.Utfall
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.util.Metrikk
import no.nav.helse.flex.util.isBeforeOrEqual
import org.apache.commons.lang3.StringUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.*

@Service
@Transactional
class MottakerAvSoknadService(
    val sykepengesoknadDAO: SykepengesoknadDAO,
    val flexSyketilfelleClient: FlexSyketilfelleClient,
    val identService: IdentService,
    val narmesteLederClient: NarmesteLederClient,
    val metrikk: Metrikk,
    val juridiskVurderingKafkaProducer: JuridiskVurderingKafkaProducer,
    val forskutteringRepository: ForskutteringRepository,
) {
    val log = logger()

    fun finnMottakerAvSoknad(sykepengesoknad: Sykepengesoknad, identer: FolkeregisterIdenter): Mottaker {
        return when (sykepengesoknad.soknadstype) {
            SELVSTENDIGE_OG_FRILANSERE,
            OPPHOLD_UTLAND,
            ARBEIDSLEDIG,
            ANNET_ARBEIDSFORHOLD,
            REISETILSKUDD -> NAV

            BEHANDLINGSDAGER,
            GRADERT_REISETILSKUDD -> when (sykepengesoknad.arbeidssituasjon) {
                ARBEIDSTAKER -> mottakerAvSoknadForArbeidstaker(sykepengesoknad, identer)
                else -> NAV
            }

            ARBEIDSTAKERE -> mottakerAvSoknadForArbeidstaker(sykepengesoknad, identer)
        }
    }

    private fun mottakerAvSoknadForArbeidstaker(
        sykepengesoknad: Sykepengesoknad,
        identer: FolkeregisterIdenter
    ): Mottaker {
        val mottakerAvKorrigertSoknad = mottakerAvKorrigertSoknad(sykepengesoknad)

        if (mottakerAvKorrigertSoknad.isPresent && mottakerAvKorrigertSoknad.get() == ARBEIDSGIVER_OG_NAV) {
            return ARBEIDSGIVER_OG_NAV
        }

        val mottakerResultat = beregnMottakerAvSoknadForArbeidstakerOgBehandlingsdager(sykepengesoknad, identer)
            .also {
                it.vurdering.forEach { jv ->
                    juridiskVurderingKafkaProducer.produserMelding(jv)
                }
            }

        if (mottakerAvKorrigertSoknad.isPresent && mottakerAvKorrigertSoknad.get() != mottakerResultat.mottaker) {
            return ARBEIDSGIVER_OG_NAV
        }

        kunHelgEtterArbeidsgiverperiodeVurdering(mottakerResultat, sykepengesoknad)?.let { return it }

        return mottakerResultat.mottaker
    }

    private fun Boolean?.tilForskuttering(): Forskuttering {
        return when (this) {
            null -> Forskuttering.UKJENT
            false -> Forskuttering.NEI
            true -> Forskuttering.JA
        }
    }

    fun arbeidsgiverForskutterer(sykepengesoknad: Sykepengesoknad): Boolean {
        if (sykepengesoknad.arbeidsgiverOrgnummer == null) {
            return false
        }
        if (StringUtils.isEmpty(sykepengesoknad.arbeidsgiverOrgnummer)) {
            return false
        }

        val forskuttering = narmesteLederClient.arbeidsgiverForskutterer(
            sykmeldtFnr = sykepengesoknad.fnr,
            orgnummer = sykepengesoknad.arbeidsgiverOrgnummer
        )
        val forskutteringFraDb = forskutteringRepository.finnForskuttering(
            brukerFnr = sykepengesoknad.fnr,
            orgnummer = sykepengesoknad.arbeidsgiverOrgnummer
        )?.arbeidsgiverForskutterer.tilForskuttering()

        if (forskuttering != forskutteringFraDb) {
            log.warn("Ulik forskuttering $forskuttering != $forskutteringFraDb for ${sykepengesoknad.id}")
        }

        return listOf(Forskuttering.JA, Forskuttering.UKJENT).contains(
            forskuttering
        )
    }

    class MottakerOgVurdering(
        val mottaker: Mottaker,
        val arbeidsgiverperiode: Arbeidsgiverperiode?,
        val vurdering: List<JuridiskVurdering>
    )

    private fun beregnMottakerAvSoknadForArbeidstakerOgBehandlingsdager(
        sykepengesoknad: Sykepengesoknad,
        identer: FolkeregisterIdenter
    ): MottakerOgVurdering {

        val arbeidsgiverperiode = flexSyketilfelleClient.beregnArbeidsgiverperiode(
            sykepengesoknad,
            identer = identer,
            forelopig = sykepengesoknad.status != Soknadstatus.SENDT
        )

        if (arbeidsgiverperiode == null) {
            // Innenfor ag perioden
            return MottakerOgVurdering(
                ARBEIDSGIVER,
                null,
                listOfNotNull(skapJuridiskVurdering(Utfall.VILKAR_OPPFYLT, sykepengesoknad))
            )
        }

        val sykepengesoknadFom = sykepengesoknad.fom
        val sykepengesoknadTom = sykepengesoknad.tom
        val arbeidsgiverperiodeTom = arbeidsgiverperiode.arbeidsgiverPeriode!!.tom
        val oppbruktArbeidsgiverperiode = arbeidsgiverperiode.oppbruktArbeidsgiverperiode

        if (!oppbruktArbeidsgiverperiode || sykepengesoknadTom!!.isBeforeOrEqual(arbeidsgiverperiodeTom)) {
            // Ikke oppbrukt agperiode / innenfor ag perioden
            return MottakerOgVurdering(
                ARBEIDSGIVER,
                arbeidsgiverperiode,
                listOfNotNull(skapJuridiskVurdering(Utfall.VILKAR_IKKE_OPPFYLT, sykepengesoknad, arbeidsgiverperiode))
            )
        }

        // Oppbrukt arbeidsgiverperiode

        if (sykepengesoknadFom!!.isBeforeOrEqual(arbeidsgiverperiodeTom)) {
            // Både innenfor og utafor
            return MottakerOgVurdering(
                ARBEIDSGIVER_OG_NAV,
                arbeidsgiverperiode,
                listOfNotNull(
                    skapJuridiskVurdering(
                        Utfall.VILKAR_IKKE_OPPFYLT,
                        sykepengesoknad,
                        arbeidsgiverperiode,
                        Periode(fom = sykepengesoknadFom, tom = arbeidsgiverperiodeTom)
                    ),
                    skapJuridiskVurdering(
                        Utfall.VILKAR_OPPFYLT,
                        sykepengesoknad,
                        arbeidsgiverperiode,
                        Periode(fom = arbeidsgiverperiodeTom.plusDays(1), tom = sykepengesoknadTom)
                    ),
                )
            )
        }

        if (
            sykepengesoknad.arbeidssituasjon == ARBEIDSTAKER &&
            arbeidsgiverForskutterer(sykepengesoknad)
        ) {
            // Utenfor arbeidgiverperiode og arbeidsgiver forskutter
            return MottakerOgVurdering(
                ARBEIDSGIVER_OG_NAV,
                arbeidsgiverperiode,
                listOfNotNull(skapJuridiskVurdering(Utfall.VILKAR_OPPFYLT, sykepengesoknad, arbeidsgiverperiode))
            )
        }

        // Utenfor arbeidgiverperiode
        return MottakerOgVurdering(
            NAV,
            arbeidsgiverperiode,
            listOfNotNull(skapJuridiskVurdering(Utfall.VILKAR_OPPFYLT, sykepengesoknad, arbeidsgiverperiode))
        )
    }

    fun skapJuridiskVurdering(
        utfall: Utfall,
        sykepengesoknad: Sykepengesoknad,
        arbeidsgiverperiode: Arbeidsgiverperiode = Arbeidsgiverperiode(),
        periode: Periode? = null,
    ): JuridiskVurdering? {
        if (sykepengesoknad.status != Soknadstatus.SENDT) {
            return null
        }

        return JuridiskVurdering(
            fodselsnummer = sykepengesoknad.fnr,
            sporing = hashMapOf(soknad to listOf(sykepengesoknad.id))
                .also { map ->
                    sykepengesoknad.sykmeldingId?.let {
                        map[sykmelding] = listOf(it)
                    }
                    sykepengesoknad.arbeidsgiverOrgnummer?.let {
                        map[organisasjonsnummer] = listOf(it)
                    }
                },
            input = hashMapOf<String, Any>(
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
            output = mapOf(
                "periode" to (periode ?: Periode(fom = sykepengesoknad.fom!!, tom = sykepengesoknad.tom!!)),
                "versjon" to LocalDate.of(2022, 2, 1),
            ),
            lovverk = "folketrygdloven",
            paragraf = "8-17",
            ledd = 1,
            bokstav = "a",
            punktum = null,
            lovverksversjon = LocalDate.of(2018, 1, 1),
            utfall = utfall
        )
    }

    private fun mottakerAvKorrigertSoknad(sykepengesoknad: Sykepengesoknad): Optional<Mottaker> {
        return Optional.ofNullable(sykepengesoknad.korrigerer)
            .map { korrigertSoknadUuid ->
                sykepengesoknadDAO.finnMottakerAvSoknad(korrigertSoknadUuid)
                    .orElseThrow {
                        log.error("Finner ikke mottaker for en korrigert søknad")
                        RuntimeException("Finner ikke mottaker for en korrigert søknad")
                    }
            }
    }
}
