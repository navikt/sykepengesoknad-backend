package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.client.sykmeldinger.FlexSykmeldingerBackendClient
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.sykmelding.SykmeldingTilSoknadOpprettelse
import no.nav.helse.flex.domain.sykmelding.tilSykmeldingTilSoknadOpprettelse
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.unleash.UnleashToggles
import no.nav.helse.flex.warnSecure
import no.nav.syfo.sykmelding.kafka.model.STATUS_BEKREFTET
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import org.springframework.stereotype.Component
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

const val VENTETIDSPERIODE = 16

@Component
class NaringsdrivendeSoknadService(
    private val flexSyketilfelleClient: FlexSyketilfelleClient,
    private val flexSykmeldingerBackendClient: FlexSykmeldingerBackendClient,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val unleashToggles: UnleashToggles,
) {
    private val log = logger()

    fun finnAndreSykmeldingerSomManglerSoknad(
        sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
        arbeidssituasjon: Arbeidssituasjon,
        identer: FolkeregisterIdenter,
    ): List<SykmeldingKafkaMessageDTO> {
        val skalOppretteVentetidsoknader = unleashToggles.opprettVentetidsoknaderEnabled(identer.originalIdent)
        return try {
            val sykmeldingIder =
                flexSyketilfelleClient.hentSykmeldingerMedSammeVentetid(sykmeldingKafkaMessage, identer)
            logSykmeldingerMedSammeVentetid(sykmeldingIder, sykmeldingKafkaMessage)

            val andreSykmeldingIder = sykmeldingIder.filterNot { it == sykmeldingKafkaMessage.sykmelding.id }.toSet()

            val andreSøknaderMedSammeVentetid = sykepengesoknadRepository.findBySykmeldingUuidIn(andreSykmeldingIder)

            val andreSykmeldingIderMedSoknader =
                andreSøknaderMedSammeVentetid
                    .map { it.sykmeldingUuid!! }
                    .toSet()

            if (unleashToggles.sammenlignSykmeldingKafkaEnabled(identer.originalIdent)) {
                sammenlignOriginalKafkaMelding(sykmeldingKafkaMessage)
            }

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
                    togglePå = skalOppretteVentetidsoknader,
                    finnesSoknadFørSykmeldingen = finnesSoknadMedSammeArbeidssituasjonFørSykmeldingen,
                ),
            )
            if (skalOppretteVentetidsoknader) {
                andreSykmeldingerViSkalOppretteSoknadFor
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            if (skalOppretteVentetidsoknader) {
                throw e
            } else {
                log.warn("Feil ved henting av sykmeldinger med samme ventetid ${sykmeldingKafkaMessage.sykmelding.id}", e)
                emptyList()
            }
        }
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
        togglePå: Boolean,
        finnesSoknadFørSykmeldingen: Boolean,
    ): String =
        "(Toggle ${if (togglePå) "På" else "Av"}) (SFS ${if (finnesSoknadFørSykmeldingen) "J" else "N"}) " +
            "Fant ${andreSykmeldingerMedSammeArbeidsforhold.size} andre sykmeldinger: ${sykmeldingKafkaMessage.sykmelding.loglinje}: " +
            andreSykmeldingerMedSammeArbeidsforhold
                .sortedBy { it.sykmelding.fom }
                .joinToString { it.sykmelding.loglinje }

    private fun sammenlignOriginalKafkaMelding(sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO) {
        flexSykmeldingerBackendClient
            .hentSykmeldinger(sykmeldingIder = setOf(sykmeldingKafkaMessage.sykmelding.id), fom = null)
            .first()
            .sammenlign(sykmeldingKafkaMessage = sykmeldingKafkaMessage)
    }

    private fun SykmeldingKafkaMessageDTO.sammenlign(sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO) {
        val forskjeller =
            this.tilSykmeldingTilSoknadOpprettelse().finnForskjeller(
                sykmeldingKafkaMessage.tilSykmeldingTilSoknadOpprettelse(),
            )
        if (forskjeller.isNotEmpty()) {
            log.warnSecure(
                message =
                    "Sykmelding hentet fra sykmeldinger-backend er ikke lik den originale sykmeldingen: " +
                        sykmeldingKafkaMessage.sykmelding.id,
                secureMessage = forskjeller,
            )
        } else {
            log.info(
                "Sykmelding hentet fra sykmeldinger-backend er lik den originale sykmeldingen: ${sykmeldingKafkaMessage.sykmelding.id}. " +
                    "Har merknader ${!sykmeldingKafkaMessage.sykmelding.merknader.isNullOrEmpty()}",
            )
        }
    }
}

internal fun SykmeldingTilSoknadOpprettelse.finnForskjeller(sammenlignbar: SykmeldingTilSoknadOpprettelse): String {
    val normalisertHentet = this.copy(eventTimestamp = this.eventTimestamp.truncatedTo(ChronoUnit.MICROS))
    val normalisertOriginal =
        sammenlignbar.copy(
            eventTimestamp = sammenlignbar.eventTimestamp.plus(500, ChronoUnit.NANOS).truncatedTo(ChronoUnit.MICROS),
        )
    return finnForskjellerRekursivt("sykmeldingTilSoknadOpprettelse", normalisertOriginal, normalisertHentet)
        .joinToString(separator = "\n")
}

private fun finnForskjellerRekursivt(
    sti: String,
    original: Any?,
    hentet: Any?,
): List<String> {
    if (original == hentet) return emptyList()
    if (original == null || hentet == null) return listOf("  $sti: original=$original, hentet=$hentet")

    if (original::class.isData) {
        return original::class.memberProperties.flatMap { subProp ->
            @Suppress("UNCHECKED_CAST")
            val sub = subProp as KProperty1<Any, *>
            val originalVerdi = sub.get(original)
            val hentetVerdi = sub.get(hentet)
            if (subProp.name == "beskrivelse" && originalVerdi == "Sykmeldingen er til manuell behandling") {
                return@flatMap emptyList()
            }
            finnForskjellerRekursivt("$sti.${subProp.name}", originalVerdi, hentetVerdi)
        }
    }

    if (original is List<*> && hentet is List<*>) {
        if (original.size != hentet.size) {
            return listOf("  $sti: original har ${original.size} elementer, hentet har ${hentet.size} elementer")
        }
        return original.zip(hentet).flatMapIndexed { index, (o, h) ->
            finnForskjellerRekursivt("$sti[$index]", o, h)
        }
    }

    return listOf("  $sti: original=$original, hentet=$hentet")
}
