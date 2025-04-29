package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.client.brreg.RolleDto
import no.nav.helse.flex.client.brreg.RollerDto
import no.nav.helse.flex.client.brreg.Rolletype
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.BrregRolle
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.testoppsett.simpleDispatcher
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_BRREG
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should be empty`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be null`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class OpprettSoknadServiceTest : FellesTestOppsett() {
    @Autowired
    private lateinit var opprettSoknadService: OpprettSoknadService

    @BeforeEach
    fun setup() {
        brregMockWebServer.dispatcher = simpleDispatcher { MockResponse().setResponseCode(200) }
        fakeUnleash.resetAll()
    }

    @Test
    fun `burde ha tom liste med roller når brreg ikke er enabled`() {
        fakeUnleash.disable(UNLEASH_CONTEXT_BRREG)

        val selvstendigNaringsdrivendeInfo =
            opprettSoknadService.hentSelvstendigNaringsdrivendeInfo(
                Arbeidssituasjon.NAERINGSDRIVENDE,
                FolkeregisterIdenter(
                    originalIdent = "fnr",
                    andreIdenter = emptyList(),
                ),
                soknadsId = "1",
            )

        selvstendigNaringsdrivendeInfo.`should not be null`().roller.`should be empty`()
    }

    @Test
    fun `burde ha tom liste med roller når brreg gir NOT_FOUND`() {
        fakeUnleash.enable(UNLEASH_CONTEXT_BRREG)

        brregMockWebServer.dispatcher =
            simpleDispatcher {
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        mapOf(
                            "Message" to "Feil fra Brreg API ved henting av roller. Status: hovedStatus: 1, " +
                                "underStatuser: 180: Personen XXXXXXXXXXX finnes ikke i vår database",
                        ).serialisertTilString(),
                    )
                    .setResponseCode(404)
            }

        val selvstendigNaringsdrivendeInfo =
            opprettSoknadService.hentSelvstendigNaringsdrivendeInfo(
                Arbeidssituasjon.NAERINGSDRIVENDE,
                FolkeregisterIdenter(
                    originalIdent = "fnr",
                    andreIdenter = emptyList(),
                ),
                soknadsId = "1",
            )

        selvstendigNaringsdrivendeInfo.`should not be null`().roller.`should be empty`()
    }

    @Test
    fun `burde ikke ha tom liste med roller når brreg gir roller`() {
        fakeUnleash.enable(UNLEASH_CONTEXT_BRREG)

        brregMockWebServer.dispatcher =
            simpleDispatcher {
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        RollerDto(
                            roller =
                                listOf(
                                    RolleDto(
                                        rolletype = Rolletype.INNH,
                                        organisasjonsnummer = "orgnummer",
                                        organisasjonsnavn = "orgnavn",
                                    ),
                                ),
                        ).serialisertTilString(),
                    )
            }

        val selvstendigNaringsdrivendeInfo =
            opprettSoknadService.hentSelvstendigNaringsdrivendeInfo(
                Arbeidssituasjon.NAERINGSDRIVENDE,
                FolkeregisterIdenter(
                    originalIdent = "fnr",
                    andreIdenter = emptyList(),
                ),
                soknadsId = "1",
            )

        selvstendigNaringsdrivendeInfo.`should not be null`()
            .roller `should be equal to`
            listOf(
                BrregRolle(
                    orgnummer = "orgnummer",
                    orgnavn = "orgnavn",
                    rolletype = Rolletype.INNH.name,
                ),
            )
    }
}
