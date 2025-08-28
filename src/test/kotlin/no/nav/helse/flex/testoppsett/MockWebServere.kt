package no.nav.helse.flex.testoppsett

import no.nav.helse.flex.medlemskap.MedlemskapMockDispatcher
import no.nav.helse.flex.mockdispatcher.*
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

fun startMockWebServere(): MockWebServere {
    val pdlMockWebserver =
        MockWebServer().apply {
            System.setProperty("pdl.api.url", "http://localhost:$port")
            dispatcher = PdlMockDispatcher
        }
    val medlemskapMockWebServer =
        MockWebServer().apply {
            System.setProperty("MEDLEMSKAP_VURDERING_URL", "http://localhost:$port")
            dispatcher = MedlemskapMockDispatcher
        }
    val inntektskomponentenMockWebServer =
        MockWebServer().apply {
            System.setProperty("INNTEKTSKOMPONENTEN_URL", "http://localhost:$port")
            dispatcher = InntektskomponentenMockDispatcher
        }
    val pensjonsgivendeInntektMockWebServer =
        MockWebServer().apply {
            System.setProperty("SIGRUN_URL", "http://localhost:$port")
            dispatcher = SigrunMockDispatcher
        }
    val eregMockWebServer =
        MockWebServer().apply {
            System.setProperty("EREG_URL", "http://localhost:$port")
            dispatcher = EregMockDispatcher
        }
    val yrkesskadeMockWebServer =
        MockWebServer().apply {
            System.setProperty("YRKESSKADE_URL", "http://localhost:$port")
            dispatcher = YrkesskadeMockDispatcher
        }
    val innsendingApiMockWebServer =
        MockWebServer().apply {
            System.setProperty("INNSENDING_API_URL", "http://localhost:$port")
            dispatcher = InnsendingApiMockDispatcher
        }
    val grunnbeloepApiMockWebServer =
        MockWebServer().apply {
            System.setProperty("GRUNNBELOEP_API_URL", "http://localhost:$port")
            dispatcher = GrunnbeloepApiMockDispatcher
        }
    val aaregMockWebServer =
        MockWebServer().apply {
            System.setProperty("AAREG_URL", "http://localhost:$port")
            dispatcher = AaregMockDispatcher
        }
    val brregMockWebServer =
        MockWebServer().apply {
            System.setProperty("BRREG_API_URL", "http://localhost:$port")
            dispatcher = simpleDispatcher { MockResponse().setResponseCode(200) }
        }
    val arbeidssokerregisterMockDispatcher =
        MockWebServer().apply {
            System.setProperty("ARBEIDSSOEKERREGISTERET_API_URL", "http://localhost:$port")
            dispatcher = ArbeidssokerregisterMockDispatcher
        }

    // i need to add somethnig h ere?
    val enhetsregisterMockWebServer =
        MockWebServer().apply {
            System.setProperty("ENHETSREGISTER_URL", "http://localhost:$port")
        }

    return MockWebServere(
        pdlMockWebserver = pdlMockWebserver,
        medlemskapMockWebServer = medlemskapMockWebServer,
        inntektskomponentenMockWebServer = inntektskomponentenMockWebServer,
        eregMockWebServer = eregMockWebServer,
        yrkesskadeMockWebServer = yrkesskadeMockWebServer,
        innsendingApiMockWebServer = innsendingApiMockWebServer,
        pensjonsgivendeInntektMockWebServer = pensjonsgivendeInntektMockWebServer,
        grunnbeloepApiMockWebServer = grunnbeloepApiMockWebServer,
        aaregMockWebServer = aaregMockWebServer,
        brregMockWebServer = brregMockWebServer,
        arbeidssokerregisterMockDispatcher = arbeidssokerregisterMockDispatcher,
        enhetsregisterMockWebServer = enhetsregisterMockWebServer,
    )
}

data class MockWebServere(
    val pdlMockWebserver: MockWebServer,
    val medlemskapMockWebServer: MockWebServer,
    val inntektskomponentenMockWebServer: MockWebServer,
    val eregMockWebServer: MockWebServer,
    val yrkesskadeMockWebServer: MockWebServer,
    val innsendingApiMockWebServer: MockWebServer,
    val pensjonsgivendeInntektMockWebServer: MockWebServer,
    val grunnbeloepApiMockWebServer: MockWebServer,
    val aaregMockWebServer: MockWebServer,
    val brregMockWebServer: MockWebServer,
    val arbeidssokerregisterMockDispatcher: MockWebServer,
    val enhetsregisterMockWebServer: MockWebServer,
)

fun simpleDispatcher(dispatcherFunc: (RecordedRequest) -> MockResponse): Dispatcher =
    object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = dispatcherFunc(request)
    }
