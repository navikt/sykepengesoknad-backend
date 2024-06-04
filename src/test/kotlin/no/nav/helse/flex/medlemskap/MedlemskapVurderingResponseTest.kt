package no.nav.helse.flex.medlemskap

import com.fasterxml.jackson.databind.DatabindException
import no.nav.helse.flex.util.OBJECT_MAPPER
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should contain same`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tester hvordan deserialisering av response fra LovMe i forbindelse med henting av spørsmål om
 * medlemskap oppfører seg i mulige feilsituasjoner.
 */
class MedlemskapVurderingResponseTest {
    @Test
    fun deserialiserUavklart() {
        val jsonResponse = """
        {
          "svar": "UAVKLART",
          "sporsmal": [
            "OPPHOLDSTILATELSE",
            "ARBEID_UTENFOR_NORGE",
            "OPPHOLD_UTENFOR_EØS_OMRÅDE",
            "OPPHOLD_UTENFOR_NORGE"
          ]
        }
        """

        val response = OBJECT_MAPPER.readValue(jsonResponse, MedlemskapVurderingResponse::class.java)

        response.svar `should be equal to` MedlemskapVurderingSvarType.UAVKLART
        response.sporsmal `should contain same`
            listOf(
                MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
                MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE,
                MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_EØS_OMRÅDE,
                MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_NORGE,
            )
    }

    @Test
    fun deserialiserUkjentSvar() {
        val jsonResponse = """
        {
          "svar": "UKJENT",
          "sporsmal": [
            "OPPHOLDSTILATELSE",
            "OPPHOLD_UTENFOR_NORGE"
          ]
        }
        """

        assertThrows<DatabindException> {
            OBJECT_MAPPER.readValue(jsonResponse, MedlemskapVurderingResponse::class.java)
        }
    }

    @Test
    fun `deserialiser ukjent felt kjentOppholdstilatelse uten feil`() {
        val jsonResponse = """
        {
          "svar": "UAVKLART",
          "sporsmal": [
            "OPPHOLDSTILATELSE",
            "OPPHOLD_UTENFOR_NORGE",
            "ARBEID_UTENFOR_NORGE"
          ],
          "kjentOppholdstilatelse": {
            "fom": "2022-01-01",
            "tom": "2024-02-03"
          }
        }
        """

        val response = OBJECT_MAPPER.readValue(jsonResponse, MedlemskapVurderingResponse::class.java)

        response.svar `should be equal to` MedlemskapVurderingSvarType.UAVKLART
        response.sporsmal `should contain same`
            listOf(
                MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
                MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_NORGE,
                MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE,
            )
    }

    @Test
    fun deserialiserUtenSporsmal() {
        val jsonResponse = """
        {
          "svar": "UAVKLART"
        }
        """

        assertThrows<DatabindException> {
            OBJECT_MAPPER.readValue(jsonResponse, MedlemskapVurderingResponse::class.java)
        }
    }

    @Test
    fun deserialiserTomListeMedSporsmal() {
        val jsonResponse = """
        {
          "svar": "UAVKLART",
          "sporsmal": []
        }
        """

        val response = OBJECT_MAPPER.readValue(jsonResponse, MedlemskapVurderingResponse::class.java)

        response.svar `should be equal to` MedlemskapVurderingSvarType.UAVKLART
        response.sporsmal shouldHaveSize 0
    }
}
