package no.nav.helse.flex.service

import no.nav.helse.flex.client.brreg.BrregClient
import no.nav.helse.flex.client.brreg.RolleDto
import no.nav.helse.flex.client.brreg.Rolletype
import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.domain.BrregRolle
import no.nav.helse.flex.domain.SelvstendigNaringsdrivendeInfo
import no.nav.helse.flex.domain.Ventetid
import no.nav.helse.flex.domain.VentetidRequest
import no.nav.helse.flex.logger
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException

@Service
class SelvstendigNaringsdrivendeInfoService(
    private val brregClient: BrregClient,
    private val flexSyketilfelleClient: FlexSyketilfelleClient,
) {
    private val log = logger()

    fun hentSelvstendigNaringsdrivendeInfo(
        identer: FolkeregisterIdenter,
        sykmeldingId: String,
    ): SelvstendigNaringsdrivendeInfo =
        SelvstendigNaringsdrivendeInfo(
            roller = hentRoller(identer),
            ventetid = hentVentetid(identer, sykmeldingId),
        )

    private fun hentRoller(identer: FolkeregisterIdenter): List<BrregRolle> =
        identer
            .alle()
            .flatMap { fnr ->
                // BrregClient kaster exception når den mottar en tom liste med roller.
                try {
                    hentRoller(fnr)
                } catch (_: HttpClientErrorException.NotFound) {
                    emptyList()
                }
            }.map(::tilBrregRolle)

    private fun hentVentetid(
        identer: FolkeregisterIdenter,
        sykmeldingId: String,
    ): Ventetid {
        val ventetidResponse =
            flexSyketilfelleClient.hentVentetid(
                identer,
                sykmeldingId,
                VentetidRequest(returnerPerioderInnenforVentetid = true),
            )

        val ventetid =
            ventetidResponse.ventetid
                // Det skal alltid kunne beregnes en ventetid for en sykmlding hvis flex-syketilfelle har mottatt bitene.
                ?: throw VentetidException("Det ble ikke returnert ventetid for sykmeldingId: $sykmeldingId.")

        return Ventetid(fom = ventetid.fom, tom = ventetid.tom)
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
