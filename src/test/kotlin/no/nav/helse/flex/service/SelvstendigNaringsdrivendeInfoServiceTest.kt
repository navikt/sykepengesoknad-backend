package no.nav.helse.flex.service

import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.FellesTestOppsett.Companion.enhetsregisterMockWebServer
import no.nav.helse.flex.client.bregDirect.NAERINGSKODE_BARNEPASSER
import no.nav.helse.flex.client.brreg.RolleDto
import no.nav.helse.flex.client.brreg.RollerDto
import no.nav.helse.flex.client.brreg.Rolletype
import no.nav.helse.flex.client.flexsyketilfelle.FomTomPeriode
import no.nav.helse.flex.client.flexsyketilfelle.VentetidResponse
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Ventetid
import no.nav.helse.flex.mockdispatcher.withContentTypeApplicationJson
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.amshove.kluent.invoking
import org.amshove.kluent.`should be empty`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.test.context.TestPropertySource
import org.springframework.web.client.HttpServerErrorException
import java.time.LocalDate

private const val ORGNAVN = "orgnavn"
private const val ORGNUMMER = "orgnummer"

@TestPropertySource(properties = ["CLIENT_RETRY_ATTEMPTS=1"])
class SelvstendigNaringsdrivendeInfoServiceTest : FakesTestOppsett() {
    @Autowired
    @Qualifier("brregMockWebServer")
    lateinit var brregMockWebServer: MockWebServer

    @Autowired
    @Qualifier("ventetidMockWebServer")
    lateinit var ventetidMockWebServer: MockWebServer

    @Autowired
    lateinit var selvstendigNaringsdrivendeInfoService: SelvstendigNaringsdrivendeInfoService

    private val fom = LocalDate.now()
    private val tom = LocalDate.now().plusDays(1)

    @Test
    fun `Returnerer ventetid og roller for én ident`() {
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody((Rolletype.INNH).tilRollerDto().serialisertTilString())
            },
        )

        ventetidMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody(lagventetidResponse(fom, tom).serialisertTilString())
            },
        )

        val selvstendigNaringsdrivendeInfo =
            selvstendigNaringsdrivendeInfoService
                .hentSelvstendigNaringsdrivendeInfo(
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
            it.ventetid `should be equal to` Ventetid(fom, tom)
            it.erBarnepasser `should be equal to` false
        }
    }

    @Test
    fun `Returnerer roller for sykmeldt som er barnepasser`() {
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody((Rolletype.INNH).tilRollerDto().serialisertTilString())
            },
        )

        val json = """{"naeringskode1": {"kode": "$NAERINGSKODE_BARNEPASSER"}}"""
        enhetsregisterMockWebServer.enqueue(withContentTypeApplicationJson { MockResponse().setBody(json) })

        ventetidMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody(lagventetidResponse(fom, tom).serialisertTilString())
            },
        )

        val selvstendigNaringsdrivendeInfo =
            selvstendigNaringsdrivendeInfoService
                .hentSelvstendigNaringsdrivendeInfo(
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
            it.ventetid `should be equal to` Ventetid(fom, tom)
            it.erBarnepasser `should be equal to` true
        }
    }

    @Test
    fun `Skal ikke vurdere om er barnepasser med mindre bruker er NAERINGSDRIVENDE`() {
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody((Rolletype.INNH).tilRollerDto().serialisertTilString())
            },
        )

        ventetidMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody(lagventetidResponse(fom, tom).serialisertTilString())
            },
        )

        val selvstendigNaringsdrivendeInfo =
            selvstendigNaringsdrivendeInfoService
                .hentSelvstendigNaringsdrivendeInfo(
                    identer = FolkeregisterIdenter("11111111111", andreIdenter = emptyList()),
                    sykmeldingId = "sykmelding-id",
                    arbeidssituasjon = Arbeidssituasjon.JORDBRUKER,
                )

        selvstendigNaringsdrivendeInfo.roller.single().also {
            it.orgnavn `should be equal to` ORGNAVN
            it.orgnummer `should be equal to` ORGNUMMER
            it.rolletype `should be equal to` "INNH"
        }

        selvstendigNaringsdrivendeInfo.also {
            it.ventetid `should be equal to` Ventetid(fom, tom)
            it.erBarnepasser `should be equal to` false
        }
    }

    @Test
    fun `Returnerer ventetid og roller for flere identer`() {
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody((Rolletype.INNH).tilRollerDto().serialisertTilString())
            },
        )
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody((Rolletype.DAGL).tilRollerDto().serialisertTilString())
            },
        )
        ventetidMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody(lagventetidResponse(fom, tom).serialisertTilString())
            },
        )

        val selvstendigNaringsdrivendeInfo =
            selvstendigNaringsdrivendeInfoService
                .hentSelvstendigNaringsdrivendeInfo(
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
            it.ventetid `should be equal to` Ventetid(fom, tom)
            it.erBarnepasser `should be equal to` false
        }
    }

    @Test
    fun `Returnerer ventetid og roller når roller mangler for én ident`() {
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody((Rolletype.INNH).tilRollerDto().serialisertTilString())
            },
        )

        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setResponseCode(404)
            },
        )

        ventetidMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody(lagventetidResponse(fom, tom).serialisertTilString())
            },
        )

        val selvstendigNaringsdrivendeInfo =
            selvstendigNaringsdrivendeInfoService
                .hentSelvstendigNaringsdrivendeInfo(
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
            it.ventetid `should be equal to` Ventetid(fom, tom)
            it.erBarnepasser `should be equal to` false
        }
    }

    @Test
    fun `Returnerer ventetid og tom liste med roller når én ident mangler roller`() {
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setResponseCode(404)
            },
        )

        ventetidMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody(lagventetidResponse(fom, tom).serialisertTilString())
            },
        )

        val selvstendigNaringsdrivendeInfo =
            selvstendigNaringsdrivendeInfoService
                .hentSelvstendigNaringsdrivendeInfo(
                    identer = FolkeregisterIdenter("11111111111", emptyList()),
                    sykmeldingId = "sykmelding-id",
                    arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                )

        selvstendigNaringsdrivendeInfo.roller.`should be empty`()

        selvstendigNaringsdrivendeInfo.also {
            it.ventetid `should be equal to` Ventetid(fom, tom)
            it.erBarnepasser `should be equal to` false
        }
    }

    @Test
    fun `Returnerer ventetid og tom liste med roller når alle identer mangler roller`() {
        repeat(2) {
            brregMockWebServer.enqueue(
                withContentTypeApplicationJson {
                    MockResponse().setResponseCode(404)
                },
            )
        }

        ventetidMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody(lagventetidResponse(fom, tom).serialisertTilString())
            },
        )

        val selvstendigNaringsdrivendeInfo =
            selvstendigNaringsdrivendeInfoService
                .hentSelvstendigNaringsdrivendeInfo(
                    identer = FolkeregisterIdenter("11111111111", listOf("22222222222")),
                    sykmeldingId = "sykmelding-id",
                    arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                )

        selvstendigNaringsdrivendeInfo.roller.`should be empty`()

        selvstendigNaringsdrivendeInfo.also {
            it.ventetid `should be equal to` Ventetid(fom, tom)
            it.erBarnepasser `should be equal to` false
        }
    }

    @Test
    fun `Det returneres null for ventetid når flex-syketilfelle ikke returneres ventetid`() {
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody((Rolletype.INNH).tilRollerDto().serialisertTilString())
            },
        )

        ventetidMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody(
                    lagventetidResponse(fom, tom).copy(ventetid = null).serialisertTilString(),
                )
            },
        )

        selvstendigNaringsdrivendeInfoService
            .hentSelvstendigNaringsdrivendeInfo(
                FolkeregisterIdenter("11111111111", andreIdenter = emptyList()),
                sykmeldingId = "sykmelding-id",
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
            ).also {
                it.ventetid `should be equal to` null
                it.roller.single().also { rolle ->
                    rolle.orgnavn `should be equal to` ORGNAVN
                    rolle.orgnummer `should be equal to` ORGNUMMER
                    rolle.rolletype `should be equal to` "INNH"
                }
                it.erBarnepasser `should be equal to` false
            }
    }

    @Test
    fun `ServerError propagerer ved henting av ventetid`() {
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody((Rolletype.INNH).tilRollerDto().serialisertTilString())
            },
        )

        ventetidMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setResponseCode(500)
            },
        )

        invoking {
            selvstendigNaringsdrivendeInfoService.hentSelvstendigNaringsdrivendeInfo(
                FolkeregisterIdenter("11111111111", andreIdenter = emptyList()),
                sykmeldingId = "sykmelding-id",
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
            )
        }.shouldThrow(HttpServerErrorException::class)
    }

    @Test
    fun `ServerError propagerer ved henting av roller`() {
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setResponseCode(500)
            },
        )

        invoking {
            selvstendigNaringsdrivendeInfoService.hentSelvstendigNaringsdrivendeInfo(
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
                MockResponse().setBody((Rolletype.INNH).tilRollerDto().serialisertTilString())
            },
        )

        enhetsregisterMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setResponseCode(500)
            },
        )

        ventetidMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody(lagventetidResponse(fom, tom).serialisertTilString())
            },
        )

        val selvstendigNaringsdrivendeInfo =
            selvstendigNaringsdrivendeInfoService
                .hentSelvstendigNaringsdrivendeInfo(
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
            it.ventetid `should be equal to` Ventetid(fom, tom)
            it.erBarnepasser `should be equal to` false
        }
    }

    @Test
    fun `ServerError propagerer ikke ved henting av næringskoder fra enhetsregisteret`() {
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody((Rolletype.INNH).tilRollerDto().serialisertTilString())
            },
        )

        enhetsregisterMockWebServer.enqueue(withContentTypeApplicationJson { MockResponse().setResponseCode(500) })

        ventetidMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody(lagventetidResponse(fom, tom).serialisertTilString())
            },
        )

        val selvstendigNaringsdrivendeInfo =
            selvstendigNaringsdrivendeInfoService
                .hentSelvstendigNaringsdrivendeInfo(
                    identer = FolkeregisterIdenter("11111111111", andreIdenter = emptyList()),
                    sykmeldingId = "sykmelding-id",
                    arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                )

        selvstendigNaringsdrivendeInfo.roller.single().also {
            it.orgnavn `should be equal to` ORGNAVN
            it.orgnummer `should be equal to` ORGNUMMER
            it.rolletype `should be equal to` "INNH"
        }

        selvstendigNaringsdrivendeInfo.ventetid `should be equal to` Ventetid(fom, tom)
    }
}

private fun lagventetidResponse(
    fom: LocalDate,
    tom: LocalDate,
): VentetidResponse = VentetidResponse(FomTomPeriode(fom = fom, tom = tom))

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
