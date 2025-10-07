package no.nav.helse.flex.service

import no.nav.helse.flex.client.bregDirect.EnhetsregisterClient
import no.nav.helse.flex.client.brreg.BrregClient
import no.nav.helse.flex.client.brreg.RolleDto
import no.nav.helse.flex.client.brreg.Rolletype
import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.client.flexsyketilfelle.VentetidRequest
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Arbeidssituasjon.NAERINGSDRIVENDE
import no.nav.helse.flex.domain.BrregRolle
import no.nav.helse.flex.domain.SelvstendigNaringsdrivendeInfo
import no.nav.helse.flex.domain.Ventetid
import no.nav.helse.flex.logger
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException

@Service
class SelvstendigNaringsdrivendeInfoService(
    private val brregClient: BrregClient,
    private val flexSyketilfelleClient: FlexSyketilfelleClient,
    private val enhetsregisterClient: EnhetsregisterClient,
) {
    private val log = logger()

    fun hentSelvstendigNaringsdrivendeInfo(
        identer: FolkeregisterIdenter,
        sykmeldingId: String,
        arbeidssituasjon: Arbeidssituasjon,
    ): SelvstendigNaringsdrivendeInfo {
        val roller = hentRoller(identer)
        return SelvstendigNaringsdrivendeInfo(
            roller = roller,
            ventetid = hentVentetid(identer, sykmeldingId),
            erBarnepasser = erNaeringsdrivendeBarnepasser(roller, sykmeldingId, arbeidssituasjon),
        )
    }

    private fun hentRoller(identer: FolkeregisterIdenter): List<BrregRolle> =
        identer
            .alle()
            .flatMap { fnr ->
                // BrregClient kaster HttpClientErrorException når den mottar en tom liste med roller.
                // Returnerer en tom liste siden det ikke er en en feilsituasjon
                try {
                    hentRoller(fnr)
                } catch (_: HttpClientErrorException.NotFound) {
                    emptyList()
                }
            }.map(::tilBrregRolle)

    private fun erNaeringsdrivendeBarnepasser(
        roller: List<BrregRolle>,
        sykmeldingId: String,
        arbeidssituasjon: Arbeidssituasjon,
    ): Boolean {
        if (arbeidssituasjon != NAERINGSDRIVENDE) {
            return false
        }

        val orgnummer =
            roller.firstOrNull { it.rolletype == Rolletype.INNH.name }?.orgnummer
                ?: run {
                    log.warn("Fant ikke rolle INNH for sykmelding: $sykmeldingId. Kan ikke avgjøre om sykmeldt er barnepasser.")
                    return false
                }
        return try {
            val erBarnepasser = enhetsregisterClient.erBarnepasser(orgnummer)
            if (erBarnepasser) {
                log.info("Sykmeldt har næringskode for BARNEPASSER for sykmelding: $sykmeldingId")
            } else {
                log.info("Sykmeldt har ikke næringskode for BARNEPASSER for sykmelding: $sykmeldingId")
            }
            return erBarnepasser
        } catch (e: Exception) {
            log.error("Kall til Enhetsregisteret feilet ved sjekk av barnepasser for sykmelding: $sykmeldingId", e)
            false
        }
    }

    private fun hentVentetid(
        identer: FolkeregisterIdenter,
        sykmeldingId: String,
    ): Ventetid? {
        val ventetidResponse =
            flexSyketilfelleClient.hentVentetid(
                identer,
                sykmeldingId,
                VentetidRequest(returnerPerioderInnenforVentetid = true),
            )

        // TODO: Kast VentetidException når vi vet flex-syketilfelle alltid skal returnere ventetid.
        return ventetidResponse.ventetid?.let {
            Ventetid(fom = it.fom, tom = it.tom)
        } ?: run {
            log.error("Det ble ikke returnert ventetid for sykmelding: $sykmeldingId")
            null
        }
    }

    private fun hentRoller(fnr: String): List<RolleDto> {
        val selvstendigNaringsdrivendeRoller =
            listOf(
                Rolletype.INNH,
                Rolletype.DTPR,
                Rolletype.DTSO,
                Rolletype.KOMP,
            )
        return brregClient.hentRoller(fnr, selvstendigNaringsdrivendeRoller)
    }

    private fun tilBrregRolle(rolle: RolleDto) =
        BrregRolle(
            orgnummer = rolle.organisasjonsnummer,
            orgnavn = rolle.organisasjonsnavn,
            rolletype = rolle.rolletype.name,
        )
}
