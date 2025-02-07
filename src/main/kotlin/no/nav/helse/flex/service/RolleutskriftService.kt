package no.nav.helse.flex.service

import no.nav.helse.flex.client.brreg.BrregClient
import no.nav.helse.flex.client.brreg.Rolle
import no.nav.helse.flex.client.brreg.RolleType
import no.nav.helse.flex.domain.SelvstendigNaringsdrivende
import org.springframework.stereotype.Service

@Service
class RolleutskriftService(
    private val brregClient: BrregClient,
) {
    fun hentSelvstendigNaringsdrivende(fnr: String): SelvstendigNaringsdrivende =
        SelvstendigNaringsdrivende.mapFraRoller(hentSelvstendigNaringsdrivendeRoller(fnr))

    internal fun hentSelvstendigNaringsdrivendeRoller(fnr: String): List<Rolle> {
        val selvstendigNaringsdrivendeRoller = RolleType.entries.filter(RolleType::erSelvstendigNaringdrivende)
        return brregClient.hentRoller(fnr, selvstendigNaringsdrivendeRoller)
    }
}
