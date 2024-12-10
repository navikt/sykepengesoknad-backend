package no.nav.helse.flex.sigrun

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.client.sigrun.PensjonsgivendeInntekt
import no.nav.helse.flex.client.sigrun.Skatteordning
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknad
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher.enqueueMockResponse
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

private const val FNR = "12345678910"

class SykepengegrunnlagForNaeringsdrivendeTest : FellesTestOppsett() {
    @BeforeEach
    fun resetMockWebServer() {
        SigrunMockDispatcher.antallKall.set(0)
    }

    @Test
    fun `Beregner sykepengegrunnlag for tre år med inntekt under 6G`() {
        with(sigrunMockWebServer) {
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2023",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2023-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 400_000,
                        ),
                    ),
            )
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2022",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2022-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 350_000,
                        ),
                    ),
            )
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2021",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2021-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 300_000,
                        ),
                    ),
            )
        }

        val sykepengegrunnlag =
            sykepengegrunnlagForNaeringsdrivende.hentSykepengegrunnlagForNaeringsdrivende(lagSykepengesoknad())
        SigrunMockDispatcher.antallKall.get() `should be equal to` 3

        sykepengegrunnlag `should not be` null
        sykepengegrunnlag!!.let {
            it.grunnbeloepPerAar.size `should be equal to` 3
            it.gjennomsnittPerAar.size `should be equal to` 3
            it.beregnetSnittOgEndring25.let {
                it.snitt `should be equal to` 392514.toBigInteger()
                it.p25 `should be equal to` 490642.toBigInteger()
                it.m25 `should be equal to` 294385.toBigInteger()
            }
        }
    }

    private fun lagSykepengesoknad(): Sykepengesoknad =
        opprettNyNaeringsdrivendeSoknad().copy(
            fnr = FNR,
            startSykeforlop = LocalDate.now(),
            fom = LocalDate.now().minusDays(30),
            tom = LocalDate.now().minusDays(1),
            sykmeldingSkrevet = Instant.now(),
            aktivertDato = LocalDate.now().minusDays(30),
        )
}
