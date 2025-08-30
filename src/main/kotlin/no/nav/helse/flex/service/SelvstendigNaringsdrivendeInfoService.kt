package no.nav.helse.flex.service

import no.nav.helse.flex.client.brreg.BrregClient
import no.nav.helse.flex.client.brreg.RolleDto
import no.nav.helse.flex.client.brreg.Rolletype
import no.nav.helse.flex.domain.BrregRolle
import no.nav.helse.flex.domain.SelvstendigNaringsdrivendeInfo
import org.springframework.stereotype.Service

@Service
class SelvstendigNaringsdrivendeInfoService(
    private val brregClient: BrregClient,
) {
    fun hentSelvstendigNaringsdrivendeInfo(identer: FolkeregisterIdenter): SelvstendigNaringsdrivendeInfo {
        val roller = identer.alle().flatMap { hentSelvstendigNaringsdrivendeRoller(it) }.map(::tilBrregRolle)
        return SelvstendigNaringsdrivendeInfo(roller = roller)
    }

    private fun hentSelvstendigNaringsdrivendeRoller(fnr: String): List<RolleDto> {
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
