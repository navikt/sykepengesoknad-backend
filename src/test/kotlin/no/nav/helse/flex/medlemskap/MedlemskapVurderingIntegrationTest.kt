package no.nav.helse.flex.medlemskap

import no.nav.helse.flex.BaseTestClass
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should contain same`
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldStartWith
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.*

class MedlemskapVurderingIntegrationTest : BaseTestClass() {

    @Autowired
    private lateinit var medlemskapVurderingClient: MedlemskapVurderingClient

    @Autowired
    private lateinit var medlemskapVurderingRepository: MedlemskapVurderingRepository

    private val sykepengesoknadId = UUID.randomUUID().toString()

    @BeforeAll
    fun `Tøm requests til medlemskapVurdering`() {
        repeat(medlemskapMockWebServer.requestCount) {
            medlemskapMockWebServer.takeRequest()
        }
    }

    private val fom = LocalDate.of(2023, 1, 1)
    private val tom = LocalDate.of(2023, 1, 31)
    private val request = MedlemskapVurderingRequest(
        fnr = "",
        fom = fom,
        tom = tom,
        sykepengesoknadId = sykepengesoknadId
    )

    @AfterEach
    fun resetDatabase() {
        medlemskapVurderingRepository.deleteAll()
    }

    @Test
    fun `hentMedlemskapVurdering svarer med UAVKLART og liste med spørsmål`() {
        val fnr = "31111111111"
        val response = medlemskapVurderingClient.hentMedlemskapVurdering(request.copy(fnr = fnr))

        response.svar `should be equal to` MedlemskapVurderingSvarType.UAVKLART
        response.sporsmal `should contain same` listOf(
            MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
            MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE,
            MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_EØS_OMRÅDE,
            MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_NORGE
        )

        val takeRequest = medlemskapMockWebServer.takeRequest()
        takeRequest.headers["fnr"] `should be equal to` fnr
        takeRequest.headers["Authorization"]!!.shouldStartWith("Bearer ey")
        takeRequest.path `should be equal to` "/$MEDLEMSKAP_VURDERING_PATH?fom=$fom&tom=$tom"

        medlemskapVurderingRepository.findAll() shouldHaveSize 1
    }

    @Test
    fun `hentMedlemskapVurdering svarer med JA og tom liste`() {
        val fnr = "31111111112"
        val response = medlemskapVurderingClient.hentMedlemskapVurdering(request.copy(fnr = fnr))

        response.svar `should be equal to` MedlemskapVurderingSvarType.JA
        response.sporsmal shouldHaveSize 0

        val takeRequest = medlemskapMockWebServer.takeRequest()
        takeRequest.headers["fnr"] `should be equal to` fnr

        medlemskapVurderingRepository.findAll() shouldHaveSize 1
    }

    @Test
    fun `hentMedlemskapVurdering svarer med NEI og tom liste`() {
        val fnr = "31111111113"
        val response = medlemskapVurderingClient.hentMedlemskapVurdering(request.copy(fnr = fnr))

        response.svar `should be equal to` MedlemskapVurderingSvarType.NEI
        response.sporsmal shouldHaveSize 0

        val takeRequest = medlemskapMockWebServer.takeRequest()
        takeRequest.headers["fnr"] `should be equal to` fnr

        medlemskapVurderingRepository.findAll() shouldHaveSize 1
    }

    @Test
    fun `hentMedlemskapVurdering kaster exception når det returneres HttpStatus 5xx`() {
        val fnr = "31111111114"
        val exception =
            assertThrows<MedlemskapVurderingClientException> {
                medlemskapVurderingClient.hentMedlemskapVurdering(
                    request.copy(fnr = fnr)
                )
            }

        exception.message `should be equal to` "Feil ved kall til MedlemskapVurdering."
        exception.cause!!.message?.shouldStartWith("500 Server Error")

        val takeRequest = medlemskapMockWebServer.takeRequest()
        takeRequest.headers["fnr"] `should be equal to` fnr

        medlemskapVurderingRepository.findAll() shouldHaveSize 0
    }

    @Test
    fun `hentMedlemskapVurdering kaster exception når det returneres HttpStatus 4xx`() {
        val fnr = "31111111115"
        val exception =
            assertThrows<MedlemskapVurderingClientException> {
                medlemskapVurderingClient.hentMedlemskapVurdering(
                    request.copy(fnr = fnr)
                )
            }

        exception.message `should be equal to` "Feil ved kall til MedlemskapVurdering."
        exception.cause!!.message?.shouldStartWith("400 Client Error")

        val takeRequest = medlemskapMockWebServer.takeRequest()
        takeRequest.headers["fnr"] `should be equal to` fnr

        medlemskapVurderingRepository.findAll() shouldHaveSize 0
    }

    @Test
    fun `hentMedlemskapVurdering kaster exception når vi får UAVKLART, men tom liste med spørsmål`() {
        val fnr = "31111111116"
        val exception =
            assertThrows<MedlemskapVurderingResponseException> {
                medlemskapVurderingClient.hentMedlemskapVurdering(
                    request.copy(fnr = fnr)
                )
            }

        exception.message `should be equal to` "MedlemskapVurdering returnerte svar.UAVKLART uten spørsmål."

        val takeRequest = medlemskapMockWebServer.takeRequest()
        takeRequest.headers["fnr"] `should be equal to` fnr

        // Verdier blir lagret før vi validerer responsen.
        medlemskapVurderingRepository.findAll() shouldHaveSize 1
    }

    @Test
    fun `hentMedlemskapVurdering kaster exception når vi får avklart JA, men liste MED spørsmål`() {
        val fnr = "31111111117"
        val exception =
            assertThrows<MedlemskapVurderingResponseException> {
                medlemskapVurderingClient.hentMedlemskapVurdering(
                    request.copy(fnr = fnr)
                )
            }

        exception.message `should be equal to` "MedlemskapVurdering returnerte spørsmål selv om svar var svar.JA."

        val takeRequest = medlemskapMockWebServer.takeRequest()
        takeRequest.headers["fnr"] `should be equal to` fnr

        // Verdier blir lagret før vi validerer responsen.
        medlemskapVurderingRepository.findAll() shouldHaveSize 1
    }

    @Test
    fun `hentMedlemskapVurdering kaster exception når vi får avklart NEI, men liste MED spørsmål`() {
        val fnr = "31111111118"
        val exception =
            assertThrows<MedlemskapVurderingResponseException> {
                medlemskapVurderingClient.hentMedlemskapVurdering(
                    request.copy(fnr = fnr)
                )
            }

        exception.message `should be equal to` "MedlemskapVurdering returnerte spørsmål selv om svar var svar.NEI."

        val takeRequest = medlemskapMockWebServer.takeRequest()
        takeRequest.headers["fnr"] `should be equal to` fnr

        // Verdier blir lagret før vi validerer responsen.
        medlemskapVurderingRepository.findAll() shouldHaveSize 1
    }
}
