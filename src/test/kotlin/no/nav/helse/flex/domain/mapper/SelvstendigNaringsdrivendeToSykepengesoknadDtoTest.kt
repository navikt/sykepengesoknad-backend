package no.nav.helse.flex.domain.mapper

import no.nav.helse.flex.domain.*
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentSoknadsPerioderMedFaktiskGrad
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknad
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.testutil.besvarsporsmal
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be null`
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
        val (soknad, fom, tom) =
            opprettNyNaeringsdrivendeSoknad().run {
                Triple(this, requireNotNull(fom), requireNotNull(tom))
            }

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
                    rolleDTO.orgnummer `should be equal to` "orgnummer"
                    rolleDTO.rolletype `should be equal to` "INNH"
                }
            }
            it.brukerHarOppgittForsikring `should be equal to` false
        }
    }

    @Test
    fun `Inneholder summert inntektsinformasjon i selvstendigNaringsdrivende`() {
        val (soknad, fom, tom) =
            opprettNyNaeringsdrivendeSoknad().run {
                Triple(this, requireNotNull(fom), requireNotNull(tom))
            }

        val soknadDTO =
            konverterTilSykepengesoknadDTO(
                sykepengesoknad =
                    soknad.copy(
                        soknadPerioder = soknadPerioder,
                        selvstendigNaringsdrivende =
                            SelvstendigNaringsdrivendeInfo(
                                roller = emptyList(),
                                sykepengegrunnlagNaeringsdrivende = lagSykepengegrunnlagNaeringsdrivende(),
                                erBarnepasser = false,
                                brukerHarOppgittForsikring = false,
                            ),
                    ),
                mottaker = Mottaker.ARBEIDSGIVER_OG_NAV,
                erEttersending = false,
                soknadsperioder = hentSoknadsPerioderMedFaktiskGrad(soknad).first,
            )

        soknadDTO.selvstendigNaringsdrivende!!.inntekt!!.also { naringsdrivendeInntektDTO ->
            naringsdrivendeInntektDTO.norskPersonidentifikator `should be equal to` "123456789"
            naringsdrivendeInntektDTO.inntektsAar.size `should be equal to` 3

            naringsdrivendeInntektDTO.inntektsAar.find { it.aar == "2021" }!!.pensjonsgivendeInntekt.also {
                it!!.pensjonsgivendeInntektAvLoennsinntekt `should be equal to` 10_000
                it.pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel `should be equal to` 190_000
                it.pensjonsgivendeInntektAvNaeringsinntekt `should be equal to` 500_000
                it.pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage `should be equal to` 300_000
            }

            naringsdrivendeInntektDTO.inntektsAar.find { it.aar == "2022" }!!.pensjonsgivendeInntekt.also {
                it!!.pensjonsgivendeInntektAvLoennsinntekt `should be equal to` 100_000
                it.pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel `should be equal to` 100_000
                it.pensjonsgivendeInntektAvNaeringsinntekt `should be equal to` 700_000
                it.pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage `should be equal to` 100_000
            }

            naringsdrivendeInntektDTO.inntektsAar.find { it.aar == "2023" }!!.pensjonsgivendeInntekt.also {
                it!!.pensjonsgivendeInntektAvLoennsinntekt `should be equal to` 200_000
                it.pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel `should be equal to` 100_000
                it.pensjonsgivendeInntektAvNaeringsinntekt `should be equal to` 600_000
                it.pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage `should be equal to` 100_000
            }
        }
    }

    @Test
    fun `Inneholder spørsmål om fravær for sykmeldingen`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad()
        val soknadDTO = lagSykepengesoknadDTO(soknad)

        soknadDTO.sporsmal!!.find { it.tag == "FRAVAR_FOR_SYKMELDINGEN_V2" }.also { fravaerSpm ->
            fravaerSpm?.svar.`should not be null`()
            fravaerSpm.sporsmalstekst `should be equal to`
                "Var du borte fra jobb i fire uker eller mer rett før du ble sykmeldt 1. juni 2018?"
            fravaerSpm.svar!!.size `should be equal to` 1
            fravaerSpm.svar!!.first().verdi `should be equal to` "JA"
        }
    }

    @Test
    fun `Inneholder hovedspørsmål for Selvstendig Næringsdrivende i forenklet format`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad()
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
                                sykepengegrunnlagNaeringsdrivende = lagSykepengegrunnlagNaeringsdrivende(),
                                erBarnepasser = false,
                                brukerHarOppgittForsikring = false,
                            ),
                    ),
                mottaker = Mottaker.ARBEIDSGIVER_OG_NAV,
                erEttersending = false,
                soknadsperioder = hentSoknadsPerioderMedFaktiskGrad(soknad).first,
            )

        soknadDTO.selvstendigNaringsdrivende!!.hovedSporsmalSvar.let {
            it.size `should be equal to` 11
            it[TILBAKE_I_ARBEID] `should be equal to` true
            it["ARBEID_UNDERVEIS_100_PROSENT_0"] `should be equal to` false
            it["JOBBET_DU_GRADERT_1"] `should be equal to` false
            it[NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT] `should be equal to` false
            it[ANDRE_INNTEKTSKILDER] `should be equal to` true
            it[OPPHOLD_UTENFOR_EOS] `should be equal to` true
            it[NARINGSDRIVENDE_OPPHOLD_I_UTLANDET] `should be equal to` false
            it[FRAVAR_FOR_SYKMELDINGEN_V2] `should be equal to` true
            it[NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET] `should be equal to` true
            it[NARINGSDRIVENDE_NY_I_ARBEIDSLIVET] `should be equal to` false
            it[NARINGSDRIVENDE_VARIG_ENDRING] `should be equal to` true
        }
    }

    @Test
    fun `Inneholder kun besvarte hovedspørsmål for Selvstendig Næringsdrivende i forenklet format`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad()
                .besvarsporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET, "NEI")

        val soknadDTO =
            konverterTilSykepengesoknadDTO(
                sykepengesoknad =
                    soknad.copy(
                        soknadPerioder = soknadPerioder,
                        selvstendigNaringsdrivende =
                            SelvstendigNaringsdrivendeInfo(
                                roller = emptyList(),
                                sykepengegrunnlagNaeringsdrivende = lagSykepengegrunnlagNaeringsdrivende(),
                                erBarnepasser = false,
                                brukerHarOppgittForsikring = false,
                            ),
                    ),
                mottaker = Mottaker.ARBEIDSGIVER_OG_NAV,
                erEttersending = false,
                soknadsperioder = hentSoknadsPerioderMedFaktiskGrad(soknad).first,
            )

        soknadDTO.selvstendigNaringsdrivende!!.hovedSporsmalSvar.let {
            it.size `should be equal to` 9
            it[TILBAKE_I_ARBEID] `should be equal to` true
            it["ARBEID_UNDERVEIS_100_PROSENT_0"] `should be equal to` false
            it["JOBBET_DU_GRADERT_1"] `should be equal to` false
            it[NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT] `should be equal to` false
            it[ANDRE_INNTEKTSKILDER] `should be equal to` true
            it[OPPHOLD_UTENFOR_EOS] `should be equal to` true
            it[NARINGSDRIVENDE_OPPHOLD_I_UTLANDET] `should be equal to` false
            it[FRAVAR_FOR_SYKMELDINGEN_V2] `should be equal to` true
            it[NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET] `should be equal to` false
        }
    }

    @Test
    fun `Inneholder oppdelte hovedspørsmål for Selvstendig Næringsdrivende`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad()
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
                                sykepengegrunnlagNaeringsdrivende = lagSykepengegrunnlagNaeringsdrivende(),
                                erBarnepasser = false,
                                brukerHarOppgittForsikring = false,
                            ),
                    ),
                mottaker = Mottaker.ARBEIDSGIVER_OG_NAV,
                erEttersending = false,
                soknadsperioder = hentSoknadsPerioderMedFaktiskGrad(soknad).first,
            )

        soknadDTO.selvstendigNaringsdrivende!!.hovedSporsmalSvar.let {
            it.size `should be equal to` 11
            it[TILBAKE_I_ARBEID] `should be equal to` true
            it["ARBEID_UNDERVEIS_100_PROSENT_0"] `should be equal to` false
            it["JOBBET_DU_GRADERT_1"] `should be equal to` false
            it[NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT] `should be equal to` false
            it[ANDRE_INNTEKTSKILDER] `should be equal to` true
            it[OPPHOLD_UTENFOR_EOS] `should be equal to` true
            it[NARINGSDRIVENDE_OPPHOLD_I_UTLANDET] `should be equal to` false
            it[FRAVAR_FOR_SYKMELDINGEN_V2] `should be equal to` true
            it[NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET] `should be equal to` false
            it[NARINGSDRIVENDE_NY_I_ARBEIDSLIVET] `should be equal to` false
            it[NARINGSDRIVENDE_VARIG_ENDRING] `should be equal to` false
        }
    }

    @Test
    fun `Inneholder brukers svar på forsikring`() {
        val soknad = opprettNyNaeringsdrivendeSoknad()

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

        soknadDTO.selvstendigNaringsdrivende!!.also {
            it.brukerHarOppgittForsikring `should be equal to` true
        }
    }

    private fun lagSykepengesoknadDTO(soknad: Sykepengesoknad): SykepengesoknadDTO =
        konverterTilSykepengesoknadDTO(
            sykepengesoknad = soknad,
            mottaker = Mottaker.NAV,
            erEttersending = false,
            soknadsperioder = hentSoknadsPerioderMedFaktiskGrad(soknad).first,
        )
}
