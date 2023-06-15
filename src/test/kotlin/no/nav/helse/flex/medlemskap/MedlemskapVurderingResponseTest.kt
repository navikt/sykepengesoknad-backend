package no.nav.helse.flex.medlemskap

import com.fasterxml.jackson.databind.DatabindException
import no.nav.security.mock.oauth2.http.objectMapper
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should contain same`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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

        val response = objectMapper.readValue(jsonResponse, MedlemskapVurderingResponse::class.java)

        response.svar `should be equal to` MedlemskapVurderingSvarType.UAVKLART
        response.sporsmal `should contain same` listOf(
            MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
            MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE,
            MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_EØS_OMRÅDE,
            MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_NORGE
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
            objectMapper.readValue(jsonResponse, MedlemskapVurderingResponse::class.java)
        }
    }

    @Test
    fun deserialiserUtenSporsmal() {
        val jsonResponse = """
        {
          "svar": "UAVKLART"
        }
        """

        assertThrows<DatabindException> {
            objectMapper.readValue(jsonResponse, MedlemskapVurderingResponse::class.java)
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

        val response = objectMapper.readValue(jsonResponse, MedlemskapVurderingResponse::class.java)

        response.svar `should be equal to` MedlemskapVurderingSvarType.UAVKLART
        response.sporsmal shouldHaveSize 0
    }
}
