package no.nav.helse.flex.domain.mapper

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.helse.flex.client.sigrun.HentPensjonsgivendeInntektResponse
import no.nav.helse.flex.client.sigrun.PensjonsgivendeInntekt
import no.nav.helse.flex.client.sigrun.Skatteordning
import no.nav.helse.flex.domain.*
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentSoknadsPerioderMedFaktiskGrad
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknadGradert
import no.nav.helse.flex.service.SykepengegrunnlagNaeringsdrivende
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SkatteordningDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.testutil.besvarsporsmal
import no.nav.helse.flex.util.getSporsmalMedTagOrNull
import org.junit.jupiter.api.Test
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
    fun `Inneholder roller selv om inntektsinformasjon mangler`() {
        val soknad = opprettNyNaeringsdrivendeSoknadGradert()

        val soknadDTO =
            konverterTilSykepengesoknadDTO(
                sykepengesoknad =
                    soknad.copy(
                        soknadPerioder = soknadPerioder,
                        selvstendigNaringsdrivende =
                            SelvstendigNaringsdrivendeInfo(
                                roller =
                                    listOf(
                                        BrregRolle(
                                            orgnummer = "orgnummer",
                                            orgnavn = "orgnavn",
                                            rolletype = "INNH",
                                        ),
                                    ),
                                erBarnepasser = false,
                            ),
                    ),
                mottaker = Mottaker.ARBEIDSGIVER_OG_NAV,
                erEttersending = false,
                soknadsperioder = hentSoknadsPerioderMedFaktiskGrad(soknad).first,
            )

        soknadDTO.selvstendigNaringsdrivende!!.also {
            it.roller.also { roller ->
                roller.single().also { rolleDTO ->
                    rolleDTO.orgnummer shouldBe "orgnummer"
                    rolleDTO.rolletype shouldBe "INNH"
                }
            }
        }
    }

    @Test
    fun `Inneholder summert inntektsinformasjon i selvstendigNaringsdrivende`() {
        val soknad = opprettNyNaeringsdrivendeSoknadGradert()

        val soknadDTO =
            konverterTilSykepengesoknadDTO(
                sykepengesoknad =
                    soknad.copy(
                        soknadPerioder = soknadPerioder,
                        selvstendigNaringsdrivende =
                            SelvstendigNaringsdrivendeInfo(
                                roller = emptyList(),
                                sykepengegrunnlagNaeringsdrivende = lagDetailertSykepengegrunnlagNaeringsdrivende(),
                                erBarnepasser = false,
                                brukerHarOppgittForsikring = false,
                            ),
                    ),
                mottaker = Mottaker.ARBEIDSGIVER_OG_NAV,
                erEttersending = false,
                soknadsperioder = hentSoknadsPerioderMedFaktiskGrad(soknad).first,
            )

        soknadDTO.selvstendigNaringsdrivende!!.inntekt!!.also { naringsdrivendeInntektDTO ->
            naringsdrivendeInntektDTO.norskPersonidentifikator shouldBe "123456789"
            naringsdrivendeInntektDTO.inntektsAar.size shouldBe 3

            naringsdrivendeInntektDTO.inntektsAar.find { it.aar == "2021" }!!.pensjonsgivendeInntekt.also {
                it!!.pensjonsgivendeInntektAvLoennsinntekt shouldBe 10_000
                it.pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel shouldBe 190_000
                it.pensjonsgivendeInntektAvNaeringsinntekt shouldBe 500_000
                it.pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage shouldBe 300_000
            }

            naringsdrivendeInntektDTO.inntektsAar.find { it.aar == "2022" }!!.pensjonsgivendeInntekt.also {
                it!!.pensjonsgivendeInntektAvLoennsinntekt shouldBe 100_000
                it.pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel shouldBe 100_000
                it.pensjonsgivendeInntektAvNaeringsinntekt shouldBe 700_000
                it.pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage shouldBe 100_000
            }

            naringsdrivendeInntektDTO.inntektsAar.find { it.aar == "2023" }!!.pensjonsgivendeInntekt.also {
                it!!.pensjonsgivendeInntektAvLoennsinntekt shouldBe 200_000
                it.pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel shouldBe 100_000
                it.pensjonsgivendeInntektAvNaeringsinntekt shouldBe 600_000
                it.pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage shouldBe 100_000
            }
        }
    }

    @Test
    fun `Inneholder ikke spørsmål om yrkesskade`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknadGradert()
        val soknadDTO = lagSykepengesoknadDTO(soknad)

        soknad.getSporsmalMedTagOrNull(YRKESSKADE_V2) shouldBe null
        soknadDTO.getSporsmalMedTagOrNull(YRKESSKADE_V2) shouldBe null
    }

    @Test
    fun `Inneholder melding til nav-dager`() {
        val soknad = opprettNyNaeringsdrivendeSoknadGradert()
        val soknadDTO = lagSykepengesoknadDTO(soknad)

        soknad.meldingTilNavDagerFraSykmelding shouldNotBe null
        soknad.meldingTilNavDagerFraSykmelding shouldBe
            soknadDTO.meldingTilNavDagerFraSykmelding!!.map { Periode(it.fom!!, it.tom!!) }
    }

    @Test
    fun `Inneholder hovedspørsmål for Selvstendig Næringsdrivende i forenklet format`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknadGradert()
                .besvarsporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET, "JA")
                .besvarsporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET, "NEI")
                .besvarsporsmal(NARINGSDRIVENDE_VARIG_ENDRING, "JA")

        val soknadDTO =
            konverterTilSykepengesoknadDTO(
                sykepengesoknad =
                    soknad.copy(
                        soknadPerioder = soknadPerioder,
                        selvstendigNaringsdrivende =
                            SelvstendigNaringsdrivendeInfo(
                                roller = emptyList(),
                                sykepengegrunnlagNaeringsdrivende = lagDetailertSykepengegrunnlagNaeringsdrivende(),
                                erBarnepasser = false,
                                brukerHarOppgittForsikring = false,
                            ),
                    ),
                mottaker = Mottaker.ARBEIDSGIVER_OG_NAV,
                erEttersending = false,
                soknadsperioder = hentSoknadsPerioderMedFaktiskGrad(soknad).first,
            )

        soknadDTO.selvstendigNaringsdrivende!!.hovedSporsmalSvar.let {
            it.size shouldBe 10
            it[TILBAKE_I_ARBEID] shouldBe true
            it["ARBEID_UNDERVEIS_100_PROSENT_0"] shouldBe false
            it["JOBBET_DU_GRADERT_1"] shouldBe false
            it[NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT] shouldBe false
            it[ANDRE_INNTEKTSKILDER_V2] shouldBe true
            it[OPPHOLD_UTENFOR_EOS] shouldBe true
            it[NARINGSDRIVENDE_OPPHOLD_I_UTLANDET] shouldBe false
            it[NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET] shouldBe true
            it[NARINGSDRIVENDE_NY_I_ARBEIDSLIVET] shouldBe false
            it[NARINGSDRIVENDE_VARIG_ENDRING] shouldBe true
        }
    }

    @Test
    fun `Inneholder kun besvarte hovedspørsmål for Selvstendig Næringsdrivende i forenklet format`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknadGradert()
                .besvarsporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET, "NEI")

        val soknadDTO =
            konverterTilSykepengesoknadDTO(
                sykepengesoknad =
                    soknad.copy(
                        soknadPerioder = soknadPerioder,
                        selvstendigNaringsdrivende =
                            SelvstendigNaringsdrivendeInfo(
                                roller = emptyList(),
                                sykepengegrunnlagNaeringsdrivende = lagDetailertSykepengegrunnlagNaeringsdrivende(),
                                erBarnepasser = false,
                                brukerHarOppgittForsikring = false,
                            ),
                    ),
                mottaker = Mottaker.ARBEIDSGIVER_OG_NAV,
                erEttersending = false,
                soknadsperioder = hentSoknadsPerioderMedFaktiskGrad(soknad).first,
            )

        soknadDTO.selvstendigNaringsdrivende!!.hovedSporsmalSvar.let {
            it.size shouldBe 8
            it[TILBAKE_I_ARBEID] shouldBe true
            it["ARBEID_UNDERVEIS_100_PROSENT_0"] shouldBe false
            it["JOBBET_DU_GRADERT_1"] shouldBe false
            it[NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT] shouldBe false
            it[ANDRE_INNTEKTSKILDER_V2] shouldBe true
            it[OPPHOLD_UTENFOR_EOS] shouldBe true
            it[NARINGSDRIVENDE_OPPHOLD_I_UTLANDET] shouldBe false
            it[NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET] shouldBe false
        }
    }

    @Test
    fun `Inneholder oppdelte hovedspørsmål for Selvstendig Næringsdrivende`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknadGradert()
                .besvarsporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET, "NEI")
                .besvarsporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET, "NEI")
                .besvarsporsmal(NARINGSDRIVENDE_VARIG_ENDRING, "NEI")

        val soknadDTO =
            konverterTilSykepengesoknadDTO(
                sykepengesoknad =
                    soknad.copy(
                        soknadPerioder = soknadPerioder,
                        selvstendigNaringsdrivende =
                            SelvstendigNaringsdrivendeInfo(
                                roller = emptyList(),
                                sykepengegrunnlagNaeringsdrivende = lagDetailertSykepengegrunnlagNaeringsdrivende(),
                                erBarnepasser = false,
                                brukerHarOppgittForsikring = false,
                            ),
                    ),
                mottaker = Mottaker.ARBEIDSGIVER_OG_NAV,
                erEttersending = false,
                soknadsperioder = hentSoknadsPerioderMedFaktiskGrad(soknad).first,
            )

        soknadDTO.selvstendigNaringsdrivende!!.hovedSporsmalSvar.let {
            it.size shouldBe 10
            it[TILBAKE_I_ARBEID] shouldBe true
            it["ARBEID_UNDERVEIS_100_PROSENT_0"] shouldBe false
            it["JOBBET_DU_GRADERT_1"] shouldBe false
            it[NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT] shouldBe false
            it[ANDRE_INNTEKTSKILDER_V2] shouldBe true
            it[OPPHOLD_UTENFOR_EOS] shouldBe true
            it[NARINGSDRIVENDE_OPPHOLD_I_UTLANDET] shouldBe false
            it[NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET] shouldBe false
            it[NARINGSDRIVENDE_NY_I_ARBEIDSLIVET] shouldBe false
            it[NARINGSDRIVENDE_VARIG_ENDRING] shouldBe false
        }
    }

    @Test
    fun `Inneholder brukers svar på forsikring`() {
        val soknad = opprettNyNaeringsdrivendeSoknadGradert()

        val soknadDTO =
            konverterTilSykepengesoknadDTO(
                sykepengesoknad =
                    soknad.copy(
                        soknadPerioder = soknadPerioder,
                        selvstendigNaringsdrivende =
                            SelvstendigNaringsdrivendeInfo(
                                roller = emptyList(),
                                erBarnepasser = false,
                                brukerHarOppgittForsikring = true,
                            ),
                    ),
                mottaker = Mottaker.ARBEIDSGIVER_OG_NAV,
                erEttersending = false,
                soknadsperioder = hentSoknadsPerioderMedFaktiskGrad(soknad).first,
            )
    }

    @Test
    fun `Inneholder inntekt per skatteordning ved siden av summert inntekt`() {
        val grunnlag =
            SykepengegrunnlagNaeringsdrivende(
                inntekter =
                    listOf(
                        HentPensjonsgivendeInntektResponse(
                            norskPersonidentifikator = "123456789",
                            inntektsaar = "2023",
                            pensjonsgivendeInntekt =
                                listOf(
                                    PensjonsgivendeInntekt(
                                        datoForFastsetting = LocalDate.parse("2023-07-17"),
                                        skatteordning = Skatteordning.FASTLAND,
                                        pensjonsgivendeInntektAvLoennsinntekt = 100_000,
                                        pensjonsgivendeInntektAvNaeringsinntekt = 400_000,
                                    ),
                                    PensjonsgivendeInntekt(
                                        datoForFastsetting = LocalDate.parse("2023-07-18"),
                                        skatteordning = Skatteordning.SVALBARD,
                                        pensjonsgivendeInntektAvLoennsinntekt = 50_000,
                                        pensjonsgivendeInntektAvNaeringsinntekt = 200_000,
                                    ),
                                ),
                        ),
                        HentPensjonsgivendeInntektResponse(
                            norskPersonidentifikator = "123456789",
                            inntektsaar = "2022",
                            pensjonsgivendeInntekt = emptyList(),
                        ),
                    ),
                harFunnetInntektFoerSykepengegrunnlaget = false,
            )

        val soknadDTO = lagSykepengesoknadDTOMedGrunnlag(grunnlag)

        soknadDTO.selvstendigNaringsdrivende!!.inntekt!!.also { inntekt ->
            inntekt.norskPersonidentifikator shouldBe "123456789"
            inntekt.inntektsAar.size shouldBe 2

            inntekt.inntektsAar.single { it.aar == "2023" }.also { aar ->
                aar.erFerdigLignet shouldBe true
                aar.pensjonsgivendeInntekt!!.pensjonsgivendeInntektAvLoennsinntekt shouldBe 150_000
                aar.pensjonsgivendeInntekt!!.pensjonsgivendeInntektAvNaeringsinntekt shouldBe 600_000

                aar.pensjonsgivendeInntektPerSkatteordning.size shouldBe 2
                aar.pensjonsgivendeInntektPerSkatteordning[0].also {
                    it.skatteordning shouldBe SkatteordningDTO.FASTLAND
                    it.datoForFastsetting shouldBe LocalDate.parse("2023-07-17")
                    it.pensjonsgivendeInntektAvLoennsinntekt shouldBe 100_000
                    it.pensjonsgivendeInntektAvNaeringsinntekt shouldBe 400_000
                }
                aar.pensjonsgivendeInntektPerSkatteordning[1].also {
                    it.skatteordning shouldBe SkatteordningDTO.SVALBARD
                    it.datoForFastsetting shouldBe LocalDate.parse("2023-07-18")
                    it.pensjonsgivendeInntektAvLoennsinntekt shouldBe 50_000
                    it.pensjonsgivendeInntektAvNaeringsinntekt shouldBe 200_000
                }
            }

            inntekt.inntektsAar.single { it.aar == "2022" }.also { aar ->
                aar.erFerdigLignet shouldBe false
                aar.pensjonsgivendeInntekt shouldBe null
                aar.pensjonsgivendeInntektPerSkatteordning.size shouldBe 0
            }
        }
    }

    private fun lagSykepengesoknadDTOMedGrunnlag(grunnlag: SykepengegrunnlagNaeringsdrivende): SykepengesoknadDTO {
        val soknad = opprettNyNaeringsdrivendeSoknadGradert()
        return konverterTilSykepengesoknadDTO(
            sykepengesoknad =
                soknad.copy(
                    soknadPerioder = soknadPerioder,
                    selvstendigNaringsdrivende =
                        SelvstendigNaringsdrivendeInfo(
                            roller = emptyList(),
                            sykepengegrunnlagNaeringsdrivende = grunnlag,
                            erBarnepasser = false,
                        ),
                ),
            mottaker = Mottaker.ARBEIDSGIVER_OG_NAV,
            erEttersending = false,
            soknadsperioder = hentSoknadsPerioderMedFaktiskGrad(soknad).first,
        )
    }

    private fun lagSykepengesoknadDTO(soknad: Sykepengesoknad): SykepengesoknadDTO =
        konverterTilSykepengesoknadDTO(
            sykepengesoknad = soknad,
            mottaker = Mottaker.NAV,
            erEttersending = false,
            soknadsperioder = hentSoknadsPerioderMedFaktiskGrad(soknad).first,
        )

    private fun lagDetailertSykepengegrunnlagNaeringsdrivende(fnr: String = "123456789"): SykepengegrunnlagNaeringsdrivende {
        val inntekterPerAar =
            mapOf(
                2021 to
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = LocalDate.parse("2021-07-17"),
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 10_000,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 190_000,
                            pensjonsgivendeInntektAvNaeringsinntekt = 500_000,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 300_000,
                        ),
                    ),
                2022 to
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = LocalDate.parse("2022-07-17"),
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 100_000,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 100_000,
                            pensjonsgivendeInntektAvNaeringsinntekt = 700_000,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 100_000,
                        ),
                    ),
                2023 to
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = LocalDate.parse("2023-07-17"),
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 200_000,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 100_000,
                            pensjonsgivendeInntektAvNaeringsinntekt = 600_000,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 100_000,
                        ),
                    ),
            )
        return SykepengegrunnlagNaeringsdrivende(
            inntekter =
                listOf(2023, 2022, 2021).map { aar ->
                    HentPensjonsgivendeInntektResponse(
                        norskPersonidentifikator = fnr,
                        inntektsaar = aar.toString(),
                        pensjonsgivendeInntekt = inntekterPerAar[aar] ?: emptyList(),
                    )
                },
            harFunnetInntektFoerSykepengegrunnlaget = false,
        )
    }
}
