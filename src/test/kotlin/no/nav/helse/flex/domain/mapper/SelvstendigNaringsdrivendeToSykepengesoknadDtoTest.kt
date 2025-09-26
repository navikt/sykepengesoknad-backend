package no.nav.helse.flex.domain.mapper

import no.nav.helse.flex.domain.BrregRolle
import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.SelvstendigNaringsdrivendeInfo
import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.Ventetid
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentSoknadsPerioderMedFaktiskGrad
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknad
import no.nav.helse.flex.soknadsopprettelse.lagSykepengegrunnlagNaeringsdrivende
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
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
    fun `Inneholder roller og ventetid selv om inntektsinformasjon mangler`() {
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
                                ventetid =
                                    Ventetid(
                                        fom = fom,
                                        tom = tom,
                                    ),
                            ),
                    ),
                mottaker = Mottaker.ARBEIDSGIVER_OG_NAV,
                erEttersending = false,
                soknadsperioder = hentSoknadsPerioderMedFaktiskGrad(soknad).first,
            )

        soknadDTO.selvstendigNaringsdrivende!!.also {
            it.ventetid!!.also { ventetid ->
                ventetid.fom `should be equal to` fom
                ventetid.tom `should be equal to` tom
            }
            it.roller.also { roller ->
                roller.single().also { rolleDTO ->
                    rolleDTO.orgnummer `should be equal to` "orgnummer"
                    rolleDTO.rolletype `should be equal to` "INNH"
                }
            }
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
                                ventetid =
                                    Ventetid(
                                        fom = fom,
                                        tom = tom,
                                    ),
                            ),
                    ),
                mottaker = Mottaker.ARBEIDSGIVER_OG_NAV,
                erEttersending = false,
                soknadsperioder = hentSoknadsPerioderMedFaktiskGrad(soknad).first,
            )

        soknadDTO.selvstendigNaringsdrivende!!.ventetid!!.also {
            fom `should be equal to` fom
            tom `should be equal to` tom
        }

        soknadDTO.selvstendigNaringsdrivende!!.inntekt!!.also { naringsdrivendeInntektDTO ->
            naringsdrivendeInntektDTO.norskPersonidentifikator `should be equal to` "123456789"
            naringsdrivendeInntektDTO.inntektsAar.size `should be equal to` 3

            naringsdrivendeInntektDTO.inntektsAar.find { it.aar == "2021" }!!.pensjonsgivendeInntekt.also {
                it.pensjonsgivendeInntektAvLoennsinntekt `should be equal to` 10_000
                it.pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel `should be equal to` 190_000
                it.pensjonsgivendeInntektAvNaeringsinntekt `should be equal to` 500_000
                it.pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage `should be equal to` 300_000
            }

            naringsdrivendeInntektDTO.inntektsAar.find { it.aar == "2022" }!!.pensjonsgivendeInntekt.also {
                it.pensjonsgivendeInntektAvLoennsinntekt `should be equal to` 100_000
                it.pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel `should be equal to` 100_000
                it.pensjonsgivendeInntektAvNaeringsinntekt `should be equal to` 700_000
                it.pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage `should be equal to` 100_000
            }

            naringsdrivendeInntektDTO.inntektsAar.find { it.aar == "2023" }!!.pensjonsgivendeInntekt.also {
                it.pensjonsgivendeInntektAvLoennsinntekt `should be equal to` 200_000
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

    private fun lagSykepengesoknadDTO(soknad: Sykepengesoknad): SykepengesoknadDTO =
        konverterTilSykepengesoknadDTO(
            sykepengesoknad = soknad,
            mottaker = Mottaker.NAV,
            erEttersending = false,
            soknadsperioder = hentSoknadsPerioderMedFaktiskGrad(soknad).first,
        )
}
