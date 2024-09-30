package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.aktivering.AktiveringBestilling
import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.exception.SkalRebehandlesException
import no.nav.helse.flex.domain.exception.UventetArbeidssituasjonException
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.kafka.producer.RebehandlingSykmeldingSendtProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.LockRepository
import no.nav.helse.flex.service.GjenapneSykmeldingService
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger.KlippOgOpprett
import no.nav.syfo.sykmelding.kafka.model.STATUS_BEKREFTET
import no.nav.syfo.sykmelding.kafka.model.STATUS_SENDT
import no.nav.syfo.sykmelding.kafka.model.ShortNameKafkaDTO
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.TransactionInterceptor

@Service
@Transactional(rollbackFor = [Throwable::class])
class BehandleSendtBekreftetSykmelding(
    private val rebehandlingSykmeldingSendtProducer: RebehandlingSykmeldingSendtProducer,
    private val identService: IdentService,
    private val flexSyketilfelleClient: FlexSyketilfelleClient,
    private val sykmeldingStatusService: GjenapneSykmeldingService,
    private val klippOgOpprett: KlippOgOpprett,
    private val skalOppretteSoknader: SkalOppretteSoknader,
    private val lockRepository: LockRepository,
) {
    val log = logger()

    fun prosesserSykmelding(
        sykmeldingId: String,
        sykmeldingKafkaMessage: SykmeldingKafkaMessage?,
        topic: String,
    ): List<AktiveringBestilling> {
        if (sykmeldingKafkaMessage == null) {
            log.info("Mottok tombstone event for sykmelding $sykmeldingId")
            sykmeldingStatusService.prosesserTombstoneSykmelding(sykmeldingId, topic)
            return emptyList()
        }
        try {
            return prosseserKafkaMessage(sykmeldingKafkaMessage)
        } catch (e: SkalRebehandlesException) {
            log.error(
                "Feil under opprettelse av søknad for sykmelding ${sykmeldingKafkaMessage.event.sykmeldingId}, " +
                    "legger til rebehandling ${e.rebehandlingsTid}",
                e,
            )
            rebehandlingSykmeldingSendtProducer.leggPaRebehandlingTopic(sykmeldingKafkaMessage, e.rebehandlingsTid)
            TransactionInterceptor.currentTransactionStatus()
                .setRollbackOnly() // Kjører rollback, uten å kaste en ny exception
            return emptyList()
        }
    }

    fun prosseserKafkaMessage(sykmeldingKafkaMessage: SykmeldingKafkaMessage): List<AktiveringBestilling> {
        return when (sykmeldingKafkaMessage.event.statusEvent) {
            STATUS_BEKREFTET -> handterBekreftetSykmelding(sykmeldingKafkaMessage)
            STATUS_SENDT -> handterSendtSykmelding(sykmeldingKafkaMessage)
            else -> {
                log.info(
                    "Ignorerer statusmelding for sykmelding ${sykmeldingKafkaMessage.sykmelding.id} med " +
                        "status ${sykmeldingKafkaMessage.event.statusEvent}",
                )
                emptyList()
            }
        }
    }

    private fun handterBekreftetSykmelding(sykmeldingStatusKafkaMessageDTO: SykmeldingKafkaMessage): List<AktiveringBestilling> {
        return when (val arbeidssituasjon = sykmeldingStatusKafkaMessageDTO.hentArbeidssituasjon()) {
            Arbeidssituasjon.NAERINGSDRIVENDE,
            Arbeidssituasjon.FRILANSER,
            Arbeidssituasjon.ARBEIDSLEDIG,
            Arbeidssituasjon.FISKER,
            Arbeidssituasjon.JORDBRUKER,
            Arbeidssituasjon.ANNET,
            -> {
                eksterneKallKlippOgOpprett(sykmeldingStatusKafkaMessageDTO, arbeidssituasjon)
            }

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
        arbeidssituasjon: Arbeidssituasjon,
    ): List<AktiveringBestilling> {
        val fnr = sykmeldingKafkaMessage.kafkaMetadata.fnr

        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(fnr)

        val skalOppretteSoknad =
            skalOppretteSoknader.skalOppretteSoknader(
                sykmeldingKafkaMessage = sykmeldingKafkaMessage,
                arbeidssituasjon = arbeidssituasjon,
                identer = identer,
            )
        if (!skalOppretteSoknad) {
            return emptyList()
        }

        val låstIdenter = lockRepository.settAdvisoryLock(keys = identer.alle().map { it.toLong() }.toLongArray())
        if (!låstIdenter) {
            throw RuntimeException("Det finnes allerede en advisory lock for sykmelding ${sykmeldingKafkaMessage.sykmelding.id}")
        }

        val sykeForloep = flexSyketilfelleClient.hentSykeforloep(identer, sykmeldingKafkaMessage)

        return klippOgOpprett.klippOgOpprett(sykmeldingKafkaMessage, arbeidssituasjon, identer, sykeForloep)
    }

    private fun handterSendtSykmelding(sykmeldingStatusKafkaMessageDTO: SykmeldingKafkaMessage): List<AktiveringBestilling> {
        return when (val arbeidssituasjon = sykmeldingStatusKafkaMessageDTO.hentArbeidssituasjon()) {
            Arbeidssituasjon.ARBEIDSTAKER ->
                eksterneKallKlippOgOpprett(
                    sykmeldingStatusKafkaMessageDTO,
                    arbeidssituasjon,
                )

            else -> throw UventetArbeidssituasjonException(
                "Uventet arbeidssituasjon $arbeidssituasjon for sendt sykmelding ${sykmeldingStatusKafkaMessageDTO.sykmelding.id}",
            )
        }
    }
}

fun SykmeldingKafkaMessage.hentArbeidssituasjon(): Arbeidssituasjon? {
    this.event.sporsmals?.firstOrNull { sporsmal -> sporsmal.shortName == ShortNameKafkaDTO.ARBEIDSSITUASJON }?.svar?.let { it ->
        val arbeidssituasjon =
            Arbeidssituasjon.valueOf(
                it,
            )
        return when (arbeidssituasjon) {
            Arbeidssituasjon.NAERINGSDRIVENDE ->
                this.event.brukerSvar?.arbeidssituasjon?.let {
                    Arbeidssituasjon.valueOf(it.svar.name)
                }

            else -> arbeidssituasjon
        }
    }
    return null
}

fun SykmeldingKafkaMessage.harForsikring(): Boolean {
    this.event.sporsmals?.firstOrNull { sporsmal -> sporsmal.shortName == ShortNameKafkaDTO.FORSIKRING }?.svar?.let { return it == "JA" }
    return false
}
