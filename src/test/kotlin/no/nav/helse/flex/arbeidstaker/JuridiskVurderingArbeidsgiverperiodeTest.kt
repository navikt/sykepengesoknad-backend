package no.nav.helse.flex.arbeidstaker

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.client.narmesteleder.Forskuttering
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidsgiverperiode
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.juridiskvurdering.Utfall
import no.nav.helse.flex.mockArbeidsgiverForskutterer
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.mockFlexSyketilfelleSykeforloep
import no.nav.helse.flex.testdata.getSykmeldingDto
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilJuridiskVurdering
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be null`
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class JuridiskVurderingArbeidsgiverperiodeTest : BaseTestClass() {

    private final val fnr = "12345678910"
    private final val orgnr = "123454543"
    private final val fredagen = LocalDate.of(2021, 12, 17)

    @BeforeEach
    fun setUp() {
        mockArbeidsgiverForskutterer(Forskuttering.JA, orgnr)
        databaseReset.resetDatabase()
    }

    @Test
    fun `søknaden er helt innafor arbeidsgiverperioden`() {
        `send sykmelding og besvar søknad`(
            sykmeldingFom = fredagen.minusDays(2),
            sykmeldingTom = fredagen,
            oppbruktAgPeriode = false,
            arbeidsgiverperiodeFom = fredagen.minusDays(2),
            arbeidsgiverperiodeTom = fredagen,
        )

        val vurdering = juridiskVurderingKafkaConsumer
            .ventPåRecords(antall = 2)
            .tilJuridiskVurdering()
            .first { it.paragraf == "8-17" }

        vurdering.utfall `should be equal to` Utfall.VILKAR_IKKE_OPPFYLT
        vurdering.ledd `should be equal to` 1
        vurdering.bokstav `should be equal to` "a"
        vurdering.punktum.`should be null`()
        vurdering.input `should be equal to` mapOf(
            "arbeidsgiverperiode" to mapOf(
                "fom" to "2021-12-15",
                "tom" to "2021-12-17",
            ),
            "sykepengesoknadTom" to "2021-12-17",
            "sykepengesoknadFom" to "2021-12-15",
            "oppbruktArbeidsgiverperiode" to false,
            "versjon" to "2022-02-01",
        )
        vurdering.output `should be equal to` mapOf(
            "periode" to mapOf(
                "fom" to "2021-12-15",
                "tom" to "2021-12-17",
            ),
            "versjon" to "2022-02-01",
        )
    }

    @Test
    fun `søknaden er helt utafor arbeidsgiverperioden`() {
        `send sykmelding og besvar søknad`(
            sykmeldingFom = fredagen.minusDays(2),
            sykmeldingTom = fredagen,
            oppbruktAgPeriode = true,
            arbeidsgiverperiodeFom = fredagen.minusDays(5),
            arbeidsgiverperiodeTom = fredagen.minusDays(4),
        )

        val vurdering = juridiskVurderingKafkaConsumer
            .ventPåRecords(antall = 2)
            .tilJuridiskVurdering()
            .first { it.paragraf == "8-17" }

        vurdering.ledd `should be equal to` 1
        vurdering.bokstav `should be equal to` "a"
        vurdering.punktum.`should be null`()
        vurdering.utfall `should be equal to` Utfall.VILKAR_OPPFYLT
        vurdering.input `should be equal to` mapOf(
            "arbeidsgiverperiode" to mapOf(
                "fom" to "2021-12-12",
                "tom" to "2021-12-13",
            ),
            "sykepengesoknadTom" to "2021-12-17",
            "sykepengesoknadFom" to "2021-12-15",
            "oppbruktArbeidsgiverperiode" to true,
            "versjon" to "2022-02-01",
        )
        vurdering.output `should be equal to` mapOf(
            "periode" to mapOf(
                "fom" to "2021-12-15",
                "tom" to "2021-12-17",
            ),
            "versjon" to "2022-02-01",
        )
    }

    @Test
    fun `søknaden er delvis utafor arbeidsgiverperioden`() {
        `send sykmelding og besvar søknad`(
            sykmeldingFom = fredagen.minusDays(2),
            sykmeldingTom = fredagen,
            oppbruktAgPeriode = true,
            arbeidsgiverperiodeFom = fredagen.minusDays(5),
            arbeidsgiverperiodeTom = fredagen.minusDays(1),
        )

        val vurderinger = juridiskVurderingKafkaConsumer
            .ventPåRecords(antall = 3)
            .tilJuridiskVurdering()
        val vurderingInnenfor = vurderinger
            .filter { it.utfall == Utfall.VILKAR_IKKE_OPPFYLT }
            .first { it.paragraf == "8-17" }

        vurderingInnenfor.ledd `should be equal to` 1
        vurderingInnenfor.bokstav `should be equal to` "a"
        vurderingInnenfor.punktum.`should be null`()

        vurderingInnenfor.kilde `should be equal to` "sykepengesoknad-backend"
        vurderingInnenfor.versjonAvKode `should be equal to` "sykepengesoknad-backend-test-12432536"

        vurderingInnenfor.utfall `should be equal to` Utfall.VILKAR_IKKE_OPPFYLT
        vurderingInnenfor.input `should be equal to` mapOf(
            "arbeidsgiverperiode" to mapOf(
                "fom" to "2021-12-12",
                "tom" to "2021-12-16",
            ),
            "sykepengesoknadTom" to "2021-12-17",
            "sykepengesoknadFom" to "2021-12-15",
            "oppbruktArbeidsgiverperiode" to true,
            "versjon" to "2022-02-01",
        )
        vurderingInnenfor.output `should be equal to` mapOf(
            "periode" to mapOf(
                "fom" to "2021-12-15",
                "tom" to "2021-12-16",
            ),
            "versjon" to "2022-02-01",
        )

        val vurderingUtafor = vurderinger
            .filter { it.utfall == Utfall.VILKAR_OPPFYLT }
            .first { it.paragraf == "8-17" }

        vurderingUtafor `should be equal to` vurderingInnenfor.copy(
            utfall = vurderingUtafor.utfall,
            output = vurderingUtafor.output,
            tidsstempel = vurderingUtafor.tidsstempel,
            id = vurderingUtafor.id,
        )

        vurderingUtafor.output `should be equal to` mapOf(
            "periode" to mapOf(
                "fom" to "2021-12-17",
                "tom" to "2021-12-17",
            ),
            "versjon" to "2022-02-01",
        )
    }

    fun `send sykmelding og besvar søknad`(
        sykmeldingFom: LocalDate,
        sykmeldingTom: LocalDate,
        oppbruktAgPeriode: Boolean,
        arbeidsgiverperiodeFom: LocalDate,
        arbeidsgiverperiodeTom: LocalDate
    ) {
        flexSyketilfelleMockRestServiceServer?.reset()
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = orgnr, orgNavn = "Jobben")
        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingId,
            fom = sykmeldingFom,
            tom = sykmeldingTom
        )

        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        flexSyketilfelleMockRestServiceServer?.reset()
        mockFlexSyketilfelleArbeidsgiverperiode(
            arbeidsgiverperiode = Arbeidsgiverperiode(
                antallBrukteDager = 16,
                oppbruktArbeidsgiverperiode = oppbruktAgPeriode,
                arbeidsgiverPeriode = Periode(fom = arbeidsgiverperiodeFom, tom = arbeidsgiverperiodeTom)
            )
        )
        val soknaden = hentSoknader(fnr).find { it.status == RSSoknadstatus.NY }!!

        val sendtSoknad = SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "FRAVAR_FOR_SYKMELDINGEN", svar = "NEI")
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

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
    }
}
