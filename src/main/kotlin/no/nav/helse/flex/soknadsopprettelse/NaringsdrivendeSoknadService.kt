package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.client.sykmeldinger.FlexSykmeldingerBackendClient
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.unleash.UnleashToggles
import no.nav.syfo.sykmelding.kafka.model.STATUS_BEKREFTET
import org.springframework.stereotype.Component
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

@Component
class NaringsdrivendeSoknadService(
    private val flexSyketilfelleClient: FlexSyketilfelleClient,
    private val flexSykmeldingerBackendClient: FlexSykmeldingerBackendClient,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val unleashToggles: UnleashToggles,
) {
    private val log = logger()

    fun finnAndreSykmeldingerSomManglerSoknad(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        arbeidssituasjon: Arbeidssituasjon,
        identer: FolkeregisterIdenter,
    ): List<SykmeldingKafkaMessage> {
        val skalOppretteVentetidsoknader = unleashToggles.opprettVentetidsoknaderEnabled(identer.originalIdent)
        return try {
            val sykmeldingIder =
                flexSyketilfelleClient.hentSykmeldingerMedSammeVentetid(sykmeldingKafkaMessage, identer)
            log.info("Fant ${sykmeldingIder.size} sykmeldinger med samme ventetid ${sykmeldingKafkaMessage.sykmelding.id}: $sykmeldingIder")

            val andreSykmeldingIder = sykmeldingIder.filterNot { it == sykmeldingKafkaMessage.sykmelding.id }.toSet()

            val andreSykmeldingIderMedSoknader =
                sykepengesoknadRepository
                    .findBySykmeldingUuidIn(andreSykmeldingIder)
                    .map { it.sykmeldingUuid!! }
                    .toSet()

            if (unleashToggles.sammenlignSykmeldingKafkaEnabled(identer.originalIdent)) {
                sammenlignOriginalKafkaMelding(sykmeldingKafkaMessage)
            }

            val andreSykmeldingerSomManglerSoknad = andreSykmeldingIder - andreSykmeldingIderMedSoknader
            val andreSykmeldingerMedSammeArbeidsforhold =
                if (andreSykmeldingerSomManglerSoknad.isEmpty()) {
                    emptyList()
                } else {
                    flexSykmeldingerBackendClient
                        .hentSykmeldinger(sykmeldingIder = andreSykmeldingerSomManglerSoknad)
                        .filter { it.hentArbeidssituasjon() == arbeidssituasjon }
                        .filter { it.event.statusEvent == STATUS_BEKREFTET }
                }

            log.info(lagLoglinje(andreSykmeldingerMedSammeArbeidsforhold, sykmeldingKafkaMessage, skalOppretteVentetidsoknader))
            if (skalOppretteVentetidsoknader) {
                andreSykmeldingerMedSammeArbeidsforhold
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            if (skalOppretteVentetidsoknader) {
                throw e
            } else {
                log.warn("Feil ved henting av sykmeldinger med samme ventetid", e)
                emptyList()
            }
        }
    }

    private fun lagLoglinje(
        andreSykmeldingerMedSammeArbeidsforhold: List<SykmeldingKafkaMessage>,
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        togglePå: Boolean,
    ): String =
        "(Toggle ${if (togglePå) "På" else "Av" }) Fant ${andreSykmeldingerMedSammeArbeidsforhold.size} " +
            "andre sykmeldinger: ${sykmeldingKafkaMessage.sykmelding.loglinje}: " +
            "${andreSykmeldingerMedSammeArbeidsforhold.sortedBy { it.sykmelding.fom }.joinToString { it.sykmelding.loglinje }}}"

    private fun sammenlignOriginalKafkaMelding(sykmeldingKafkaMessage: SykmeldingKafkaMessage) {
        flexSykmeldingerBackendClient
            .hentSykmeldinger(sykmeldingIder = setOf(sykmeldingKafkaMessage.sykmelding.id))
            .first()
            .sammenlign(sykmeldingKafkaMessage = sykmeldingKafkaMessage)
    }
}

fun SykmeldingKafkaMessage.sammenlign(sykmeldingKafkaMessage: SykmeldingKafkaMessage) {
    val forskjeller = finnForskjeller(sykmeldingKafkaMessage)
    if (forskjeller.isNotEmpty()) {
        logger().warn(
            "Sykmelding hentet fra sykmeldinger-backend er ikke lik den originale sykmeldingen: ${sykmeldingKafkaMessage.sykmelding.id}\n$forskjeller",
        )
    }
}

internal fun SykmeldingKafkaMessage.finnForskjeller(sammenlignbarSykmeldingKafkaMessage: SykmeldingKafkaMessage): String =
    listOf(
        "sykmelding" to Pair(sammenlignbarSykmeldingKafkaMessage.sykmelding, this.sykmelding),
        "kafkaMetadata" to Pair(sammenlignbarSykmeldingKafkaMessage.kafkaMetadata, this.kafkaMetadata),
        "event" to Pair(sammenlignbarSykmeldingKafkaMessage.event, this.event),
    ).flatMap { (navn, verdier) ->
        val (original, hentet) = verdier
        finnForskjellerRekursivt(navn, original, hentet)
    }.joinToString(separator = "\n")

private fun finnForskjellerRekursivt(
    sti: String,
    original: Any?,
    hentet: Any?,
): List<String> {
    if (original == hentet) return emptyList()
    if (original == null || hentet == null) return listOf("  $sti: original=$original, hentet=$hentet")

    if (ignorerForSammenligning.contains(sti)) return emptyList()

    if (original::class.isData) {
        return original::class.memberProperties.flatMap { subProp ->
            @Suppress("UNCHECKED_CAST")
            val sub = subProp as KProperty1<Any, *>
            finnForskjellerRekursivt("$sti.${subProp.name}", sub.get(original), sub.get(hentet))
        }
    }

    if (original is List<*> && hentet is List<*>) {
        if (original.size !=
            hentet.size
        ) {
            return listOf("  $sti: original har ${original.size} elementer, hentet har ${hentet.size} elementer")
        }
        return original.zip(hentet).flatMapIndexed { index, (o, h) ->
            finnForskjellerRekursivt("$sti[$index]", o, h)
        }
    }

    return listOf("  $sti: original=$original, hentet=$hentet")
}

val ignorerForSammenligning =
    listOf(
        "kafkaMetadata.timestamp",
        "sykmelding.signaturDato",
        "sykmelding.mottattTidspunkt",
        "event.timestamp",
        "sykmelding.arbeidsgiver.yrkesbetegnelse",
        "sykmelding.behandler.tlf",
    )
