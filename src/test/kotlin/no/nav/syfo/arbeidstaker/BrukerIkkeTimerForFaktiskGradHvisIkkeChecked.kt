package no.nav.syfo.arbeidstaker

import no.nav.syfo.BaseTestClass
import no.nav.syfo.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.rest.SoknadMetadata
import no.nav.syfo.hentSoknader
import no.nav.syfo.mockFlexSyketilfelleArbeidsgiverperiode
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

@TestMethodOrder(MethodOrderer.MethodName::class)
class BrukerIkkeTimerForFaktiskGradHvisIkkeChecked : BaseTestClass() {

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var opprettSoknadService: OpprettSoknadService

    final val fnr = "123456789"
    final val aktorid = "${fnr}00"

    val start = LocalDate.of(2020, 9, 22)
    val slutt = LocalDate.of(2020, 10, 10)

    private val soknadMetadata = SoknadMetadata(
        startSykeforlop = start,
        sykmeldingSkrevet = start.atStartOfDay(),
        arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
        sykmeldingsperioder = listOf(
            SykmeldingsperiodeAGDTO(
                fom = start,
                tom = slutt,
                gradert = GradertDTO(grad = 50, reisetilskudd = false),
                type = PeriodetypeDTO.GRADERT,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false,
            ),
        ).tilSoknadsperioder(),
        fnr = fnr,
        fom = start,
        tom = slutt,
        soknadstype = Soknadstype.ARBEIDSTAKERE,
        status = Soknadstatus.NY,
        sykmeldingId = "sykmeldingId",
        arbeidsgiverNavn = "Kjells markiser",
        arbeidsgiverOrgnummer = "848274932"
    )

    @Test
    fun `1 - vi oppretter en arbeidstakersoknad`() {
        // Opprett søknad
        opprettSoknadService.opprettSoknadFraSoknadMetadata(soknadMetadata, sykepengesoknadDAO)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    fun `3 - vi svarer på sporsmalene og sender den inn`() {
        flexSyketilfelleMockRestServiceServer?.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()

        val soknaden = hentSoknader(fnr).first()

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(FRAVAR_FOR_SYKMELDINGEN, "NEI")
            .besvarSporsmal(TILBAKE_I_ARBEID, "NEI")
            .besvarSporsmal(PERMITTERT_NAA, "NEI")
            .besvarSporsmal(PERMITTERT_PERIODE, "NEI")
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "NEI")
            .besvarSporsmal(FERIE_V2, "NEI")
            .besvarSporsmal(PERMISJON_V2, "NEI")
            .besvarSporsmal(UTLAND_V2, "NEI")
            .besvarSporsmal("JOBBET_DU_GRADERT_0", "JA", false)
            .besvarSporsmal("HVOR_MANGE_TIMER_PER_UKE_0", "23", false)
            .besvarSporsmal("HVOR_MYE_PROSENT_0", "CHECKED", false)
            .besvarSporsmal("HVOR_MYE_PROSENT_VERDI_0", "51", false)
            .besvarSporsmal("HVOR_MYE_TIMER_VERDI_0", "12", true)
            .besvarSporsmal(ANDRE_INNTEKTSKILDER, "NEI")
            .besvarSporsmal(UTDANNING, "NEI")
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
            .sendSoknad()
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    fun `4 - vi sjekker at faktisk grad er hentet ut korrekt`() {

        val soknaden = hentSoknader(fnr).first()

        assertThat(soknaden.status).isEqualTo(RSSoknadstatus.SENDT)

        val soknadPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        assertThat(soknadPaKafka.soknadsperioder!![0].faktiskGrad).isEqualTo(51)
    }
}
