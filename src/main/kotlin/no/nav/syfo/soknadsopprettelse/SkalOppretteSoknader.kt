package no.nav.syfo.soknadsopprettelse

import no.nav.syfo.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.syfo.domain.*
import no.nav.syfo.domain.Arbeidssituasjon.*
import no.nav.syfo.domain.SykmeldingBehandletResultat.*
import no.nav.syfo.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.syfo.logger
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO.*
import no.nav.syfo.service.FolkeregisterIdenter
import no.nav.syfo.util.Metrikk
import org.springframework.stereotype.Service

@Service
class SkalOppretteSoknader(
    private val flexSyketilfelleClient: FlexSyketilfelleClient,
    private val metrikk: Metrikk,
) {
    private val log = logger()

    fun skalOppretteSoknader(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        arbeidssituasjon: Arbeidssituasjon,
        identer: FolkeregisterIdenter,
    ): SykmeldingBehandletResultat {

        val sykmelding = sykmeldingKafkaMessage.sykmelding
        val sykmeldingId = sykmelding.id

        val perioder = sykmelding.sykmeldingsperioder
        if (perioder.any { it.type == AVVENTENDE }) {
            metrikk.utelattSykmeldingFraSoknadOpprettelse("avventende")
            log.info("Sykmelding ${sykmelding.id} har periodetype AVVENTENDE vi ennå ikke oppretter søknader for")
            return AVVENTENDE_SYKMELDING
        }

        if (sykmelding.merknader?.any { it.type == "UNDER_BEHANDLING" } == true) {
            metrikk.utelattSykmeldingFraSoknadOpprettelse("under_behandling")
            log.info("Sykmelding ${sykmelding.id} har merknad UNDER_BEHANDLING vi ikke oppretter søknader for")
            return UNDER_BEHANDLING
        }

        if (perioder.any { it.type == REISETILSKUDD && !it.reisetilskudd }) {
            log.info("Sykmelding ${sykmelding.id} har periodetype REISETILSKUDD og reisetilskudd flagg false. Veldig rart. Oppretter ikke søknad.")
            metrikk.utelattSykmeldingFraSoknadOpprettelse("flagg_false_periodetype_reisetilskudd")
            return IKKE_DIGITALISERT
        }

        if (perioder.any { it.reisetilskudd && it.type != REISETILSKUDD }) {
            val periodetyper = perioder.map { it.type.name }
            metrikk.utelattSykmeldingFraSoknadOpprettelse("flagg_true_periodetype_ikke_reisetilskudd")
            log.info("Sykmelding ${sykmelding.id} har reisetilskudd flagg true og type ikke reisetilskudd. Type: $periodetyper.  Veldig rart. Oppretter ikke søknad.")
            return IKKE_DIGITALISERT
        }

        if ((arbeidssituasjon == FRILANSER || arbeidssituasjon == NAERINGSDRIVENDE) && !sykmeldingKafkaMessage.harForsikring()) {
            val erUtenforVentetid = flexSyketilfelleClient.erUtenforVentetid(
                identer = identer,
                sykmeldingId = sykmeldingId,
                erUtenforVentetidRequest = ErUtenforVentetidRequest(
                    sykmeldingKafkaMessage = sykmeldingKafkaMessage
                )
            )
            if (!erUtenforVentetid) {
                log.info("Sykmelding $sykmeldingId er beregnet til å være innenfor ventetiden. Oppretter ikke søknad")
                return INNENFOR_VENTETID
            }
        }

        return SYKMELDING_OK
    }
}
