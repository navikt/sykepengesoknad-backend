package no.nav.helse.flex.arbeidstaker

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FaktiskGradUtregningNyttArbeidUnderveisSporsmalTest : FellesTestOppsett() {
    private val fnr = "12345678900"

    @BeforeAll
    fun configureUnleash() {
        fakeUnleash.resetAll()
        fakeUnleash.enable(UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL)
    }

    @Test
    fun `Jobbet 18,75 timer og normalarbeidsuke gir faktisk grad 50 prosent`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        // Mandag
                        fom = LocalDate.of(2022, 12, 12),
                        // Fredag
                        tom = LocalDate.of(2022, 12, 16),
                    ),
            ),
        )

        val perioderMedGrad =
            besvarOgSendSoknad(
                listOf(
                    "JOBBER_DU_NORMAL_ARBEIDSUKE_0" to "JA",
                    "HVOR_MYE_TIMER_0" to "CHECKED",
                    "HVOR_MYE_TIMER_VERDI_0" to "18,75",
                    "ARBEID_UNDERVEIS_100_PROSENT_0" to "JA",
                ),
            )

        perioderMedGrad shouldBeEqualTo
            listOf(
                Triple(50, 100, 37.5),
            )
    }

    @Test
    fun `Jobbet 10 timer og 40 timersuke gir faktisk grad 25 prosent`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        // Mandag
                        fom = LocalDate.of(2022, 12, 12),
                        // Fredag
                        tom = LocalDate.of(2022, 12, 16),
                    ),
            ),
        )

        val perioderMedGrad =
            besvarOgSendSoknad(
                listOf(
                    "JOBBER_DU_NORMAL_ARBEIDSUKE_0" to "NEI",
                    "HVOR_MANGE_TIMER_PER_UKE_0" to "40",
                    "HVOR_MYE_TIMER_0" to "CHECKED",
                    "HVOR_MYE_TIMER_VERDI_0" to "10",
                    "ARBEID_UNDERVEIS_100_PROSENT_0" to "JA",
                ),
            )

        perioderMedGrad shouldBeEqualTo
            listOf(
                Triple(25, 100, 40.0),
            )
    }

    @Test
    fun `Jobbet 10 timer og 40 timersuke over 2 uker gir faktisk grad 13 prosent`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        // Mandag
                        fom = LocalDate.of(2022, 12, 5),
                        // Fredag
                        tom = LocalDate.of(2022, 12, 16),
                    ),
            ),
        )

        val perioderMedGrad =
            besvarOgSendSoknad(
                listOf(
                    "JOBBER_DU_NORMAL_ARBEIDSUKE_0" to "NEI",
                    "HVOR_MANGE_TIMER_PER_UKE_0" to "40",
                    "HVOR_MYE_TIMER_0" to "CHECKED",
                    "HVOR_MYE_TIMER_VERDI_0" to "10",
                    "ARBEID_UNDERVEIS_100_PROSENT_0" to "JA",
                ),
            )

        perioderMedGrad shouldBeEqualTo
            listOf(
                Triple(13, 100, 40.0),
            )
    }

    @Test
    fun `Jobbet 9 timer og 40 timersuke over 2 uker gir faktisk grad 11 prosent`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        // Mandag
                        fom = LocalDate.of(2022, 12, 5),
                        // Fredag
                        tom = LocalDate.of(2022, 12, 16),
                    ),
            ),
        )

        val perioderMedGrad =
            besvarOgSendSoknad(
                listOf(
                    "JOBBER_DU_NORMAL_ARBEIDSUKE_0" to "NEI",
                    "HVOR_MANGE_TIMER_PER_UKE_0" to "40",
                    "HVOR_MYE_TIMER_0" to "CHECKED",
                    "HVOR_MYE_TIMER_VERDI_0" to "9",
                    "ARBEID_UNDERVEIS_100_PROSENT_0" to "JA",
                ),
            )

        perioderMedGrad shouldBeEqualTo
            listOf(
                Triple(11, 100, 40.0),
            )
    }

    @Test
    fun `Jobbet 15 prosent og 40 timersuke over 2 uker gir faktisk grad 15 prosent`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        // Fredag
                        fom = LocalDate.of(2022, 12, 5),
                        // Fredag
                        tom = LocalDate.of(2022, 12, 16),
                    ),
            ),
        )

        val perioderMedGrad =
            besvarOgSendSoknad(
                listOf(
                    "JOBBER_DU_NORMAL_ARBEIDSUKE_0" to "NEI",
                    "HVOR_MANGE_TIMER_PER_UKE_0" to "40",
                    "HVOR_MYE_PROSENT_0" to "CHECKED",
                    "HVOR_MYE_PROSENT_VERDI_0" to "15",
                    "ARBEID_UNDERVEIS_100_PROSENT_0" to "JA",
                ),
            )

        perioderMedGrad shouldBeEqualTo
            listOf(
                Triple(15, 100, 40.0),
            )
    }

    @Test
    fun `Jobbet 15 prosent og normal timersuke over 2 uker gir faktisk grad 15 prosent`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        // Mandag
                        fom = LocalDate.of(2022, 12, 5),
                        // Fredag
                        tom = LocalDate.of(2022, 12, 16),
                    ),
            ),
        )

        val perioderMedGrad =
            besvarOgSendSoknad(
                listOf(
                    "JOBBER_DU_NORMAL_ARBEIDSUKE_0" to "JA",
                    "HVOR_MYE_PROSENT_0" to "CHECKED",
                    "HVOR_MYE_PROSENT_VERDI_0" to "15",
                    "ARBEID_UNDERVEIS_100_PROSENT_0" to "JA",
                ),
            )

        perioderMedGrad shouldBeEqualTo
            listOf(
                Triple(15, 100, 37.5),
            )
    }

    fun besvarOgSendSoknad(svar: List<Pair<String, String>>): List<Triple<Int?, Int?, Double?>> {
        val hentetViaRest = hentSoknader(fnr)
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknad = hentetViaRest.first { it.status == RSSoknadstatus.NY }
        SoknadBesvarer(rSSykepengesoknad = soknad, mockMvc = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(TILBAKE_I_ARBEID, "NEI")
            .besvarSporsmal(FERIE_V2, "NEI")
            .besvarSporsmal(PERMISJON_V2, "NEI")
            .besvarSporsmal(UTLAND_V2, "NEI")
            .besvarSporsmal(ANDRE_INNTEKTSKILDER_V2, "NEI")
            .besvarSporsmal(TIL_SLUTT, svar = "Jeg lover å ikke lyve!", ferdigBesvart = false)
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED").also {
                svar.forEachIndexed { index, pair ->
                    it.besvarSporsmal(pair.first, pair.second, index == svar.size - 1)
                }
            }
            .sendSoknad()
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
        val soknadPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        return soknadPaKafka.soknadsperioder!!.map {
            Triple(it.faktiskGrad, it.sykmeldingsgrad, it.avtaltTimer)
        }
    }
}
