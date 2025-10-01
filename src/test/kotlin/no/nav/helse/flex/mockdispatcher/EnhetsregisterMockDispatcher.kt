package no.nav.helse.flex.mockdispatcher

object EnhetsregisterMockDispatcher : FellesQueueDispatcher<String>(
    defaultFactory = {
        val orgnr = it.path?.substringAfterLast("/") ?: "976967631"
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
    },
)
