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
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.GjenapneSykmeldingService
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger.Klipp
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
    private val klipp: Klipp,
    private val skalOppretteSoknader: SkalOppretteSoknader,
    private val lockRepository: LockRepository,
    private val naringsdrivendeSoknadService: NaringsdrivendeSoknadService,
    private val opprettSoknadService: OpprettSoknadService,
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
            TransactionInterceptor
                .currentTransactionStatus()
                .setRollbackOnly() // Kjører rollback, uten å kaste en ny exception
            return emptyList()
        }
    }

    fun prosseserKafkaMessage(sykmeldingKafkaMessage: SykmeldingKafkaMessage): List<AktiveringBestilling> =
        when (sykmeldingKafkaMessage.event.statusEvent) {
            STATUS_BEKREFTET -> {
                handterBekreftetSykmelding(sykmeldingKafkaMessage)
            }

            STATUS_SENDT -> {
                handterSendtSykmelding(sykmeldingKafkaMessage)
            }

            else -> {
                log.info(
                    "Ignorerer statusmelding for sykmelding ${sykmeldingKafkaMessage.sykmelding.id} med " +
                        "status ${sykmeldingKafkaMessage.event.statusEvent}",
                )
                emptyList()
            }
        }

    private fun handterBekreftetSykmelding(sykmeldingStatusKafkaMessageDTO: SykmeldingKafkaMessage): List<AktiveringBestilling> =
        when (val arbeidssituasjon = sykmeldingStatusKafkaMessageDTO.hentArbeidssituasjon()) {
            Arbeidssituasjon.ARBEIDSLEDIG,
            Arbeidssituasjon.FISKER,
            Arbeidssituasjon.JORDBRUKER,
            Arbeidssituasjon.BARNEPASSER,
            Arbeidssituasjon.ANNET,
            -> {
                opprettSoknad(sykmeldingStatusKafkaMessageDTO, arbeidssituasjon)
            }

            Arbeidssituasjon.NAERINGSDRIVENDE,
            Arbeidssituasjon.FRILANSER,
            -> {
                opprettSoknadNaringsdrivende(sykmeldingStatusKafkaMessageDTO, arbeidssituasjon)
            }

            Arbeidssituasjon.ARBEIDSTAKER -> {
                // Bekreftet sykmelding for arbeidstaker tilsvarer strengt fortrolig addresse.
                // At denne bekreftes og ikke sendes styres av sykefravaer frontend
                emptyList()
            }

            null -> {
                emptyList()
            }
        }

    private fun handterSendtSykmelding(sykmeldingStatusKafkaMessageDTO: SykmeldingKafkaMessage): List<AktiveringBestilling> =
        when (val arbeidssituasjon = sykmeldingStatusKafkaMessageDTO.hentArbeidssituasjon()) {
            Arbeidssituasjon.ARBEIDSTAKER -> {
                opprettSoknadArbeidstaker(sykmeldingStatusKafkaMessageDTO, arbeidssituasjon)
            }

            else -> {
                throw UventetArbeidssituasjonException(
                    "Uventet arbeidssituasjon $arbeidssituasjon for sendt sykmelding ${sykmeldingStatusKafkaMessageDTO.sykmelding.id}",
                )
            }
        }

    private fun opprettSoknad(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        arbeidssituasjon: Arbeidssituasjon,
    ): List<AktiveringBestilling> {
        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(sykmeldingKafkaMessage.kafkaMetadata.fnr)

        val skalOppretteSoknad =
            skalOppretteSoknader.skalOppretteSoknader(
                sykmeldingKafkaMessage = sykmeldingKafkaMessage,
            )
        if (!skalOppretteSoknad) {
            return emptyList()
        }

        låsIdenter(identer, sykmeldingKafkaMessage)

        val sykeForloep = flexSyketilfelleClient.hentSykeforloep(identer, sykmeldingKafkaMessage)

        return opprettSoknadService.opprettSykepengesoknaderForSykmelding(
            sykmeldingKafkaMessage = sykmeldingKafkaMessage,
            arbeidssituasjon = arbeidssituasjon,
            identer = identer,
            arbeidsgiverStatusDTO = sykmeldingKafkaMessage.event.arbeidsgiver,
            flexSyketilfelleSykeforloep = sykeForloep,
        )
    }

    private fun opprettSoknadArbeidstaker(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        arbeidssituasjon: Arbeidssituasjon,
    ): List<AktiveringBestilling> {
        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(sykmeldingKafkaMessage.kafkaMetadata.fnr)

        val skalOppretteSoknad =
            skalOppretteSoknader.skalOppretteSoknader(
                sykmeldingKafkaMessage = sykmeldingKafkaMessage,
            )
        if (!skalOppretteSoknad) {
            return emptyList()
        }

        låsIdenter(identer, sykmeldingKafkaMessage)

        val sykeForloep = flexSyketilfelleClient.hentSykeforloep(identer, sykmeldingKafkaMessage)

        val klippetSykmeldingKafkaMessage =
            klipp.klippArbeidstaker(
                sykmeldingKafkaMessage = sykmeldingKafkaMessage,
                arbeidssituasjon = arbeidssituasjon,
                identer = identer,
            )

        return opprettSoknadService.opprettSykepengesoknaderForSykmelding(
            sykmeldingKafkaMessage = klippetSykmeldingKafkaMessage,
            arbeidssituasjon = arbeidssituasjon,
            identer = identer,
            arbeidsgiverStatusDTO = sykmeldingKafkaMessage.event.arbeidsgiver,
            flexSyketilfelleSykeforloep = sykeForloep,
        )
    }

    private fun opprettSoknadNaringsdrivende(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        arbeidssituasjon: Arbeidssituasjon,
    ): List<AktiveringBestilling> {
        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(fnr = sykmeldingKafkaMessage.kafkaMetadata.fnr)

        val skalOppretteSoknad =
            skalOppretteSoknader.skalOppretteNaringsdrivendeSoknader(
                sykmeldingKafkaMessage = sykmeldingKafkaMessage,
                arbeidssituasjon = arbeidssituasjon,
                identer = identer,
            )
        if (!skalOppretteSoknad) {
            return emptyList()
        }

        val sykmeldingerSomSkalHaSoknader =
            naringsdrivendeSoknadService.finnSykmeldingerSomManglerSoknad(
                sykmeldingId = sykmeldingKafkaMessage.sykmelding.id,
                arbeidssituasjon = arbeidssituasjon,
            )

        låsIdenter(identer, sykmeldingKafkaMessage)

        val sykeForloep = flexSyketilfelleClient.hentSykeforloep(identer, sykmeldingKafkaMessage)

        return (setOf(sykmeldingKafkaMessage) + sykmeldingerSomSkalHaSoknader).flatMap {
            opprettSoknadService.opprettSykepengesoknaderForSykmelding(
                sykmeldingKafkaMessage = it,
                arbeidssituasjon = it.hentArbeidssituasjon()!!,
                identer = identer,
                arbeidsgiverStatusDTO = it.event.arbeidsgiver,
                flexSyketilfelleSykeforloep = sykeForloep,
            )
        }
    }

    private fun låsIdenter(
        identer: FolkeregisterIdenter,
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
    ) {
        val låstIdenter = lockRepository.settAdvisoryLock(keys = identer.alle().map { it.toLong() }.toLongArray())
        if (!låstIdenter) {
            throw RuntimeException("Det finnes allerede en advisory lock for sykmelding ${sykmeldingKafkaMessage.sykmelding.id}")
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
            Arbeidssituasjon.NAERINGSDRIVENDE -> {
                this.event.brukerSvar?.arbeidssituasjon?.let {
                    Arbeidssituasjon.valueOf(it.svar.name)
                }
            }

            else -> {
                arbeidssituasjon
            }
        }
    }
    return null
}

fun SykmeldingKafkaMessage.brukerHarOppgittForsikring(): Boolean {
    this.event.sporsmals
        ?.firstOrNull { sporsmal -> sporsmal.shortName == ShortNameKafkaDTO.FORSIKRING }
        ?.svar
        ?.let { return it == "JA" }
    return false
}
