package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.aktivering.AktiveringBestilling
import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.exception.SkalRebehandlesException
import no.nav.helse.flex.domain.exception.UventetArbeidssituasjonException
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.domain.sykmeldingbekreftelse.SykmeldingBekreftelseHendelseType
import no.nav.helse.flex.domain.sykmeldingbekreftelse.SykmeldingBekreftelseKafkaHendelse
import no.nav.helse.flex.domain.sykmeldingbekreftelse.SykmeldtBekreftelseStatus
import no.nav.helse.flex.kafka.producer.RebehandlingSykmeldingSendtProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.LockRepository
import no.nav.helse.flex.service.GjenapneSykmeldingService
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger.KlippOgOpprett
import no.nav.syfo.sykmelding.kafka.model.STATUS_BEKREFTET
import no.nav.syfo.sykmelding.kafka.model.STATUS_SENDT
import no.nav.syfo.sykmelding.kafka.model.ShortNameKafkaDTO
import org.apache.kafka.common.protocol.types.Field
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
        sykmeldingBekreftelse: SykmeldingBekreftelseKafkaHendelse?,
        topic: String,
    ): List<AktiveringBestilling> {
        when (sykmeldingBekreftelse?.hendelseType) {
            null -> {
                log.info("Mottok tombstone event for sykmelding $sykmeldingId")
                sykmeldingStatusService.prosesserTombstoneSykmelding(sykmeldingId)
                return emptyList()
            }

            SykmeldingBekreftelseHendelseType.NY -> {
                return emptyList()
            }

            SykmeldingBekreftelseHendelseType.SYKMELDING_OPPDATERT,
            SykmeldingBekreftelseHendelseType.VALIDERING_OPPDATERT,
            SykmeldingBekreftelseHendelseType.BRUKERSTATUS_OPPDATERT,
            -> {
                if (skalTidligereSoknaderSlettes(sykmeldingId)) {
                    sykmeldingStatusService.prosesserTombstoneSykmelding(sykmeldingId)
                }

                return if (sykmeldingBekreftelse.sykmeldtBekreftelse.status == SykmeldtBekreftelseStatus.BEKREFTET) {
                    provKlippOgOpprett(sykmeldingBekreftelse)
                } else {
                    emptyList()
                }
            }
        }
    }

    private fun skalTidligereSoknaderSlettes(sykmeldingId: String): Boolean =
        TODO(
            "Dersom forrige status har " +
                "sykmeldtBekreftelse.status == BEKREFTET" +
                "&& sykmeldtBekreftelse.arbeidssituasjon != ARBEIDSTAKER",
        )

    private fun konverterSykmeldingBekreftelseTilSykmeldingKafkaMessage(
        sykmeldingBekreftelse: SykmeldingBekreftelseKafkaHendelse,
    ): SykmeldingKafkaMessage {
        TODO("Not implemented")
    }

    private fun provKlippOgOpprett(sykmeldingBekreftelse: SykmeldingBekreftelseKafkaHendelse): List<AktiveringBestilling> {
        val sykmeldtSituasjon =
            requireNotNull(sykmeldingBekreftelse.sykmeldtBekreftelse.situasjon) {
                "sykmeldt situasjon er påkrevd dersom status BEKREFTET, sykmeldingId: ${sykmeldingBekreftelse.sykmelding.sykmeldingId}"
            }
        val arbeidssituasjon = sykmeldtSituasjon.arbeidssituasjon
        val sykmeldingKafkaMessage = konverterSykmeldingBekreftelseTilSykmeldingKafkaMessage(sykmeldingBekreftelse)

        try {
            return eksterneKallKlippOgOpprett(sykmeldingKafkaMessage, arbeidssituasjon)
        } catch (e: SkalRebehandlesException) {
            log.error(
                "Feil under opprettelse av søknad for sykmelding ${sykmeldingKafkaMessage.event.sykmeldingId}, " +
                    "legger til rebehandling ${e.rebehandlingsTid}",
                e,
            )
            rebehandlingSykmeldingSendtProducer.leggPaRebehandlingTopic(
                sykmeldingKafkaMessage,
                e.rebehandlingsTid,
            )
            TransactionInterceptor
                .currentTransactionStatus()
                .setRollbackOnly() // Kjører rollback, uten å kaste en ny exception
            return emptyList()
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
