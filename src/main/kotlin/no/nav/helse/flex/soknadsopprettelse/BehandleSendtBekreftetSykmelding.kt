package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.aktivering.kafka.AktiveringBestilling
import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.exception.SkalRebehandlesException
import no.nav.helse.flex.domain.exception.UventetArbeidssituasjonException
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.kafka.producer.RebehandlingSykmeldingSendtProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.service.GjenapneSykmeldingService
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.util.Metrikk
import no.nav.syfo.model.sykmeldingstatus.STATUS_BEKREFTET
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.TransactionInterceptor

@Service
@Transactional
class BehandleSendtBekreftetSykmelding(
    private val rebehandlingSykmeldingSendtProducer: RebehandlingSykmeldingSendtProducer,
    private val identService: IdentService,
    private val flexSyketilfelleClient: FlexSyketilfelleClient,
    private val metrikk: Metrikk,
    private val sykmeldingStatusService: GjenapneSykmeldingService,
    private val klippOgOpprett: KlippOgOpprett,
    private val skalOppretteSoknader: SkalOppretteSoknader
) {
    val log = logger()

    fun prosesserSykmelding(sykmeldingId: String, sykmeldingKafkaMessage: SykmeldingKafkaMessage?, topic: String): List<AktiveringBestilling> {
        if (sykmeldingKafkaMessage == null) {
            log.info("Mottok tombstone event for sykmelding $sykmeldingId")
            sykmeldingStatusService.prosesserTombstoneSykmelding(sykmeldingId, topic)
            return emptyList()
        }
        try {
            return prosseserKafkaMessage(sykmeldingKafkaMessage)
        } catch (e: SkalRebehandlesException) {
            logger().error(
                "Feil under opprettelse av søknad for sykmelding ${sykmeldingKafkaMessage.event.sykmeldingId}, legger til rebehandling ${e.rebehandlingsTid}",
                e
            )
            rebehandlingSykmeldingSendtProducer.leggPaRebehandlingTopic(sykmeldingKafkaMessage, e.rebehandlingsTid)
            TransactionInterceptor.currentTransactionStatus()
                .setRollbackOnly() // Kjører rollback, uten å kaste en ny exception
            return emptyList()
        }
    }

    fun prosseserKafkaMessage(sykmeldingKafkaMessage: SykmeldingKafkaMessage): List<AktiveringBestilling> {
        metrikk.mottattSykmelding.increment()
        return when (val sykmeldingStatus = sykmeldingKafkaMessage.event.statusEvent) {
            STATUS_BEKREFTET -> handterBekreftetSykmelding(sykmeldingKafkaMessage)
            STATUS_SENDT -> handterSendtSykmelding(sykmeldingKafkaMessage)
            else -> {
                log.info("Ignorerer statusmelding for sykmelding ${sykmeldingKafkaMessage.sykmelding.id} med status $sykmeldingStatus")
                emptyList()
            }
        }
    }

    private fun handterBekreftetSykmelding(sykmeldingStatusKafkaMessageDTO: SykmeldingKafkaMessage): List<AktiveringBestilling> {
        return when (val arbeidssituasjon = sykmeldingStatusKafkaMessageDTO.hentArbeidssituasjon()) {
            Arbeidssituasjon.NAERINGSDRIVENDE -> eksterneKallKlippOgOpprett(
                sykmeldingStatusKafkaMessageDTO,
                arbeidssituasjon
            )
            Arbeidssituasjon.FRILANSER -> eksterneKallKlippOgOpprett(sykmeldingStatusKafkaMessageDTO, arbeidssituasjon)
            Arbeidssituasjon.ARBEIDSLEDIG -> eksterneKallKlippOgOpprett(
                sykmeldingStatusKafkaMessageDTO,
                arbeidssituasjon
            )
            Arbeidssituasjon.ANNET -> eksterneKallKlippOgOpprett(sykmeldingStatusKafkaMessageDTO, arbeidssituasjon)
            Arbeidssituasjon.ARBEIDSTAKER -> {
                // Bekreftet sykmelding for arbeidstaker tilsvarer strengt fortrolig addresse.
                // At denne bekreftes og ikke sendes styres av sykefravaer frontend
                emptyList()
            }
            null -> emptyList()
        }
    }

    private fun eksterneKallKlippOgOpprett(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        arbeidssituasjon: Arbeidssituasjon
    ): List<AktiveringBestilling> {

        val fnr = sykmeldingKafkaMessage.kafkaMetadata.fnr

        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(fnr)

        val skalOppretteSoknad = skalOppretteSoknader.skalOppretteSoknader(
            sykmeldingKafkaMessage = sykmeldingKafkaMessage,
            arbeidssituasjon = arbeidssituasjon,
            identer = identer,
        )
        if (!skalOppretteSoknad) {
            return emptyList()
        }
        val sykeForloep = flexSyketilfelleClient.hentSykeforloep(identer)

        return klippOgOpprett.klippOgOpprett(sykmeldingKafkaMessage, arbeidssituasjon, identer, sykeForloep)
    }

    private fun handterSendtSykmelding(sykmeldingStatusKafkaMessageDTO: SykmeldingKafkaMessage): List<AktiveringBestilling> {
        return when (val arbeidssituasjon = sykmeldingStatusKafkaMessageDTO.hentArbeidssituasjon()) {
            Arbeidssituasjon.ARBEIDSTAKER -> eksterneKallKlippOgOpprett(
                sykmeldingStatusKafkaMessageDTO,
                arbeidssituasjon
            )
            else -> throw UventetArbeidssituasjonException("Uventet arbeidssituasjon $arbeidssituasjon for sendt sykmelding ${sykmeldingStatusKafkaMessageDTO.sykmelding.id}")
        }
    }
}

fun SykmeldingKafkaMessage.hentArbeidssituasjon(): Arbeidssituasjon? {
    this.event.sporsmals?.firstOrNull { sporsmal -> sporsmal.shortName == ShortNameDTO.ARBEIDSSITUASJON }?.svar?.let {
        return no.nav.helse.flex.domain.Arbeidssituasjon.valueOf(
            it
        )
    }
    return null
}

fun SykmeldingKafkaMessage.harForsikring(): Boolean {
    this.event.sporsmals?.firstOrNull { sporsmal -> sporsmal.shortName == ShortNameDTO.FORSIKRING }?.svar?.let { return it == "JA" }
    return false
}
