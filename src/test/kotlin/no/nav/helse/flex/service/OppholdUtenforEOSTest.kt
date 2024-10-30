package no.nav.helse.flex.service

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.*
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
                    besvarPeriodeSporsmal("OPPHOLD_UTENFOR_EOS", "OPPHOLD_UTENFOR_EOS_NAR", utenforEOSFom, utenforEOSTom)
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
                    svar = """{"fom":"${fom.format(
                        DateTimeFormatter.ISO_LOCAL_DATE,
                    )}","tom":"${tom.format(DateTimeFormatter.ISO_LOCAL_DATE)}"}""",
                )
        } else {
            besvarSporsmal(tag = hovedTag, svar = "NEI")
        }
    }

    private fun verifiserKafkaSoknader(forventetAntallSoknader: Int = 1) {
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = forventetAntallSoknader).tilSoknader().let { kafkaSoknader ->
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
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
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
                timestamp = LocalDate.of(2024, 9, 27).atStartOfDay().atOffset(ZoneOffset.UTC).minusWeeks(3),
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

    @Test
    fun `søknad om opphold utenfor EØS opprettes ikke dersom det finnes eksisterende søknad`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        opprettUtlandssoknad(fnr)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().let { kafkaSoknader ->
            kafkaSoknader.size `should be equal to` 1
            kafkaSoknader.first().type `should be equal to` SoknadstypeDTO.OPPHOLD_UTLAND
            kafkaSoknader.first().status `should be equal to` SoknadsstatusDTO.NY
        }

        mockFlexSyketilfelleArbeidsgiverperiode()

        val soknadOppholdUtenforEOS =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )

        SoknadBesvarer(soknadOppholdUtenforEOS, this, fnr)
            .besvarSporsmal(
                tag = PERIODEUTLAND,
                svar = "{\"fom\":\"${LocalDate.of(2024, 9, 27).minusWeeks(4).format(
                    DateTimeFormatter.ISO_LOCAL_DATE,
                )}\",\"tom\":\"${LocalDate.of(2024, 9, 27).minusWeeks(2).format(
                    DateTimeFormatter.ISO_LOCAL_DATE,
                )}\"}",
            )
            .besvarSporsmal(LAND, svarListe = listOf("Kina"))
            .besvarSporsmal(ARBEIDSGIVER, svar = "NEI")
            .besvarSporsmal(AVKLART_MED_ARBEIDSGIVER_ELLER_NAV, svar = "NEI", ferdigBesvart = false)
            .besvarSporsmal(AVKLART_MED_SYKMELDER, svar = "NEI")
            .oppsummering()
            .sendSoknad()

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().let { kafkaSoknader ->
            kafkaSoknader.size `should be equal to` 1
            kafkaSoknader.first().type `should be equal to` SoknadstypeDTO.OPPHOLD_UTLAND
            kafkaSoknader.first().status `should be equal to` SoknadsstatusDTO.SENDT
        }

        val soknaden =
            settOppSykepengeSoknad(
                LocalDate.of(2024, 9, 6),
                LocalDate.of(2024, 9, 25),
            )

        val sendtSykepengeSoknad =
            soknadBesvarer(
                soknaden,
                utenforEOSFom = LocalDate.of(2024, 9, 6),
                utenforEOSTom = LocalDate.of(2024, 9, 25),
                ferieFom = null,
                ferieTom = null,
                permisjonFom = null,
                permisjonTom = null,
            ).sendSoknad()

        sendtSykepengeSoknad.status `should be equal to` sendtSykepengeSoknad.status
        verifiserKafkaSoknader()

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSykepengeSoknad.id)
        soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver

        // Sjekker at Opphold Utland søknad ikke har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        oppholdUtlandSoknader.size `should be equal to` 1
        oppholdUtlandSoknader.first().id `should be equal to` soknadOppholdUtenforEOS.id
    }

    @Test
    fun `lager ikke søknad hvis opphold utenfor EØS kun er i helg`() {
        val soknaden =
            settOppSykepengeSoknad(
                LocalDate.of(2024, 9, 6),
                LocalDate.of(2024, 9, 25),
            )

        val sendtSykepengeSoknad =
            soknadBesvarer(
                soknaden,
                utenforEOSFom = LocalDate.of(2024, 9, 14),
                utenforEOSTom = LocalDate.of(2024, 9, 15),
                ferieFom = null,
                ferieTom = null,
                permisjonFom = null,
                permisjonTom = null,
            ).sendSoknad()

        // September 2024
        // Mandag   Tirsdag   Onsdag    Torsdag   Fredag   Lørdag   Søndag
        // 2        3         4         5         6        7        8
        // ---      ---       ---       ---       ---      Helg     Helg
        // 9        10        11        12        13       14       15
        // ---      ---       ---       ---       ---      Helg(OU) Helg(OU)

        sendtSykepengeSoknad.status `should be equal to` sendtSykepengeSoknad.status
        verifiserKafkaSoknader()

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSykepengeSoknad.id)
        soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver

        // Sjekker at Opphold Utland søknad ikke har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        oppholdUtlandSoknader.size `should be equal to` 0
    }

    @Test
    fun `lager ikke søknad hvis opphold utenfor EØS er kun i ferie`() {
        val soknaden =
            settOppSykepengeSoknad(
                LocalDate.of(2024, 9, 6),
                LocalDate.of(2024, 9, 25),
            )

        val sendtSykepengeSoknad =
            soknadBesvarer(
                soknaden,
                utenforEOSFom = LocalDate.of(2024, 9, 9),
                utenforEOSTom = LocalDate.of(2024, 9, 13),
                ferieFom = LocalDate.of(2024, 9, 9),
                ferieTom = LocalDate.of(2024, 9, 13),
                permisjonFom = null,
                permisjonTom = null,
            )
                .sendSoknad()

        // September 2024
        // Mandag   Tirsdag   Onsdag    Torsdag   Fredag   Lørdag   Søndag
        // 2        3         4         5         6        7        8
        // ---      ---       ---       ---       ---      Helg     Helg
        // 9        10        11        12        13       14       15
        // Ferie    Ferie     Ferie     Ferie     Ferie    Helg     Helg

        sendtSykepengeSoknad.status `should be equal to` sendtSykepengeSoknad.status
        verifiserKafkaSoknader()

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSykepengeSoknad.id)
        soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver

        // Sjekker at Opphold Utland søknad ikke har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        oppholdUtlandSoknader.size `should be equal to` 0
    }

    @Test
    fun `lager ikke søknad hvis opphold utenfor EØS dekker hele perioden med permisjon`() {
        val soknaden =
            settOppSykepengeSoknad(
                LocalDate.of(2024, 9, 6),
                LocalDate.of(2024, 9, 25),
            )

        val sendtSykepengeSoknad =
            soknadBesvarer(
                soknaden,
                utenforEOSFom = LocalDate.of(2024, 9, 6),
                utenforEOSTom = LocalDate.of(2024, 9, 25),
                ferieFom = null,
                ferieTom = null,
                permisjonFom = LocalDate.of(2024, 9, 6),
                permisjonTom = LocalDate.of(2024, 9, 25),
            )
                .sendSoknad()

        // September 2024
        // Mandag   Tirsdag   Onsdag    Torsdag   Fredag   Lørdag   Søndag
        // 2        3         4         5         6        7        8
        // ---      ---       ---       ---       Perm     Helg     Helg
        // 9        10        11        12        13       14       15
        // Perm     Perm      Perm      Perm      Perm     Helg     Helg
        // 16       17        18        19        20       21       22
        // Perm     Perm      Perm      Perm      Perm     Helg     Helg
        // 23       24        25        26        27       28       29
        // Perm     Perm      Perm      ---       ---      ---      ---

        sendtSykepengeSoknad.status `should be equal to` sendtSykepengeSoknad.status
        verifiserKafkaSoknader()

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSykepengeSoknad.id)
        soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver

        // Sjekker at Opphold Utland søknad ikke har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        oppholdUtlandSoknader.size `should be equal to` 0
    }

    @Test
    fun `lager ikke søknad hvis opphold utenfor EØS dekker hele perioden med helg og ferie`() {
        val soknaden =
            settOppSykepengeSoknad(
                LocalDate.of(2024, 9, 6),
                LocalDate.of(2024, 9, 25),
            )

        val sendtSykepengeSoknad =
            soknadBesvarer(
                soknaden,
                utenforEOSFom = LocalDate.of(2024, 9, 6),
                utenforEOSTom = LocalDate.of(2024, 9, 25),
                ferieFom = LocalDate.of(2024, 9, 6),
                ferieTom = LocalDate.of(2024, 9, 25),
                permisjonFom = null,
                permisjonTom = null,
            )
                .sendSoknad()

        // September 2024
        // Mandag   Tirsdag   Onsdag    Torsdag   Fredag   Lørdag   Søndag
        // 2        3         4         5         6        7        8
        // ---      ---       ---       ---       Ferie    Helg     Helg
        // 9        10        11        12        13       14       15
        // Ferie    Ferie     Ferie     Ferie     Ferie    Helg     Helg
        // 16       17        18        19        20       21       22
        // Ferie    Ferie     Ferie     Ferie     Ferie    Helg     Helg
        // 23       24        25        26        27       28       29
        // Ferie    Ferie    Ferie      ---       ---      ---      ---

        sendtSykepengeSoknad.status `should be equal to` sendtSykepengeSoknad.status
        verifiserKafkaSoknader()

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSykepengeSoknad.id)
        soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver

        // Sjekker at Opphold Utland søknad ikke har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        oppholdUtlandSoknader.size `should be equal to` 0
    }

    @Test
    fun `lager søknad hvis opphold utenfor EØS ikke dekker hele perioden med helg og ferie`() {
        val soknaden =
            settOppSykepengeSoknad(
                LocalDate.of(2024, 9, 6),
                LocalDate.of(2024, 9, 25),
            )

        val sendtSykepengeSoknad =
            soknadBesvarer(
                soknaden,
                utenforEOSFom = LocalDate.of(2024, 9, 6),
                utenforEOSTom = LocalDate.of(2024, 9, 25),
                ferieFom = LocalDate.of(2024, 9, 9),
                ferieTom = LocalDate.of(2024, 9, 25),
                permisjonFom = null,
                permisjonTom = null,
            ).sendSoknad()

        // September 2024
        // Mandag   Tirsdag   Onsdag    Torsdag   Fredag   Lørdag   Søndag
        // 2        3         4         5         6        7        8
        // ---      ---       ---       ---       ---      Helg     Helg
        // 9        10        11        12        13       14       15
        // Ferie    Ferie     Ferie     Ferie     Ferie    Helg     Helg
        // 16       17        18        19        20       21       22
        // Ferie    Ferie     Ferie     Ferie     Ferie    Helg     Helg
        // 23       24        25        26        27       28       29
        // Ferie    Ferie    Ferie      ---       ---      ---      ---

        sendtSykepengeSoknad.status `should be equal to` sendtSykepengeSoknad.status
        verifiserKafkaSoknader(2)

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSykepengeSoknad.id)
        soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver

        // Sjekker at Opphold Utland søknad har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        oppholdUtlandSoknader.size `should be equal to` 1
        oppholdUtlandSoknader.first().fnr `should be equal to` fnr
    }

    @Test
    fun `oppretter utland søknad hvis utlandsopphold er utenfor helg eller ferie`() {
        val soknaden =
            settOppSykepengeSoknad(
                LocalDate.of(2024, 9, 6),
                LocalDate.of(2024, 9, 25),
            )

        val sendtSykepengeSoknad =
            soknadBesvarer(
                soknaden,
                ferieFom = LocalDate.of(2024, 9, 6),
                ferieTom = LocalDate.of(2024, 9, 11),
                utenforEOSFom = LocalDate.of(2024, 9, 6),
                utenforEOSTom = LocalDate.of(2024, 9, 25),
                permisjonFom = null,
                permisjonTom = null,
            )
                .sendSoknad()

        // September 2024
        // Mandag   Tirsdag   Onsdag    Torsdag   Fredag   Lørdag   Søndag
        // 2        3         4         5         6        7        8
        // ---      ---       ---       ---       Ferie    Helg     Helg
        // 9        10        11        12        13       14       15
        // Ferie    Ferie     Ferie     ---       ---      Helg     Helg
        // 16       17        18        19        20       21       22
        // ---      ---       ---       ---       ---      Helg     Helg
        // 23       24        25
        // ---      ---       ---

        sendtSykepengeSoknad.status `should be equal to` sendtSykepengeSoknad.status
        verifiserKafkaSoknader(2)

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSykepengeSoknad.id)
        soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver

        // Sjekker at Opphold Utland søknad har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        oppholdUtlandSoknader.size `should be equal to` 1
        oppholdUtlandSoknader.first().fnr `should be equal to` fnr
    }

    @Test
    fun `lager ikke søknad hvis opphold utenfor EØS dekker hele perioden med helg, ferie og permisjon`() {
        val soknaden =
            settOppSykepengeSoknad(
                LocalDate.of(2024, 9, 6),
                LocalDate.of(2024, 9, 25),
            )

        val sendtSykepengeSoknad =
            soknadBesvarer(
                soknaden,
                utenforEOSFom = LocalDate.of(2024, 9, 6),
                utenforEOSTom = LocalDate.of(2024, 9, 25),
                ferieFom = LocalDate.of(2024, 9, 6),
                ferieTom = LocalDate.of(2024, 9, 6),
                permisjonFom = LocalDate.of(2024, 9, 9),
                permisjonTom = LocalDate.of(2024, 9, 25),
            )
                .sendSoknad()

        // September 2024
        // Mandag   Tirsdag   Onsdag    Torsdag   Fredag   Lørdag   Søndag
        // 2        3         4         5         6        7        8
        // ---      ---       ---       ---       Ferie    Helg     Helg
        // 9        10        11        12        13       14       15
        // Perm     Perm      Perm      Perm      Perm     Helg     Helg
        // 16       17        18        19        20       21       22
        // Perm     Perm      Perm      Perm      Perm     Helg     Helg
        // 23       24        25        26        27       28       29
        // Perm     Perm      Perm      ---       ---      ---      ---

        sendtSykepengeSoknad.status `should be equal to` sendtSykepengeSoknad.status
        verifiserKafkaSoknader()

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSykepengeSoknad.id)
        soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver

        // Sjekker at Opphold Utland søknad ikke har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        oppholdUtlandSoknader.size `should be equal to` 0
    }

    @Test
    fun `oppretter søknad hvis opphold utenfor EØS har noen dager utenfor perioder med ferie og permisjon`() {
        val soknaden =
            settOppSykepengeSoknad(
                LocalDate.of(2024, 9, 6),
                LocalDate.of(2024, 9, 25),
            )

        val sendtSykepengeSoknad =
            soknadBesvarer(
                soknaden,
                utenforEOSFom = LocalDate.of(2024, 9, 6),
                utenforEOSTom = LocalDate.of(2024, 9, 18),
                ferieFom = LocalDate.of(2024, 9, 10),
                ferieTom = LocalDate.of(2024, 9, 12),
                permisjonFom = LocalDate.of(2024, 9, 17),
                permisjonTom = LocalDate.of(2024, 9, 18),
            )
                .sendSoknad()

        // September 2024
        // Mandag   Tirsdag   Onsdag    Torsdag   Fredag   Lørdag   Søndag
        // 2        3         4         5         6        7        8
        // ---      ---       ---       ---       ---      Helg     Helg
        // 9        10        11        12        13       14       15
        // ---      Ferie     Ferie     Ferie     ---      Helg     Helg
        // 16       17        18        19        20       21       22
        // ---      Perm      Perm      ---       ---      Helg     Helg
        // 23       24        25        26        27       28       29
        // ---      ---       ---       ---       ---      Helg     Helg

        sendtSykepengeSoknad.status `should be equal to` sendtSykepengeSoknad.status
        verifiserKafkaSoknader(2)

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSykepengeSoknad.id)
        soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver

        // Sjekker at Opphold Utland søknad ikke har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        oppholdUtlandSoknader.size `should be equal to` 1
        oppholdUtlandSoknader.first().fnr `should be equal to` fnr
    }
}
