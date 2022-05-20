package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.client.pdl.AKTORID
import no.nav.helse.flex.client.pdl.FOLKEREGISTERIDENT
import no.nav.helse.flex.client.pdl.GetPersonResponse
import no.nav.helse.flex.client.pdl.HentIdenter
import no.nav.helse.flex.client.pdl.PdlClient
import no.nav.helse.flex.client.pdl.PdlIdent
import no.nav.helse.flex.client.pdl.ResponseData
import no.nav.helse.flex.util.OBJECT_MAPPER
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

object PdlMockDispatcher : Dispatcher() {

    override fun dispatch(request: RecordedRequest): MockResponse {
        val graphReq: PdlClient.GraphQLRequest = OBJECT_MAPPER.readValue(request.body.readUtf8())
        val ident = graphReq.variables["ident"] ?: return MockResponse().setStatus("400").setBody("Ingen ident variabel")

        if (ident.startsWith("2")) {
            return skapResponse(listOf(ident, ident.replaceFirstChar { "1" }))
        }
        if (ident.startsWith("3")) {
            return skapResponse(listOf(ident, ident.replaceFirstChar { "1" }, ident.replaceFirstChar { "2" }))
        }
        return skapResponse(listOf(ident))
    }

    fun skapResponse(identer: List<String>): MockResponse {
        val pdlIdenter = identer
            .map { PdlIdent(gruppe = FOLKEREGISTERIDENT, ident = it) }.toMutableList()
            .also { it.add(PdlIdent(gruppe = AKTORID, ident = identer.first() + "00")) }

        return MockResponse().setBody(
            GetPersonResponse(
                data = ResponseData(
                    hentIdenter = HentIdenter(
                        identer = pdlIdenter
                    )
                ),
                errors = null
            ).serialisertTilString()
        )
    }
}
