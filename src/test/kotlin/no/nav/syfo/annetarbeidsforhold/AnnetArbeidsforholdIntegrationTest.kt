package no.nav.syfo.annetarbeidsforhold

import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.syfo.BaseTestClass
import no.nav.syfo.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.rest.SoknadMetadata
import no.nav.syfo.hentSoknader
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.soknadsopprettelse.*
import no.nav.syfo.soknadsopprettelse.OpprettSoknadService
import no.nav.syfo.testutil.SoknadBesvarer
import no.nav.syfo.testutil.opprettSoknadFraSoknadMetadata
import no.nav.syfo.tilSoknader
import no.nav.syfo.ventPåRecords
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@TestMethodOrder(MethodOrderer.MethodName::class)
class AnnetArbeidsforholdIntegrationTest : BaseTestClass() {

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var opprettSoknadService: OpprettSoknadService

    final val fnr = "123456789"
    final val aktorid = "${fnr}00"

    private val soknadMetadata = SoknadMetadata(
        startSykeforlop = LocalDate.of(2018, 1, 1),
        sykmeldingSkrevet = LocalDateTime.of(2018, 1, 1, 12, 0),
        arbeidssituasjon = Arbeidssituasjon.ANNET,
        sykmeldingsperioder = listOf(
            SykmeldingsperiodeAGDTO(
                fom = (LocalDate.of(2018, 1, 1)),
                tom = (LocalDate.of(2018, 1, 10)),
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
        tom = LocalDate.of(2018, 1, 10),
        status = Soknadstatus.NY,
        sykmeldingId = "sykmeldingId",
        arbeidsgiverNavn = null,
        soknadstype = Soknadstype.ANNET_ARBEIDSFORHOLD,
        arbeidsgiverOrgnummer = null,
    )

    @Test
    fun `1 - vi oppretter en arbeidsledigsøknad`() {
        // Opprett søknad
        opprettSoknadService.opprettSoknadFraSoknadMetadata(soknadMetadata, sykepengesoknadDAO)
        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.ANNET_ARBEIDSFORHOLD)
    }

    @Test
    fun `2 - søknaden har alle spørsmål`() {
        val soknader = hentSoknader(fnr)
        assertThat(soknader).hasSize(1)
        val soknaden = soknader.first()

        assertThat(soknaden.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                FRISKMELDT,
                PERMISJON_V2,
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
    fun `3 - spørsmålstekstene endrer seg når vi blir friskmeldt midt i søknadsperioden`() {
        val soknaden = hentSoknader(fnr).first()

        assertThat(soknaden.sporsmal!!.first { it.tag == ANDRE_INNTEKTSKILDER }.sporsmalstekst)
            .isEqualTo(
                "Har du hatt inntekt mens du har vært sykmeldt i perioden 1. - 10. januar 2018?"
            )
        assertThat(soknaden.sporsmal!!.first { it.tag == UTDANNING }.sporsmalstekst)
            .isEqualTo(
                "Har du vært under utdanning i løpet av perioden 1. - 10. januar 2018?"
            )
        assertThat(soknaden.sporsmal!!.first { it.tag == ARBEIDSLEDIG_UTLAND }.sporsmalstekst)
            .isEqualTo(
                "Var du på reise utenfor EØS mens du var sykmeldt 1. - 10. januar 2018?"
            )
        assertThat(soknaden.sporsmal!!.first { it.tag == PERMISJON_V2 }.sporsmalstekst)
            .isEqualTo(
                "Tok du permisjon mens du var sykmeldt 1. - 10. januar 2018?"
            )
        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(FRISKMELDT, "NEI", false)
            .besvarSporsmal(FRISKMELDT_START, LocalDate.of(2018, 1, 5).format(DateTimeFormatter.ISO_LOCAL_DATE))
            .also {
                val oppdatertSoknad = it.rSSykepengesoknad

                assertThat(oppdatertSoknad.sporsmal!!.first { it.tag == ANDRE_INNTEKTSKILDER }.sporsmalstekst)
                    .isEqualTo(
                        "Har du hatt inntekt mens du har vært sykmeldt i perioden 1. - 4. januar 2018?"
                    )
                assertThat(oppdatertSoknad.sporsmal!!.first { it.tag == UTDANNING }.sporsmalstekst)
                    .isEqualTo(
                        "Har du vært under utdanning i løpet av perioden 1. - 4. januar 2018?"
                    )
                assertThat(oppdatertSoknad.sporsmal!!.first { it.tag == ARBEIDSLEDIG_UTLAND }.sporsmalstekst)
                    .isEqualTo(
                        "Var du på reise utenfor EØS mens du var sykmeldt 1. - 4. januar 2018?"
                    )
                assertThat(oppdatertSoknad.sporsmal!!.first { it.tag == PERMISJON_V2 }.sporsmalstekst)
                    .isEqualTo(
                        "Tok du permisjon mens du var sykmeldt 1. - 4. januar 2018?"
                    )
            }
    }

    @Test
    fun `4 - unødvendige spørsmål forsvinner når man blir friskmeldt første dag i søknadsperioden`() {
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
    fun `5 - unødvendige spørsmål kommer tilbake når man svarer at man ikke ble friskmeldt likevel`() {
        val soknaden = hentSoknader(fnr).first()

        assertThat(soknaden.sporsmal!!.map { it.tag }).isEqualTo(
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

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(FRISKMELDT, "JA")
            .also {
                assertThat(it.rSSykepengesoknad.sporsmal!!.map { it.tag }).isEqualTo(
                    listOf(
                        ANSVARSERKLARING,
                        FRISKMELDT,
                        PERMISJON_V2,
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
    fun `6 - Vi svarer på alle spørsmål og sender inn søknaden`() {
        val soknaden = hentSoknader(fnr).first()

        val sendtSoknad = SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(FRISKMELDT, "JA")
            .besvarSporsmal(PERMISJON_V2, "NEI")
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(PERMITTERT_NAA, "NEI")
            .besvarSporsmal(PERMITTERT_PERIODE, "NEI")
            .besvarSporsmal(ANDRE_INNTEKTSKILDER, "NEI")
            .besvarSporsmal(UTDANNING, "NEI")
            .besvarSporsmal(ARBEIDSLEDIG_UTLAND, "NEI")
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "NEI")
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
            .sendSoknad()

        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.ANNET_ARBEIDSFORHOLD)
        assertThat(soknader.last().status).isEqualTo(SoknadsstatusDTO.SENDT)
    }
}
