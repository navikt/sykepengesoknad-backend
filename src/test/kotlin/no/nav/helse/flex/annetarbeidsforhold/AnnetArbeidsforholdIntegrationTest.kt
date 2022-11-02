package no.nav.helse.flex.annetarbeidsforhold

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.aktivering.AktiverEnkeltSoknad
import no.nav.helse.flex.aktivering.kafka.AktiveringProducer
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.rest.SoknadMetadata
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.ANDRE_INNTEKTSKILDER
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING
import no.nav.helse.flex.soknadsopprettelse.ARBEIDSLEDIG_UTLAND
import no.nav.helse.flex.soknadsopprettelse.ARBEID_UTENFOR_NORGE
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER
import no.nav.helse.flex.soknadsopprettelse.FRISKMELDT
import no.nav.helse.flex.soknadsopprettelse.FRISKMELDT_START
import no.nav.helse.flex.soknadsopprettelse.OpprettSoknadService
import no.nav.helse.flex.soknadsopprettelse.PERMISJON_V2
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@TestMethodOrder(MethodOrderer.MethodName::class)
class AnnetArbeidsforholdIntegrationTest : BaseTestClass() {

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var opprettSoknadService: OpprettSoknadService

    @Autowired
    private lateinit var aktiveringProducer: AktiveringProducer

    @Autowired
    private lateinit var aktiverEnkeltSoknad: AktiverEnkeltSoknad

    final val fnr = "123456789"

    private val soknadMetadata = SoknadMetadata(
        startSykeforlop = LocalDate.of(2018, 1, 1),
        sykmeldingSkrevet = LocalDateTime.of(2018, 1, 1, 12, 0).tilOsloInstant(),
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
        sykmeldingId = "sykmeldingId",
        arbeidsgiverNavn = null,
        soknadstype = Soknadstype.ANNET_ARBEIDSFORHOLD,
        arbeidsgiverOrgnummer = null,
    )

    @Test
    fun `1 - vi oppretter en arbeidsledigsøknad`() {
        // Opprett søknad
        opprettSoknadService.opprettSoknadFraSoknadMetadata(soknadMetadata, sykepengesoknadDAO, aktiveringProducer, aktiverEnkeltSoknad)

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1, duration = Duration.ofSeconds(5)).tilSoknader()
        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.ANNET_ARBEIDSFORHOLD)
    }

    @Test
    fun `2 - søknaden har alle spørsmål`() {
        val soknader = hentSoknaderMetadata(fnr)
        assertThat(soknader).hasSize(1)

        val soknaden = hentSoknad(soknader.first().id, fnr)
        assertThat(soknaden.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                FRISKMELDT,
                PERMISJON_V2,
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
    fun `3 - spørsmålstekstene endrer seg når vi blir friskmeldt midt i søknadsperioden`() {
        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

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
            .besvarSporsmal(FRISKMELDT_START, LocalDate.of(2018, 1, 5).format(DateTimeFormatter.ISO_LOCAL_DATE), mutert = true)
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
    fun `5 - unødvendige spørsmål kommer tilbake når man svarer at man ikke ble friskmeldt likevel`() {
        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        assertThat(soknaden.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                FRISKMELDT,
                ARBEID_UTENFOR_NORGE,
                VAER_KLAR_OVER_AT,
                BEKREFT_OPPLYSNINGER
            )
        )

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(FRISKMELDT, "JA", mutert = true)
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
                        VAER_KLAR_OVER_AT,
                        BEKREFT_OPPLYSNINGER
                    )
                )
            }
    }

    @Test
    fun `6 - Vi svarer på alle spørsmål og sender inn søknaden`() {
        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        val sendtSoknad = SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(FRISKMELDT, "JA")
            .besvarSporsmal(PERMISJON_V2, "NEI")
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
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
