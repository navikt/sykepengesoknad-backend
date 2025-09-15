package no.nav.helse.flex.service

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
                // BrregClient kaster HttpClientErrorException når den mottar en tom liste med roller.
                // Returnerer en tom liste siden det ikke er en en feilsituasjon
                try {
                    hentRoller(fnr)
                } catch (_: HttpClientErrorException.NotFound) {
                    emptyList()
                }
            }.map(::tilBrregRolle)

    private fun hentVentetid(
        identer: FolkeregisterIdenter,
        sykmeldingId: String,
    ): Ventetid? {
        val ikkeHentFor =
            listOf(
                "a81b046f-0274-456e-b3c6-ae64a0c2848d",
                "07441f9c-e86d-471f-86b9-84945b9d4b38",
                "3336a26e-a6cd-4be9-92eb-8d0acde57ba9",
            )

        if (ikkeHentFor.contains(sykmeldingId)) {
            log.info("Henter ikke ventetid for sykmelding: $sykmeldingId da flex-syketilfelle ikke returnerer ventetid for sykmeldingen.")
            return null
        }

        val ventetidResponse =
            flexSyketilfelleClient.hentVentetid(
                identer,
                sykmeldingId,
                VentetidRequest(returnerPerioderInnenforVentetid = true),
            )

        // Det skal alltid kunne beregnes en ventetid for en periode hvis flex-syketilfelle har mottatt bitene fra
        // sykmeldingen.
        return ventetidResponse.ventetid?.let {
            Ventetid(fom = it.fom, tom = it.tom)
        } ?: throw VentetidException("Det ble ikke returnert ventetid for sykmelding: $sykmeldingId")
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
