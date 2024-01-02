package no.nav.helse.flex.arbeidstaker

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.EGENMELDINGER
import no.nav.helse.flex.soknadsopprettelse.EGENMELDINGER_NAR
import no.nav.helse.flex.soknadsopprettelse.PAPIRSYKMELDING_NAR
import no.nav.helse.flex.soknadsopprettelse.TIDLIGERE_EGENMELDING
import no.nav.helse.flex.soknadsopprettelse.TIDLIGERE_PAPIRSYKMELDING
import no.nav.helse.flex.soknadsopprettelse.TIDLIGERE_SYK
import no.nav.helse.flex.sykepengesoknad.kafka.PeriodeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL
import no.nav.helse.flex.util.DatoUtil
import no.nav.helse.flex.ventPåRecords
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@TestMethodOrder(MethodOrderer.MethodName::class)
class GammeltEgenmeldingSporsmalTest : BaseTestClass() {
    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @BeforeAll
    fun configureUnleash() {
        fakeUnleash.resetAll()
        fakeUnleash.enable(UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL)
    }

    private val fnr = "12345678900"

    @Test
    fun `1 - vi lager en sykmelding med gammelt format`() {
        val soknad =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = LocalDate.now().minusDays(20),
                            tom = LocalDate.now().minusDays(10),
                        ),
                ),
            ).first()
        val hentet = sykepengesoknadDAO.finnSykepengesoknad(soknad.id)

        val nyesporsmal =
            hentet.sporsmal.toMutableList().also {
                it.add(1, gammeltEgenmeldingSpm(LocalDate.now().minusDays(19)))
            }

        sykepengesoknadDAO.byttUtSporsmal(hentet.copy(sporsmal = nyesporsmal))
    }

    @Test
    fun `2 - vi besvarer og sender inn søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()

        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = fnr,
            )

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
                .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
                .besvarSporsmal(tag = "EGENMELDINGER", svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(
                    tag = "TIDLIGERE_PAPIRSYKMELDING",
                    svar = "CHECKED",
                    ferdigBesvart = false,
                )
                .besvarSporsmal(
                    tag = "PAPIRSYKMELDING_NAR",
                    svar = """{"fom":"${soknaden.fom!!.minusDays(14)}","tom":"${soknaden.fom!!.minusDays(7)}"}""",
                )
                .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
                .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
                .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
                .besvarSporsmal(tag = "UTLAND_V2", svar = "NEI")
                .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_0", svar = "NEI")
                .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
                .besvarSporsmal(tag = "TIL_SLUTT", svar = "Jeg lover å ikke lyve!", ferdigBesvart = false)
                .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val soknadenPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        assertThat(soknadenPaKafka.status).isEqualTo(SoknadsstatusDTO.SENDT)
        assertThat(soknadenPaKafka.papirsykmeldinger).isEqualTo(
            listOf(
                PeriodeDTO(
                    fom = soknaden.fom!!.minusDays(14),
                    tom = soknaden.fom!!.minusDays(7),
                ),
            ),
        )
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }
}

fun gammeltEgenmeldingSpm(tidligsteFomForSykmelding: LocalDate): Sporsmal {
    return Sporsmal(
        tag = EGENMELDINGER,
        sporsmalstekst = "Vi har registrert at du ble sykmeldt ${
            DatoUtil.formatterDatoMedUkedag(
                tidligsteFomForSykmelding,
            )
        }. Var du syk og borte fra jobb i perioden ${
            DatoUtil.formatterPeriode(
                tidligsteFomForSykmelding.minusDays(16),
                tidligsteFomForSykmelding.minusDays(1),
            )
        }?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = TIDLIGERE_SYK,
                    svartype = Svartype.CHECKBOX_GRUPPE,
                    undersporsmal =
                        listOf(
                            Sporsmal(
                                tag = TIDLIGERE_EGENMELDING,
                                sporsmalstekst = "Jeg var syk med egenmelding",
                                svartype = Svartype.CHECKBOX,
                                kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                                undersporsmal =
                                    listOf(
                                        Sporsmal(
                                            tag = EGENMELDINGER_NAR,
                                            sporsmalstekst = "Hvilke dager var du syk med egenmelding? Du trenger bare oppgi dager før ${
                                                DatoUtil.formatterDato(
                                                    tidligsteFomForSykmelding,
                                                )
                                            }.",
                                            svartype = Svartype.PERIODER,
                                            min =
                                                tidligsteFomForSykmelding.minusMonths(6)
                                                    .format(DateTimeFormatter.ISO_LOCAL_DATE),
                                            max =
                                                tidligsteFomForSykmelding.minusDays(1)
                                                    .format(DateTimeFormatter.ISO_LOCAL_DATE),
                                        ),
                                    ),
                            ),
                            Sporsmal(
                                tag = TIDLIGERE_PAPIRSYKMELDING,
                                sporsmalstekst = "Jeg var syk med papirsykmelding",
                                svartype = Svartype.CHECKBOX,
                                kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                                undersporsmal =
                                    listOf(
                                        Sporsmal(
                                            tag = PAPIRSYKMELDING_NAR,
                                            sporsmalstekst =
                                                "Hvilke dager var du syk med papirsykmelding? Du " +
                                                    "trenger bare oppgi dager før ${
                                                        DatoUtil.formatterDato(
                                                            tidligsteFomForSykmelding,
                                                        )
                                                    }.",
                                            svartype = Svartype.PERIODER,
                                            min =
                                                tidligsteFomForSykmelding.minusMonths(6)
                                                    .format(DateTimeFormatter.ISO_LOCAL_DATE),
                                            max =
                                                tidligsteFomForSykmelding.minusDays(1)
                                                    .format(DateTimeFormatter.ISO_LOCAL_DATE),
                                        ),
                                    ),
                            ),
                        ),
                ),
            ),
    )
}
