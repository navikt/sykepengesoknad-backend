package no.nav.helse.flex.domain.mapper

import no.nav.helse.flex.domain.BrregRolle
import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.SelvstendigNaringsdrivendeInfo
import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentSoknadsPerioderMedFaktiskGrad
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknad
import no.nav.helse.flex.soknadsopprettelse.lagSykepengegrunnlagNaeringsdrivende
import no.nav.helse.flex.sykepengesoknad.kafka.*
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.time.LocalDate

@Suppress("DEPRECATION")
class SelvstendigNaringsdrivendeToSykepengesoknadDtoTest {
    private val dagensDato = LocalDate.parse("2025-01-01")
    private val soknadPerioder =
        listOf(
            Soknadsperiode(
                fom = dagensDato.minusDays(20),
                tom = dagensDato,
                grad = 100,
                sykmeldingstype = null,
            ),
        )

    @Test
    fun `Inneholder roller i selvstendigNaringsdrivende`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad().copy(
                soknadPerioder = soknadPerioder,
                selvstendigNaringsdrivende =
                    SelvstendigNaringsdrivendeInfo(
                        listOf(
                            BrregRolle(
                                "123456789",
                                "Test",
                                "ROLLE",
                            ),
                        ),
                    ),
            )
        val soknadDto =
            lagSykepengesoknadDTO(soknad)

        soknadDto.selvstendigNaringsdrivende?.let {
            it.roller `should be equal to`
                listOf(
                    RolleDTO(
                        "123456789",
                        "ROLLE",
                    ),
                )
            it.sykepengegrunnlagNaeringsdrivende `should be equal to` null
        }
    }

    @Test
    fun `Inneholder sykepengegrunnlag i selvstedigNaringsdrivende`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad().copy(
                soknadPerioder = soknadPerioder,
                selvstendigNaringsdrivende =
                    SelvstendigNaringsdrivendeInfo(
                        roller = emptyList(),
                        sykepengegrunnlagNaeringsdrivende = lagSykepengegrunnlagNaeringsdrivende(),
                    ),
            )

        val soknadDto = lagSykepengesoknadDTO(soknad)

        soknadDto.selvstendigNaringsdrivende?.let {
            it.roller `should be equal to` emptyList()
            it.sykepengegrunnlagNaeringsdrivende `should be equal to` sykepengegrunnlagNaeringsdrivendeDTO
        }
    }

    @Test
    fun `Inneholder summert inntektsinformasjon i selvstedigNaringsdrivende`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad().copy(
                soknadPerioder = soknadPerioder,
                selvstendigNaringsdrivende =
                    SelvstendigNaringsdrivendeInfo(
                        roller = emptyList(),
                        sykepengegrunnlagNaeringsdrivende = lagSykepengegrunnlagNaeringsdrivende(),
                    ),
            )
        val soknadDTO =
            lagSykepengesoknadDTO(soknad)

        soknadDTO.selvstendigNaringsdrivende!!.naringsdrivendeInntekt!!.also { naringsdrivendeInntektDTO ->
            naringsdrivendeInntektDTO.norskPersonidentifikator `should be equal to` "123456789"
            naringsdrivendeInntektDTO.inntekt.size `should be equal to` 3

            naringsdrivendeInntektDTO.inntekt.find { it.inntektsaar == "2021" }!!.pensjonsgivendeInntekt.also {
                it.pensjonsgivendeInntektAvLoennsinntekt `should be equal to` 10_000
                it.pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel `should be equal to` 190_000
                it.pensjonsgivendeInntektAvNaeringsinntekt `should be equal to` 500_000
                it.pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage `should be equal to` 300_000
            }

            naringsdrivendeInntektDTO.inntekt.find { it.inntektsaar == "2022" }!!.pensjonsgivendeInntekt.also {
                it.pensjonsgivendeInntektAvLoennsinntekt `should be equal to` 100_000
                it.pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel `should be equal to` 100_000
                it.pensjonsgivendeInntektAvNaeringsinntekt `should be equal to` 700_000
                it.pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage `should be equal to` 100_000
            }

            naringsdrivendeInntektDTO.inntekt.find { it.inntektsaar == "2023" }!!.pensjonsgivendeInntekt.also {
                it.pensjonsgivendeInntektAvLoennsinntekt `should be equal to` 200_000
                it.pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel `should be equal to` 100_000
                it.pensjonsgivendeInntektAvNaeringsinntekt `should be equal to` 600_000
                it.pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage `should be equal to` 100_000
            }
        }
    }

    private fun lagSykepengesoknadDTO(soknad: Sykepengesoknad): SykepengesoknadDTO =
        konverterTilSykepengesoknadDTO(
            sykepengesoknad = soknad,
            mottaker = Mottaker.ARBEIDSGIVER_OG_NAV,
            erEttersending = false,
            soknadsperioder = hentSoknadsPerioderMedFaktiskGrad(soknad).first,
        )
}

private val sykepengegrunnlagNaeringsdrivendeDTO =
    SykepengegrunnlagNaeringsdrivendeDTO(
        gjennomsnittPerAar =
            listOf(
                AarVerdiDTO(aar = "2023", verdi = BigInteger("851782")),
                AarVerdiDTO(aar = "2022", verdi = BigInteger("872694")),
                AarVerdiDTO(aar = "2021", verdi = BigInteger("890920")),
            ),
        grunnbeloepPerAar =
            listOf(
                AarVerdiDTO(aar = "2021", verdi = BigInteger("104716")),
                AarVerdiDTO(aar = "2022", verdi = BigInteger("109784")),
                AarVerdiDTO(aar = "2023", verdi = BigInteger("116239")),
            ),
        grunnbeloepPaaSykmeldingstidspunkt = 124028,
        beregnetSnittOgEndring25 =
            BeregnetDTO(
                snitt = BigInteger("871798"),
                p25 = BigInteger("1089748"),
                m25 = BigInteger("653849"),
            ),
        inntekter =
            listOf(
                HentPensjonsgivendeInntektResponseDTO(
                    norskPersonidentifikator = "123456789",
                    inntektsaar = "2023",
                    pensjonsgivendeInntekt =
                        listOf(
                            PensjonsgivendeInntektDTO(
                                datoForFastsetting = LocalDate.parse("2023-07-17").toString(),
                                skatteordning = SkatteordningDTO.FASTLAND,
                                pensjonsgivendeInntektAvLoennsinntekt = 100_000,
                                pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 50_000,
                                pensjonsgivendeInntektAvNaeringsinntekt = 300_000,
                                pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 50_000,
                            ),
                            PensjonsgivendeInntektDTO(
                                datoForFastsetting = LocalDate.parse("2023-07-17").toString(),
                                skatteordning = SkatteordningDTO.SVALBARD,
                                pensjonsgivendeInntektAvLoennsinntekt = 100_000,
                                pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 50_000,
                                pensjonsgivendeInntektAvNaeringsinntekt = 300_000,
                                pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 50_000,
                            ),
                        ),
                ),
                HentPensjonsgivendeInntektResponseDTO(
                    norskPersonidentifikator = "123456789",
                    inntektsaar = "2022",
                    pensjonsgivendeInntekt =
                        listOf(
                            PensjonsgivendeInntektDTO(
                                datoForFastsetting = LocalDate.parse("2022-07-17").toString(),
                                skatteordning = SkatteordningDTO.FASTLAND,
                                pensjonsgivendeInntektAvLoennsinntekt = 50_000,
                                pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 50_000,
                                pensjonsgivendeInntektAvNaeringsinntekt = 350_000,
                                pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 50_000,
                            ),
                            PensjonsgivendeInntektDTO(
                                datoForFastsetting = LocalDate.parse("2022-07-17").toString(),
                                skatteordning = SkatteordningDTO.SVALBARD,
                                pensjonsgivendeInntektAvLoennsinntekt = 50_000,
                                pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 50_000,
                                pensjonsgivendeInntektAvNaeringsinntekt = 350_000,
                                pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 50_000,
                            ),
                        ),
                ),
                HentPensjonsgivendeInntektResponseDTO(
                    norskPersonidentifikator = "123456789",
                    inntektsaar = "2021",
                    pensjonsgivendeInntekt =
                        listOf(
                            PensjonsgivendeInntektDTO(
                                datoForFastsetting = LocalDate.parse("2021-07-17").toString(),
                                skatteordning = SkatteordningDTO.FASTLAND,
                                pensjonsgivendeInntektAvLoennsinntekt = 10_000,
                                pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 190_000,
                                pensjonsgivendeInntektAvNaeringsinntekt = 500_000,
                                pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 300_000,
                            ),
                        ),
                ),
            ),
    )
