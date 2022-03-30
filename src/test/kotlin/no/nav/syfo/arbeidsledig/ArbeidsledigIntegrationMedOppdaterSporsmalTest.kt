package no.nav.syfo.arbeidsledig

import no.nav.helse.flex.sykepengesoknad.kafka.PeriodeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.syfo.*
import no.nav.syfo.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.rest.SoknadMetadata
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.soknadsopprettelse.*
import no.nav.syfo.soknadsopprettelse.OpprettSoknadService
import no.nav.syfo.testutil.SoknadBesvarer
import no.nav.syfo.testutil.opprettSoknadFraSoknadMetadata
import no.nav.syfo.util.tilOsloInstant
import org.amshove.kluent.`should be true`
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@TestMethodOrder(MethodOrderer.MethodName::class)
class ArbeidsledigIntegrationMedOppdaterSporsmalTest : BaseTestClass() {

    @Autowired
    private lateinit var opprettSoknadService: OpprettSoknadService

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    final val fnr = "123456789"
    final val aktorid = fnr + "00"

    private val soknadMetadata = SoknadMetadata(
        startSykeforlop = LocalDate.of(2018, 1, 1),
        sykmeldingSkrevet = LocalDateTime.of(2018, 1, 1, 12, 0).tilOsloInstant(),
        arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
        sykmeldingsperioder = listOf(
            SykmeldingsperiodeAGDTO(
                fom = (LocalDate.of(2018, 1, 1)),
                tom = (LocalDate.of(2020, 5, 10)),
                gradert = GradertDTO(grad = 100, reisetilskudd = false),
                type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false,
            ),
        ).tilSoknadsperioder(),
        fnr = fnr,
        fom = LocalDate.of(2018, 1, 1),
        tom = LocalDate.of(2020, 5, 10),
        status = Soknadstatus.NY,
        sykmeldingId = "sykmeldingId",
        arbeidsgiverNavn = null,
        soknadstype = Soknadstype.ARBEIDSLEDIG,
        arbeidsgiverOrgnummer = null
    )

    @Test
    fun `01 - vi oppretter en arbeidsledigsøknad`() {
        // Opprett søknad
        opprettSoknadService.opprettSoknadFraSoknadMetadata(soknadMetadata, sykepengesoknadDAO)

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.ARBEIDSLEDIG)
    }

    @Test
    fun `02 - søknaden har alle spørsmål`() {
        val soknader = hentSoknader(fnr)
        assertThat(soknader).hasSize(1)
        val soknaden = soknader.first()

        assertThat(soknaden.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                FRISKMELDT,
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER,
                UTDANNING,
                ARBEIDSLEDIG_UTLAND,
                PERMITTERT_NAA,
                PERMITTERT_PERIODE,
                VAER_KLAR_OVER_AT,
                BEKREFT_OPPLYSNINGER
            )
        )
    }

    @Test
    fun `03 - vi svarer på ansvarserklæringa som ikke muterer søknaden`() {
        val soknaden = hentSoknader(fnr).first()

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .also {
                assertThat(it.muterteSoknaden).isFalse()
            }
    }

    @Test
    fun `04 - vi svarer at vi ble friskmeldt midt i søknadsperioden - Det muterer søknaden`() {
        val soknaden = hentSoknader(fnr).first()

        assertThat(soknaden.sporsmal!!.first { it.tag == ANDRE_INNTEKTSKILDER }.sporsmalstekst)
            .isEqualTo(
                "Har du hatt inntekt mens du har vært sykmeldt i perioden 1. januar 2018 - 10. mai 2020?"
            )
        assertThat(soknaden.sporsmal!!.first { it.tag == UTDANNING }.sporsmalstekst)
            .isEqualTo(
                "Har du vært under utdanning i løpet av perioden 1. januar 2018 - 10. mai 2020?"
            )
        assertThat(soknaden.sporsmal!!.first { it.tag == ARBEIDSLEDIG_UTLAND }.sporsmalstekst)
            .isEqualTo(
                "Var du på reise utenfor EØS mens du var sykmeldt 1. januar 2018 - 10. mai 2020?"
            )

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(FRISKMELDT, "NEI", false)
            .besvarSporsmal(FRISKMELDT_START, LocalDate.of(2018, 1, 5).format(DateTimeFormatter.ISO_LOCAL_DATE))
            .also {
                assertThat(it.muterteSoknaden).isTrue()

                assertThat(it.rSSykepengesoknad.sporsmal!!.first { it.tag == ANDRE_INNTEKTSKILDER }.sporsmalstekst)
                    .isEqualTo(
                        "Har du hatt inntekt mens du har vært sykmeldt i perioden 1. - 4. januar 2018?"
                    )
                assertThat(it.rSSykepengesoknad.sporsmal!!.first { it.tag == UTDANNING }.sporsmalstekst)
                    .isEqualTo(
                        "Har du vært under utdanning i løpet av perioden 1. - 4. januar 2018?"
                    )
                assertThat(it.rSSykepengesoknad.sporsmal!!.first { it.tag == ARBEIDSLEDIG_UTLAND }.sporsmalstekst)
                    .isEqualTo(
                        "Var du på reise utenfor EØS mens du var sykmeldt 1. - 4. januar 2018?"
                    )
            }
    }

    @Test
    fun `05 - unødvendige spørsmål forsvinner når man blir friskmeldt første dag i søknadsperioden`() {
        val soknaden = hentSoknader(fnr).first()

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(FRISKMELDT_START, LocalDate.of(2018, 1, 1).format(DateTimeFormatter.ISO_LOCAL_DATE))
            .also {
                assertThat(it.rSSykepengesoknad.sporsmal!!.map { it.tag }).isEqualTo(
                    listOf(
                        ANSVARSERKLARING,
                        FRISKMELDT,
                        ARBEID_UTENFOR_NORGE,
                        PERMITTERT_NAA,
                        PERMITTERT_PERIODE,
                        VAER_KLAR_OVER_AT,
                        BEKREFT_OPPLYSNINGER
                    )
                )
            }
    }

    @Test
    fun `06 - unødvendige spørsmål kommer tilbake når man svarer at man ikke ble friskmeldt likevel`() {
        val soknaden = hentSoknader(fnr).first()

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(FRISKMELDT, "JA")
            .also {
                assertThat(it.rSSykepengesoknad.sporsmal!!.map { it.tag }).isEqualTo(
                    listOf(
                        ANSVARSERKLARING,
                        FRISKMELDT,
                        ARBEID_UTENFOR_NORGE,
                        ANDRE_INNTEKTSKILDER,
                        UTDANNING,
                        ARBEIDSLEDIG_UTLAND,
                        PERMITTERT_NAA,
                        PERMITTERT_PERIODE,
                        VAER_KLAR_OVER_AT,
                        BEKREFT_OPPLYSNINGER
                    )
                )
            }
    }

    @Test
    fun `07 - vi kan ikke sende inn søknaden før alle spørsmål er besvart`() {
        val soknaden = hentSoknader(fnr).first()
        sendSoknadMedResult(fnr, soknaden.id).andExpect(((MockMvcResultMatchers.status().isBadRequest)))
    }

    @Test
    fun `08 - vi besvarer alle sporsmal`() {
        val soknaden = hentSoknader(fnr).first()

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(FRISKMELDT, "NEI")
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "JA")
            .besvarSporsmal(PERMITTERT_NAA, "NEI")
            .besvarSporsmal(PERMITTERT_PERIODE, "NEI")
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
    }

    @Test
    fun `09 - vi får en feil dersom spørsmål id ikke finnes i søknaden`() {
        val soknaden = hentSoknader(fnr).first()

        val json =
            oppdaterSporsmalMedResult(fnr, soknaden.sporsmal!![0].copy(id = "FEILID"), soknadsId = soknaden.id)
                .andExpect(MockMvcResultMatchers.status().isBadRequest).andReturn().response.contentAsString
        assertThat(json).isEqualTo("""{"reason":"SPORSMAL_FINNES_IKKE_I_SOKNAD"}""")
    }

    @Test
    fun `10 - vi sender inn søknaden - Den får da status sendt og blir publisert på kafka`() {

        sendSoknad(fnr, hentSoknader(fnr).first().id)
        val soknaden = hentSoknader(fnr).first()

        assertThat(soknaden.status).isEqualTo(RSSoknadstatus.SENDT)
        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.ARBEIDSLEDIG)
        assertThat(soknader.last().status).isEqualTo(SoknadsstatusDTO.SENDT)
        assertThat(soknader.last().permitteringer).hasSize(0)
        soknader.last().arbeidUtenforNorge!!.`should be true`()
    }

    @Test
    fun `11 - vi kan ikke besvare spørsmål på en søknad som er sendt`() {

        val soknaden = hentSoknader(fnr).first()

        assertThat(soknaden.status).isEqualTo(RSSoknadstatus.SENDT)

        val json = oppdaterSporsmalMedResult(fnr, soknaden.sporsmal!![0], soknadsId = soknaden.id)
            .andExpect(MockMvcResultMatchers.status().isBadRequest).andReturn().response.contentAsString
        assertThat(json).isEqualTo("""{"reason":"FEIL_STATUS_FOR_OPPDATER_SPORSMAL"}""")
    }

    @Test
    fun `12 - vi korrigerer søknaden og svarer at vi er permittert`() {

        val soknadId = hentSoknader(fnr).first().id
        korrigerSoknad(soknadId, fnr)
        val soknad = hentSoknader(fnr).find { it.korrigerer == soknadId }!!

        SoknadBesvarer(rSSykepengesoknad = soknad, mockMvc = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(PERMITTERT_NAA, "JA", false)
            .besvarSporsmal(PERMITTERT_NAA_NAR, "2020-02-01")
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
            .sendSoknad()

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().permitteringer).isEqualTo(listOf(PeriodeDTO(fom = LocalDate.of(2020, 2, 1))))
    }

    @Test
    fun `13 - vi korrigerer søknaden og svarer at vi var permittert i en periode`() {

        val soknadId = hentSoknader(fnr).find { it.status == RSSoknadstatus.SENDT }!!.id
        korrigerSoknad(soknadId, fnr)
        val soknad = hentSoknader(fnr).find { it.korrigerer == soknadId }!!

        SoknadBesvarer(rSSykepengesoknad = soknad, mockMvc = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(PERMITTERT_NAA, "NEI")
            .besvarSporsmal(PERMITTERT_PERIODE, "JA", false)
            .besvarSporsmal(
                PERMITTERT_PERIODE_NAR,
                "{\"fom\":\"${soknad.fom!!.minusDays(14)}\",\"tom\":\"${soknad.fom!!.minusDays(7)}\"}"
            )
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
            .sendSoknad()

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().permitteringer).isEqualTo(
            listOf(PeriodeDTO(fom = soknad.fom!!.minusDays(14), tom = soknad.fom!!.minusDays(7)))
        )
        assertThat(soknader[0].sendTilGosys).isNull()
        assertThat(soknader[0].merknader).isNull()
    }
}
