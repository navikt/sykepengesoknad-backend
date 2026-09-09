package no.nav.helse.flex.testoppsett

import mockwebserver3.MockWebServer
import no.nav.helse.flex.mockdispatcher.*

fun startMockWebServere(): MockWebServere {
    val pdlMockWebserver =
        MockWebServer().apply {
            start()
            System.setProperty("pdl.api.url", "http://localhost:$port")
            dispatcher = PdlMockDispatcher
        }
    val medlemskapMockWebServer =
        MockWebServer().apply {
            start()
            System.setProperty("MEDLEMSKAP_VURDERING_URL", "http://localhost:$port")
            dispatcher = MedlemskapMockDispatcher
        }
    val inntektskomponentenMockWebServer =
        MockWebServer().apply {
            start()
            System.setProperty("INNTEKTSKOMPONENTEN_URL", "http://localhost:$port")
            dispatcher = InntektskomponentenMockDispatcher
        }
    val pensjonsgivendeInntektMockWebServer =
        MockWebServer().apply {
            start()
            System.setProperty("SIGRUN_URL", "http://localhost:$port")
            dispatcher = SigrunMockDispatcher
        }
    val eregMockWebServer =
        MockWebServer().apply {
            start()
            System.setProperty("EREG_URL", "http://localhost:$port")
            dispatcher = EregMockDispatcher
        }
    val yrkesskadeMockWebServer =
        MockWebServer().apply {
            start()
            System.setProperty("YRKESSKADE_URL", "http://localhost:$port")
            dispatcher = YrkesskadeMockDispatcher
        }
    val innsendingApiMockWebServer =
        MockWebServer().apply {
            start()
            System.setProperty("INNSENDING_API_URL", "http://localhost:$port")
            dispatcher = InnsendingApiMockDispatcher
        }
    val aaregMockWebServer =
        MockWebServer().apply {
            start()
            System.setProperty("AAREG_URL", "http://localhost:$port")
            dispatcher = AaregMockDispatcher
        }
    val brregMockWebServer =
        MockWebServer().apply {
            start()
            System.setProperty("BRREG_API_URL", "http://localhost:$port")
            dispatcher = BrregMockDispatcher
        }
    val arbeidssokerregisterMockWebServer =
        MockWebServer().apply {
            start()
            System.setProperty("ARBEIDSSOEKERREGISTERET_API_URL", "http://localhost:$port")
            dispatcher = ArbeidssokerregisterMockDispatcher
        }

    val enhetsregisterMockWebServer =
        MockWebServer().apply {
            start()
            System.setProperty("enhetsregister.api.url", "http://localhost:$port")
            dispatcher = EnhetsregisterMockDispatcher
        }

    val flexSykmeldingMockWebServer =
        MockWebServer().apply {
            start()
            System.setProperty("FLEX_SYKMELDINGER_BACKEND_API_URL", "http://localhost:$port")
            dispatcher = FlexSykmeldingMockDispatcher
        }

    val texasMockWebServer =
        MockWebServer().apply {
            start()
            System.setProperty("NAIS_TOKEN_ENDPOINT", "http://localhost:$port")
            dispatcher = TexasMockDispatcher
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
        flexSykmeldingMockWebServer = flexSykmeldingMockWebServer,
        texasMockWebServer = texasMockWebServer,
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
    val flexSykmeldingMockWebServer: MockWebServer,
    val texasMockWebServer: MockWebServer,
)
