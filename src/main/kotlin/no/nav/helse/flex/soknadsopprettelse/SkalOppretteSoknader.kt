package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Arbeidssituasjon.FRILANSER
import no.nav.helse.flex.domain.Arbeidssituasjon.NAERINGSDRIVENDE
import no.nav.helse.flex.domain.ErUtenforVentetidRequest
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.logger
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO.AVVENTENDE
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO.REISETILSKUDD
import org.springframework.stereotype.Service

@Service
class SkalOppretteSoknader(
    private val flexSyketilfelleClient: FlexSyketilfelleClient,
) {
    private val log = logger()

    fun skalOppretteSoknader(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        arbeidssituasjon: Arbeidssituasjon,
        identer: FolkeregisterIdenter,
    ): Boolean {
        val sykmelding = sykmeldingKafkaMessage.sykmelding
        val sykmeldingId = sykmelding.id

        val perioder = sykmelding.sykmeldingsperioder
        if (perioder.any { it.type == AVVENTENDE }) {
            log.info("Sykmelding ${sykmelding.id} har periodetype AVVENTENDE vi ennå ikke oppretter søknader for")
            return false
        }

        if (perioder.any { it.type == REISETILSKUDD && !it.reisetilskudd }) {
            log.info(
                "Sykmelding ${sykmelding.id} har periodetype REISETILSKUDD og reisetilskudd flagg false. " +
                    "Veldig rart. Oppretter ikke søknad.",
            )
            return false
        }

        if (perioder.any { it.reisetilskudd && it.type != REISETILSKUDD }) {
            val periodetyper = perioder.map { it.type.name }
            log.info(
                "Sykmelding ${sykmelding.id} har reisetilskudd flagg true og type ikke reisetilskudd. Type: " +
                    "$periodetyper.  Veldig rart. Oppretter ikke søknad.",
            )
            return false
        }

        if (!listOf(FRILANSER, NAERINGSDRIVENDE).contains(arbeidssituasjon)) {
            return true
        }

        val harForsikring = sykmeldingKafkaMessage.harForsikring()
        val erUtenforVentetid =
            flexSyketilfelleClient.erUtenforVentetid(
                identer = identer,
                sykmeldingId = sykmeldingId,
                erUtenforVentetidRequest =
                    ErUtenforVentetidRequest(
                        sykmeldingKafkaMessage = sykmeldingKafkaMessage,
                    ),
            )
        when {
            !erUtenforVentetid && !harForsikring -> {
                log.info(
                    "Sykmelding: $sykmeldingId for: ${arbeidssituasjon.name} er innenfor ventetiden. Bruker har IKKE forsikring. Oppretter ikke søknad.",
                )
                return false
            }

            !erUtenforVentetid && harForsikring -> {
                log.info(
                    "Sykmelding: $sykmeldingId for: ${arbeidssituasjon.name} er innenfor ventetiden men bruker HAR forsikring. Oppretter søknad.",
                )
            }

            else -> {
                log.info("Sykmelding: $sykmeldingId for: ${arbeidssituasjon.name} er utenfor ventetiden. Oppretter søknad.")
            }
        }
        return true
    }
}
