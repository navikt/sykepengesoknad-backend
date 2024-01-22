package no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger

import no.nav.helse.flex.aktivering.kafka.AktiveringBestilling
import no.nav.helse.flex.aktivering.kafka.AktiveringProducer
import no.nav.helse.flex.domain.Opprinnelse
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.KlippVariant
import no.nav.helse.flex.repository.KlippetSykepengesoknadDbRecord
import no.nav.helse.flex.repository.KlippetSykepengesoknadRepository
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.sporsmal.SporsmalGenerator
import no.nav.helse.flex.util.serialisertTilString
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.*

@Component
@Transactional(rollbackFor = [Throwable::class])
class Soknadsklipper(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val aktiveringProducer: AktiveringProducer,
    private val soknadProducer: SoknadProducer,
    private val klippetSykepengesoknadRepository: KlippetSykepengesoknadRepository,
    private val sporsmalGenerator: SporsmalGenerator,
) {
    val log = logger()

    fun klippSoknaderSomOverlapperEtter(
        sykmeldingId: String,
        sok: Sykepengesoknad,
        sykmeldingPeriode: ClosedRange<LocalDate>,
    ) {
        log.info(
            "Sykmelding $sykmeldingId klipper søknad ${sok.id} tom fra: ${sok.tom} til: ${
                sykmeldingPeriode.start.minusDays(1)
            }",
        )

        val nyePerioder =
            sykepengesoknadDAO.klippSoknadTom(
                sykepengesoknadUuid = sok.id,
                nyTom = sykmeldingPeriode.start.minusDays(1),
                tom = sok.tom!!,
                fom = sok.fom!!,
            )

        klippetSykepengesoknadRepository.save(
            KlippetSykepengesoknadDbRecord(
                sykepengesoknadUuid = sok.id,
                sykmeldingUuid = sykmeldingId,
                klippVariant = KlippVariant.SOKNAD_STARTER_INNI_SLUTTER_ETTER,
                periodeFor = sok.soknadPerioder!!.serialisertTilString(),
                periodeEtter = nyePerioder.serialisertTilString(),
                timestamp = Instant.now(),
            ),
        )

        if (sok.status != Soknadstatus.FREMTIDIG) {
            sporsmalGenerator.lagSporsmalPaSoknad(sok.id)
            val oppdatertSoknad = sykepengesoknadDAO.finnSykepengesoknad(sok.id)
            soknadProducer.soknadEvent(oppdatertSoknad, null, false)
        }

        if (sok.status == Soknadstatus.FREMTIDIG && nyePerioder.maxOf { it.tom } < LocalDate.now()) {
            aktiveringProducer.leggPaAktiveringTopic(AktiveringBestilling(sok.fnr, sok.id))
        }
    }

    fun klippSoknaderSomOverlapperFullstendig(
        sykmeldingId: String,
        sok: Sykepengesoknad,
    ) {
        log.info("Sykmelding $sykmeldingId overlapper søknad ${sok.id} fullstendig")

        klippetSykepengesoknadRepository.save(
            KlippetSykepengesoknadDbRecord(
                sykepengesoknadUuid = sok.id,
                sykmeldingUuid = sykmeldingId,
                klippVariant = KlippVariant.SOKNAD_STARTER_FOR_SLUTTER_ETTER,
                periodeFor = sok.soknadPerioder!!.serialisertTilString(),
                periodeEtter = null,
                timestamp = Instant.now(),
            ),
        )

        val fullstendigOverlappetSoknad = sok.copy(status = Soknadstatus.SLETTET)
        sykepengesoknadDAO.slettSoknad(fullstendigOverlappetSoknad)

        soknadProducer.soknadEvent(fullstendigOverlappetSoknad, null, false)
    }

    fun klippSoknaderSomOverlapperFor(
        sykmeldingId: String,
        sok: Sykepengesoknad,
        sykmeldingPeriode: ClosedRange<LocalDate>,
    ) {
        log.info(
            "Sykmelding $sykmeldingId klipper søknad ${sok.id} fom fra: ${sok.fom} til: ${
                sykmeldingPeriode.endInclusive.plusDays(
                    1,
                )
            }",
        )

        val nyePerioder =
            sykepengesoknadDAO.klippSoknadFom(
                sykepengesoknadUuid = sok.id,
                nyFom = sykmeldingPeriode.endInclusive.plusDays(1),
                fom = sok.fom!!,
                tom = sok.tom!!,
            )

        klippetSykepengesoknadRepository.save(
            KlippetSykepengesoknadDbRecord(
                sykepengesoknadUuid = sok.id,
                sykmeldingUuid = sykmeldingId,
                klippVariant = KlippVariant.SOKNAD_STARTER_FOR_SLUTTER_INNI,
                periodeFor = sok.soknadPerioder!!.serialisertTilString(),
                periodeEtter = nyePerioder.serialisertTilString(),
                timestamp = Instant.now(),
            ),
        )

        if (sok.status != Soknadstatus.FREMTIDIG) {
            sporsmalGenerator.lagSporsmalPaSoknad(sok.id)
            val oppdatertSoknad = sykepengesoknadDAO.finnSykepengesoknad(sok.id)
            soknadProducer.soknadEvent(oppdatertSoknad, null, false)
        }
    }

    fun klippSoknaderSomOverlapperInni(
        sykmeldingId: String,
        sok: Sykepengesoknad,
        sykmeldingPeriode: ClosedRange<LocalDate>,
    ) {
        log.info("Sykmelding $sykmeldingId overlapper søknad ${sok.id} inni")

        val soknadStart = sok
        val soknadSlutt =
            sykepengesoknadDAO.lagreSykepengesoknad(
                sok.copy(
                    id = UUID.randomUUID().toString(),
                    opprettet = Instant.now(),
                    opprinnelse = Opprinnelse.SYKEPENGESOKNAD_BACKEND,
                ),
            )

        klippSoknaderSomOverlapperEtter(
            sykmeldingId,
            soknadStart,
            sykmeldingPeriode,
        )

        klippSoknaderSomOverlapperFor(
            sykmeldingId,
            soknadSlutt,
            sykmeldingPeriode,
        )
    }
}
