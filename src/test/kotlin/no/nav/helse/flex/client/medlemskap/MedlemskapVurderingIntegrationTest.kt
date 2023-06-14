package no.nav.helse.flex.client.medlemskap

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import java.time.LocalDate

class MedlemskapVurderingIntegrationTest : BaseTestClass() {

    @Autowired
    private lateinit var medlemskapVurderingClient: MedlemskapVurderingClient

    @Autowired
    private lateinit var medlemskapVurderingRepository: MedlemskapVurderingRepository

    private val fom = LocalDate.of(2023, 1, 1)
    private val tom = LocalDate.of(2023, 1, 31)
    private val request = MedlemskapVurderingRequest(
        fnr = "31111111111",
        fom = fom,
        tom = tom
    )

    @AfterEach
    fun slettFraDatabase() {
        medlemskapVurderingRepository.deleteAll()
    }

    @Test
    fun `hentMedlemskapVurdering request har riktig fnr, fom og tom`() {
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.JA,
                    sporsmal = emptyList()
                ).serialisertTilString()
            ).addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        )

        medlemskapVurderingClient.hentMedlemskapVurdering(request)

        val takeRequest = medlemskapMockWebServer.takeRequest()
        takeRequest.headers["fnr"] `should be equal to` request.fnr
        takeRequest.headers["Authorization"]!!.shouldStartWith("Bearer ey")
        takeRequest.path `should be equal to` "/$MEDLEMSKAP_VURDERING_PATH?fom=$fom&tom=$tom"
    }

    @Test
    fun `hentMedlemskapVurdering svarer med UAVKLART og liste med spørsmål`() {
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.UAVKLART,
                    sporsmal = listOf(
                        MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
                        MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE,
                        MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_EØS_OMRÅDE,
                        MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_NORGE
                    )
                ).serialisertTilString()
            ).addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        )

        val response = medlemskapVurderingClient.hentMedlemskapVurdering(request)

        response.svar `should be equal to` MedlemskapVurderingSvarType.UAVKLART
        response.sporsmal `should contain same` listOf(
            MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
            MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE,
            MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_EØS_OMRÅDE,
            MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_NORGE
        )

        medlemskapVurderingRepository.findAll() shouldHaveSize 1
    }

    @Test
    fun `hentMedlemskapVurdering svarer med JA og tom liste`() {
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.JA,
                    sporsmal = emptyList()
                ).serialisertTilString()
            ).addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        )

        val response = medlemskapVurderingClient.hentMedlemskapVurdering(request)

        response.svar `should be equal to` MedlemskapVurderingSvarType.JA
        response.sporsmal shouldHaveSize 0

        medlemskapVurderingRepository.findAll() shouldHaveSize 1
    }

    @Test
    fun `hentMedlemskapVurdering svarer med NEI og tom liste`() {
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.NEI,
                    sporsmal = emptyList()
                ).serialisertTilString()
            ).addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        )

        val response = medlemskapVurderingClient.hentMedlemskapVurdering(request)

        response.svar `should be equal to` MedlemskapVurderingSvarType.NEI
        response.sporsmal shouldHaveSize 0

        medlemskapVurderingRepository.findAll() shouldHaveSize 1
    }

    @Test
    fun `hentMedlemskapVurdering kaster exception når det returneres HttpStatus 5xx`() {
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(500)
        )

        val exception =
            assertThrows<MedlemskapVurderingClientException> {
                medlemskapVurderingClient.hentMedlemskapVurdering(request)
            }

        exception.message `should be equal to` "Feil ved kall til MedlemskapVurdering."
        exception.cause!!.message?.shouldStartWith("500 Server Error")

        medlemskapVurderingRepository.findAll() shouldHaveSize 0
    }

    @Test
    fun `hentMedlemskapVurdering kaster exception når det returneres HttpStatus 4xx`() {
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(400)
        )

        val exception =
            assertThrows<MedlemskapVurderingClientException> {
                medlemskapVurderingClient.hentMedlemskapVurdering(request)
            }

        exception.message `should be equal to` "Feil ved kall til MedlemskapVurdering."
        exception.cause!!.message?.shouldStartWith("400 Client Error")

        medlemskapVurderingRepository.findAll() shouldHaveSize 0
    }

    @Test
    fun `hentMedlemskapVurdering kaster exception når vi får UAVKLART, men tom liste med spørsmål`() {
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.UAVKLART,
                    sporsmal = emptyList()
                ).serialisertTilString()
            ).addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        )

        val exception =
            assertThrows<MedlemskapVurderingClientException> {
                medlemskapVurderingClient.hentMedlemskapVurdering(request)
            }

        exception.message `should be equal to` "MedlemskapVurdering returnerte svar.UAVKLART uten spørsmål."

        // Verdier blir lagret før vi validerer responsen.
        medlemskapVurderingRepository.findAll() shouldHaveSize 1
    }

    @Test
    fun `hentMedlemskapVurdering kaster exception når vi får avklart JA, men liste MED spørsmål`() {
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.JA,
                    sporsmal = listOf(
                        MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE
                    )
                ).serialisertTilString()
            ).addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        )

        val exception =
            assertThrows<MedlemskapVurderingClientException> {
                medlemskapVurderingClient.hentMedlemskapVurdering(request)
            }

        exception.message `should be equal to` "MedlemskapVurdering returnerte spørsmål selv om svar var svar.JA."

        // Verdier blir lagret før vi validerer responsen.
        medlemskapVurderingRepository.findAll() shouldHaveSize 1
    }

    @Test
    fun `hentMedlemskapVurdering kaster exception når vi får avklart NEI, men liste MED spørsmål`() {
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.NEI,
                    sporsmal = listOf(
                        MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE
                    )
                ).serialisertTilString()
            ).addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        )
        val exception =
            assertThrows<MedlemskapVurderingClientException> {
                medlemskapVurderingClient.hentMedlemskapVurdering(request)
            }

        exception.message `should be equal to` "MedlemskapVurdering returnerte spørsmål selv om svar var svar.JA."

        // Verdier blir lagret før vi validerer responsen.
        medlemskapVurderingRepository.findAll() shouldHaveSize 1
    }
}
