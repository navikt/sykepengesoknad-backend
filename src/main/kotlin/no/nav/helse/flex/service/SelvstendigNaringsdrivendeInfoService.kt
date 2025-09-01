package no.nav.helse.flex.service

import no.nav.helse.flex.client.brreg.BrregClient
import no.nav.helse.flex.client.brreg.RolleDto
import no.nav.helse.flex.client.brreg.Rolletype
import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.domain.BrregRolle
import no.nav.helse.flex.domain.SelvstendigNaringsdrivendeInfo
import no.nav.helse.flex.domain.VenteperiodeRequest
import no.nav.helse.flex.domain.Ventetid
import org.springframework.stereotype.Service

@Service
class SelvstendigNaringsdrivendeInfoService(
    private val brregClient: BrregClient,
    private val flexSyketilfelleClient: FlexSyketilfelleClient,
) {
    fun hentSelvstendigNaringsdrivendeInfo(identer: FolkeregisterIdenter): SelvstendigNaringsdrivendeInfo {
        val roller = identer.alle().flatMap { hentRoller(it) }.map(::tilBrregRolle)
        return SelvstendigNaringsdrivendeInfo(roller = roller)
    }

    fun hentVentetid(
        identer: FolkeregisterIdenter,
        sykmeldingId: String,
    ): Ventetid {
        val ventetid =
            flexSyketilfelleClient
                .hentVenteperiode(identer, sykmeldingId, VenteperiodeRequest(returnerPerioderInnenforVentetid = true))
                .let {
                    Ventetid(fom = it.venteperiode!!.fom, tom = it.venteperiode.tom)
                }
        return ventetid
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
