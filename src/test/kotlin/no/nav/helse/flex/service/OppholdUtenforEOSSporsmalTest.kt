package no.nav.helse.flex.service

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.OffsetDateTime

@TestMethodOrder(MethodOrderer.MethodName::class)
class OppholdUtenforEOSSporsmalTest : FellesTestOppsett() {
    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var oppholdUtenforEOSService: OppholdUtenforEOSService

    private final val fnr = "123456789"
    private val fom = LocalDate.now().minusWeeks(3)
    private val tom = LocalDate.now().minusDays(2)

    @BeforeEach
    fun setUp() {
        flexSyketilfelleMockRestServiceServer.reset()
        databaseReset.resetDatabase()
        fakeUnleash.resetAll()
        fakeUnleash.disable(UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS)
    }

    @Test
    fun `arbeidstaker søknader får gammelt UTLAND_V2 spørsmålet ved toggle av`() {
        mockFlexSyketilfelleArbeidsgiverperiode()
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(fom = fom, tom = tom),
                timestamp = OffsetDateTime.now().minusWeeks(3),
            ),
        )

        val hentetViaRest = hentSoknaderMetadata(fnr)
        hentetViaRest.size `should be equal to` 1
        hentetViaRest[0].soknadstype `should be equal to` RSSoknadstype.ARBEIDSTAKERE
        hentetViaRest[0].status `should be equal to` RSSoknadstatus.NY

        mockFlexSyketilfelleArbeidsgiverperiode()
        val rsSykepengesoknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )

        val sendtSykepengeSoknad =
            SoknadBesvarer(rSSykepengesoknad = rsSykepengesoknad, mockMvc = this, fnr = fnr)
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
        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.size `should be equal to` 1
        kafkaSoknader.first().status `should be equal to` SoknadsstatusDTO.SENDT
        kafkaSoknader.first().arbeidUtenforNorge `should be equal to` null
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSykepengeSoknad.id)
        soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver
        rsSykepengesoknad.sporsmal!!.map { it.tag } `should be equal to`
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                FERIE_V2,
                PERMISJON_V2,
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                ANDRE_INNTEKTSKILDER_V2,
                UTLAND_V2,
                TIL_SLUTT,
            )

        soknadFraDatabase.sporsmal.any { it.tag == OPPHOLD_UTENFOR_EOS } `should be equal to` false

        // Sjekker at Opphold Utland søknad har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        oppholdUtlandSoknader.size `should be equal to` 0
    }

    @Test
    fun `arbeidsledig søknader får gammelt ARBEIDSLEDIG_UTLAND spørsmålet ved toggle av`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                sykmeldingsperioder = heltSykmeldt(fom = fom, tom = tom),
                timestamp = OffsetDateTime.now().minusWeeks(3),
            ),
        )

        val hentetViaRest = hentSoknaderMetadata(fnr)
        hentetViaRest.size `should be equal to` 1
        hentetViaRest[0].soknadstype `should be equal to` RSSoknadstype.ARBEIDSLEDIG
        hentetViaRest[0].status `should be equal to` RSSoknadstatus.NY

        val rsSykepengesoknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        val sendtSykepengeSoknad =
            SoknadBesvarer(rSSykepengesoknad = rsSykepengesoknad, mockMvc = this, fnr = fnr)
                .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
                .besvarSporsmal(tag = "FRISKMELDT", svar = "JA")
                .besvarSporsmal(tag = "ARBEID_UTENFOR_NORGE", svar = "NEI")
                .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER", svar = "NEI")
                .besvarSporsmal(tag = "ARBEIDSLEDIG_UTLAND", svar = "NEI")
                .besvarSporsmal(tag = "TIL_SLUTT", svar = "svar 1, og 2", ferdigBesvart = false)
                .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
                .sendSoknad()
        sendtSykepengeSoknad.status `should be equal to` sendtSykepengeSoknad.status
        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.size `should be equal to` 1
        kafkaSoknader.first().status `should be equal to` SoknadsstatusDTO.SENDT
        kafkaSoknader.first().arbeidUtenforNorge `should be equal to` false

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSykepengeSoknad.id)
        rsSykepengesoknad.sporsmal!!.map { it.tag } `should be equal to`
            listOf(
                ANSVARSERKLARING,
                FRISKMELDT,
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER,
                ARBEIDSLEDIG_UTLAND,
                TIL_SLUTT,
            )

        soknadFraDatabase.sporsmal.any { it.tag == OPPHOLD_UTENFOR_EOS } `should be equal to` false

        // Sjekker at Opphold Utland søknad har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        oppholdUtlandSoknader.size `should be equal to` 0
    }

    @Test
    fun `frilanser søknader får gammelt UTLAND spørsmålet ved toggle av`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.FRILANSER,
                sykmeldingsperioder = heltSykmeldt(fom = fom, tom = tom),
                timestamp = OffsetDateTime.now().minusWeeks(3),
            ),
        )

        val hentetViaRest = hentSoknaderMetadata(fnr)
        hentetViaRest.size `should be equal to` 1
        hentetViaRest[0].soknadstype `should be equal to` RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE
        hentetViaRest[0].status `should be equal to` RSSoknadstatus.NY

        val rsSykepengesoknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        val sendtSykepengeSoknad =
            SoknadBesvarer(rSSykepengesoknad = rsSykepengesoknad, mockMvc = this, fnr = fnr)
                .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
                .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
                .besvarSporsmal("ARBEID_UNDERVEIS_100_PROSENT_0", "NEI")
                .besvarSporsmal(tag = "ARBEID_UTENFOR_NORGE", svar = "NEI")
                .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER", svar = "NEI")
                .besvarSporsmal(tag = "UTLAND", svar = "NEI")
                .besvarSporsmal(tag = "TIL_SLUTT", svar = "svar 1, og 2", ferdigBesvart = false)
                .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
                .sendSoknad()
        sendtSykepengeSoknad.status `should be equal to` sendtSykepengeSoknad.status
        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.size `should be equal to` 1
        kafkaSoknader.first().status `should be equal to` SoknadsstatusDTO.SENDT
        kafkaSoknader.first().arbeidUtenforNorge `should be equal to` false
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 1)

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSykepengeSoknad.id)
        rsSykepengesoknad.sporsmal!!.map { it.tag } `should be equal to`
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER,
                UTLAND,
                TIL_SLUTT,
            )
        soknadFraDatabase.sporsmal.any { it.tag == OPPHOLD_UTENFOR_EOS } `should be equal to` false

        // Sjekker at Opphold Utland søknad har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        oppholdUtlandSoknader.size `should be equal to` 0
    }

    @Test
    fun `gammel arbeidstakersøknad får gammelt opphold utland spørsmål ved mutering etter toggle er slått på`() {
        mockFlexSyketilfelleArbeidsgiverperiode()
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(fom = fom, tom = tom),
                timestamp = OffsetDateTime.now().minusWeeks(3),
            ),
        )

        // skrur på nytt opphold utland spørsmål
        fakeUnleash.enable(UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        hentetViaRest.size `should be equal to` 1
        hentetViaRest[0].soknadstype `should be equal to` RSSoknadstype.ARBEIDSTAKERE
        hentetViaRest[0].status `should be equal to` RSSoknadstatus.NY

        mockFlexSyketilfelleArbeidsgiverperiode()
        val rsSykepengesoknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )

        val sendtSykepengeSoknad =
            SoknadBesvarer(rSSykepengesoknad = rsSykepengesoknad, mockMvc = this, fnr = fnr)
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
        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.size `should be equal to` 1
        kafkaSoknader.first().status `should be equal to` SoknadsstatusDTO.SENDT
        kafkaSoknader.first().arbeidUtenforNorge `should be equal to` null
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSykepengeSoknad.id)
        soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver
        rsSykepengesoknad.sporsmal!!.map { it.tag } `should be equal to`
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                FERIE_V2,
                PERMISJON_V2,
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                ANDRE_INNTEKTSKILDER_V2,
                UTLAND_V2,
                TIL_SLUTT,
            )

        soknadFraDatabase.sporsmal.any { it.tag == OPPHOLD_UTENFOR_EOS } `should be equal to` false

        // Sjekker at Opphold Utland søknad har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        oppholdUtlandSoknader.size `should be equal to` 0
    }

    @Test
    fun `ny arbeidstakersøknad får nytt opphold utland spørsmål ved mutering etter toggle er slått av`() {
        // skrur på nytt opphold utland spørsmål
        fakeUnleash.enable(UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS)

        mockFlexSyketilfelleArbeidsgiverperiode()
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(fom = fom, tom = tom),
                timestamp = OffsetDateTime.now().minusWeeks(3),
            ),
        )

        // skrur av nytt opphold utland spørsmål
        fakeUnleash.disable(UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        hentetViaRest.size `should be equal to` 1
        hentetViaRest[0].soknadstype `should be equal to` RSSoknadstype.ARBEIDSTAKERE
        hentetViaRest[0].status `should be equal to` RSSoknadstatus.NY

        mockFlexSyketilfelleArbeidsgiverperiode()
        val rsSykepengesoknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )

        val sendtSykepengeSoknad =
            SoknadBesvarer(rSSykepengesoknad = rsSykepengesoknad, mockMvc = this, fnr = fnr)
                .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
                .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
                .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
                .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
                .besvarSporsmal(tag = "OPPHOLD_UTENFOR_EOS", svar = "NEI")
                .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_0", svar = "NEI")
                .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
                .besvarSporsmal(tag = "TIL_SLUTT", svar = "Jeg lover å ikke lyve!", ferdigBesvart = false)
                .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED").sendSoknad()
        sendtSykepengeSoknad.status `should be equal to` sendtSykepengeSoknad.status
        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.size `should be equal to` 1
        kafkaSoknader.first().status `should be equal to` SoknadsstatusDTO.SENDT
        kafkaSoknader.first().arbeidUtenforNorge `should be equal to` null
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSykepengeSoknad.id)
        soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver
        rsSykepengesoknad.sporsmal!!.map { it.tag } `should be equal to`
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                FERIE_V2,
                PERMISJON_V2,
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                ANDRE_INNTEKTSKILDER_V2,
                OPPHOLD_UTENFOR_EOS,
                TIL_SLUTT,
            )

        soknadFraDatabase.sporsmal.any { it.tag == UTLAND_V2 } `should be equal to` false

        // Sjekker at Opphold Utland søknad har blitt laget
        val oppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer = listOf(fnr), Soknadstype.OPPHOLD_UTLAND)
        oppholdUtlandSoknader.size `should be equal to` 0
    }
}
