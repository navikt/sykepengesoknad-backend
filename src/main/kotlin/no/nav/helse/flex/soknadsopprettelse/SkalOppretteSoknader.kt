package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.client.flexsyketilfelle.VentetidRequest
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.logger
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO.AVVENTENDE
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO.REISETILSKUDD
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import org.springframework.stereotype.Service

@Service
class SkalOppretteSoknader(
    private val flexSyketilfelleClient: FlexSyketilfelleClient,
) {
    private val log = logger()

    fun skalOppretteNaringsdrivendeSoknader(
        sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
        identer: FolkeregisterIdenter,
        arbeidssituasjon: Arbeidssituasjon,
    ): Boolean {
        val sykmeldingId = sykmeldingKafkaMessage.sykmelding.id
        val brukerHarOppgittForsikring = sykmeldingKafkaMessage.brukerHarOppgittForsikring()
        val erUtenforVentetid =
            flexSyketilfelleClient.erUtenforVentetid(
                identer = identer,
                sykmeldingId = sykmeldingId,
                ventetidRequest =
                    VentetidRequest(
                        sykmeldingKafkaMessage = sykmeldingKafkaMessage,
                        kunSendtBekreftet = true,
                    ),
            )

        if (!erUtenforVentetid && !brukerHarOppgittForsikring) {
            log.info(
                "Sykmelding: $sykmeldingId for: ${arbeidssituasjon.name} er innenfor ventetiden. Bruker har IKKE forsikring. Oppretter ikke søknad.",
            )
            return false
        }

        log.info(
            if (!erUtenforVentetid) {
                "Sykmelding: $sykmeldingId for: ${arbeidssituasjon.name} er innenfor ventetiden men bruker HAR forsikring. Oppretter søknad."
            } else {
                "Sykmelding: $sykmeldingId for: ${arbeidssituasjon.name} er utenfor ventetiden. Oppretter søknad."
            },
        )
        return true
    }
}

fun SykmeldingKafkaMessageDTO.harUgyldigePerioder(): Boolean {
    val sykmeldingId = this.sykmelding.id
    val perioder = this.sykmelding.sykmeldingsperioder

    val ugyldigPeriodeMeldinger =
        buildList {
            if (perioder.isEmpty()) {
                add("Sykmelding $sykmeldingId har ingen sykmeldingsperioder. Oppretter ikke søknad.")
            }
            if (perioder.any { it.type == AVVENTENDE }) {
                add("Sykmelding $sykmeldingId har periodetype AVVENTENDE vi ennå ikke oppretter søknader for.")
            }
            if (perioder.any { it.type == REISETILSKUDD && !it.reisetilskudd }) {
                add(
                    "Sykmelding $sykmeldingId har periodetype REISETILSKUDD og reisetilskudd flagg false. Veldig rart. Oppretter ikke søknad.",
                )
            }
            if (perioder.any { it.reisetilskudd && it.type != REISETILSKUDD }) {
                add(
                    "Sykmelding $sykmeldingId har reisetilskudd flagg true og type ikke reisetilskudd. " +
                        "Type: ${perioder.map { it.type.name }}. Veldig rart. Oppretter ikke søknad.",
                )
            }
        }

    if (ugyldigPeriodeMeldinger.isNotEmpty()) {
        logger().warn(ugyldigPeriodeMeldinger.joinToString(" "))
        return true
    }
    return false
}
