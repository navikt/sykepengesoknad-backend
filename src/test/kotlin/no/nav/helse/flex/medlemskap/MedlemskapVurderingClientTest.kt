package no.nav.helse.flex.medlemskap

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should contain same`
import org.amshove.kluent.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
    fun `Fjerner spørsmål om OPPHOLDSTILLATELSE hvis kjentOppholdstillatelse mangler for angitt søknad`() {
        val sykepengesoknadId = "3b33fe80-1b97-37a2-9c92-106dea8783be"
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
            it.svar `should be equal to` MedlemskapVurderingSvarType.UAVKLART
            it.sporsmal shouldNotContain MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE
            it.sporsmal `should contain same` listOf(MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE)
            it.kjentOppholdstillatelse `should be equal to` null
        }

        verify(medlemskapVurderingRepository).save(any())
    }

    @Test
    fun `Kaster exception når spørsmål om OPPHOLDSTILLATELSE mangler kjentOppholdstillatelse`() {
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

        assertThrows<MedlemskapVurderingResponseException> {
            medlemskapVurderingClient.hentMedlemskapVurdering(request)
        }

        verify(medlemskapVurderingRepository).save(any())
    }
}
