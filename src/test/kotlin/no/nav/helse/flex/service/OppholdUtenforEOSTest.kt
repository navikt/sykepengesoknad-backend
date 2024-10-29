package no.nav.helse.flex.service

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class OppholdUtenforEOSTest : FellesTestOppsett() {
    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    private val fnr = "12345678900"

    @BeforeEach
    fun beforeEach() {
        databaseReset.resetDatabase()
        fakeUnleash.resetAll()
    }

    private val fom = LocalDate.of(2024, 9, 1)
    private val tom = LocalDate.of(2024, 9, 30)

    @Test
    fun `lager ikke søknad hvis opphold utenfor EØS dekker hele perioden med helg, ferie og permisjon`() {
        val soknaden = settOppSykepengeSoknad(fom, tom)

        val ferieFom = LocalDate.of(2024, 9, 5)
        val ferieTom = LocalDate.of(2024, 9, 8)
        // 9 = lørdag
        // 10 = søndag
        val permisjonStart = LocalDate.of(2024, 9, 11)
        val permisjonVarer = LocalDate.of(2024, 9, 17)

        val sendtSykepengeSoknad =
            soknadBesvarer(
                soknaden,
                ferieFom = ferieFom,
                ferieTom = ferieTom,
                permisjonFom = permisjonStart,
                permisjonTom = permisjonVarer,
                utenforEOSFom = LocalDate.of(2024, 9, 6),
                utenforEOSTom = LocalDate.of(2024, 9, 16),
            ).sendSoknad()

        sendtSykepengeSoknad.status `should be equal to` sendtSykepengeSoknad.status
        verifiserKafkaSoknader()

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSykepengeSoknad.id)
        soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver

        // Sjekker at Opphold Utland søknad ikke har blitt laget
        val oppholdUtlandSoknader =
            sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        oppholdUtlandSoknader.size `should be equal to` 0
    }

    private fun soknadBesvarer(
        soknaden: RSSykepengesoknad,
        ferieFom: LocalDate?,
        ferieTom: LocalDate?,
        permisjonFom: LocalDate?,
        permisjonTom: LocalDate?,
        utenforEOSFom: LocalDate?,
        utenforEOSTom: LocalDate?,
    ): SoknadBesvarer {
        val soknadBesvarer =
            SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
                .apply {
                    besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
                    besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
                    besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_0", svar = "NEI")
                    besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
                    oppsummering()
                    besvarPeriodeSporsmal(
                        "OPPHOLD_UTENFOR_EOS",
                        "OPPHOLD_UTENFOR_EOS_NAR",
                        utenforEOSFom,
                        utenforEOSTom,
                    )
                    besvarPeriodeSporsmal("FERIE_V2", "FERIE_NAR_V2", ferieFom, ferieTom)
                    besvarPeriodeSporsmal("PERMISJON_V2", "PERMISJON_NAR_V2", permisjonFom, permisjonTom)
                }

        return soknadBesvarer
    }

    private fun SoknadBesvarer.besvarPeriodeSporsmal(
        hovedTag: String,
        narTag: String,
        fom: LocalDate?,
        tom: LocalDate?,
    ) {
        if (fom != null && tom != null) {
            besvarSporsmal(tag = hovedTag, svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(
                    tag = narTag,
                    svar = """{"fom":"${
                        fom.format(
                            DateTimeFormatter.ISO_LOCAL_DATE,
                        )
                    }","tom":"${tom.format(DateTimeFormatter.ISO_LOCAL_DATE)}"}""",
                )
        } else {
            besvarSporsmal(tag = hovedTag, svar = "NEI")
        }
    }

    private fun verifiserKafkaSoknader(forventetAntallSoknader: Int = 1) {
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = forventetAntallSoknader).tilSoknader()
            .let { kafkaSoknader ->
                kafkaSoknader.size `should be equal to` forventetAntallSoknader
                kafkaSoknader.first().status `should be equal to` SoknadsstatusDTO.SENDT
                kafkaSoknader.first().type `should be equal to` SoknadstypeDTO.ARBEIDSTAKERE
                kafkaSoknader.first().arbeidUtenforNorge `should be equal to` null

                // Sjekker at opphold_utland søknad er lagt på kafka
                if (forventetAntallSoknader == 2) {
                    kafkaSoknader[1].status `should be equal to` SoknadsstatusDTO.NY
                    kafkaSoknader[1].type `should be equal to` SoknadstypeDTO.OPPHOLD_UTLAND
                }
            }
        juridiskVurderingKafkaConsumer.hentProduserteRecords()
    }

    private fun settOppSykepengeSoknad(
        sykmeldingsperiodeStart: LocalDate,
        sykmeldingsperiodeSlutt: LocalDate,
    ): RSSykepengesoknad {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(sykmeldingsperiodeStart, sykmeldingsperiodeSlutt),
                timestamp = tom.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).minusWeeks(3),
            ),
        )

        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden =
            hentSoknad(
                soknadId =
                    hentSoknaderMetadata(
                        fnr,
                    ).first { it.soknadstype == RSSoknadstype.ARBEIDSTAKERE && it.status == RSSoknadstatus.NY }.id,
                fnr = fnr,
            )
        return soknaden
    }
}
