package no.nav.helse.flex.client.medlemskap

import no.nav.helse.flex.BaseTestClass
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should contain same`
import org.amshove.kluent.shouldStartWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class MedlemskapVurderingClientIntegrationTest : BaseTestClass() {

    @Autowired
    private lateinit var medlemskapVurderingClient: MedlemskapVurderingClient

    @Test
    fun `hentMedlemskapVurdering svarer med UAVKLART og liste med spørsmål`() {
        val fnr = "31111111111"
        val fom = LocalDate.of(2023, 1, 1)
        val tom = LocalDate.of(2023, 1, 31)

        val request = MedlemskapVurderingRequest(fnr = fnr, fom = fom, tom = tom)
        val response = medlemskapVurderingClient.hentMedlemskapVurdering(request)

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
    }
}
