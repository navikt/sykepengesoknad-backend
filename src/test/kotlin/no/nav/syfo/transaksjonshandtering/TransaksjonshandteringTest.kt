package no.nav.syfo.transaksjonshandtering

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doCallRealMethod
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
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
import no.nav.syfo.sendSoknadMedResult
import no.nav.syfo.soknadsopprettelse.*
import no.nav.syfo.soknadsopprettelse.OpprettSoknadService
import no.nav.syfo.testutil.SoknadBesvarer
import no.nav.syfo.testutil.opprettSoknadFraSoknadMetadata
import no.nav.syfo.util.tilOsloInstant
import no.nav.syfo.ventPåRecords
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.LocalDate
import java.time.LocalDateTime

@TestMethodOrder(MethodOrderer.MethodName::class)
class TransaksjonshandteringTest : BaseTestClass() {

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
            ).tilSoknadsperioder(),
        ),
        fnr = fnr,
        fom = LocalDate.of(2018, 1, 1),
        tom = LocalDate.of(2020, 5, 10),
        status = Soknadstatus.NY,
        sykmeldingId = "sykmeldingId",
        arbeidsgiverNavn = null,
        soknadstype = Soknadstype.ARBEIDSTAKERE,
        arbeidsgiverOrgnummer = null
    )

    @Test
    fun `01 - vi oppretter en arbeidsledigsøknad`() {
        // Opprett søknad
        opprettSoknadService.opprettSoknadFraSoknadMetadata(soknadMetadata, sykepengesoknadDAO)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    fun `02 - vi besvarer alle sporsmal`() {
        val soknaden = hentSoknader(fnr).first()

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(FRISKMELDT, "JA")
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "JA")
            .besvarSporsmal(PERMITTERT_NAA, "NEI")
            .besvarSporsmal(PERMITTERT_PERIODE, "NEI")
            .besvarSporsmal(ANDRE_INNTEKTSKILDER, "NEI")
            .besvarSporsmal(UTDANNING, "NEI")
            .besvarSporsmal(ARBEIDSLEDIG_UTLAND, "NEI")
    }

    @Test
    fun `04 - vi sender inn søknaden, den feiler på kafka`() {
        doThrow(RuntimeException("sdfsdf")).whenever(aivenKafkaProducer).produserMelding(any())
        sendSoknadMedResult(fnr, hentSoknader(fnr).first().id)
            .andExpect(((MockMvcResultMatchers.status().isInternalServerError)))

        val soknaden = hentSoknader(fnr).first()
        assertThat(soknaden.status).isEqualTo(RSSoknadstatus.NY)
    }

    @Test
    fun `05 - reset aivekafkaproducer`() {
        doCallRealMethod().whenever(aivenKafkaProducer).produserMelding(any())
    }
}
