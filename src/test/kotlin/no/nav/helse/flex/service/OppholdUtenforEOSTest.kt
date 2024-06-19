package no.nav.helse.flex.service

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS
import org.amshove.kluent.`should be equal to`
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

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
        fakeUnleash.resetAll()
    }

    private fun forsteLordagEtter(dato: LocalDate): LocalDate {
        var helg = dato
        while (helg.dayOfWeek != DayOfWeek.SATURDAY) {
            helg = helg.plusDays(1)
        }
        return helg
    }

    private fun soknadBesvarer(
        soknaden: RSSykepengesoknad,
        ferieFom: LocalDate? = null,
        ferieTom: LocalDate? = null,
        permisjonFom: LocalDate? = null,
        permisjonTom: LocalDate? = null,
        utenforEOSFom: LocalDate? = fom,
        utenforEOSTom: LocalDate? = tom,
    ): SoknadBesvarer {
        val soknadBesvarer =
            SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
                .apply {
                    besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
                    besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
                    besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_0", svar = "NEI")
                    besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
                    besvarSporsmal(tag = "TIL_SLUTT", svar = "Jeg lover å ikke lyve!", ferdigBesvart = false)
                    besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
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

    private fun verifiserKafkaSoknader() {
        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.size `should be equal to` 1
        kafkaSoknader.first().status `should be equal to` SoknadsstatusDTO.SENDT
        kafkaSoknader.first().arbeidUtenforNorge `should be equal to` null
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
                timestamp = OffsetDateTime.now().minusWeeks(3),
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
        fakeUnleash.enable(UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS)
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        opprettUtlandssoknad(fnr)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0)

        hentSoknaderMetadata(fnr).size `should be equal to` 1

        mockFlexSyketilfelleArbeidsgiverperiode()

        val tidligereOppholdUtenforEOSStarterNoenDagerForSykmeldingStart = LocalDate.now().minusWeeks(4)
        val tidligereOppholdUtenforEOSSlutterNoenDagerEtterSykmeldingStart = LocalDate.now().minusWeeks(2)

        val soknadOppholdUtenforEOS =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )

        SoknadBesvarer(soknadOppholdUtenforEOS, this, fnr)
            .besvarSporsmal(
                tag = PERIODEUTLAND,
                svar = "{\"fom\":\"${tidligereOppholdUtenforEOSStarterNoenDagerForSykmeldingStart.format(
                    DateTimeFormatter.ISO_LOCAL_DATE,
                )}\",\"tom\":\"${tidligereOppholdUtenforEOSSlutterNoenDagerEtterSykmeldingStart.format(
                    DateTimeFormatter.ISO_LOCAL_DATE,
                )}\"}",
            )
            .besvarSporsmal(LAND, svarListe = listOf("Kina"))
            .besvarSporsmal(ARBEIDSGIVER, svar = "NEI")
            .besvarSporsmal(TIL_SLUTT, "svar", ferdigBesvart = false)
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
            .sendSoknad()

        val soknaden = settOppSykepengeSoknad(fom, tom)

        val sendtSykepengeSoknad = soknadBesvarer(soknaden, utenforEOSFom = fom, utenforEOSTom = tom).sendSoknad()
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
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
    fun `sjekk om det kun er det gamle spørsmålet som blir stilt om toggel er av`() {
        fakeUnleash.disable(UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS)
        val soknaden = settOppSykepengeSoknad(fom, tom)

        val sendtSykepengeSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
                .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
                .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
                .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
                .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
                .besvarSporsmal(tag = "UTLAND_V2", svar = "NEI")
                .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_0", svar = "NEI")
                .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
                .besvarSporsmal(tag = "TIL_SLUTT", svar = "Jeg lover å ikke lyve!", ferdigBesvart = false)
                .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED").sendSoknad()
        sendtSykepengeSoknad.status `should be equal to` sendtSykepengeSoknad.status
        verifiserKafkaSoknader()

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSykepengeSoknad.id)
        soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver
        assertThat(soknaden.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                FERIE_V2,
                PERMISJON_V2,
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                ANDRE_INNTEKTSKILDER_V2,
                UTLAND_V2,
                TIL_SLUTT,
            ),
        )
        soknadFraDatabase.sporsmal.any { it.tag == OPPHOLD_UTENFOR_EOS } `should be equal to` false

        // Sjekker at Opphold Utland søknad har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        oppholdUtlandSoknader.size `should be equal to` 0
    }

    @Test
    fun `lager ikke søknad hvis opphold utenfor EØS kun er i helg`() {
        fakeUnleash.enable(UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS)
        val soknaden = settOppSykepengeSoknad(fom, tom)

        var oppholdUtenforEOSIStartePaLordag = soknaden.fom

        if (oppholdUtenforEOSIStartePaLordag != null) {
            var dato = oppholdUtenforEOSIStartePaLordag
            var fantLordag: LocalDate? = null

            for (i in 0..7) {
                if (dato?.dayOfWeek == DayOfWeek.SATURDAY) {
                    fantLordag = dato
                    break
                }
                dato = dato?.plusDays(1)
            }

            oppholdUtenforEOSIStartePaLordag = fantLordag ?: throw RuntimeException("Fant ikke en lørdag innenfor en 8 dagers periode!")
        }

        val oppholdUtenforEOSVarerUtHelgen = oppholdUtenforEOSIStartePaLordag?.plusDays(1)
        val sendtSykepengeSoknad =
            soknadBesvarer(
                soknaden,
                utenforEOSFom = oppholdUtenforEOSIStartePaLordag,
                utenforEOSTom = oppholdUtenforEOSVarerUtHelgen,
            ).sendSoknad()

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
        fakeUnleash.enable(UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS)
        val soknaden = settOppSykepengeSoknad(fom, tom)

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
        fakeUnleash.enable(UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS)
        val soknaden = settOppSykepengeSoknad(fom, tom)

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
        fakeUnleash.enable(UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS)
        val soknaden = settOppSykepengeSoknad(fom, tom)

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
        fakeUnleash.enable(UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS)
        val soknaden = settOppSykepengeSoknad(fom, tom)

        val ferieStarterForsteDagenIPerioden = soknaden.fom
        val ferieVarerTilForsteHelg = forsteLordagEtter(fom).plusDays(2)
        val oppholdUtenforEOSIHelePerioden = soknaden.tom

        val sendtSykepengeSoknad =
            soknadBesvarer(
                soknaden,
                ferieFom = ferieStarterForsteDagenIPerioden,
                ferieTom = ferieVarerTilForsteHelg,
                utenforEOSTom = oppholdUtenforEOSIHelePerioden,
            ).sendSoknad()
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
        fakeUnleash.enable(UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS)
        val soknaden = settOppSykepengeSoknad(fom, tom)

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
        fakeUnleash.enable(UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS)
        val soknaden = settOppSykepengeSoknad(fom, tom)

        val starterMedFerie = soknaden.fom

        val ferieVarerTilForsteHelg = forsteLordagEtter(fom).minusDays(1)
        val permisjonStarterEtterHelg = ferieVarerTilForsteHelg.plusDays(3)
        val permisjonVarerUtHelePerioden = soknaden.tom
        val oppholdUtenforEOSSamtidigSomPermisjon = soknaden.tom

        val sendtSykepengeSoknad =
            soknadBesvarer(
                soknaden,
                ferieFom = starterMedFerie,
                ferieTom = ferieVarerTilForsteHelg,
                permisjonFom = permisjonStarterEtterHelg,
                permisjonTom = permisjonVarerUtHelePerioden,
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
        fakeUnleash.enable(UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS)
        val soknaden = settOppSykepengeSoknad(fom, tom)

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
