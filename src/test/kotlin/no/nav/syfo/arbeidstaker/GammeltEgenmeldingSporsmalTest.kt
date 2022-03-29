package no.nav.syfo.arbeidstaker

import no.nav.helse.flex.sykepengesoknad.kafka.PeriodeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.syfo.BaseTestClass
import no.nav.syfo.client.narmesteleder.Forskuttering
import no.nav.syfo.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.Sporsmal
import no.nav.syfo.domain.Svartype
import no.nav.syfo.domain.Visningskriterie
import no.nav.syfo.domain.rest.SoknadMetadata
import no.nav.syfo.hentSoknader
import no.nav.syfo.mockArbeidsgiverForskutterer
import no.nav.syfo.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.soknadsopprettelse.EGENMELDINGER
import no.nav.syfo.soknadsopprettelse.EGENMELDINGER_NAR
import no.nav.syfo.soknadsopprettelse.FRAVAR_FOR_SYKMELDINGEN
import no.nav.syfo.soknadsopprettelse.PAPIRSYKMELDING_NAR
import no.nav.syfo.soknadsopprettelse.TIDLIGERE_EGENMELDING
import no.nav.syfo.soknadsopprettelse.TIDLIGERE_PAPIRSYKMELDING
import no.nav.syfo.soknadsopprettelse.TIDLIGERE_SYK
import no.nav.syfo.soknadsopprettelse.settOppSoknadArbeidstaker
import no.nav.syfo.soknadsopprettelse.tilSoknadsperioder
import no.nav.syfo.testutil.SoknadBesvarer
import no.nav.syfo.tilSoknader
import no.nav.syfo.util.DatoUtil
import no.nav.syfo.util.tilOsloInstant
import no.nav.syfo.ventPåRecords
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@TestMethodOrder(MethodOrderer.MethodName::class)
class GammeltEgenmeldingSporsmalTest : BaseTestClass() {

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    final val fnr = "123456789"
    final val aktorid = "${fnr}00"

    @BeforeEach
    fun setUp() {
        mockArbeidsgiverForskutterer(Forskuttering.JA)
    }

    @Test
    fun `1 - vi lager en sykmelding med gammelt format`() {
        val fom = LocalDate.now().minusDays(19)
        val soknadMetadata = SoknadMetadata(
            fnr = fnr,
            status = Soknadstatus.NY,
            startSykeforlop = LocalDate.now().minusDays(24),
            fom = fom,
            tom = LocalDate.now().minusDays(10),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            arbeidsgiverOrgnummer = "123456789",
            arbeidsgiverNavn = "ARBEIDSGIVER A/S",
            sykmeldingId = "289148ba-4c3c-4b3f-b7a3-385b7e7c927d",
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            sykmeldingSkrevet = LocalDateTime.now().minusDays(19).tilOsloInstant(),
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = fom,
                    tom = LocalDate.now().minusDays(15),
                    gradert = GradertDTO(grad = 100, reisetilskudd = false),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
            ).tilSoknadsperioder(),
            egenmeldtSykmelding = null

        )
        val standardSoknad = settOppSoknadArbeidstaker(
            soknadMetadata = soknadMetadata,
            erForsteSoknadISykeforlop = true,
            tidligsteFomForSykmelding = fom,
        )

        val nyesporsmal = standardSoknad.sporsmal.map {
            if (it.tag == FRAVAR_FOR_SYKMELDINGEN) {
                gammeltEgenmeldingSpm(fom)
            } else {
                it
            }
        }

        sykepengesoknadDAO.lagreSykepengesoknad(standardSoknad.copy(sporsmal = nyesporsmal))
    }

    @Test
    fun `2 - vi besvarer og sender inn søknaden`() {
        flexSyketilfelleMockRestServiceServer?.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()

        val soknaden = hentSoknader(fnr).find { it.status == RSSoknadstatus.NY }!!

        val sendtSoknad = SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "PERMITTERT_NAA", svar = "NEI")
            .besvarSporsmal(tag = "PERMITTERT_PERIODE", svar = "NEI")
            .besvarSporsmal(tag = "EGENMELDINGER", svar = "JA", ferdigBesvart = false)
            .besvarSporsmal(
                tag = "TIDLIGERE_PAPIRSYKMELDING",
                svar = "CHECKED",
                ferdigBesvart = false
            )
            .besvarSporsmal(
                tag = "PAPIRSYKMELDING_NAR",
                svar = """{"fom":"${soknaden.fom!!.minusDays(14)}","tom":"${soknaden.fom!!.minusDays(7)}"}"""
            )
            .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
            .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
            .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
            .besvarSporsmal(tag = "UTLAND_V2", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UTENFOR_NORGE", svar = "NEI")
            .besvarSporsmal(tag = "JOBBET_DU_100_PROSENT_0", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER", svar = "NEI")
            .besvarSporsmal(tag = "UTDANNING", svar = "NEI")
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val soknadenPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        assertThat(soknadenPaKafka.status).isEqualTo(SoknadsstatusDTO.SENDT)
        assertThat(soknadenPaKafka.papirsykmeldinger).isEqualTo(
            listOf(
                PeriodeDTO(
                    fom = soknaden.fom!!.minusDays(14),
                    tom = soknaden.fom!!.minusDays(7)
                )
            )
        )
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }
}

fun gammeltEgenmeldingSpm(tidligsteFomForSykmelding: LocalDate): Sporsmal {
    return Sporsmal(
        tag = EGENMELDINGER,
        sporsmalstekst = "Vi har registrert at du ble sykmeldt ${
        DatoUtil.formatterDatoMedUkedag(
            tidligsteFomForSykmelding
        )
        }. Var du syk og borte fra jobb i perioden ${
        DatoUtil.formatterPeriode(
            tidligsteFomForSykmelding.minusDays(16),
            tidligsteFomForSykmelding.minusDays(1)
        )
        }?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal = listOf(
            Sporsmal(
                tag = TIDLIGERE_SYK,
                svartype = Svartype.CHECKBOX_GRUPPE,
                undersporsmal = listOf(
                    Sporsmal(
                        tag = TIDLIGERE_EGENMELDING,
                        sporsmalstekst = "Jeg var syk med egenmelding",
                        svartype = Svartype.CHECKBOX,
                        kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                        undersporsmal = listOf(
                            Sporsmal(
                                tag = EGENMELDINGER_NAR,
                                sporsmalstekst = "Hvilke dager var du syk med egenmelding? Du trenger bare oppgi dager før ${
                                DatoUtil.formatterDato(
                                    tidligsteFomForSykmelding
                                )
                                }.",
                                svartype = Svartype.PERIODER,
                                min = tidligsteFomForSykmelding.minusMonths(6)
                                    .format(DateTimeFormatter.ISO_LOCAL_DATE),
                                max = tidligsteFomForSykmelding.minusDays(1)
                                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
                            )
                        )
                    ),
                    Sporsmal(
                        tag = TIDLIGERE_PAPIRSYKMELDING,
                        sporsmalstekst = "Jeg var syk med papirsykmelding",
                        svartype = Svartype.CHECKBOX,
                        kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                        undersporsmal = listOf(
                            Sporsmal(
                                tag = PAPIRSYKMELDING_NAR,
                                sporsmalstekst = "Hvilke dager var du syk med papirsykmelding? Du trenger bare oppgi dager før ${
                                DatoUtil.formatterDato(
                                    tidligsteFomForSykmelding
                                )
                                }.",
                                svartype = Svartype.PERIODER,
                                min = tidligsteFomForSykmelding.minusMonths(6)
                                    .format(DateTimeFormatter.ISO_LOCAL_DATE),
                                max = tidligsteFomForSykmelding.minusDays(1)
                                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
                            )
                        )
                    )
                )
            )
        )
    )
}
