import no.nav.helse.flex.client.bregDirect.EnhetsregisterClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

class EnhetsregisterClientTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: EnhetsregisterClient

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val restClient =
            RestClient
                .builder()
                .baseUrl(mockWebServer.url("/").toString().removeSuffix("/"))
                .build()
        client =
            EnhetsregisterClient(
                restClientBuilder = RestClient.builder(),
                baseUrl = mockWebServer.url("/").toString().removeSuffix("/"),
            )
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `erDagmamma returns false for non-dagmamma org`() {
        val json =
            """
            {
              "respons_klasse": "Enhet",
              "organisasjonsnummer": "509100675",
              "navn": "Sesam stasjon",
              "naeringskode1": {
                "kode": "41.109",
                "beskrivelse": "Utvikling og salg av egen fast eiendom ellers"
              },
              "naeringskode2": {
                "kode": "41.109",
                "beskrivelse": "Utvikling og salg av egen fast eiendom ellers"
              },
              "naeringskode3": {
                "kode": "41.109",
                "beskrivelse": "Utvikling og salg av egen fast eiendom ellers"
              }
            }
            """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(json).setHeader("Content-Type", "application/json"))
        assertFalse(client.erDagmamma("509100675"))
    }

    @Test
    fun `erDagmamma returns true for dagmamma org`() {
        val json =
            """
{
  "organisasjonsnummer": "922720262",
  "navn": "DAGMAMMA MARIE JOHANSEN",
  "organisasjonsform": {
    "kode": "ENK",
    "beskrivelse": "Enkeltpersonforetak",
    "_links": {
      "self": {
        "href": "https://data.brreg.no/enhetsregisteret/api/organisasjonsformer/ENK"
      }
    }
  },
  "registreringsdatoEnhetsregisteret": "2019-06-19",
  "registrertIMvaregisteret": false,
  "naeringskode1": {
    "kode": "88.912",
    "beskrivelse": "Barneparker og dagmammaer"
  },
  "harRegistrertAntallAnsatte": false,
  "forretningsadresse": {
    "land": "Norge",
    "landkode": "NO",
    "postnummer": "0190",
    "poststed": "OSLO",
    "adresse": [
      "H0207",
      "Ingenstedsgaten 17"
    ],
    "kommune": "OSLO",
    "kommunenummer": "0301"
  },
  "institusjonellSektorkode": {
    "kode": "8200",
    "beskrivelse": "Personlig næringsdrivende"
  },
  "registrertIForetaksregisteret": false,
  "registrertIStiftelsesregisteret": false,
  "registrertIFrivillighetsregisteret": false,
  "konkurs": false,
  "underAvvikling": false,
  "underTvangsavviklingEllerTvangsopplosning": false,
  "maalform": "Bokmål",
  "aktivitet": [
    "Dagmamma og barnepass tjeneste i privat hjemme."
  ],
  "registrertIPartiregisteret": false,
  "paategninger": [],
  "_links": {
    "self": {
      "href": "https://data.brreg.no/enhetsregisteret/api/enheter/922720193"
    }
  },
  "respons_klasse": "Enhet"
}
            """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(json).setHeader("Content-Type", "application/json"))
        assertTrue(client.erDagmamma("922720193"))
    }
}
