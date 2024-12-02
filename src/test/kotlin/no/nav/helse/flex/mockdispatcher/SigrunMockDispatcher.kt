package no.nav.helse.flex.mockdispatcher

import no.nav.helse.flex.client.sigrun.HentPensjonsgivendeInntektResponse
import no.nav.helse.flex.client.sigrun.PensjonsgivendeInntekt
import no.nav.helse.flex.client.sigrun.Skatteordning
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.atomic.AtomicInteger

object SigrunMockDispatcher : Dispatcher() {
    val antallKall = AtomicInteger(0)

    override fun dispatch(request: RecordedRequest): MockResponse {
        val fnr = request.headers["Nav-Personident"]!!
        val inntektsAar = request.headers["inntektsaar"]!!
        antallKall.incrementAndGet()

        // Brukt til testing av at det ikke gjøres retries når det kastes en PensjonsgivendeInntektClientException.
        if (fnr == "01017011111") {
            return MockResponse()
                .setResponseCode(404)
                .setBody("{\"errorCode\": \"PGIF-007\", \"errorMessage\": \"Ikke treff på oppgitt personidentifikator.\"}")
                .addHeader("Content-Type", "application/json")
        }

        // Bruk til testing av at det skal gjøres retries når det kastes en exception fordi body er null.
        if (fnr == "01017022222") {
            return MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
        }

        val over1G = 1_000_000
        val under1G = 100_000

        val personMedInntektOver1GSiste3Aar = "87654321234"
        val personMedFlereTyperInntekt = "86543214356"
        val personMedInntektUnder1GSiste3Aar = "24859597781"
        val personMedInntektOver1G2Av3Aar = "11929798688"
        val personMedInntektOver1G1Av3Aar = "07830099810"
        val personMedInntekt2Av3Aar = "56909901141"
        val personUtenInntektSiste3Aar = "56830375185"
        val personUtenInntektForsteAar = "21127575934"
        val personMedInntektAar4 = "12899497862"
        val personLangTilbakeITid = "06028033456"
        val personUtenPensjonsgivendeInntektAlleAar = "27654767992"

        val naeringsinntekt =
            when (fnr) {
                personMedInntektOver1GSiste3Aar -> inntektForAar(inntektsAar, over1G, over1G, over1G, under1G)
                personMedFlereTyperInntekt -> inntektForAar(inntektsAar, inntekt2022 = over1G)
                personMedInntektUnder1GSiste3Aar -> inntektForAar(inntektsAar, under1G, under1G, under1G, under1G)
                personMedInntektOver1G2Av3Aar -> inntektForAar(inntektsAar, over1G, under1G, over1G, under1G)
                personMedInntektOver1G1Av3Aar -> inntektForAar(inntektsAar, over1G, under1G, under1G, under1G)
                personMedInntekt2Av3Aar -> inntektForAar(inntektsAar, under1G, null, under1G, over1G)
                personUtenInntektSiste3Aar -> inntektForAar(inntektsAar, null, null, null, under1G)
                personUtenInntektForsteAar -> inntektForAar(inntektsAar, null, under1G, over1G, under1G)
                personMedInntektAar4 -> inntektForAar(inntektsAar, null, null, null, under1G)
                personUtenPensjonsgivendeInntektAlleAar -> inntektForAar(inntektsAar, null, null, null, null)
                // Excel Scenario 2 - inntektsAar 2018
                personLangTilbakeITid ->
                    inntektForAar(
                        inntektsAar,
                        inntekt2017 = null,
                        inntekt2016 = 670_000,
                        inntekt2015 = 590_000,
                        inntekt2014 = 490_000,
                    )

                else -> inntektForAar(inntektsAar, 400000, 350000, 300000, under1G)
            }

        val lonnsinntekt =
            when (fnr) {
                personMedFlereTyperInntekt -> inntektForAar(inntektsAar, inntekt2023 = over1G)
                else -> inntektForAar(inntektsAar)
            }

        val naeringsinntektFraFiskeFangstEllerFamiliebarnehage =
            when (fnr) {
                personMedFlereTyperInntekt -> inntektForAar(inntektsAar, inntekt2021 = over1G)
                else -> inntektForAar(inntektsAar)
            }

        // Sjekk om inntekt er null og kast feilen
        if (naeringsinntekt == null) {
            return MockResponse()
                .setResponseCode(404)
                .setBody("{\"errorCode\": \"PGIF-008\", \"errorMessage\": \"Ingen pensjonsgivende inntekt funnet.\"}")
                .addHeader("Content-Type", "application/json")
        }

        val ret =
            when (inntektsAar) {
                "2023", "2022", "2021", "2020", "2019", "2018", "2017", "2016", "2015", "2014" ->
                    pensjonsgivendeInntekt(
                        fnr = fnr,
                        inntektsAar = inntektsAar,
                        naeringsinntekt = naeringsinntekt,
                        lonnsinntekt = lonnsinntekt,
                        naeringsinntektFraFiskeFangstEllerFamiliebarnehage = naeringsinntektFraFiskeFangstEllerFamiliebarnehage,
                    )

                else -> MockResponse().setResponseCode(404)
            }

        return ret
    }

    private fun inntektForAar(
        inntektsAar: String,
        inntekt2023: Int? = 0,
        inntekt2022: Int? = 0,
        inntekt2021: Int? = 0,
        inntekt2020: Int? = 0,
        inntekt2019: Int? = 0,
        inntekt2018: Int? = 0,
        inntekt2017: Int? = 0,
        inntekt2016: Int? = 0,
        inntekt2015: Int? = 0,
        inntekt2014: Int? = 0,
    ): Int? {
        return when (inntektsAar) {
            "2023" -> inntekt2023
            "2022" -> inntekt2022
            "2021" -> inntekt2021
            "2020" -> inntekt2020
            "2019" -> inntekt2019
            "2018" -> inntekt2018
            "2017" -> inntekt2017
            "2016" -> inntekt2016
            "2015" -> inntekt2015
            "2014" -> inntekt2014
            else -> null
        }
    }

    private fun pensjonsgivendeInntekt(
        fnr: String,
        inntektsAar: String,
        lonnsinntekt: Int? = 0,
        loennsinntektBarePensjonsdel: Int = 0,
        naeringsinntekt: Int? = 0,
        naeringsinntektFraFiskeFangstEllerFamiliebarnehage: Int? = 0,
    ): MockResponse {
        return HentPensjonsgivendeInntektResponse(
            norskPersonidentifikator = fnr,
            inntektsaar = inntektsAar,
            pensjonsgivendeInntekt =
                listOf(
                    // for å gjøre testing enklere legges all fiske/familiebhg på Svalbard
                    if (naeringsinntektFraFiskeFangstEllerFamiliebarnehage == 0) {
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "$inntektsAar-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = lonnsinntekt,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = loennsinntektBarePensjonsdel,
                            pensjonsgivendeInntektAvNaeringsinntekt = naeringsinntekt,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 0,
                        )
                    } else {
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "$inntektsAar-07-17",
                            skatteordning = Skatteordning.SVALBARD,
                            pensjonsgivendeInntektAvLoennsinntekt = 0,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 0,
                            pensjonsgivendeInntektAvNaeringsinntekt = 0,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage =
                            naeringsinntektFraFiskeFangstEllerFamiliebarnehage,
                        )
                    },
                ),
        ).tilMockResponse()
    }

    private fun HentPensjonsgivendeInntektResponse.tilMockResponse(): MockResponse {
        return MockResponse().setBody(this.serialisertTilString()).addHeader("Content-Type", "application/json")
    }
}
