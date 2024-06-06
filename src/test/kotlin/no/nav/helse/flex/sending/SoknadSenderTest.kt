package no.nav.helse.flex.sending

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.OPPHOLD_UTENFOR_EOS
import no.nav.helse.flex.soknadsopprettelse.OPPHOLD_UTENFOR_EOS_NAR
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import org.amshove.kluent.shouldBeEquivalentTo
import org.amshove.kluent.shouldBeNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

@OptIn(ExperimentalStdlibApi::class)
class SoknadSenderTest : FellesTestOppsett() {
    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    private val fnr = "12345678900"

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
        utenforEOSTom: LocalDate? = soknaden.tom,
    ): SoknadBesvarer {
        val soknadBesvart =
            SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
                .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
                .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
                .besvarSporsmal(OPPHOLD_UTENFOR_EOS, "JA", mutert = false, ferdigBesvart = false)
                .besvarSporsmal(
                    OPPHOLD_UTENFOR_EOS_NAR,
                    svar = """{"fom":"${soknaden.fom!!}","tom":"$utenforEOSTom"}""",
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
        assertThat(kafkaSoknader).hasSize(1)
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.SENDT)
        kafkaSoknader[0].arbeidUtenforNorge.shouldBeNull()
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    fun `lager ikke søknad hvis opphold utenfor EØS kun er i helg`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
            ),
        )

        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = fnr,
            )

        val oppholdUtenforEOSIEnDag = soknaden.fom?.plusDays(1)

        val sendtSoknad = soknadBesvarer(soknaden, utenforEOSTom = oppholdUtenforEOSIEnDag).sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)
        verifiserKafkaSoknader()

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSoknad.id)
        assertThat(soknadFraDatabase.status).shouldBeEquivalentTo(SoknadsstatusDTO.SENDT)

        // Sjekker at Opphold Utland søknad ikke har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        assertThat(oppholdUtlandSoknader.size).isEqualTo(0)
    }

    @Test
    fun `lager ikke søknad hvis opphold utenfor EØS er kun i ferie`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
            ),
        )

        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = fnr,
            )

        val ferieStarterForsteDagenIPerioden = soknaden.fom
        val ferieVarerIFemDager = soknaden.fom!!.plusDays(5)
        val oppholdUtenforEOSSamtidigSomFerie = soknaden.fom!!.plusDays(5)

        val sendtSoknad =
            soknadBesvarer(
                soknaden,
                ferieFom = ferieStarterForsteDagenIPerioden,
                ferieTom = ferieVarerIFemDager,
                utenforEOSTom = oppholdUtenforEOSSamtidigSomFerie,
            )
                .sendSoknad()

        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)
        verifiserKafkaSoknader()

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSoknad.id)
        assertThat(soknadFraDatabase.status).shouldBeEquivalentTo(SoknadsstatusDTO.SENDT)

        // Sjekker at Opphold Utland søknad ikke har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        assertThat(oppholdUtlandSoknader.size).isEqualTo(0)
    }

    @Test
    fun `lager ikke søknad hvis opphold utenfor EØS dekker hele perioden med helg og ferie`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
            ),
        )

        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = fnr,
            )

        val ferieStarterForsteDagenIPerioden = soknaden.fom
        val ferieVarerIHelePerioden = soknaden.tom
        val oppholdUtenforEOSSamtidigSomFerie = soknaden.tom

        val sendtSoknad =
            soknadBesvarer(
                soknaden,
                ferieFom = ferieStarterForsteDagenIPerioden,
                ferieTom = ferieVarerIHelePerioden,
                utenforEOSTom = oppholdUtenforEOSSamtidigSomFerie,
            )
                .sendSoknad()

        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)
        verifiserKafkaSoknader()

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSoknad.id)
        assertThat(soknadFraDatabase.status).shouldBeEquivalentTo(SoknadsstatusDTO.SENDT)

        // Sjekker at Opphold Utland søknad ikke har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        assertThat(oppholdUtlandSoknader.size).isEqualTo(0)
    }

    @Test
    fun `lager søknad hvis opphold utenfor EØS ikke dekker hele perioden med helg og ferie`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
            ),
        )

        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = fnr,
            )

        val ferieStarterForsteDagenIPerioden = soknaden.fom
        val ferieVarerToDagerMindreEnnPerioden = soknaden.tom?.minusDays(2) // Siste dagen i soknadsperioden er helg
        val oppholdUtenforEOSIHelePerioden = soknaden.tom

        val sendtSoknad =
            soknadBesvarer(
                soknaden,
                ferieFom = ferieStarterForsteDagenIPerioden,
                ferieTom = ferieVarerToDagerMindreEnnPerioden,
                utenforEOSTom = oppholdUtenforEOSIHelePerioden,
            )
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)
        verifiserKafkaSoknader()

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSoknad.id)
        assertThat(soknadFraDatabase.status).shouldBeEquivalentTo(SoknadsstatusDTO.SENDT)

        // Sjekker at Opphold Utland søknad har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        assertThat(oppholdUtlandSoknader.size).isEqualTo(1)
        assertThat(oppholdUtlandSoknader.first().fnr).isEqualTo(fnr)
    }

    @Test
    fun `oppretter utland søknad hvis utlandsopphold er utenfor helg eller ferie`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
            ),
        )

        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = fnr,
            )

        val ferieStarterForsteDagenIPerioden = soknaden.fom
        val ferieVarerIFemDager = soknaden.fom!!.plusDays(5)

        val sendtSoknad =
            soknadBesvarer(soknaden, ferieFom = ferieStarterForsteDagenIPerioden, ferieTom = ferieVarerIFemDager)
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)
        verifiserKafkaSoknader()

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSoknad.id)
        assertThat(soknadFraDatabase.status).shouldBeEquivalentTo(SoknadsstatusDTO.SENDT)

        // Sjekker at Opphold Utland søknad har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        assertThat(oppholdUtlandSoknader.size).isEqualTo(1)
        assertThat(oppholdUtlandSoknader.first().fnr).isEqualTo(fnr)
    }

    @Test
    fun `lager ikke søknad hvis opphold utenfor EØS dekker hele perioden med permisjon`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
            ),
        )

        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = fnr,
            )

        val permisjonStarterForsteDagenIPerioden = soknaden.fom
        val permisjonVarerIHelePerioden = soknaden.tom
        val oppholdUtenforEOSSamtidigSomPermisjon = soknaden.tom

        val sendtSoknad =
            soknadBesvarer(
                soknaden,
                permisjonFom = permisjonStarterForsteDagenIPerioden,
                permisjonTom = permisjonVarerIHelePerioden,
                utenforEOSTom = oppholdUtenforEOSSamtidigSomPermisjon,
            )
                .sendSoknad()

        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)
        verifiserKafkaSoknader()

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSoknad.id)
        assertThat(soknadFraDatabase.status).shouldBeEquivalentTo(SoknadsstatusDTO.SENDT)

        // Sjekker at Opphold Utland søknad ikke har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        assertThat(oppholdUtlandSoknader.size).isEqualTo(0)
    }

    @Test
    fun `lager ikke søknad hvis opphold utenfor EØS dekker hele perioden med helg, ferie og permisjon`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
            ),
        )

        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = fnr,
            )

        val ferieStarterEtterForsteHelg = soknaden.fom?.plusDays(2)
        val ferieVarerTilAndreHelg = soknaden.fom?.plusDays(7)
        val permisjonStarterIAndreHelg = soknaden.fom?.plusDays(8)
        val permisjonVarerUtPerioden = soknaden.tom
        val oppholdUtenforEOSSamtidigSomPermisjon = soknaden.tom

        val sendtSoknad =
            soknadBesvarer(
                soknaden,
                ferieFom = ferieStarterEtterForsteHelg,
                ferieTom = ferieVarerTilAndreHelg,
                permisjonFom = permisjonStarterIAndreHelg,
                permisjonTom = permisjonVarerUtPerioden,
                utenforEOSTom = oppholdUtenforEOSSamtidigSomPermisjon,
            )
                .sendSoknad()

        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)
        verifiserKafkaSoknader()

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSoknad.id)
        assertThat(soknadFraDatabase.status).shouldBeEquivalentTo(SoknadsstatusDTO.SENDT)

        // Sjekker at Opphold Utland søknad ikke har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        assertThat(oppholdUtlandSoknader.size).isEqualTo(0)
    }

    @Test
    fun `lager søknad hvis opphold utenfor EØS har noen dager utenfor perioder med ferie og permisjon`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
            ),
        )

        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = fnr,
            )

        val ferieBegynnerIMidtenAvForsteUke = soknaden.fom?.plusDays(4)
        val ferieVarerTilSluttenAvForsteUke = soknaden.fom?.plusDays(6)
        val permisjonStarterIMidtenAvAndreUke = soknaden.fom?.plusDays(11)
        val permisjonVarerTilTorsdagIAndreUke = soknaden.fom?.plusDays(12)
        val oppholdUtenforEOSTilTorsdagAndreUke = soknaden.fom?.plusDays(12)

        val sendtSoknad =
            soknadBesvarer(
                soknaden,
                ferieFom = ferieBegynnerIMidtenAvForsteUke,
                ferieTom = ferieVarerTilSluttenAvForsteUke,
                permisjonFom = permisjonStarterIMidtenAvAndreUke,
                permisjonTom = permisjonVarerTilTorsdagIAndreUke,
                utenforEOSTom = oppholdUtenforEOSTilTorsdagAndreUke,
            )
                .sendSoknad()

        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)
        verifiserKafkaSoknader()

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSoknad.id)
        assertThat(soknadFraDatabase.status).shouldBeEquivalentTo(SoknadsstatusDTO.SENDT)

        // Sjekker at Opphold Utland søknad ikke har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        assertThat(oppholdUtlandSoknader.size).isEqualTo(1)
        assertThat(oppholdUtlandSoknader.first().fnr).isEqualTo(fnr)
    }
}
