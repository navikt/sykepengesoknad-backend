package no.nav.helse.flex.testoppsett

import no.nav.helse.flex.medlemskap.MedlemskapMockDispatcher
import no.nav.helse.flex.mockdispatcher.*
import okhttp3.mockwebserver.MockWebServer

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
    val aaregMockWebServer =
        MockWebServer().apply {
            System.setProperty("AAREG_URL", "http://localhost:$port")
            dispatcher = AaregMockDispatcher
        }
    val brregMockWebServer =
        MockWebServer().apply {
            System.setProperty("BRREG_API_URL", "http://localhost:$port")
            dispatcher = BrregMockDispatcher
        }
    val arbeidssokerregisterMockWebServer =
        MockWebServer().apply {
            System.setProperty("ARBEIDSSOEKERREGISTERET_API_URL", "http://localhost:$port")
            dispatcher = ArbeidssokerregisterMockDispatcher
        }

    val enhetsregisterMockWebServer =
        MockWebServer().apply {
            System.setProperty("enhetsregister.api.url", "http://localhost:$port")
            dispatcher = EnhetsregisterMockDispatcher
        }

    return MockWebServere(
        pdlMockWebserver = pdlMockWebserver,
        medlemskapMockWebServer = medlemskapMockWebServer,
        inntektskomponentenMockWebServer = inntektskomponentenMockWebServer,
        eregMockWebServer = eregMockWebServer,
        yrkesskadeMockWebServer = yrkesskadeMockWebServer,
        innsendingApiMockWebServer = innsendingApiMockWebServer,
        pensjonsgivendeInntektMockWebServer = pensjonsgivendeInntektMockWebServer,
        aaregMockWebServer = aaregMockWebServer,
        brregMockWebServer = brregMockWebServer,
        arbeidssokerregisterMockWebServer = arbeidssokerregisterMockWebServer,
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
    val aaregMockWebServer: MockWebServer,
    val brregMockWebServer: MockWebServer,
    val arbeidssokerregisterMockWebServer: MockWebServer,
    val enhetsregisterMockWebServer: MockWebServer,
)
