package no.nav.helse.flex.reisetilskudd

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSporsmal
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSvar
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Kvittering
import no.nav.helse.flex.domain.Utgiftstype
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.lagreSvar
import no.nav.helse.flex.mockFlexSyketilfelleSykeforloep
import no.nav.helse.flex.slettSvar
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.STATUS_SENDT
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.*
import java.time.Instant
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class SlettKvitteringerIntegrasjonsTest : FellesTestOppsett() {
    private val fnr = "12345678900"

    @BeforeAll
    fun konfigurerUnleash() {
        fakeUnleash.resetAll()
    }

    @Test
    @Order(1)
    fun `Det finnes ingen søknader`() {
        val soknader = hentSoknaderMetadata(fnr)
        soknader.shouldBeEmpty()
    }

    @Test
    @Order(2)
    fun `Opprett en søkand med gradert reisetilskudd`() {
        val fom: LocalDate = LocalDate.of(2022, 1, 1)
        val tom: LocalDate = LocalDate.of(2022, 1, 6)

        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                statusEvent = STATUS_SENDT,
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "123454543", orgNavn = "Gatekjøkkenet"),
            )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId

        val sykmelding =
            skapArbeidsgiverSykmelding(
                sykmeldingId = sykmeldingId,
                fom = fom,
                tom = tom,
                type = PeriodetypeDTO.GRADERT,
                gradert = GradertDTO(50, true),
                reisetilskudd = false,
            )

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        Assertions.assertThat(soknader).hasSize(1)
        Assertions.assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.GRADERT_REISETILSKUDD)
    }

    @Test
    @Order(3)
    fun `Besvarer spørsmålet om at reisetilskudd ble brukt`() {
        val reisetilskudd =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        SoknadBesvarer(reisetilskudd, this, fnr)
            .besvarSporsmal(BRUKTE_REISETILSKUDDET, "JA", mutert = true)

        val reisetilskuddEtterSvar =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        reisetilskuddEtterSvar
            .sporsmal!!
            .find { it.tag == BRUKTE_REISETILSKUDDET }!!
            .svar
            .first()
            .verdi shouldBeEqualTo "JA"

        Assertions.assertThat(reisetilskuddEtterSvar.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                FERIE_V2,
                PERMISJON_V2,
                "JOBBET_DU_GRADERT_0",
                ANDRE_INNTEKTSKILDER_V2,
                OPPHOLD_UTENFOR_EOS,
                BRUKTE_REISETILSKUDDET,
                TRANSPORT_TIL_DAGLIG,
                REISE_MED_BIL,
                KVITTERINGER,
                UTBETALING,
                TIL_SLUTT,
            ),
        )
    }

    @Test
    @Order(4)
    fun `Lagrer to kvitteringer og får returnert id på lagret svar`() {
        val (soknadId, kvitteringSpm) = hentKvitteringSpm()

        val forsteSvar =
            RSSvar(
                verdi =
                    Kvittering(
                        blobId = "9a186e3c-aeeb-4566-a865-15aa9139d364",
                        belop = 100,
                        typeUtgift = Utgiftstype.PARKERING,
                        opprettet = Instant.now(),
                    ).serialisertTilString(),
                id = null,
            )

        val andreSvar =
            RSSvar(
                verdi =
                    Kvittering(
                        blobId = "1b186e3c-aeeb-4566-a865-15aa9139d365",
                        belop = 200,
                        typeUtgift = Utgiftstype.TAXI,
                        opprettet = Instant.now(),
                    ).serialisertTilString(),
                id = null,
            )

        val lagretForsteSvar = lagreSvar(fnr, soknadId, kvitteringSpm.id!!, forsteSvar)
        lagretForsteSvar.oppdatertSporsmal.svar
            .first()
            .id `should not be` null

        val lagretAndreSvar = lagreSvar(fnr, soknadId, kvitteringSpm.id!!, andreSvar)
        lagretAndreSvar.oppdatertSporsmal.svar
            .first()
            .id `should not be` null

        val (_, lagretSpm) = hentKvitteringSpm()
        lagretSpm.svar.size `should be` 2

        val forsteLagretSvar = lagretSpm.svar.first()
        val forsteKvittering: Kvittering = objectMapper.readValue(forsteLagretSvar.verdi)
        forsteKvittering.typeUtgift.`should be equal to`(Utgiftstype.PARKERING)

        val andreLagretSvar = lagretSpm.svar.drop(1).first()
        val andreKvittering: Kvittering = objectMapper.readValue(andreLagretSvar.verdi)
        andreKvittering.typeUtgift.`should be equal to`(Utgiftstype.TAXI)
    }

    @Test
    @Order(5)
    fun `Sletter begge kvitteringer uten å laste søknad på nytt`() {
        val (soknadId, lagretSpm) = hentKvitteringSpm()
        lagretSpm.svar.size `should be` 2

        val forsteSvar = lagretSpm.svar.first()
        val andreSvar = lagretSpm.svar.drop(1).first()

        slettSvar(fnr, soknadId, lagretSpm.id!!, forsteSvar.id!!)
        slettSvar(fnr, soknadId, lagretSpm.id!!, andreSvar.id!!)
    }

    private fun hentKvitteringSpm(): Pair<String, RSSporsmal> {
        val soknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        val kvitteringSpm = soknad.sporsmal!!.first { it.tag == KVITTERINGER }
        return Pair(soknad.id, kvitteringSpm)
    }
}
