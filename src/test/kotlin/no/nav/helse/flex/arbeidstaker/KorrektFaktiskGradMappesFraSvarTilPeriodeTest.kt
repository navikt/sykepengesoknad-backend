package no.nav.helse.flex.arbeidstaker

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.rest.SoknadMetadata
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.juridiskvurdering.Utfall
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.ANDRE_INNTEKTSKILDER
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING
import no.nav.helse.flex.soknadsopprettelse.ARBEID_UTENFOR_NORGE
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER
import no.nav.helse.flex.soknadsopprettelse.FERIE_V2
import no.nav.helse.flex.soknadsopprettelse.FRAVAR_FOR_SYKMELDINGEN
import no.nav.helse.flex.soknadsopprettelse.OpprettSoknadService
import no.nav.helse.flex.soknadsopprettelse.PERMISJON_V2
import no.nav.helse.flex.soknadsopprettelse.PERMITTERT_NAA
import no.nav.helse.flex.soknadsopprettelse.PERMITTERT_PERIODE
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_I_ARBEID
import no.nav.helse.flex.soknadsopprettelse.UTDANNING
import no.nav.helse.flex.soknadsopprettelse.UTLAND_V2
import no.nav.helse.flex.soknadsopprettelse.tilSoknadsperioder
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.testutil.jsonTilHashMap
import no.nav.helse.flex.testutil.opprettSoknadFraSoknadMetadata
import no.nav.helse.flex.tilJuridiskVurdering
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.util.tilOsloInstant
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import org.amshove.kluent.`should be equal to`
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime

@TestMethodOrder(MethodOrderer.MethodName::class)
class KorrektFaktiskGradMappesFraSvarTilPeriodeTest : BaseTestClass() {

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var opprettSoknadService: OpprettSoknadService

    final val fnr = "12345678910"

    private val soknadMetadata = SoknadMetadata(
        startSykeforlop = LocalDate.of(2018, 1, 1),
        sykmeldingSkrevet = LocalDateTime.of(2018, 1, 1, 12, 0).tilOsloInstant(),
        arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
        sykmeldingsperioder = mutableListOf(
            SykmeldingsperiodeAGDTO(
                fom = (LocalDate.of(2018, 1, 13)),
                tom = (LocalDate.of(2018, 1, 14)),
                gradert = GradertDTO(grad = 100, reisetilskudd = false),
                type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false,
            ),
            SykmeldingsperiodeAGDTO(
                fom = (LocalDate.of(2018, 1, 15)),
                tom = (LocalDate.of(2018, 1, 29)),
                gradert = GradertDTO(grad = 70, reisetilskudd = false),
                type = PeriodetypeDTO.GRADERT,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false,
            ),
        ).also { it.shuffle() }.tilSoknadsperioder(), // Viktig med shuffle da sorteringen ved opprettelse er fiksen,
        fnr = fnr,
        fom = LocalDate.of(2018, 1, 1),
        tom = LocalDate.of(2018, 1, 10),
        status = Soknadstatus.NY,
        soknadstype = Soknadstype.ARBEIDSTAKERE,
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
            .besvarSporsmal("JOBBET_DU_GRADERT_1", "JA", false)
            .besvarSporsmal("HVOR_MANGE_TIMER_PER_UKE_1", "50", false)
            .besvarSporsmal("HVOR_MYE_PROSENT_1", "CHECKED", false)
            .besvarSporsmal("HVOR_MYE_PROSENT_VERDI_1", "50")
            .besvarSporsmal("JOBBET_DU_100_PROSENT_0", "NEI")
            .besvarSporsmal("HVOR_MYE_PROSENT_0", "CHECKED")
            .besvarSporsmal(ANDRE_INNTEKTSKILDER, "NEI")
            .besvarSporsmal(UTDANNING, "NEI")
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
            .sendSoknad()
    }

    @Test
    fun `4 - vi sjekker at faktisk grad er hentet ut korrekt`() {
        val soknaden = hentSoknader(fnr).first()

        assertThat(soknaden.status).isEqualTo(RSSoknadstatus.SENDT)

        val soknadPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        assertThat(soknadPaKafka.soknadsperioder!!.map { Pair(it.faktiskGrad, it.sykmeldingsgrad) }).isEqualTo(
            listOf(
                Pair(null, 100),
                Pair(50, 70)
            )
        )
    }

    @Test
    fun `5 - vi sjekker at faktisk grad er riktig på jurdisk vurdering på kafka`() {
        val vurdering = juridiskVurderingKafkaConsumer
            .ventPåRecords(antall = 2)
            .tilJuridiskVurdering()
            .first { it.paragraf == "8-13" }
        vurdering.eventName `should be equal to` "subsumsjon"
        vurdering.utfall `should be equal to` Utfall.VILKAR_BEREGNET
        vurdering.input `should be equal to` """
{
  "fravar": [],
  "versjon": "2022-02-01",
  "arbeidUnderveis": [
    {
      "tag": "JOBBET_DU_100_PROSENT_0",
      "svar": [
        "NEI"
      ],
      "undersporsmal": []
    },
    {
      "tag": "JOBBET_DU_GRADERT_1",
      "svar": [
        "JA"
      ],
      "undersporsmal": [
        {
          "tag": "HVOR_MANGE_TIMER_PER_UKE_1",
          "svar": [
            "50"
          ],
          "undersporsmal": []
        },
        {
          "tag": "HVOR_MYE_HAR_DU_JOBBET_1",
          "svar": [],
          "undersporsmal": [
            {
              "tag": "HVOR_MYE_PROSENT_1",
              "svar": [
                "CHECKED"
              ],
              "undersporsmal": [
                {
                  "tag": "HVOR_MYE_PROSENT_VERDI_1",
                  "svar": [
                    "50"
                  ],
                  "undersporsmal": []
                }
              ]
            },
            {
              "tag": "HVOR_MYE_TIMER_1",
              "svar": [],
              "undersporsmal": []
            }
          ]
        }
      ]
    }
  ]
}            
        """.jsonTilHashMap()
        vurdering.output `should be equal to` """
{
  "perioder": [
    {
      "fom": "2018-01-13",
      "tom": "2018-01-14",
      "faktiskGrad": null
    },
    {
      "fom": "2018-01-15",
      "tom": "2018-01-29",
      "faktiskGrad": 50
    }
  ],
  "versjon": "2022-02-01"
}            
        """.jsonTilHashMap()
    }
}
