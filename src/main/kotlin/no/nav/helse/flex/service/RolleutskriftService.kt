package no.nav.helse.flex.service

import no.nav.helse.flex.client.brreg.BrregClient
import no.nav.helse.flex.client.brreg.Rolle
import no.nav.helse.flex.client.brreg.Rolletype
import no.nav.helse.flex.domain.SelvstendigNaringsdrivende
import org.springframework.stereotype.Service

@Service
class RolleutskriftService(
    private val brregClient: BrregClient,
) {
    fun hentSelvstendigNaringsdrivende(fnr: String): SelvstendigNaringsdrivende =
        SelvstendigNaringsdrivende.mapFraRoller(hentSelvstendigNaringsdrivendeRoller(fnr))

    internal fun hentSelvstendigNaringsdrivendeRoller(fnr: String): List<Rolle> {
        val selvstendigNaringsdrivendeRoller = Rolletype.entries.filter(Rolletype::erSelvstendigNaringdrivende)
        return brregClient.hentRoller(fnr, selvstendigNaringsdrivendeRoller)
    }
}
