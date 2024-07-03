package no.nav.helse.flex.utvikling

import no.nav.helse.flex.Application
import no.nav.helse.flex.FellesTestOppsett.Companion.medlemskapMockWebServer
import no.nav.helse.flex.medlemskap.KjentOppholdstillatelse
import no.nav.helse.flex.medlemskap.MedlemskapVurderingResponse
import no.nav.helse.flex.medlemskap.MedlemskapVurderingSporsmal
import no.nav.helse.flex.medlemskap.MedlemskapVurderingSvarType
import no.nav.helse.flex.mockdispatcher.FlexSyketilfelleMockDispatcher
import no.nav.helse.flex.util.serialisertTilString
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.time.LocalDate

@Configuration
@EnableMockOAuth2Server
@Profile("dev")
class AuthConfig

fun main(args: Array<String>) {
    // Stiller alle spørsmål om medlemskap i UtviklingApplication.
    medlemskapMockWebServer.enqueue(
        MockResponse().setResponseCode(200).setBody(
            MedlemskapVurderingResponse(
                svar = MedlemskapVurderingSvarType.UAVKLART,
                sporsmal =
                    listOf(
                        MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
                        MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE,
                        MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_NORGE,
                        MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_EØS_OMRÅDE,
                    ),
                kjentOppholdstillatelse =
                    KjentOppholdstillatelse(
                        fom = LocalDate.now().minusMonths(1),
                        tom = LocalDate.now().plusMonths(2),
                    ),
            ).serialisertTilString(),
        ),
    )

    // Denne har ikke MockWebServer i testene
    MockWebServer().apply {
        System.setProperty("flex.syketilfelle.url", "http://localhost:$port")
        dispatcher = FlexSyketilfelleMockDispatcher
    }

    System.setProperty("spring.kafka.consumer.auto-offset-reset", "earliest")
    System.setProperty("server.port", "80")
    System.setProperty("spring.profiles.active", "dev,test,sykmeldinger")
    runApplication<Application>(*args)
}
