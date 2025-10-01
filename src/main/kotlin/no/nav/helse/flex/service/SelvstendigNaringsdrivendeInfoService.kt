package no.nav.helse.flex.service

import no.nav.helse.flex.client.bregDirect.EnhetsregisterClient
import no.nav.helse.flex.client.brreg.BrregClient
import no.nav.helse.flex.client.brreg.RolleDto
import no.nav.helse.flex.client.brreg.Rolletype
import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.client.flexsyketilfelle.VentetidRequest
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
    ): SelvstendigNaringsdrivendeInfo {
        val roller = hentRoller(identer)

        try {
            loggBarnepasser(roller, sykmeldingId)
        } catch (e: Exception) {
            log.error("Feil ved henting av næringskoder fra Enhetsregisteret for for sykmelding: $sykmeldingId", e)
        }

        return SelvstendigNaringsdrivendeInfo(
            roller = roller,
            ventetid = hentVentetid(identer, sykmeldingId),
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

    private fun loggBarnepasser(
        roller: List<BrregRolle>,
        sykmeldingId: String,
    ) {
        roller.firstOrNull { it.rolletype == Rolletype.INNH.name }?.orgnummer?.let {
            if (enhetsregisterClient.erBarnepasser(it)) {
                log.info("Hentet roller for sykmelding: $sykmeldingId. Sykmeldt har rolle ${Rolletype.INNH} og er barnepasser.")
            } else {
                log.info("Hentet roller for sykmelding: $sykmeldingId. Sykmeldt har rolle ${Rolletype.INNH} men er IKKE barnepasser.")
            }
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

        // TODO: Kast exception når vi flex-syketilfelle skal returnere ventetid for alle tilfeller.
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

class VentetidException(
    string: String,
) : RuntimeException(string)
