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
        // TODO: Hent roller for alle identer?
        val fnr = identer.originalIdent
        val rolleDtoer = hentSelvstendigNaringsdrivendeRoller(fnr)
        val roller = rolleDtoer.map(::mapFraRolleDto)
        return SelvstendigNaringsdrivendeInfo(roller = roller)
    }

    internal fun hentSelvstendigNaringsdrivendeRoller(fnr: String): List<RolleDto> {
        val selvstendigNaringsdrivendeRoller =
            listOf(
                Rolletype.INNH,
                Rolletype.DTPR,
                Rolletype.DTSO,
                Rolletype.KOMP,
            )
        return brregClient.hentRoller(fnr, selvstendigNaringsdrivendeRoller)
    }

    companion object {
        fun mapFraRolleDto(rolle: RolleDto) =
            BrregRolle(
                orgnummer = rolle.organisasjonsnummer,
                orgnavn = rolle.organisasjonsnavn,
                rolletype = rolle.rolletype.name,
            )
    }
}
