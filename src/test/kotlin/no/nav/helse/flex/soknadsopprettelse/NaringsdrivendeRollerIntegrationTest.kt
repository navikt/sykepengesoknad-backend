package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.client.brreg.RolleDto
import no.nav.helse.flex.client.brreg.RollerDto
import no.nav.helse.flex.client.brreg.Rolletype
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.BrregRolle
import no.nav.helse.flex.domain.Venteperiode
import no.nav.helse.flex.domain.VenteperiodeResponse
import no.nav.helse.flex.domain.Ventetid
import no.nav.helse.flex.mockFlexSyketilfelleVenteperiode
import no.nav.helse.flex.mockdispatcher.withContentTypeApplicationJson
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.testoppsett.simpleDispatcher
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_BRREG
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should be empty`
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class NaringsdrivendeRollerIntegrationTest : FellesTestOppsett() {
    @Autowired
    private lateinit var opprettSoknadService: OpprettSoknadService

    @BeforeEach
    fun setup() {
        brregMockWebServer.dispatcher = simpleDispatcher { MockResponse().setResponseCode(200) }
        fakeUnleash.resetAll()
    }

    @BeforeEach
    fun setUp() {
        flexSyketilfelleMockRestServiceServer.reset()
    }

    @Test
    fun `Returnerer tom liste med roller når kall til Brreg ikke er enabled`() {
        fakeUnleash.disable(UNLEASH_CONTEXT_BRREG)

        mockFlexSyketilfelleVenteperiode(
            "sykmelding-id",
            VenteperiodeResponse(Venteperiode(LocalDate.now(), LocalDate.now().plusDays(1))),
        )

        opprettSoknadService
            .hentSelvstendigNaringsdrivendeInfo(
                Arbeidssituasjon.NAERINGSDRIVENDE,
                FolkeregisterIdenter(
                    originalIdent = "11111111111",
                    andreIdenter = emptyList(),
                ),
                "sykmelding-id",
            ).also {
                it!!.roller.`should be empty`()
                it.ventetid `should be equal to` Ventetid(LocalDate.now(), LocalDate.now().plusDays(1))
            }
    }

    @Test
    fun `Returnerer tom liste med roller når Brreg svarer med NOT_FOUND`() {
        fakeUnleash.enable(UNLEASH_CONTEXT_BRREG)

        mockFlexSyketilfelleVenteperiode(
            "sykmelding-id",
            VenteperiodeResponse(Venteperiode(LocalDate.now(), LocalDate.now().plusDays(1))),
        )

        brregMockWebServer.dispatcher =
            simpleDispatcher {
                withContentTypeApplicationJson {
                    MockResponse()
                        .setBody(
                            mapOf(
                                "Message" to "Feil fra Brreg API ved henting av roller. Status: hovedStatus: 1, " +
                                    "underStatuser: 180: Personen XXXXXXXXXXX finnes ikke i vår database",
                            ).serialisertTilString(),
                        ).setResponseCode(404)
                }
            }

        opprettSoknadService
            .hentSelvstendigNaringsdrivendeInfo(
                Arbeidssituasjon.NAERINGSDRIVENDE,
                FolkeregisterIdenter(
                    originalIdent = "11111111111",
                    andreIdenter = emptyList(),
                ),
                "sykmelding-id",
            ).also {
                it!!.roller.`should be empty`()
                it.ventetid `should be equal to` Ventetid(LocalDate.now(), LocalDate.now().plusDays(1))
            }
    }

    @Test
    fun `Returnerer liste med roller når kall til Brreg er enabled`() {
        fakeUnleash.enable(UNLEASH_CONTEXT_BRREG)

        mockFlexSyketilfelleVenteperiode(
            "sykmelding-id",
            VenteperiodeResponse(Venteperiode(LocalDate.now(), LocalDate.now().plusDays(1))),
        )

        brregMockWebServer.dispatcher =
            simpleDispatcher {
                withContentTypeApplicationJson {
                    MockResponse()
                        .setBody(
                            RollerDto(
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
            }

        opprettSoknadService
            .hentSelvstendigNaringsdrivendeInfo(
                Arbeidssituasjon.NAERINGSDRIVENDE,
                FolkeregisterIdenter(
                    originalIdent = "11111111111",
                    andreIdenter = emptyList(),
                ),
                "sykmelding-id",
            ).also {
                it!!.roller `should be equal to`
                    listOf(
                        BrregRolle(
                            orgnummer = "orgnummer",
                            orgnavn = "orgnavn",
                            rolletype = Rolletype.INNH.name,
                        ),
                    )
                it.ventetid `should be equal to` Ventetid(LocalDate.now(), LocalDate.now().plusDays(1))
            }
    }
}
