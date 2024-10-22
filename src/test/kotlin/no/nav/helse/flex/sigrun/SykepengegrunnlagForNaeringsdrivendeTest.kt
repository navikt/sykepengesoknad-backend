package no.nav.helse.flex.sigrun

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknad
import no.nav.helse.flex.util.objectMapper
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class SykepengegrunnlagForNaeringsdrivendeTest : FellesTestOppsett() {
    @Disabled
    @Test
    fun `sjekker utregning av sykepengegrunnlag`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad().copy(
                startSykeforlop = LocalDate.now(),
                fom = LocalDate.now().minusDays(30),
                tom = LocalDate.now().minusDays(1),
                sykmeldingSkrevet = Instant.now(),
                aktivertDato = LocalDate.now().minusDays(30),
            )
        val grunnlagVerdier = sykepengegrunnlagForNaeringsdrivende.sykepengegrunnlagNaeringsdrivende(soknad)

        grunnlagVerdier `should not be` null
        grunnlagVerdier!!.let { grunnlag ->
            grunnlag.grunnbeloepPerAar.size `should be equal to` 3
            grunnlag.gjennomsnittPerAar.size `should be equal to` 3
            grunnlag.beregnetSnittOgEndring25.let {
                it.snitt `should be equal to` 392514.toBigInteger()
                it.p25 `should be equal to` 490642.toBigInteger()
                it.m25 `should be equal to` 294385.toBigInteger()
            }
        }
    }

    @Test
    fun `sjekker utregning for inntekt mellom 6G og 12G`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad().copy(
                fnr = "87654321234",
                startSykeforlop = LocalDate.now(),
                fom = LocalDate.now().minusDays(30),
                tom = LocalDate.now().minusDays(1),
                sykmeldingSkrevet = Instant.now(),
                aktivertDato = LocalDate.now().minusDays(30),
            )

        val grunnlagVerdier = sykepengegrunnlagForNaeringsdrivende.sykepengegrunnlagNaeringsdrivende(soknad)

        grunnlagVerdier `should not be` null
        grunnlagVerdier!!.let {
            it.beregnetSnittOgEndring25.snitt `should be equal to` 871798.toBigInteger()
            it.grunnbeloepPerAar.size `should be equal to` 3
            it.gjennomsnittPerAar.size `should be equal to` 3
        }
    }

    @Disabled
    @Test
    fun `sjekker json for frontend`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad().copy(
                fnr = "87654321234",
                startSykeforlop = LocalDate.now(),
                fom = LocalDate.now().minusDays(30),
                tom = LocalDate.now().minusDays(1),
                sykmeldingSkrevet = Instant.now(),
                aktivertDato = LocalDate.now().minusDays(30),
            )

        val grunnlagVerdier = sykepengegrunnlagForNaeringsdrivende.sykepengegrunnlagNaeringsdrivende(soknad)

        grunnlagVerdier `should not be` null
        grunnlagVerdier!!.toJsonNode().toString() `should be equal to`
            objectMapper.readTree(
                """
                {"sigrunInntekt":{"inntekter":[{"aar":"2023","verdi":851781},{"aar":"2022","verdi":872694},{"aar":"2021","verdi":890920}],"g-verdier":[{"aar":"2021","verdi":104716},{"aar":"2022","verdi":109784},{"aar":"2023","verdi":116239}],"g-sykmelding":124028,"beregnet":{"snitt":871798,"p25":1089748,"m25":653849},"original-inntekt":[{"inntektsaar":"2023","pensjonsgivendeInntekt":[{"datoForFastsetting":"2023-07-17","skatteordning":"FASTLAND","loenn":0,"loenn-bare-pensjon":0,"naering":1000000,"fiske-fangst-familiebarnehage":0}],"totalInntekt":1000000},{"inntektsaar":"2022","pensjonsgivendeInntekt":[{"datoForFastsetting":"2022-07-17","skatteordning":"FASTLAND","loenn":0,"loenn-bare-pensjon":0,"naering":1000000,"fiske-fangst-familiebarnehage":0}],"totalInntekt":1000000},{"inntektsaar":"2021","pensjonsgivendeInntekt":[{"datoForFastsetting":"2021-07-17","skatteordning":"FASTLAND","loenn":0,"loenn-bare-pensjon":0,"naering":1000000,"fiske-fangst-familiebarnehage":0}],"totalInntekt":1000000}]}}
                """.trimIndent(),
            ).toString()
    }
}
