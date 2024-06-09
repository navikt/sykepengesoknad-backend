package no.nav.helse.flex.medlemskap

import com.fasterxml.jackson.databind.DatabindException
import no.nav.helse.flex.util.objectMapper
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should contain same`
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

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

        val response = objectMapper.readValue(jsonResponse, MedlemskapVurderingResponse::class.java)

        response.svar `should be equal to` MedlemskapVurderingSvarType.UAVKLART
        response.sporsmal `should contain same`
            listOf(
                MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
                MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE,
                MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_EØS_OMRÅDE,
                MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_NORGE,
            )
        response.kjentOppholdstillatelse shouldBe null
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
    fun deserialiserMidlertidigOppholdstillatelse() {
        val jsonResponse = """
        {
          "svar": "UAVKLART",
          "sporsmal": [
            "OPPHOLDSTILATELSE"
          ],
          "kjentOppholdstillatelse": {
            "fom": "2024-01-01",
            "tom": "2024-01-31"
          }
        }
        """

        val response = objectMapper.readValue(jsonResponse, MedlemskapVurderingResponse::class.java)

        response.svar `should be equal to` MedlemskapVurderingSvarType.UAVKLART
        response.sporsmal `should contain same`
            listOf(
                MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
            )
        response.kjentOppholdstillatelse?.fom `should be equal to` LocalDate.of(2024, 1, 1)
        response.kjentOppholdstillatelse?.tom `should be equal to` LocalDate.of(2024, 1, 31)
    }

    @Test
    fun deserialiserPermanentOppholdstillatelse() {
        val jsonResponse = """
        {
          "svar": "UAVKLART",
          "sporsmal": [
            "OPPHOLDSTILATELSE"
          ],
          "kjentOppholdstillatelse": {
            "fom": "2024-01-01"
          }
        }
        """

        val response = objectMapper.readValue(jsonResponse, MedlemskapVurderingResponse::class.java)

        response.svar `should be equal to` MedlemskapVurderingSvarType.UAVKLART
        response.sporsmal `should contain same`
            listOf(
                MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
            )
        response.kjentOppholdstillatelse?.fom `should be equal to` LocalDate.of(2024, 1, 1)
        response.kjentOppholdstillatelse?.tom shouldBe null
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
        response.kjentOppholdstillatelse?.tom shouldBe null
    }
}
