package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Arbeidssituasjon.FRILANSER
import no.nav.helse.flex.domain.Arbeidssituasjon.NAERINGSDRIVENDE
import no.nav.helse.flex.domain.ErUtenforVentetidRequest
import no.nav.helse.flex.domain.SykmeldingBehandletResultat
import no.nav.helse.flex.domain.SykmeldingBehandletResultat.AVVENTENDE_SYKMELDING
import no.nav.helse.flex.domain.SykmeldingBehandletResultat.IKKE_DIGITALISERT
import no.nav.helse.flex.domain.SykmeldingBehandletResultat.INNENFOR_VENTETID
import no.nav.helse.flex.domain.SykmeldingBehandletResultat.SYKMELDING_OK
import no.nav.helse.flex.domain.SykmeldingBehandletResultat.UNDER_BEHANDLING
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.logger
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.util.Metrikk
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO.AVVENTENDE
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO.REISETILSKUDD
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
