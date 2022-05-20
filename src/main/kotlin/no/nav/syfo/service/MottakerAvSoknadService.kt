package no.nav.syfo.service

import no.nav.syfo.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.syfo.client.narmesteleder.Forskuttering
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.domain.Arbeidsgiverperiode
import no.nav.syfo.domain.Arbeidssituasjon.ARBEIDSTAKER
import no.nav.syfo.domain.Mottaker
import no.nav.syfo.domain.Mottaker.ARBEIDSGIVER
import no.nav.syfo.domain.Mottaker.ARBEIDSGIVER_OG_NAV
import no.nav.syfo.domain.Mottaker.NAV
import no.nav.syfo.domain.Periode
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Soknadstype.*
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.juridiskvurdering.JuridiskVurdering
import no.nav.syfo.juridiskvurdering.JuridiskVurderingKafkaProducer
import no.nav.syfo.juridiskvurdering.SporingType.organisasjonsnummer
import no.nav.syfo.juridiskvurdering.SporingType.soknad
import no.nav.syfo.juridiskvurdering.SporingType.sykmelding
import no.nav.syfo.juridiskvurdering.Utfall
import no.nav.syfo.logger
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.util.Metrikk
import no.nav.syfo.util.isBeforeOrEqual
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

    private fun mottakerAvSoknadForArbeidstaker(sykepengesoknad: Sykepengesoknad, identer: FolkeregisterIdenter): Mottaker {
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

    fun arbeidsgiverForskutterer(arbeidsgiverOrgnummer: String, fnr: String): Boolean {
        if (StringUtils.isEmpty(arbeidsgiverOrgnummer)) {
            return false
        }

        val forskuttering = narmesteLederClient.arbeidsgiverForskutterer(
            sykmeldtFnr = fnr,
            orgnummer = arbeidsgiverOrgnummer
        )
        return listOf(Forskuttering.JA, Forskuttering.UKJENT).contains(
            forskuttering
        )
    }

    class MottakerOgVurdering(
        val mottaker: Mottaker,
        val arbeidsgiverperiode: Arbeidsgiverperiode?,
        val vurdering: List<JuridiskVurdering>
    )

    private fun beregnMottakerAvSoknadForArbeidstakerOgBehandlingsdager(sykepengesoknad: Sykepengesoknad, identer: FolkeregisterIdenter): MottakerOgVurdering {

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
            arbeidsgiverForskutterer(
                arbeidsgiverOrgnummer = sykepengesoknad.arbeidsgiverOrgnummer!!,
                fnr = sykepengesoknad.fnr
            )
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
