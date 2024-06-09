package no.nav.helse.flex.medlemskap

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should contain same`
import org.amshove.kluent.`should not be`
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldStartWith
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.*

/**
 * Tester hvordan koden som integrerer med LovMe for å hente spørsmål om medlemskap
 * oppfører seg basert på hva LovMe svarer, inkludert tester for både tekniske feil
 * (f.eks nettverksfeil) og det vi tolker som logiske feil i responsen.
 */
class MedlemskapVurderingIntegrationTest : FellesTestOppsett() {
    @Autowired
    private lateinit var medlemskapVurderingClient: MedlemskapVurderingClient

    @Autowired
    private lateinit var medlemskapVurderingRepository: MedlemskapVurderingRepository

    @AfterEach
    fun slettFraDatabase() {
        medlemskapVurderingRepository.deleteAll()
    }

    private val sykepengesoknadId = UUID.randomUUID().toString()
    private val fom = LocalDate.of(2023, 1, 1)
    private val tom = LocalDate.of(2023, 1, 31)

    private val request =
        MedlemskapVurderingRequest(
            fnr = "",
            fom = fom,
            tom = tom,
            sykepengesoknadId = sykepengesoknadId,
        )

    @Test
    fun `hentMedlemskapVurdering svarer med UAVKLART og liste med spørsmål`() {
        val fnr = "31111111111"
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.UAVKLART,
                    sporsmal =
                        listOf(
                            MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
                            MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE,
                            MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_EØS_OMRÅDE,
                            MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_NORGE,
                        ),
                ).serialisertTilString(),
            ),
        )

        val response = medlemskapVurderingClient.hentMedlemskapVurdering(request.copy(fnr = fnr))

        response.svar `should be equal to` MedlemskapVurderingSvarType.UAVKLART
        response.sporsmal `should contain same`
            listOf(
                MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
                MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE,
                MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_EØS_OMRÅDE,
                MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_NORGE,
            )
        response.kjentOppholdstillatelse shouldBe null

        val dbRecords = medlemskapVurderingRepository.findAll() shouldHaveSize 1
        dbRecords.first().sporsmal `should not be` null
    }

    @Test
    fun `hentMedlemskapVurdering svarer med UAVKLART og tom liste`() {
        val fnr = "31111111116"
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.UAVKLART,
                    sporsmal = emptyList(),
                ).serialisertTilString(),
            ),
        )

        val response = medlemskapVurderingClient.hentMedlemskapVurdering(request.copy(fnr = fnr))

        response.svar `should be equal to` MedlemskapVurderingSvarType.UAVKLART
        response.sporsmal shouldHaveSize 0
        response.kjentOppholdstillatelse shouldBe null

        medlemskapVurderingRepository.findAll() shouldHaveSize 1

        // Vi lagrer ikke tom liste med spørsmål.
        val dbRecords = medlemskapVurderingRepository.findAll() shouldHaveSize 1
        dbRecords.first().sporsmal `should be equal to` null
    }

    @Test
    fun `hentMedlemskapVurdering returnerer kjentOppholdstillatelse sammen med spørsmål om OPPHOLDSTILLATELSE`() {
        val fnr = "31111111111"
        val kjentOppholdstillatelse =
            KjentOppholdstillatelse(
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 31),
            )
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.UAVKLART,
                    sporsmal =
                        listOf(
                            MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
                        ),
                    kjentOppholdstillatelse = kjentOppholdstillatelse,
                ).serialisertTilString(),
            ),
        )

        val response = medlemskapVurderingClient.hentMedlemskapVurdering(request.copy(fnr = fnr))

        response.svar `should be equal to` MedlemskapVurderingSvarType.UAVKLART
        response.sporsmal `should contain same`
            listOf(
                MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
            )
        response.kjentOppholdstillatelse `should be equal to` kjentOppholdstillatelse

        val dbRecords = medlemskapVurderingRepository.findAll() shouldHaveSize 1
        dbRecords.first().kjentOppholdstillatelse!!.value `should be equal to` kjentOppholdstillatelse.serialisertTilString()
    }

    @Test
    fun `hentMedlemskapVurdering svarer med JA og tom liste`() {
        val fnr = "31111111112"
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.JA,
                    sporsmal = emptyList(),
                ).serialisertTilString(),
            ),
        )
        val response = medlemskapVurderingClient.hentMedlemskapVurdering(request.copy(fnr = fnr))

        response.svar `should be equal to` MedlemskapVurderingSvarType.JA
        response.sporsmal shouldHaveSize 0
        response.kjentOppholdstillatelse shouldBe null

        // Vi lagrer ikke tom liste med spørsmål.
        val dbRecords = medlemskapVurderingRepository.findAll() shouldHaveSize 1
        dbRecords.first().sporsmal `should be equal to` null
    }

    @Test
    fun `hentMedlemskapVurdering svarer med NEI og tom liste`() {
        val fnr = "31111111113"
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.NEI,
                    sporsmal = emptyList(),
                ).serialisertTilString(),
            ),
        )

        val response = medlemskapVurderingClient.hentMedlemskapVurdering(request.copy(fnr = fnr))

        response.svar `should be equal to` MedlemskapVurderingSvarType.NEI
        response.sporsmal shouldHaveSize 0
        response.kjentOppholdstillatelse shouldBe null

        medlemskapVurderingRepository.findAll() shouldHaveSize 1

        // Vi lagrer ikke tom liste med spørsmål.
        val dbRecords = medlemskapVurderingRepository.findAll() shouldHaveSize 1
        dbRecords.first().sporsmal `should be equal to` null
    }

    @Test
    fun `hentMedlemskapVurdering kaster exception når det returneres HttpStatus 5xx`() {
        val fnr = "31111111114"
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(500),
        )

        val exception =
            assertThrows<MedlemskapVurderingClientException> {
                medlemskapVurderingClient.hentMedlemskapVurdering(
                    request.copy(fnr = fnr),
                )
            }

        exception.message?.shouldStartWith("MedlemskapVurdering med Nav-Call-Id:")
        exception.cause!!.message?.shouldStartWith("500 Server Error")

        medlemskapVurderingRepository.findAll() shouldHaveSize 0
    }

    @Test
    fun `hentMedlemskapVurdering kaster exception når det returneres HttpStatus 4xx`() {
        val fnr = "31111111115"
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(400),
        )

        val exception =
            assertThrows<MedlemskapVurderingClientException> {
                medlemskapVurderingClient.hentMedlemskapVurdering(
                    request.copy(fnr = fnr),
                )
            }

        exception.message?.shouldStartWith("MedlemskapVurdering med Nav-Call-Id:")
        exception.cause!!.message?.shouldStartWith("400 Client Error")

        medlemskapVurderingRepository.findAll() shouldHaveSize 0
    }

    @Test
    fun `hentMedlemskapVurdering kaster exception når vi får avklart JA og liste med spørsmål`() {
        val fnr = "31111111117"
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.JA,
                    sporsmal =
                        listOf(
                            MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
                        ),
                ).serialisertTilString(),
            ),
        )

        val exception =
            assertThrows<MedlemskapVurderingResponseException> {
                medlemskapVurderingClient.hentMedlemskapVurdering(
                    request.copy(fnr = fnr),
                )
            }

        exception.message?.shouldStartWith("MedlemskapVurdering med Nav-Call-Id:")
        exception.message?.shouldContain("returnerte spørsmål selv om svar var svar.JA.")

        medlemskapVurderingRepository.findAll() shouldHaveSize 1

        // Verdier blir lagret før vi validerer responsen sånn at vi kan feilsøke.
        val dbRecords = medlemskapVurderingRepository.findAll() shouldHaveSize 1
        dbRecords.first().sporsmal `should not be` null
    }

    @Test
    fun `hentMedlemskapVurdering kaster exception når vi får avklart NEI og liste med spørsmål`() {
        val fnr = "31111111118"
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.NEI,
                    sporsmal =
                        listOf(
                            MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
                        ),
                ).serialisertTilString(),
            ),
        )

        val exception =
            assertThrows<MedlemskapVurderingResponseException> {
                medlemskapVurderingClient.hentMedlemskapVurdering(
                    request.copy(fnr = fnr),
                )
            }

        exception.message?.shouldStartWith("MedlemskapVurdering med Nav-Call-Id:")
        exception.message?.shouldContain("returnerte spørsmål selv om svar var svar.NEI.")

        // Verdier blir lagret før vi validerer responsen sånn at vi kan feilsøke.
        val dbRecords = medlemskapVurderingRepository.findAll() shouldHaveSize 1
        dbRecords.first().sporsmal `should not be` null
    }

    @Test
    fun `hentMedlemskapVurdering kaster exception når vi får kjentOppholdstillatelse uten tilhørende spørsmål`() {
        val fnr = "31111111117"
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.UAVKLART,
                    sporsmal =
                        listOf(
                            MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE,
                        ),
                    kjentOppholdstillatelse =
                        KjentOppholdstillatelse(
                            fom = LocalDate.of(2024, 1, 1),
                            tom = LocalDate.of(2024, 1, 31),
                        ),
                ).serialisertTilString(),
            ),
        )

        val exception =
            assertThrows<MedlemskapVurderingResponseException> {
                medlemskapVurderingClient.hentMedlemskapVurdering(
                    request.copy(fnr = fnr),
                )
            }

        exception.message?.shouldStartWith("MedlemskapVurdering med Nav-Call-Id:")
        exception.message?.shouldContain(
            "returnerte kjentOppholdstillatelse når spørsmål ${MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE} mangler.",
        )

        medlemskapVurderingRepository.findAll() shouldHaveSize 1

        // Verdier blir lagret før vi validerer responsen sånn at vi kan feilsøke.
        val dbRecords = medlemskapVurderingRepository.findAll() shouldHaveSize 1
        dbRecords.first().sporsmal `should not be` null
    }
}
