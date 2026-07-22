package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.client.sykmeldinger.FlexSykmeldingerBackendClient
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.syfo.sykmelding.kafka.model.STATUS_BEKREFTET
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import org.springframework.stereotype.Component
import java.util.*

const val VENTETIDSPERIODE = 16

@Component
class NaringsdrivendeSoknadService(
    private val flexSyketilfelleClient: FlexSyketilfelleClient,
    private val flexSykmeldingerBackendClient: FlexSykmeldingerBackendClient,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
) {
    private val log = logger()

    fun finnAndreSykmeldingerSomManglerSoknad(
        sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
        arbeidssituasjon: Arbeidssituasjon,
        identer: FolkeregisterIdenter,
    ): List<SykmeldingKafkaMessageDTO> {
        val sykmeldingIder = flexSyketilfelleClient.hentSykmeldingerMedSammeVentetid(sykmeldingKafkaMessage, identer)
        logSykmeldingerMedSammeVentetid(sykmeldingIder, sykmeldingKafkaMessage)

        val andreSykmeldingIder = sykmeldingIder.filterNot { it == sykmeldingKafkaMessage.sykmelding.id }.toSet()

        val andreSøknaderMedSammeVentetid = sykepengesoknadRepository.findBySykmeldingUuidIn(andreSykmeldingIder)

        val andreSykmeldingIderMedSoknader =
            andreSøknaderMedSammeVentetid
                .map { it.sykmeldingUuid!! }
                .toSet()

        val finnesSoknadMedSammeArbeidssituasjonFørSykmeldingen =
            andreSøknaderMedSammeVentetid
                .filter { it.arbeidssituasjon == arbeidssituasjon }
                .any { it.tom!! < sykmeldingKafkaMessage.sykmelding.fom }

        val andreSykmeldingerSomManglerSoknad = andreSykmeldingIder - andreSykmeldingIderMedSoknader
        val andreSykmeldingerViSkalOppretteSoknadFor =
            if (andreSykmeldingerSomManglerSoknad.isEmpty()) {
                emptyList()
            } else {
                flexSykmeldingerBackendClient
                    .hentSykmeldinger(
                        sykmeldingIder = andreSykmeldingerSomManglerSoknad,
                        fom = if (finnesSoknadMedSammeArbeidssituasjonFørSykmeldingen) sykmeldingKafkaMessage.sykmelding.fom else null,
                    ).filter { it.hentArbeidssituasjon() == arbeidssituasjon }
                    .filter { it.event.statusEvent == STATUS_BEKREFTET }
                    .filter { it.sykmelding.tom!! >= sykmeldingKafkaMessage.sykmelding.fom!!.minusDays(VENTETIDSPERIODE.toLong()) }
            }

        log.info(
            lagLoglinje(
                andreSykmeldingerMedSammeArbeidsforhold = andreSykmeldingerViSkalOppretteSoknadFor,
                sykmeldingKafkaMessage = sykmeldingKafkaMessage,
                finnesSoknadFørSykmeldingen = finnesSoknadMedSammeArbeidssituasjonFørSykmeldingen,
            ),
        )
        return andreSykmeldingerViSkalOppretteSoknadFor
    }

    private fun logSykmeldingerMedSammeVentetid(
        sykmeldingIder: Set<String>,
        sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
    ) {
        val uuider =
            sykmeldingIder.map {
                try {
                    UUID.fromString(it)
                } catch (_: IllegalArgumentException) {
                    "ugyldig uuid"
                }
            }
        log.info("Fant ${sykmeldingIder.size} sykmeldinger med samme ventetid ${sykmeldingKafkaMessage.sykmelding.id}: $uuider")
    }

    private fun lagLoglinje(
        andreSykmeldingerMedSammeArbeidsforhold: List<SykmeldingKafkaMessageDTO>,
        sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
        finnesSoknadFørSykmeldingen: Boolean,
    ): String =
        "(SFS ${if (finnesSoknadFørSykmeldingen) "J" else "N"}) " +
            "Fant ${andreSykmeldingerMedSammeArbeidsforhold.size} andre sykmeldinger: ${sykmeldingKafkaMessage.sykmelding.loglinje}: " +
            andreSykmeldingerMedSammeArbeidsforhold
                .sortedBy { it.sykmelding.fom }
                .joinToString { it.sykmelding.loglinje }
}
