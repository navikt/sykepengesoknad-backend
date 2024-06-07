package no.nav.helse.flex.sending

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.oppholdUtenforEOS.OppholdUtenforEOSService
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

class OppholdUtenforEOSTest : FellesTestOppsett() {
    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var oppholdUtenforEOSService: OppholdUtenforEOSService

    private val fnr = "12345678900"
    private val fom = LocalDate.now().minusWeeks(3)
    private val tom = LocalDate.now().minusDays(2)

    @BeforeEach
    fun beforeEach() {
        databaseReset.resetDatabase()
    }

    private fun soknadBesvarer(
        soknaden: RSSykepengesoknad,
        ferieFom: LocalDate? = null,
        ferieTom: LocalDate? = null,
        permisjonFom: LocalDate? = null,
        permisjonTom: LocalDate? = null,
        utenforEOSTom: LocalDate? = tom,
    ): SoknadBesvarer {
        val soknadBesvart =
            SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
                .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
                .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
                .besvarSporsmal(OPPHOLD_UTENFOR_EOS, "JA", mutert = false, ferdigBesvart = false)
                .besvarSporsmal(
                    OPPHOLD_UTENFOR_EOS_NAR,
                    svar = """{"fom":"$fom","tom":"$utenforEOSTom"}""",
                    mutert = false,
                    ferdigBesvart = true,
                )
                .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_0", svar = "NEI")
                .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
                .besvarSporsmal(tag = "TIL_SLUTT", svar = "Jeg lover å ikke lyve!", ferdigBesvart = false)
                .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")

        if (ferieFom != null && ferieTom != null) {
            soknadBesvart
                .besvarSporsmal(tag = "FERIE_V2", svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(
                    tag = "FERIE_NAR_V2",
                    svar = """{"fom":"$ferieFom","tom":"$ferieTom"}""",
                )
        } else {
            soknadBesvart.besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
        }

        if (permisjonFom != null && permisjonTom != null) {
            soknadBesvart
                .besvarSporsmal(tag = "PERMISJON_V2", svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(
                    tag = "PERMISJON_NAR_V2",
                    svar = """{"fom":"$permisjonFom","tom":"$permisjonTom"}""",
                )
        } else {
            soknadBesvart.besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
        }
        return soknadBesvart
    }

    private fun verifiserKafkaSoknader() {
        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.size `should be equal to` 1
        kafkaSoknader.first().status `should be equal to` SoknadsstatusDTO.SENDT
        kafkaSoknader.first().arbeidUtenforNorge.shouldBeNull()
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }

    private fun settOppSykepengeSoknad(): RSSykepengesoknad {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        sendSykmelding(sykmeldingKafkaMessage(
            fnr = fnr,
//            sykmeldingsperioder = heltSykmeldt(fom,tom),
//            timestamp = OffsetDateTime.now().minusWeeks(3)
        ))

        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = fnr,
            )
        return soknaden
    }

    @Test
    fun `søknad om opphold utenfor EØS opprettes ikke dersom det finnes eksisterende søknad`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        opprettUtlandssoknad(fnr)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0)

        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknadOppholdUtenforEOS =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )

        val tidligereOppholdUtenforEOSStarterNoenDagerForSykmeldingStart = LocalDateTime.now().minusWeeks(3)
        val tidligereOppholdUtenforEOSSlutterNoenDagerEtterSykmeldingStart = LocalDate.now().minusWeeks(2)
        
        SoknadBesvarer(soknadOppholdUtenforEOS, this, fnr)
            .besvarSporsmal(LAND, svarListe = listOf("Syden", "Kina"))
            .besvarSporsmal(
                tag = PERIODEUTLAND,
                svar = """{"fom":"$tidligereOppholdUtenforEOSStarterNoenDagerForSykmeldingStart",
                    |"tom":"$tidligereOppholdUtenforEOSSlutterNoenDagerEtterSykmeldingStart"}""".trimMargin(),
            )
            .besvarSporsmal(ARBEIDSGIVER, svar = "NEI")
            .besvarSporsmal(TIL_SLUTT, "svar", ferdigBesvart = false)
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
            .sendSoknad()

        val sykepengeSoknad = settOppSykepengeSoknad()
        val sendtSykepengeSoknad = soknadBesvarer(sykepengeSoknad).sendSoknad()
        sendtSykepengeSoknad.status `should be equal to` sendtSykepengeSoknad.status
        verifiserKafkaSoknader()

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSykepengeSoknad.id)
        soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver

        // Sjekker at Opphold Utland søknad ikke har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        oppholdUtlandSoknader.size `should be equal to` 0
    }

    @Test
    fun `lager ikke søknad hvis opphold utenfor EØS kun er i helg`() {
        val soknaden = settOppSykepengeSoknad()

        val oppholdUtenforEOSIEnDag = soknaden.fom?.plusDays(1)

        val sendtSykepengeSoknad = soknadBesvarer(soknaden, utenforEOSTom = oppholdUtenforEOSIEnDag).sendSoknad()
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
        val soknaden = settOppSykepengeSoknad()

        val ferieStarterForsteDagenIPerioden = soknaden.fom
        val ferieVarerIFemDager = soknaden.fom!!.plusDays(5)
        val oppholdUtenforEOSSamtidigSomFerie = soknaden.fom!!.plusDays(5)

        val sendtSykepengeSoknad =
            soknadBesvarer(
                soknaden,
                ferieFom = ferieStarterForsteDagenIPerioden,
                ferieTom = ferieVarerIFemDager,
                utenforEOSTom = oppholdUtenforEOSSamtidigSomFerie,
            )
                .sendSoknad()

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
        val soknaden = settOppSykepengeSoknad()

        val permisjonStarterForsteDagenIPerioden = soknaden.fom
        val permisjonVarerIHelePerioden = soknaden.tom
        val oppholdUtenforEOSSamtidigSomPermisjon = soknaden.tom

        val sendtSykepengeSoknad =
            soknadBesvarer(
                soknaden,
                permisjonFom = permisjonStarterForsteDagenIPerioden,
                permisjonTom = permisjonVarerIHelePerioden,
                utenforEOSTom = oppholdUtenforEOSSamtidigSomPermisjon,
            )
                .sendSoknad()

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
        val soknaden = settOppSykepengeSoknad()

        val ferieStarterForsteDagenIPerioden = soknaden.fom
        val ferieVarerIHelePerioden = soknaden.tom
        val oppholdUtenforEOSSamtidigSomFerie = soknaden.tom

        val sendtSykepengeSoknad =
            soknadBesvarer(
                soknaden,
                ferieFom = ferieStarterForsteDagenIPerioden,
                ferieTom = ferieVarerIHelePerioden,
                utenforEOSTom = oppholdUtenforEOSSamtidigSomFerie,
            )
                .sendSoknad()

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
        val soknaden = settOppSykepengeSoknad()

        val ferieStarterForsteDagenIPerioden = soknaden.fom
        val ferieVarerToDagerMindreEnnPerioden = soknaden.tom?.minusDays(2) // Siste dagen i soknadsperioden er helg
        val oppholdUtenforEOSIHelePerioden = soknaden.tom

        val sendtSykepengeSoknad =
            soknadBesvarer(
                soknaden,
                ferieFom = ferieStarterForsteDagenIPerioden,
                ferieTom = ferieVarerToDagerMindreEnnPerioden,
                utenforEOSTom = oppholdUtenforEOSIHelePerioden,
            )
                .sendSoknad()
        sendtSykepengeSoknad.status `should be equal to` sendtSykepengeSoknad.status
        verifiserKafkaSoknader()

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSykepengeSoknad.id)
        soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver

        // Sjekker at Opphold Utland søknad har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        oppholdUtlandSoknader.size `should be equal to` 1
        oppholdUtlandSoknader.first().fnr `should be equal to` fnr
    }

    @Test
    fun `oppretter utland søknad hvis utlandsopphold er utenfor helg eller ferie`() {
        val soknaden = settOppSykepengeSoknad()

        val ferieStarterForsteDagenIPerioden = soknaden.fom
        val ferieVarerIFemDager = soknaden.fom!!.plusDays(5)

        val sendtSykepengeSoknad =
            soknadBesvarer(soknaden, ferieFom = ferieStarterForsteDagenIPerioden, ferieTom = ferieVarerIFemDager)
                .sendSoknad()
        sendtSykepengeSoknad.status `should be equal to` sendtSykepengeSoknad.status
        verifiserKafkaSoknader()

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSykepengeSoknad.id)
        soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver

        // Sjekker at Opphold Utland søknad har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        oppholdUtlandSoknader.size `should be equal to` 1
        oppholdUtlandSoknader.first().fnr `should be equal to` fnr
    }

    @Test
    fun `lager ikke søknad hvis opphold utenfor EØS dekker hele perioden med helg, ferie og permisjon`() {
        val soknaden = settOppSykepengeSoknad()

        val ferieStarterEtterForsteHelg = soknaden.fom?.plusDays(2)
        val ferieVarerTilAndreHelg = soknaden.fom?.plusDays(7)
        val permisjonStarterIAndreHelg = soknaden.fom?.plusDays(8)
        val permisjonVarerUtPerioden = soknaden.tom
        val oppholdUtenforEOSSamtidigSomPermisjon = soknaden.tom

        val sendtSykepengeSoknad =
            soknadBesvarer(
                soknaden,
                ferieFom = ferieStarterEtterForsteHelg,
                ferieTom = ferieVarerTilAndreHelg,
                permisjonFom = permisjonStarterIAndreHelg,
                permisjonTom = permisjonVarerUtPerioden,
                utenforEOSTom = oppholdUtenforEOSSamtidigSomPermisjon,
            )
                .sendSoknad()

        sendtSykepengeSoknad.status `should be equal to` sendtSykepengeSoknad.status
        verifiserKafkaSoknader()

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSykepengeSoknad.id)
        soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver

        // Sjekker at Opphold Utland søknad ikke har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        oppholdUtlandSoknader.size `should be equal to` 0
    }

    @Test
    fun `lager søknad hvis opphold utenfor EØS har noen dager utenfor perioder med ferie og permisjon`() {
        val soknaden = settOppSykepengeSoknad()

        val ferieBegynnerIMidtenAvForsteUke = soknaden.fom?.plusDays(4)
        val ferieVarerTilSluttenAvForsteUke = soknaden.fom?.plusDays(6)
        val permisjonStarterIMidtenAvAndreUke = soknaden.fom?.plusDays(11)
        val permisjonVarerTilTorsdagIAndreUke = soknaden.fom?.plusDays(12)
        val oppholdUtenforEOSTilTorsdagAndreUke = soknaden.fom?.plusDays(12)

        val sendtSykepengeSoknad =
            soknadBesvarer(
                soknaden,
                ferieFom = ferieBegynnerIMidtenAvForsteUke,
                ferieTom = ferieVarerTilSluttenAvForsteUke,
                permisjonFom = permisjonStarterIMidtenAvAndreUke,
                permisjonTom = permisjonVarerTilTorsdagIAndreUke,
                utenforEOSTom = oppholdUtenforEOSTilTorsdagAndreUke,
            )
                .sendSoknad()

        sendtSykepengeSoknad.status `should be equal to` sendtSykepengeSoknad.status
        verifiserKafkaSoknader()

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSykepengeSoknad.id)
        soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver

        // Sjekker at Opphold Utland søknad ikke har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        oppholdUtlandSoknader.size `should be equal to` 1
        oppholdUtlandSoknader.first().fnr `should be equal to` fnr
    }
}
