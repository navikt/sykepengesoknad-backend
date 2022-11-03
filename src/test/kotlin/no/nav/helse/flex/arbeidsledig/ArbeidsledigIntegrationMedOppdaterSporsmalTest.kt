package no.nav.helse.flex.arbeidsledig

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.aktivering.AktiverEnkeltSoknad
import no.nav.helse.flex.aktivering.kafka.AktiveringProducer
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.rest.SoknadMetadata
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.oppdaterSporsmalMedResult
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.sendSoknad
import no.nav.helse.flex.sendSoknadMedResult
import no.nav.helse.flex.soknadsopprettelse.ANDRE_INNTEKTSKILDER
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING
import no.nav.helse.flex.soknadsopprettelse.ARBEIDSLEDIG_UTLAND
import no.nav.helse.flex.soknadsopprettelse.ARBEID_UTENFOR_NORGE
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER
import no.nav.helse.flex.soknadsopprettelse.FRISKMELDT
import no.nav.helse.flex.soknadsopprettelse.FRISKMELDT_START
import no.nav.helse.flex.soknadsopprettelse.OpprettSoknadService
import no.nav.helse.flex.soknadsopprettelse.UTDANNING
import no.nav.helse.flex.soknadsopprettelse.VAER_KLAR_OVER_AT
import no.nav.helse.flex.soknadsopprettelse.tilSoknadsperioder
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.testutil.opprettSoknadFraSoknadMetadata
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.util.tilOsloInstant
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
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
import java.util.*

@TestMethodOrder(MethodOrderer.MethodName::class)
class ArbeidsledigIntegrationMedOppdaterSporsmalTest : BaseTestClass() {

    @Autowired
    private lateinit var opprettSoknadService: OpprettSoknadService

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var aktiveringProducer: AktiveringProducer

    @Autowired
    private lateinit var aktiverEnkeltSoknad: AktiverEnkeltSoknad

    final val fnr = "123456789"

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
        sykmeldingId = "sykmeldingId",
        arbeidsgiverNavn = null,
        soknadstype = Soknadstype.ARBEIDSLEDIG,
        arbeidsgiverOrgnummer = null
    )

    @Test
    fun `01 - vi oppretter en arbeidsledigsøknad`() {
        // Opprett søknad
        opprettSoknadService.opprettSoknadFraSoknadMetadata(soknadMetadata, sykepengesoknadDAO, aktiveringProducer, aktiverEnkeltSoknad)

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.ARBEIDSLEDIG)
    }

    @Test
    fun `02 - søknaden har alle spørsmål`() {
        val soknader = hentSoknaderMetadata(fnr)
        assertThat(soknader).hasSize(1)

        val soknaden = hentSoknad(
            soknadId = soknader.first().id,
            fnr = fnr
        )
        assertThat(soknaden.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                FRISKMELDT,
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER,
                UTDANNING,
                ARBEIDSLEDIG_UTLAND,
                VAER_KLAR_OVER_AT,
                BEKREFT_OPPLYSNINGER
            )
        )
    }

    @Test
    fun `03 - vi svarer på ansvarserklæringa som ikke muterer søknaden`() {
        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .also {
                assertThat(it.muterteSoknaden).isFalse()
            }
    }

    @Test
    fun `04 - vi svarer at vi ble friskmeldt midt i søknadsperioden - Det muterer søknaden`() {
        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

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
            .besvarSporsmal(FRISKMELDT_START, LocalDate.of(2018, 1, 5).format(DateTimeFormatter.ISO_LOCAL_DATE), mutert = true)
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
        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(FRISKMELDT_START, LocalDate.of(2018, 1, 1).format(DateTimeFormatter.ISO_LOCAL_DATE), mutert = true)
            .also {
                assertThat(it.rSSykepengesoknad.sporsmal!!.map { it.tag }).isEqualTo(
                    listOf(
                        ANSVARSERKLARING,
                        FRISKMELDT,
                        ARBEID_UTENFOR_NORGE,
                        VAER_KLAR_OVER_AT,
                        BEKREFT_OPPLYSNINGER
                    )
                )
            }
    }

    @Test
    fun `06 - unødvendige spørsmål kommer tilbake når man svarer at man ikke ble friskmeldt likevel`() {
        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(FRISKMELDT, "JA", mutert = true)
            .also {
                assertThat(it.rSSykepengesoknad.sporsmal!!.map { it.tag }).isEqualTo(
                    listOf(
                        ANSVARSERKLARING,
                        FRISKMELDT,
                        ARBEID_UTENFOR_NORGE,
                        ANDRE_INNTEKTSKILDER,
                        UTDANNING,
                        ARBEIDSLEDIG_UTLAND,
                        VAER_KLAR_OVER_AT,
                        BEKREFT_OPPLYSNINGER
                    )
                )
            }
    }

    @Test
    fun `07 - vi kan ikke sende inn søknaden før alle spørsmål er besvart`() {
        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )
        sendSoknadMedResult(fnr, soknaden.id).andExpect(((MockMvcResultMatchers.status().isBadRequest)))
    }

    @Test
    fun `08 - vi besvarer alle sporsmal`() {
        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(FRISKMELDT, "NEI", mutert = true)
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "JA")
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
    }

    @Test
    fun `09 - vi får en feil dersom spørsmål id ikke finnes i søknaden`() {
        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        val json =
            oppdaterSporsmalMedResult(fnr, soknaden.sporsmal!![0].copy(id = "FEILID"), soknadsId = soknaden.id)
                .andExpect(MockMvcResultMatchers.status().isBadRequest).andReturn().response.contentAsString
        assertThat(json).isEqualTo("""{"reason":"SPORSMAL_FINNES_IKKE_I_SOKNAD"}""")
    }

    @Test
    fun `10 - vi sender inn søknaden - Den får da status sendt og blir publisert på kafka`() {
        sendSoknad(fnr, hentSoknaderMetadata(fnr).first().id)

        val soknaden = hentSoknaderMetadata(fnr).first()
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
        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        assertThat(soknaden.status).isEqualTo(RSSoknadstatus.SENDT)

        val json = oppdaterSporsmalMedResult(fnr, soknaden.sporsmal!![0], soknadsId = soknaden.id)
            .andExpect(MockMvcResultMatchers.status().isBadRequest).andReturn().response.contentAsString
        assertThat(json).isEqualTo("""{"reason":"FEIL_STATUS_FOR_OPPDATER_SPORSMAL"}""")
    }
}
