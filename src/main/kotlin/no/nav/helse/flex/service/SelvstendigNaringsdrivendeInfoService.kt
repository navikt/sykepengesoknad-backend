package no.nav.helse.flex.service

import no.nav.helse.flex.client.bregDirect.EnhetsregisterClient
import no.nav.helse.flex.client.brreg.BrregClient
import no.nav.helse.flex.client.brreg.RolleDto
import no.nav.helse.flex.client.brreg.Rolletype
import no.nav.helse.flex.domain.BrregRolle
import no.nav.helse.flex.domain.SelvstendigNaringsdrivendeInfo
import no.nav.helse.flex.logger
import org.springframework.stereotype.Service

@Service
class SelvstendigNaringsdrivendeInfoService(
    private val brregClient: BrregClient,
    private val enhetsregisterClient: EnhetsregisterClient,
) {
    fun hentSelvstendigNaringsdrivendeInfo(identer: FolkeregisterIdenter): SelvstendigNaringsdrivendeInfo {
        val rolleDtoer = identer.alle().flatMap { hentSelvstendigNaringsdrivendeRoller(it) }
        val roller = rolleDtoer.map(::mapFraRolleDto)

        val brukersOrgnr = rolleDtoer.filter { it.rolletype == Rolletype.INNH }.firstOrNull()?.organisasjonsnummer

        if (brukersOrgnr != null) {
            // enhetsregisterClient.erDagmamma("057767855")
            enhetsregisterClient.erDagmamma(brukersOrgnr)
            logger().info("Bruker er dagmamma med orgnr $brukersOrgnr")
        } else {
            logger().info("Bruker er ikke dagmamma")
        }

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
