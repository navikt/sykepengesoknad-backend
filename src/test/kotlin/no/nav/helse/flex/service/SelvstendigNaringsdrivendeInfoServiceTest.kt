package no.nav.helse.flex.service

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.QueueDispatcher
import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.client.bregDirect.NAERINGSKODE_BARNEPASSER
import no.nav.helse.flex.client.brreg.RolleDto
import no.nav.helse.flex.client.brreg.RollerDto
import no.nav.helse.flex.client.brreg.Rolletype
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.mockdispatcher.EnhetsregisterMockDispatcher
import no.nav.helse.flex.mockdispatcher.withContentTypeApplicationJson
import no.nav.helse.flex.util.serialisertTilString
import org.amshove.kluent.invoking
import org.amshove.kluent.`should be empty`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.client.HttpServerErrorException

private const val ORGNAVN = "orgnavn"
private const val ORGNUMMER = "orgnummer"

class SelvstendigNaringsdrivendeInfoServiceTest : FakesTestOppsett() {
    @Autowired
    @Qualifier("brregMockWebServer")
    lateinit var brregMockWebServer: MockWebServer

    @Autowired
    lateinit var selvstendigNaringsdrivendeInfoService: SelvstendigNaringsdrivendeInfoService

    /**
     * MockWebServeren deles mellom testene, så køen nullstilles for hver test. Uten dette vil svar
     * som en test ikke rakk å konsumere bli plukket opp av neste test.
     */
    @BeforeEach
    fun nullstillBrregKo() {
        brregMockWebServer.dispatcher = QueueDispatcher()
    }

    /**
     * MockWebServeren er delt for hele JVM-en, så dispatcheren må settes tilbake til standard når
     * klassen er ferdig. Ellers arver senere testklasser dispatcheren siste test her satte.
     */
    @AfterAll
    fun gjenopprettBrregDispatcher() {
        brregMockWebServer.dispatcher = QueueDispatcher()
    }

    @Test
    fun `Returnerer ventetid og roller for én ident med forsikring`() {
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse(body = (Rolletype.INNH).tilRollerDto().serialisertTilString())
            },
        )

        val selvstendigNaringsdrivendeInfo =
            selvstendigNaringsdrivendeInfoService
                .lagSelvstendigNaringsdrivendeInfo(
                    identer = FolkeregisterIdenter("11111111111", andreIdenter = emptyList()),
                    sykmeldingId = "sykmelding-id",
                    arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                    brukerHarOppgittForsikring = true,
                )

        selvstendigNaringsdrivendeInfo.roller.single().also {
            it.orgnavn `should be equal to` ORGNAVN
            it.orgnummer `should be equal to` ORGNUMMER
            it.rolletype `should be equal to` "INNH"
        }

        selvstendigNaringsdrivendeInfo.also {
            it.erBarnepasser `should be equal to` false
            it.brukerHarOppgittForsikring `should be equal to` true
        }
    }

    @Test
    fun `Returnerer roller for sykmeldt som er barnepasser`() {
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse(body = (Rolletype.INNH).tilRollerDto().serialisertTilString())
            },
        )

        val json = """{"naeringskode1": {"kode": "$NAERINGSKODE_BARNEPASSER"}}"""
        EnhetsregisterMockDispatcher.enqueueResponse(withContentTypeApplicationJson { MockResponse(body = json) })

        val selvstendigNaringsdrivendeInfo =
            selvstendigNaringsdrivendeInfoService
                .lagSelvstendigNaringsdrivendeInfo(
                    identer = FolkeregisterIdenter("11111111111", andreIdenter = emptyList()),
                    sykmeldingId = "sykmelding-id",
                    arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                )

        selvstendigNaringsdrivendeInfo.roller.single().also {
            it.orgnavn `should be equal to` ORGNAVN
            it.orgnummer `should be equal to` ORGNUMMER
            it.rolletype `should be equal to` "INNH"
        }

        selvstendigNaringsdrivendeInfo.also {
            it.erBarnepasser `should be equal to` true
            it.brukerHarOppgittForsikring `should be equal to` false
        }
    }

    @Test
    fun `Skal ikke vurdere om er barnepasser med mindre bruker er NAERINGSDRIVENDE`() {
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse(body = (Rolletype.INNH).tilRollerDto().serialisertTilString())
            },
        )

        val antallKall = EnhetsregisterMockDispatcher.antallKall()

        val selvstendigNaringsdrivendeInfo =
            selvstendigNaringsdrivendeInfoService
                .lagSelvstendigNaringsdrivendeInfo(
                    identer = FolkeregisterIdenter("11111111111", andreIdenter = emptyList()),
                    sykmeldingId = "sykmelding-id",
                    arbeidssituasjon = Arbeidssituasjon.BARNEPASSER,
                )

        selvstendigNaringsdrivendeInfo.roller.single().also {
            it.orgnavn `should be equal to` ORGNAVN
            it.orgnummer `should be equal to` ORGNUMMER
            it.rolletype `should be equal to` "INNH"
        }

        selvstendigNaringsdrivendeInfo.also {
            it.erBarnepasser `should be equal to` false
            it.brukerHarOppgittForsikring `should be equal to` false
        }

        EnhetsregisterMockDispatcher.antallKall() `should be equal to` antallKall
    }

    @Test
    fun `Returnerer ventetid og roller for flere identer`() {
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse(body = (Rolletype.INNH).tilRollerDto().serialisertTilString())
            },
        )
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse(body = (Rolletype.DAGL).tilRollerDto().serialisertTilString())
            },
        )

        val selvstendigNaringsdrivendeInfo =
            selvstendigNaringsdrivendeInfoService
                .lagSelvstendigNaringsdrivendeInfo(
                    identer = FolkeregisterIdenter("11111111111", andreIdenter = listOf("22222222222")),
                    sykmeldingId = "sykmelding-id",
                    arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                )

        selvstendigNaringsdrivendeInfo.roller.also {
            it.size `should be equal to` 2

            it.first().also { rolle ->
                rolle.orgnavn `should be equal to` ORGNAVN
                rolle.orgnummer `should be equal to` ORGNUMMER
                rolle.rolletype `should be equal to` "INNH"
            }

            it.last().also { rolle ->
                rolle.orgnavn `should be equal to` ORGNAVN
                rolle.orgnummer `should be equal to` ORGNUMMER
                rolle.rolletype `should be equal to` "DAGL"
            }
        }

        selvstendigNaringsdrivendeInfo.also {
            it.erBarnepasser `should be equal to` false
            it.brukerHarOppgittForsikring `should be equal to` false
        }
    }

    @Test
    fun `Returnerer ventetid og roller når roller mangler for én ident`() {
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse(body = (Rolletype.INNH).tilRollerDto().serialisertTilString())
            },
        )

        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse(code = 200, body = """{"roller": []}""")
            },
        )

        val selvstendigNaringsdrivendeInfo =
            selvstendigNaringsdrivendeInfoService
                .lagSelvstendigNaringsdrivendeInfo(
                    identer = FolkeregisterIdenter("11111111111", andreIdenter = listOf("22222222222")),
                    sykmeldingId = "sykmelding-id",
                    arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                )

        selvstendigNaringsdrivendeInfo.roller.single().also {
            it.orgnavn `should be equal to` ORGNAVN
            it.orgnummer `should be equal to` ORGNUMMER
            it.rolletype `should be equal to` "INNH"
        }

        selvstendigNaringsdrivendeInfo.also {
            it.erBarnepasser `should be equal to` false
            it.brukerHarOppgittForsikring `should be equal to` false
        }
    }

    @Test
    fun `Returnerer ventetid og tom liste med roller når én ident mangler roller`() {
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse(code = 200, body = """{"roller": []}""")
            },
        )

        val selvstendigNaringsdrivendeInfo =
            selvstendigNaringsdrivendeInfoService
                .lagSelvstendigNaringsdrivendeInfo(
                    identer = FolkeregisterIdenter("11111111111", emptyList()),
                    sykmeldingId = "sykmelding-id",
                    arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                )

        selvstendigNaringsdrivendeInfo.roller.`should be empty`()

        selvstendigNaringsdrivendeInfo.also {
            it.erBarnepasser `should be equal to` false
            it.brukerHarOppgittForsikring `should be equal to` false
        }
    }

    @Test
    fun `Returnerer ventetid og tom liste med roller når alle identer mangler roller`() {
        repeat(2) {
            brregMockWebServer.enqueue(
                withContentTypeApplicationJson {
                    MockResponse(code = 200, body = """{"roller": []}""")
                },
            )
        }

        val selvstendigNaringsdrivendeInfo =
            selvstendigNaringsdrivendeInfoService
                .lagSelvstendigNaringsdrivendeInfo(
                    identer = FolkeregisterIdenter("11111111111", listOf("22222222222")),
                    sykmeldingId = "sykmelding-id",
                    arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                )

        selvstendigNaringsdrivendeInfo.roller.`should be empty`()

        selvstendigNaringsdrivendeInfo.also {
            it.erBarnepasser `should be equal to` false
            it.brukerHarOppgittForsikring `should be equal to` false
        }
    }

    @Test
    fun `Det returneres null for ventetid når flex-syketilfelle ikke returneres ventetid`() {
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse(body = (Rolletype.INNH).tilRollerDto().serialisertTilString())
            },
        )

        selvstendigNaringsdrivendeInfoService
            .lagSelvstendigNaringsdrivendeInfo(
                FolkeregisterIdenter("11111111111", andreIdenter = emptyList()),
                sykmeldingId = "sykmelding-id",
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
            ).also {
                it.roller.single().also { rolle ->
                    rolle.orgnavn `should be equal to` ORGNAVN
                    rolle.orgnummer `should be equal to` ORGNUMMER
                    rolle.rolletype `should be equal to` "INNH"
                }
                it.erBarnepasser `should be equal to` false
                it.brukerHarOppgittForsikring `should be equal to` false
            }
    }

    @Test
    fun `ServerError propagerer ved henting av roller`() {
        // Svarer 500 på alle kall, slik at testen ikke er avhengig av hvor mange forsøk @Retryable gjør.
        brregMockWebServer.dispatcher =
            QueueDispatcher().apply {
                setFailFast(withContentTypeApplicationJson { MockResponse(code = 500) })
            }

        invoking {
            selvstendigNaringsdrivendeInfoService.lagSelvstendigNaringsdrivendeInfo(
                FolkeregisterIdenter("11111111111", andreIdenter = emptyList()),
                sykmeldingId = "sykmelding-id",
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
            )
        }.shouldThrow(HttpServerErrorException::class)
    }

    @Test
    fun `ServerError propagerer ikke ved henting av næringskoder fra Enhetsregisteret`() {
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse(body = (Rolletype.INNH).tilRollerDto().serialisertTilString())
            },
        )

        EnhetsregisterMockDispatcher.enqueueResponse(
            withContentTypeApplicationJson {
                MockResponse(code = 500)
            },
        )

        val selvstendigNaringsdrivendeInfo =
            selvstendigNaringsdrivendeInfoService
                .lagSelvstendigNaringsdrivendeInfo(
                    identer = FolkeregisterIdenter("11111111111", andreIdenter = emptyList()),
                    sykmeldingId = "sykmelding-id",
                    arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                )

        selvstendigNaringsdrivendeInfo.roller.single().also {
            it.orgnavn `should be equal to` ORGNAVN
            it.orgnummer `should be equal to` ORGNUMMER
            it.rolletype `should be equal to` "INNH"
        }

        selvstendigNaringsdrivendeInfo.also {
            it.erBarnepasser `should be equal to` false
            it.brukerHarOppgittForsikring `should be equal to` false
        }
    }
}

private fun Rolletype.tilRollerDto() =
    RollerDto(
        roller =
            listOf(
                RolleDto(
                    rolletype = this,
                    organisasjonsnummer = ORGNUMMER,
                    organisasjonsnavn = ORGNAVN,
                ),
            ),
    )
