package no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.KlippVariant
import no.nav.helse.flex.repository.KlippetSykepengesoknadDbRecord
import no.nav.helse.flex.repository.KlippetSykepengesoknadRepository
import no.nav.helse.flex.soknadsopprettelse.tilSoknadsperioder
import no.nav.helse.flex.util.serialisertTilString
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

@Component
@Transactional
class Sykmeldingsklipper(
    private val klippetSykepengesoknadRepository: KlippetSykepengesoknadRepository,
) {

    private val log = logger()

    fun klippSykmeldingSomOverlapperEtter(
        sykmeldingId: String,
        sok: Sykepengesoknad,
        sykPeriode: ClosedRange<LocalDate>,
        sokPeriode: ClosedRange<LocalDate>,
        sykmeldingPerioder: List<SykmeldingsperiodeAGDTO>,
    ): List<SykmeldingsperiodeAGDTO> {
        log.info("Sykmelding $sykmeldingId overlapper ${sok.status} søknad ${sok.id} etter ${sykPeriode.start}")

        val nyeSykmeldingPerioder = sykmeldingPerioder
            .filterNot { it.fom in sokPeriode && it.tom in sokPeriode }
            .map {
                if (it.fom in sokPeriode) {
                    return@map it.copy(
                        fom = sokPeriode.endInclusive.plusDays(1)
                    )
                }

                return@map it
            }

        klippetSykepengesoknadRepository.save(
            KlippetSykepengesoknadDbRecord(
                sykepengesoknadUuid = sok.id,
                sykmeldingUuid = sykmeldingId,
                klippVariant = KlippVariant.SYKMELDING_STARTER_FOR_SLUTTER_INNI,
                periodeFor = sykmeldingPerioder.tilSoknadsperioder().serialisertTilString(),
                periodeEtter = nyeSykmeldingPerioder.tilSoknadsperioder().serialisertTilString(),
                timestamp = Instant.now(),
            )
        )

        return nyeSykmeldingPerioder
    }

    fun klippSykmeldingSomOverlapperFor(
        sykmeldingId: String,
        sok: Sykepengesoknad,
        sykPeriode: ClosedRange<LocalDate>,
        sokPeriode: ClosedRange<LocalDate>,
        sykmeldingPerioder: List<SykmeldingsperiodeAGDTO>,
    ): List<SykmeldingsperiodeAGDTO> {
        log.info("Sykmelding $sykmeldingId overlapper ${sok.status} søknad ${sok.id} før ${sykPeriode.endInclusive}")

        val nyeSykmeldingPerioder = sykmeldingPerioder
            .filterNot { it.fom in sokPeriode && it.tom in sokPeriode }
            .map {
                if (it.tom in sokPeriode) {
                    return@map it.copy(
                        tom = sokPeriode.start.minusDays(1)
                    )
                }

                return@map it
            }

        klippetSykepengesoknadRepository.save(
            KlippetSykepengesoknadDbRecord(
                sykepengesoknadUuid = sok.id,
                sykmeldingUuid = sykmeldingId,
                klippVariant = KlippVariant.SYKMELDING_STARTER_INNI_SLUTTER_ETTER,
                periodeFor = sykmeldingPerioder.tilSoknadsperioder().serialisertTilString(),
                periodeEtter = nyeSykmeldingPerioder.tilSoknadsperioder().serialisertTilString(),
                timestamp = Instant.now(),
            )
        )

        return nyeSykmeldingPerioder
    }
}
