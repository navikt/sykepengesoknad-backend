package no.nav.helse.flex.medlemskap

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.util.*

class MedlemskapVurderingClientTest {
    private val medlemskapVurderingRepository = mock<MedlemskapVurderingRepository>()
    private val medlemskapVurderingRestTemplate = mock<RestTemplate>()
    private val url = "http://test-url"

    private val medlemskapVurderingClient =
        MedlemskapVurderingClient(
            medlemskapVurderingRepository,
            medlemskapVurderingRestTemplate,
            url,
        )

    @Test
    fun `Fjerner spørsmål om OPPHOLDSTILLATELSE hvis kjentOppholdstillatelse mangler`() {
        val sykepengesoknadId = UUID.randomUUID().toString()
        val request =
            MedlemskapVurderingRequest(
                fnr = "12345678901",
                fom = LocalDate.of(2023, 1, 1),
                tom = LocalDate.of(2023, 1, 31),
                sykepengesoknadId = sykepengesoknadId,
            )

        val mockResponse =
            MedlemskapVurderingResponse(
                svar = MedlemskapVurderingSvarType.UAVKLART,
                sporsmal =
                    listOf(
                        MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
                        MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE,
                    ),
                kjentOppholdstillatelse = null,
            )

        whenever(
            medlemskapVurderingRestTemplate.exchange(
                any<String>(),
                any<HttpMethod>(),
                any<HttpEntity<*>>(),
                any<Class<MedlemskapVurderingResponse>>(),
            ),
        ).thenReturn(ResponseEntity.ok(mockResponse))

        medlemskapVurderingClient.hentMedlemskapVurdering(request).also {
            it.svar shouldBe MedlemskapVurderingSvarType.UAVKLART
            it.sporsmal shouldNotContain MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE
            it.sporsmal shouldContainExactlyInAnyOrder listOf(MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE)
            it.kjentOppholdstillatelse shouldBe null
        }

        verify(medlemskapVurderingRepository).save(any())
    }

    @Test
    fun `Returnerer spørsmål om OPPHOLDSTILLATELSE hvis kjentOppholdstillatelse blir returnert`() {
        val sykepengesoknadId = UUID.randomUUID().toString()
        val request =
            MedlemskapVurderingRequest(
                fnr = "12345678901",
                fom = LocalDate.of(2023, 1, 1),
                tom = LocalDate.of(2023, 1, 31),
                sykepengesoknadId = sykepengesoknadId,
            )

        val mockResponse =
            MedlemskapVurderingResponse(
                svar = MedlemskapVurderingSvarType.UAVKLART,
                sporsmal =
                    listOf(
                        MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
                        MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE,
                    ),
                kjentOppholdstillatelse =
                    KjentOppholdstillatelse(
                        fom = LocalDate.of(2023, 1, 1),
                        tom = LocalDate.of(2023, 1, 31),
                    ),
            )

        whenever(
            medlemskapVurderingRestTemplate.exchange(
                any<String>(),
                any<HttpMethod>(),
                any<HttpEntity<*>>(),
                any<Class<MedlemskapVurderingResponse>>(),
            ),
        ).thenReturn(ResponseEntity.ok(mockResponse))

        medlemskapVurderingClient.hentMedlemskapVurdering(request).also {
            it.svar shouldBe MedlemskapVurderingSvarType.UAVKLART
            it.sporsmal shouldContainExactlyInAnyOrder
                listOf(
                    MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE,
                    MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
                )
            it.kjentOppholdstillatelse shouldBe
                KjentOppholdstillatelse(
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 31),
                )
        }

        verify(medlemskapVurderingRepository).save(any())
    }
}
