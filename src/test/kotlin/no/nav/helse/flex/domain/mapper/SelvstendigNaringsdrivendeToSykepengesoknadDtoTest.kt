package no.nav.helse.flex.domain.mapper

import no.nav.helse.flex.domain.BrregRolle
import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.SelvstendigNaringsdrivendeInfo
import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentSoknadsPerioderMedFaktiskGrad
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknad
import no.nav.helse.flex.soknadsopprettelse.lagSykepengegrunnlagNaeringsdrivende
import no.nav.helse.flex.sykepengesoknad.kafka.*
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.time.LocalDate

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
    fun `burde inneholde roller i selvstedig næringsdrivende`() {
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
            konverterTilSykepengesoknadDTO(
                soknad,
                Mottaker.ARBEIDSGIVER_OG_NAV,
                false,
                hentSoknadsPerioderMedFaktiskGrad(soknad).first,
            )

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
    fun `burde inneholde sykepengegrunnlag i selvstedig næringsdrivende`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad().copy(
                soknadPerioder = soknadPerioder,
                selvstendigNaringsdrivende =
                    SelvstendigNaringsdrivendeInfo(
                        roller = emptyList(),
                        sykepengegrunnlagNaeringsdrivende = lagSykepengegrunnlagNaeringsdrivende(),
                    ),
            )
        val soknadDto =
            konverterTilSykepengesoknadDTO(
                soknad,
                Mottaker.ARBEIDSGIVER_OG_NAV,
                false,
                hentSoknadsPerioderMedFaktiskGrad(soknad).first,
            )

        soknadDto.selvstendigNaringsdrivende?.let {
            it.roller `should be equal to` emptyList()
            it.sykepengegrunnlagNaeringsdrivende `should be equal to` sykepengegrunnlagNaeringsdrivendeDTO
        }
    }
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
                                pensjonsgivendeInntektAvLoennsinntekt = 0,
                                pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 0,
                                pensjonsgivendeInntektAvNaeringsinntekt = 1_000_000,
                                pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 0,
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
                                pensjonsgivendeInntektAvLoennsinntekt = 0,
                                pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 0,
                                pensjonsgivendeInntektAvNaeringsinntekt = 1_000_000,
                                pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 0,
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
                                pensjonsgivendeInntektAvLoennsinntekt = 0,
                                pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 0,
                                pensjonsgivendeInntektAvNaeringsinntekt = 1_000_000,
                                pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 0,
                            ),
                        ),
                ),
            ),
    )
