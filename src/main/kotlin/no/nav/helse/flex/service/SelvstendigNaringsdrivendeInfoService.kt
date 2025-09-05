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

    private val log = logger()

    private fun loggOmBrukerErDagmamma(rolleDtoer: List<RolleDto>) {
        val brukersOrgnr = rolleDtoer.firstOrNull { it.rolletype == Rolletype.INNH }?.organisasjonsnummer

        if (brukersOrgnr != null && enhetsregisterClient.erDagmamma(brukersOrgnr)) {
            log.info("Hentet roller fra Brreg: sykmeldt har arbeidssituasjon BARNEPASSER.")
        } else {
            log.info("Hentet roller fra Brreg: sykmeldt har IKKE arbeidssituasjon BARNEPASSER.")
        }
    }

    fun hentSelvstendigNaringsdrivendeInfo(identer: FolkeregisterIdenter): SelvstendigNaringsdrivendeInfo {
        val rolleDtoer = identer.alle().flatMap { hentSelvstendigNaringsdrivendeRoller(it) }
        val roller = rolleDtoer.map(::mapFraRolleDto)

        rolleDtoer.filter { it.rolletype == Rolletype.INNH }.firstOrNull()?.organisasjonsnummer

        loggOmBrukerErDagmamma(rolleDtoer)
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
