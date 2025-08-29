package no.nav.helse.flex.mockdispatcher

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest

// brukes nå, bedre enn den enkle? 
object EnhetsregisterMockDispatcher : QueueDispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        if (responseQueue.peek() != null) {
            return responseQueue.take()
        }

        val orgnr = request.path?.substringAfterLast("/")

        return when (orgnr) {
            "404404404" -> MockResponse().setResponseCode(404)
            else -> {
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(createEnhetsregisterResponse(orgnr))
            }
        }
    }
}

private fun createEnhetsregisterResponse(orgnr: String? = "976967631"): String =
    """
    {
      "organisasjonsnummer": "$orgnr",
      "navn": "TELENOR NORGE AS",
      "organisasjonsform": {
        "kode": "AS",
        "beskrivelse": "Aksjeselskap",
        "_links": {
          "self": {
            "href": "https://data.brreg.no/enhetsregisteret/api/organisasjonsformer/AS"
          }
        }
      },
      "postadresse": {
        "land": "Norge",
        "landkode": "NO",
        "postnummer": "1331",
        "poststed": "FORNEBU",
        "adresse": [
          "Postboks 800"
        ],
        "kommune": "BÆRUM",
        "kommunenummer": "3201"
      },
      "naeringskode1": {
        "kode": "61.200",
        "beskrivelse": "Trådløs telekommunikasjon"
      },
      "antallAnsatte": 2868,
      "forretningsadresse": {
        "land": "Norge",
        "landkode": "NO",
        "postnummer": "1360",
        "poststed": "FORNEBU",
        "adresse": [
          "Snarøyveien 30"
        ],
        "kommune": "BÆRUM",
        "kommunenummer": "3201"
      },
      "maalform": "Bokmål",
      "_links": {
        "self": {
          "href": "https://data.brreg.no/enhetsregisteret/api/enheter/$orgnr"
        }
      }
    }
    """.trimIndent()
