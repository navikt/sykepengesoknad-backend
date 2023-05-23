package no.nav.helse.flex.reisetilskudd

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.avbrytSoknad
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSMottaker
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSvar
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Kvittering
import no.nav.helse.flex.domain.Utgiftstype
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.ettersendTilArbeidsgiver
import no.nav.helse.flex.ettersendTilNav
import no.nav.helse.flex.finnMottakerAvSoknad
import no.nav.helse.flex.gjenapneSoknad
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.korrigerSoknad
import no.nav.helse.flex.lagreSvar
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.mockFlexSyketilfelleSykeforloep
import no.nav.helse.flex.sendSoknadMedResult
import no.nav.helse.flex.slettSvar
import no.nav.helse.flex.soknadsopprettelse.ANDRE_INNTEKTSKILDER_V2
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING
import no.nav.helse.flex.soknadsopprettelse.ARBEID_UTENFOR_NORGE
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER
import no.nav.helse.flex.soknadsopprettelse.BRUKTE_REISETILSKUDDET
import no.nav.helse.flex.soknadsopprettelse.FERIE_V2
import no.nav.helse.flex.soknadsopprettelse.FRAVAR_FOR_SYKMELDINGEN
import no.nav.helse.flex.soknadsopprettelse.KVITTERINGER
import no.nav.helse.flex.soknadsopprettelse.PERMISJON_V2
import no.nav.helse.flex.soknadsopprettelse.REISE_MED_BIL
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_I_ARBEID
import no.nav.helse.flex.soknadsopprettelse.TRANSPORT_TIL_DAGLIG
import no.nav.helse.flex.soknadsopprettelse.UTBETALING
import no.nav.helse.flex.soknadsopprettelse.UTLAND_V2
import no.nav.helse.flex.soknadsopprettelse.VAER_KLAR_OVER_AT
import no.nav.helse.flex.soknadsopprettelse.sporsmal.vaerKlarOverAtReisetilskudd
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.util.OBJECT_MAPPER
import no.nav.helse.flex.util.serialisertTilString
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be true`
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.Instant
import java.time.LocalDate
import java.util.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class GradertReisetilskuddIntegrationTest : BaseTestClass() {

    @Autowired
    private lateinit var namedParameterJdbcTemplate: NamedParameterJdbcTemplate

    final val fnr = "123456789"

    val sykmeldingId = UUID.randomUUID().toString()
    val fom: LocalDate = LocalDate.of(2021, 9, 2)
    val tom: LocalDate = LocalDate.of(2021, 9, 6)

    @BeforeAll
    fun `Det er ingen søknader til å begynne med`() {
        hentSoknaderMetadata(fnr).shouldBeEmpty()
    }

    @Test
    @Order(1)
    fun `Vi oppretter en reisetilskuddsøknad`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123454543", orgNavn = "Kebabbiten")

        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = skapArbeidsgiverSykmelding(
            sykmeldingId = sykmeldingId,
            fom = fom,
            tom = tom,
            type = PeriodetypeDTO.GRADERT,
            gradert = GradertDTO(50, true),
            reisetilskudd = false
        )

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC
        )

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.GRADERT_REISETILSKUDD)
    }

    @Test
    @Order(2)
    fun `Søknaden har alle spørsmål før vi har svart på om reisetilskuddet ble brukt`() {
        val soknader = hentSoknaderMetadata(fnr)
        assertThat(soknader).hasSize(1)

        val soknaden = hentSoknad(
            soknadId = soknader.first().id,
            fnr = fnr
        )

        assertThat(soknaden.sporsmal!!.first { it.tag == VAER_KLAR_OVER_AT }.undertekst).contains("sykepenger og reisetilskudd")
        assertThat(soknaden.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                FRAVAR_FOR_SYKMELDINGEN,
                TILBAKE_I_ARBEID,
                FERIE_V2,
                PERMISJON_V2,
                UTLAND_V2,
                "JOBBET_DU_GRADERT_0",
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER_V2,
                BRUKTE_REISETILSKUDDET,
                VAER_KLAR_OVER_AT,
                BEKREFT_OPPLYSNINGER
            )
        )

        assertThat(soknaden.sporsmal!!.first { it.tag == ANSVARSERKLARING }.sporsmalstekst).isEqualTo("Jeg vet at jeg kan miste retten til reisetilskudd og sykepenger hvis opplysningene jeg gir ikke er riktige eller fullstendige. Jeg vet også at NAV kan holde igjen eller kreve tilbake penger, og at å gi feil opplysninger kan være straffbart.")
        assertThat(soknaden.sporsmal!!.first { it.tag == TILBAKE_I_ARBEID }.sporsmalstekst).isEqualTo("Var du tilbake i ditt vanlige arbeid uten ekstra reiseutgifter før 7. september?")
        assertThat(soknaden.sporsmal!!.first { it.tag == "JOBBET_DU_GRADERT_0" }.sporsmalstekst).isEqualTo("Sykmeldingen sier du kunne jobbe 50 % i jobben din hos Kebabbiten. Jobbet du mer enn det?")
        assertThat(soknaden.sporsmal!!.first { it.tag == VAER_KLAR_OVER_AT }.sporsmalstekst).isEqualTo(
            vaerKlarOverAtReisetilskudd().sporsmalstekst
        )
    }

    @Test
    @Order(3)
    fun `Vi kan avbryte søknaden`() {
        val soknadId = hentSoknaderMetadata(fnr).first().id
        avbrytSoknad(fnr = fnr, soknadId = soknadId)

        val avbruttSøknad = hentSoknad(
            soknadId = soknadId,
            fnr = fnr
        )
        avbruttSøknad.status shouldBeEqualTo RSSoknadstatus.AVBRUTT
    }

    @Test
    @Order(4)
    fun `Vi kan gjenåpne søknaden`() {
        val soknadId = hentSoknaderMetadata(fnr).first().id
        gjenapneSoknad(fnr = fnr, soknadId = soknadId)

        val gjenåpnet = hentSoknad(
            soknadId = soknadId,
            fnr = fnr
        )
        gjenåpnet.status shouldBeEqualTo RSSoknadstatus.NY
    }

    @Test
    @Order(5)
    fun `Vi kan besvare et av spørsmålene`() {
        val soknadId = hentSoknaderMetadata(fnr).first().id

        val reisetilskudd = hentSoknad(
            soknadId = soknadId,
            fnr = fnr
        )
        SoknadBesvarer(reisetilskudd, this, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")

        val svaret = hentSoknad(
            soknadId = soknadId,
            fnr = fnr
        ).sporsmal!!.find { it.tag == ANSVARSERKLARING }!!.svar.first()
        svaret.verdi shouldBeEqualTo "CHECKED"
    }

    @Test
    @Order(6)
    fun `Vi kan besvare spørsmålet om at reisetilskudd ble brukt`() {
        val reisetilskudd = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )
        SoknadBesvarer(reisetilskudd, this, fnr)
            .besvarSporsmal(BRUKTE_REISETILSKUDDET, "JA", mutert = true)

        val reisetilskuddEtterSvar = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )
        reisetilskuddEtterSvar
            .sporsmal!!
            .find { it.tag == BRUKTE_REISETILSKUDDET }!!
            .svar.first().verdi shouldBeEqualTo "JA"

        assertThat(reisetilskuddEtterSvar.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                FRAVAR_FOR_SYKMELDINGEN,
                TILBAKE_I_ARBEID,
                FERIE_V2,
                PERMISJON_V2,
                UTLAND_V2,
                "JOBBET_DU_GRADERT_0",
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER_V2,
                BRUKTE_REISETILSKUDDET,
                TRANSPORT_TIL_DAGLIG,
                REISE_MED_BIL,
                KVITTERINGER,
                UTBETALING,
                VAER_KLAR_OVER_AT,
                BEKREFT_OPPLYSNINGER
            )
        )
    }

    @Test
    @Order(7)
    fun `Vi kan laste opp en kvittering`() {
        val reisetilskuddSoknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )
        val kvitteringSpm = reisetilskuddSoknad.sporsmal!!.first { it.tag == KVITTERINGER }
        val svar = RSSvar(
            verdi = Kvittering(
                blobId = "9a186e3c-aeeb-4566-a865-15aa9139d364",
                belop = 133700,
                typeUtgift = Utgiftstype.PARKERING,
                opprettet = Instant.now()
            ).serialisertTilString(),
            id = null
        )

        val spmSomBleSvart = lagreSvar(fnr, reisetilskuddSoknad.id, kvitteringSpm.id!!, svar).oppdatertSporsmal

        val returnertSvar = spmSomBleSvart.svar.first()

        val returnertKvittering: Kvittering = OBJECT_MAPPER.readValue(returnertSvar.verdi)
        returnertKvittering.typeUtgift.`should be equal to`(Utgiftstype.PARKERING)
    }

    @Test
    @Order(8)
    fun `Vi kan se den opplastede kvitteringen`() {
        val reisetilskuddSoknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )
        val kvitteringSpm = reisetilskuddSoknad.sporsmal!!.first { it.tag == KVITTERINGER }
        kvitteringSpm.svar.size `should be equal to` 1

        val returnertSvar = kvitteringSpm.svar.first()
        val returnertKvittering: Kvittering = OBJECT_MAPPER.readValue(returnertSvar.verdi)

        returnertKvittering.blobId.`should be equal to`("9a186e3c-aeeb-4566-a865-15aa9139d364")
        returnertKvittering.belop.`should be equal to`(133700)
    }

    @Test
    @Order(9)
    fun `Vi kan slette en kvittering`() {
        val reisetilskuddSoknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )
        val kvitteringSpm = reisetilskuddSoknad.sporsmal!!.first { it.tag == KVITTERINGER }
        kvitteringSpm.svar.size `should be equal to` 1

        val svaret = kvitteringSpm.svar.first()

        slettSvar(fnr, reisetilskuddSoknad.id, kvitteringSpm.id!!, svaret.id!!)

        val reisetilskuddSoknadEtter = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )
        val kvitteringSpmEtter = reisetilskuddSoknadEtter.sporsmal!!.first { it.tag == KVITTERINGER }
        kvitteringSpmEtter.svar.size `should be equal to` 0
    }

    @Test
    @Order(10)
    fun `Vi laster opp en kvittering igjen`() {
        val reisetilskuddSoknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )
        val kvitteringSpm = reisetilskuddSoknad.sporsmal!!.first { it.tag == KVITTERINGER }
        val svar = RSSvar(
            verdi = Kvittering(
                blobId = "9a186e3c-aeeb-4566-a865-15aa9139d364",
                belop = 133700,
                typeUtgift = Utgiftstype.PARKERING,
                opprettet = Instant.now()
            ).serialisertTilString(),
            id = null
        )

        lagreSvar(fnr, reisetilskuddSoknad.id, kvitteringSpm.id!!, svar)
    }

    @Test
    @Order(11)
    fun `Vi tester å sende inn søknaden før alle svar er besvart og får bad request`() {
        val reisetilskudd = hentSoknaderMetadata(fnr).first()
        sendSoknadMedResult(fnr, reisetilskudd.id)
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    @Test
    @Order(12)
    fun `Vi besvarer resten av spørsmålene`() {
        val reisetilskudd = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )
        SoknadBesvarer(reisetilskudd, this, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(FRAVAR_FOR_SYKMELDINGEN, "NEI")
            .besvarSporsmal(TILBAKE_I_ARBEID, "NEI")
            .besvarSporsmal(FERIE_V2, "NEI")
            .besvarSporsmal(PERMISJON_V2, "NEI")
            .besvarSporsmal(UTLAND_V2, "NEI")
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "NEI")
            .besvarSporsmal("JOBBET_DU_GRADERT_0", "NEI")
            .besvarSporsmal(ANDRE_INNTEKTSKILDER_V2, "NEI")
            .besvarSporsmal(TRANSPORT_TIL_DAGLIG, "NEI")
            .besvarSporsmal(REISE_MED_BIL, "NEI")
            .besvarSporsmal(UTBETALING, "JA")
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
    }

    @Test
    @Order(13)
    fun `Vi kan finne mottaker av søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val reisetilskudd = hentSoknaderMetadata(fnr).first()
        val mottaker = finnMottakerAvSoknad(reisetilskudd.id, fnr)
        mottaker.mottaker shouldBeEqualTo RSMottaker.ARBEIDSGIVER
    }

    @Test
    @Order(14)
    fun `Vi kan sende inn søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val reisetilskudd = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )
        val sendtSøknad = SoknadBesvarer(reisetilskudd, this, fnr)
            .sendSoknad()
        sendtSøknad.status shouldBeEqualTo RSSoknadstatus.SENDT
        sendtSøknad.sendtTilArbeidsgiverDato.shouldNotBeNull()
        sendtSøknad.sendtTilNAVDato.shouldBeNull()
    }

    @Test
    @Order(15)
    fun `Vi leser av alt som er produsert`() {
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 3)
    }

    @Test
    @Order(16)
    fun `Vi ettersender til NAV`() {
        val reisetilskudd = hentSoknaderMetadata(fnr).first()
        ettersendTilNav(reisetilskudd.id, fnr)
        val ettersendt = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        ettersendt.sendtNav.shouldNotBeNull()
        ettersendt.ettersending.`should be true`()
    }

    @Test
    @Order(17)
    fun `Vi prøver å ettersende til arbeidsgiver, men den er allerede sendt dit`() {
        val reisetilskudd = hentSoknaderMetadata(fnr).first()
        ettersendTilArbeidsgiver(reisetilskudd.id, fnr)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0)
    }

    @Test
    @Order(18)
    fun `Vi endrer i databasen og ettersender til arbeidsgiver`() {
        val reisetilskudd = hentSoknaderMetadata(fnr).first()

        namedParameterJdbcTemplate.update(
            "UPDATE SYKEPENGESOKNAD SET SENDT_ARBEIDSGIVER = null " +
                "WHERE SYKEPENGESOKNAD_UUID = :sykepengesoknadId",

            MapSqlParameterSource()
                .addValue("sykepengesoknadId", reisetilskudd.id)
        )

        ettersendTilArbeidsgiver(reisetilskudd.id, fnr)

        val ettersendt = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        ettersendt.sendtNav.shouldNotBeNull()
        ettersendt.sendtArbeidsgiver.shouldNotBeNull()
        ettersendt.ettersending.`should be true`()
    }

    @Test
    @Order(19)
    fun `Vi ombestemmer oss å svarer nei på reisetilskudd brukt`() {
        val soknad = hentSoknaderMetadata(fnr).first()
        val utkast = korrigerSoknad(soknad.id, fnr)
        val utkastMedSvar = SoknadBesvarer(utkast, this, fnr)
            .besvarSporsmal(BRUKTE_REISETILSKUDDET, "NEI", true, mutert = true)
            .rSSykepengesoknad

        utkastMedSvar
            .sporsmal!!
            .find { it.tag == BRUKTE_REISETILSKUDDET }!!
            .svar.first().verdi shouldBeEqualTo "NEI"
        assertThat(utkastMedSvar.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                FRAVAR_FOR_SYKMELDINGEN,
                TILBAKE_I_ARBEID,
                FERIE_V2,
                PERMISJON_V2,
                UTLAND_V2,
                "JOBBET_DU_GRADERT_0",
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER_V2,
                BRUKTE_REISETILSKUDDET,
                VAER_KLAR_OVER_AT,
                BEKREFT_OPPLYSNINGER
            )
        )
    }

    @Test
    @Order(20)
    fun `Vi sender inn den korrigerte søknaden`() {
        val reisetilskudd = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.UTKAST_TIL_KORRIGERING }.id,
            fnr = fnr
        )
        SoknadBesvarer(reisetilskudd, this, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(FRAVAR_FOR_SYKMELDINGEN, "NEI")
            .besvarSporsmal(TILBAKE_I_ARBEID, "NEI")
            .besvarSporsmal(FERIE_V2, "NEI")
            .besvarSporsmal(PERMISJON_V2, "NEI")
            .besvarSporsmal(UTLAND_V2, "NEI")
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "NEI")
            .besvarSporsmal("JOBBET_DU_GRADERT_0", "NEI")
            .besvarSporsmal(ANDRE_INNTEKTSKILDER_V2, "NEI")
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
            .sendSoknad()

        val sendtSøknad = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        sendtSøknad.status shouldBeEqualTo SoknadsstatusDTO.SENDT
        sendtSøknad.sendtNav.shouldNotBeNull()
        sendtSøknad.sendtArbeidsgiver.shouldNotBeNull()

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 5)
    }
}
