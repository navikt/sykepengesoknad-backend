package no.nav.helse.flex.arbeidstaker

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.domain.Arbeidsgiverperiode
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.juridiskvurdering.Utfall
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be null`
import org.junit.jupiter.api.*
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class JuridiskVurderingArbeidsgiverperiodeTest : FellesTestOppsett() {
    private val fnr = "12345678900"
    private val fredagen = LocalDate.of(2021, 12, 17)

    @BeforeEach
    fun setUp() {
        databaseReset.resetDatabase()
        fakeUnleash.resetAll()
    }

    @Test
    fun `søknaden er helt innafor arbeidsgiverperioden`() {
        val sykmeldingFom = fredagen.minusDays(2)
        val sykmeldingTom = fredagen

        `send sykmelding og besvar søknad`(
            sykmeldingFom = sykmeldingFom,
            sykmeldingTom = sykmeldingTom,
            oppbruktAgPeriode = false,
            arbeidsgiverperiodeFom = sykmeldingFom,
            arbeidsgiverperiodeTom = sykmeldingTom,
        )

        val alleJuridiskeVurderinger = hentJuridiskeVurderinger(3)
        val vurderinger817 = alleJuridiskeVurderinger.filter { it.paragraf == "8-17" }

        vurderinger817.first { it.bokstav == "a" }.utfall `should be equal to` Utfall.VILKAR_IKKE_OPPFYLT
        vurderinger817.first { it.bokstav == "b" }.utfall `should be equal to` Utfall.VILKAR_OPPFYLT

        vurderinger817.let { vurderinger817 ->
            vurderinger817.size `should be equal to` 2

            val forventetInput =
                mapOf(
                    "arbeidsgiverperiode" to
                        mapOf(
                            "fom" to "2021-12-15",
                            "tom" to "2021-12-17",
                        ),
                    "sykepengesoknadTom" to "2021-12-17",
                    "sykepengesoknadFom" to "2021-12-15",
                    "oppbruktArbeidsgiverperiode" to false,
                    "versjon" to "2022-02-01",
                )

            val forventetOutput =
                mapOf(
                    "periode" to
                        mapOf(
                            "fom" to "2021-12-15",
                            "tom" to "2021-12-17",
                        ),
                    "versjon" to "2022-02-01",
                )

            vurderinger817.forEach { vurdering ->
                vurdering.ledd `should be equal to` 1
                vurdering.punktum.`should be null`()
                vurdering.input `should be equal to` forventetInput
                vurdering.output `should be equal to` forventetOutput
            }
        }
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

        val alleJuridiskeVurderinger = hentJuridiskeVurderinger(3)
        val vurderinger817 = alleJuridiskeVurderinger.filter { it.paragraf == "8-17" }

        vurderinger817
            .filter { it.paragraf == "8-17" }
            .let { vurderinger817 ->
                vurderinger817.size `should be equal to` 2

                vurderinger817.first { it.bokstav == "a" }.utfall `should be equal to` Utfall.VILKAR_OPPFYLT
                vurderinger817.first { it.bokstav == "b" }.utfall `should be equal to` Utfall.VILKAR_IKKE_OPPFYLT

                vurderinger817.forEach { vurdering ->
                    vurdering.ledd `should be equal to` 1
                    vurdering.punktum.`should be null`()
                    vurdering.input `should be equal to`
                        mapOf(
                            "arbeidsgiverperiode" to
                                mapOf(
                                    "fom" to "2021-12-12",
                                    "tom" to "2021-12-13",
                                ),
                            "sykepengesoknadTom" to "2021-12-17",
                            "sykepengesoknadFom" to "2021-12-15",
                            "oppbruktArbeidsgiverperiode" to true,
                            "versjon" to "2022-02-01",
                        )
                    vurdering.output `should be equal to`
                        mapOf(
                            "periode" to
                                mapOf(
                                    "fom" to "2021-12-15",
                                    "tom" to "2021-12-17",
                                ),
                            "versjon" to "2022-02-01",
                        )
                }
            }
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

        val alleJuridiskeVurderinger = hentJuridiskeVurderinger(5)
        val vurderinger817 = alleJuridiskeVurderinger.filter { it.paragraf == "8-17" }
        val vurderinger817Innafor = vurderinger817.take(2)
        val vurderinger817Utafor = vurderinger817.drop(2)

        vurderinger817Innafor.let { vurderinger ->
            vurderinger.size `should be equal to` 2

            vurderinger.first { it.bokstav == "a" }.utfall `should be equal to` Utfall.VILKAR_IKKE_OPPFYLT
            vurderinger.first { it.bokstav == "b" }.utfall `should be equal to` Utfall.VILKAR_OPPFYLT

            vurderinger.forEach { vurdering ->
                vurdering.ledd `should be equal to` 1
                vurdering.punktum.`should be null`()
                vurdering.kilde `should be equal to` "sykepengesoknad-backend"
                vurdering.versjonAvKode `should be equal to` "sykepengesoknad-backend-test-12432536"

                vurdering.input `should be equal to`
                    mapOf(
                        "arbeidsgiverperiode" to
                            mapOf(
                                "fom" to "2021-12-12",
                                "tom" to "2021-12-16",
                            ),
                        "sykepengesoknadTom" to "2021-12-17",
                        "sykepengesoknadFom" to "2021-12-15",
                        "oppbruktArbeidsgiverperiode" to true,
                        "versjon" to "2022-02-01",
                    )

                vurdering.output `should be equal to`
                    mapOf(
                        "periode" to
                            mapOf(
                                "fom" to "2021-12-15",
                                "tom" to "2021-12-16",
                            ),
                        "versjon" to "2022-02-01",
                    )
            }
        }

        vurderinger817Utafor.let { vurderinger ->
            vurderinger.size `should be equal to` 2

            vurderinger.first { it.bokstav == "a" }.utfall `should be equal to` Utfall.VILKAR_OPPFYLT
            vurderinger.first { it.bokstav == "b" }.utfall `should be equal to` Utfall.VILKAR_IKKE_OPPFYLT

            vurderinger.forEach { vurdering ->
                vurdering.ledd `should be equal to` 1
                vurdering.punktum.`should be null`()
                vurdering.kilde `should be equal to` "sykepengesoknad-backend"
                vurdering.versjonAvKode `should be equal to` "sykepengesoknad-backend-test-12432536"

                vurdering.input `should be equal to`
                    mapOf(
                        "arbeidsgiverperiode" to
                            mapOf(
                                "fom" to "2021-12-12",
                                "tom" to "2021-12-16",
                            ),
                        "sykepengesoknadTom" to "2021-12-17",
                        "sykepengesoknadFom" to "2021-12-15",
                        "oppbruktArbeidsgiverperiode" to true,
                        "versjon" to "2022-02-01",
                    )

                vurdering.output `should be equal to`
                    mapOf(
                        "periode" to
                            mapOf(
                                "fom" to "2021-12-17",
                                "tom" to "2021-12-17",
                            ),
                        "versjon" to "2022-02-01",
                    )
            }
        }
    }

    private fun `send sykmelding og besvar søknad`(
        sykmeldingFom: LocalDate,
        sykmeldingTom: LocalDate,
        oppbruktAgPeriode: Boolean,
        arbeidsgiverperiodeFom: LocalDate,
        arbeidsgiverperiodeTom: LocalDate,
    ) {
        flexSyketilfelleMockRestServiceServer.reset()

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = sykmeldingFom,
                        tom = sykmeldingTom,
                    ),
            ),
        )

        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode(
            arbeidsgiverperiode =
                Arbeidsgiverperiode(
                    antallBrukteDager = 16,
                    oppbruktArbeidsgiverperiode = oppbruktAgPeriode,
                    arbeidsgiverPeriode = Periode(fom = arbeidsgiverperiodeFom, tom = arbeidsgiverperiodeTom),
                ),
        )

        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = fnr,
            )

        val sendtSoknad = besvarOgSendSoknad(soknaden)
        sendtSoknad.status `should be equal to` RSSoknadstatus.SENDT

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    private fun besvarOgSendSoknad(soknaden: RSSykepengesoknad) =
        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
            .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
            .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
            .besvarSporsmal(tag = "OPPHOLD_UTENFOR_EOS", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_0", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
            .oppsummering()
            .sendSoknad()
}
