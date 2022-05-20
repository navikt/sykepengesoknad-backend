package no.nav.syfo.service

import no.nav.syfo.client.pdl.AKTORID
import no.nav.syfo.client.pdl.FOLKEREGISTERIDENT
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.client.pdl.PdlIdent
import no.nav.syfo.client.pdl.ResponseData
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import java.io.Serializable

@Component
class IdentService(private val pdlClient: PdlClient) {

    private fun List<PdlIdent>.folkeregisteridenter(): List<String> {
        return this.filter { it.gruppe == FOLKEREGISTERIDENT }.map { it.ident }
    }

    @Cacheable("folkeregister-identer-med-historikk")
    fun hentFolkeregisterIdenterMedHistorikkForFnr(fnr: String): FolkeregisterIdenter {
        val identer = pdlClient.hentIdenterMedHistorikk(fnr)
        return FolkeregisterIdenter(
            originalIdent = fnr,
            andreIdenter = identer.folkeregisteridenter().filterNot { it == fnr }
        )
    }

    private fun ResponseData.aktorId(): String {
        return this.hentIdenter?.identer?.find { it.gruppe == AKTORID }?.ident
            ?: throw RuntimeException("Kunne ikke finne aktørid i pdl response")
    }
}

data class FolkeregisterIdenter(val originalIdent: String, val andreIdenter: List<String>) : Serializable {
    fun alle(): List<String> {
        return mutableListOf(originalIdent).also { it.addAll(andreIdenter) }
    }
}
