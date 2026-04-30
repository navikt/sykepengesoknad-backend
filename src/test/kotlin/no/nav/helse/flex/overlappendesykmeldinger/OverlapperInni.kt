package no.nav.helse.flex.overlappendesykmeldinger

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.repository.KlippMetrikkRepository
import no.nav.helse.flex.soknadsopprettelse.FERIE_V2
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.gradertSykmeldt
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.OffsetDateTime

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class OverlapperInni : FellesTestOppsett() {
    @Autowired
    private lateinit var klippMetrikkRepository: KlippMetrikkRepository

    val fnr = "44444444444"
    val basisDato = LocalDate.now()

    @AfterEach
    fun cleanUp() {
        databaseReset.resetDatabase()
    }

    @Test
    fun `Overlapper inni fremtidig søknad, klippes`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisDato.plusDays(1),
                        tom = basisDato.plusDays(30),
                    ),
            ),
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisDato.plusDays(10),
                        tom = basisDato.plusDays(20),
                    ),
            ),
        )

        val klippmetrikker = klippMetrikkRepository.findAll().toList()
        klippmetrikker shouldHaveSize 1
        klippmetrikker[0].soknadstatus shouldBeEqualTo "FREMTIDIG"
        klippmetrikker[0].variant shouldBeEqualTo "SOKNAD_STARTER_INNI_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad shouldBeEqualTo "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet shouldBeEqualTo true

        val hentetViaRest = hentSoknaderMetadata(fnr).sortedBy { it.fom }
        hentetViaRest shouldHaveSize 3

        val soknadKlippStart = hentetViaRest[0]
        soknadKlippStart.fom shouldBeEqualTo basisDato.plusDays(1)
        soknadKlippStart.tom shouldBeEqualTo basisDato.plusDays(9)
        hentSoknad(soknadKlippStart.id, fnr).klippet shouldBeEqualTo true

        val soknadSomOverlappet = hentetViaRest[1]
        soknadSomOverlappet.fom shouldBeEqualTo basisDato.plusDays(10)
        soknadSomOverlappet.tom shouldBeEqualTo basisDato.plusDays(20)
        hentSoknad(soknadSomOverlappet.id, fnr).klippet shouldBeEqualTo false

        val soknadKlippSlutt = hentetViaRest[2]
        soknadKlippSlutt.fom shouldBeEqualTo basisDato.plusDays(21)
        soknadKlippSlutt.tom shouldBeEqualTo basisDato.plusDays(30)
        hentSoknad(soknadKlippSlutt.id, fnr).klippet shouldBeEqualTo true
    }

    @Test
    fun `Overlapper inni ny søknad, klippes`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisDato.minusDays(30),
                        tom = basisDato.minusDays(1),
                    ),
            ),
        )
        sendSykmelding(
            forventaSoknader = 3,
            sykmeldingKafkaMessage =
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = basisDato.minusDays(20),
                            tom = basisDato.minusDays(10),
                        ),
                ),
        )

        val klippmetrikker = klippMetrikkRepository.findAll().toList()
        klippmetrikker shouldHaveSize 1
        klippmetrikker[0].soknadstatus shouldBeEqualTo "NY"
        klippmetrikker[0].variant shouldBeEqualTo "SOKNAD_STARTER_INNI_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad shouldBeEqualTo "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet shouldBeEqualTo true

        val hentetViaRest = hentSoknaderMetadata(fnr).sortedBy { it.fom }
        hentetViaRest shouldHaveSize 3

        val forsteSoknad =
            hentSoknad(
                soknadId = hentetViaRest[0].id,
                fnr = fnr,
            )
        forsteSoknad.status shouldBeEqualTo RSSoknadstatus.NY
        forsteSoknad.fom shouldBeEqualTo basisDato.minusDays(30)
        forsteSoknad.tom shouldBeEqualTo basisDato.minusDays(21)
        hentSoknad(forsteSoknad.id, fnr).klippet shouldBeEqualTo true

        val periodeSpmSok1 =
            forsteSoknad.sporsmal
                ?.find { it.tag == FERIE_V2 }
                ?.undersporsmal
                ?.first()
        periodeSpmSok1?.min shouldBeEqualTo basisDato.minusDays(30).toString()
        periodeSpmSok1?.max shouldBeEqualTo basisDato.minusDays(21).toString()

        val soknadSomOverlappet = hentetViaRest[1]
        soknadSomOverlappet.status shouldBeEqualTo RSSoknadstatus.NY
        soknadSomOverlappet.fom shouldBeEqualTo basisDato.minusDays(20)
        soknadSomOverlappet.tom shouldBeEqualTo basisDato.minusDays(10)
        hentSoknad(soknadSomOverlappet.id, fnr).klippet shouldBeEqualTo false

        val sisteSoknad =
            hentSoknad(
                soknadId = hentetViaRest[2].id,
                fnr = fnr,
            )
        sisteSoknad.status shouldBeEqualTo RSSoknadstatus.NY
        sisteSoknad.fom shouldBeEqualTo basisDato.minusDays(9)
        sisteSoknad.tom shouldBeEqualTo basisDato.minusDays(1)
        hentSoknad(sisteSoknad.id, fnr).klippet shouldBeEqualTo true

        val periodeSpmSok3 =
            sisteSoknad.sporsmal
                ?.find { it.tag == FERIE_V2 }
                ?.undersporsmal
                ?.first()
        periodeSpmSok3?.min shouldBeEqualTo basisDato.minusDays(9).toString()
        periodeSpmSok3?.max shouldBeEqualTo basisDato.minusDays(1).toString()
    }

    @Test
    fun `Eldre sykmelding overlapper inni fremtidig søknad, klippes`() {
        val sykmeldingSkrevet = OffsetDateTime.now()

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisDato.minusDays(10),
                        tom = basisDato.plusDays(10),
                    ),
                sykmeldingSkrevet = sykmeldingSkrevet,
            ),
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisDato.minusDays(5),
                        tom = basisDato.plusDays(5),
                    ),
                sykmeldingSkrevet = sykmeldingSkrevet.minusHours(1),
            ),
            forventaSoknader = 0,
        )

        val klippmetrikker = klippMetrikkRepository.findAll().toList()
        klippmetrikker shouldHaveSize 1
        klippmetrikker[0].soknadstatus shouldBeEqualTo "FREMTIDIG"
        klippmetrikker[0].variant shouldBeEqualTo "SYKMELDING_STARTER_FOR_SLUTTER_ETTER"
        klippmetrikker[0].endringIUforegrad shouldBeEqualTo "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet shouldBeEqualTo true

        val soknader = hentSoknader(fnr)
        soknader shouldHaveSize 1
        soknader[0].fom shouldBeEqualTo basisDato.minusDays(10)
        soknader[0].tom shouldBeEqualTo basisDato.plusDays(10)
    }

    @Test
    fun `Case fra prod, klippes bort feil`() {
        val orgnummer = "123456789"
        val fom = LocalDate.of(2026, 1, 3)
        val tom = LocalDate.of(2026, 1, 18)
        val syketilfelleStartDato = LocalDate.of(2026, 1, 1)
        val arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = orgnummer, orgNavn = "Arbeidsgiver AS")

        // sykmelding-1 er en GRADERT 50% sykmelding med behandletTidspunkt 15:55
        val soknadFraForsteSykmelding =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingId = "sykmelding-1",
                    sykmeldingsperioder = gradertSykmeldt(fom = fom, tom = tom, grad = 50),
                    sykmeldingSkrevet = OffsetDateTime.parse("2026-01-05T15:55:02.661Z"),
                    signaturDato = OffsetDateTime.parse("2026-01-05T14:56:22.802Z"),
                    timestamp = OffsetDateTime.parse("2026-01-05T15:23:25.982Z"),
                    syketilfelleStartDato = syketilfelleStartDato,
                    arbeidsgiver = arbeidsgiver,
                    mottattTidspunkt = OffsetDateTime.parse("2026-01-05T15:05:09Z"),
                    kontaktDato = LocalDate.of(2026, 1, 2),
                ),
            ).also {
                it shouldHaveSize 1
                it[0].status shouldBeEqualTo SoknadsstatusDTO.NY
                it[0].fom shouldBeEqualTo fom
                it[0].tom shouldBeEqualTo tom
            }.first()

        // sykmelding-2 er en AKTIVITET_IKKE_MULIG sykmelding med behandletTidspunkt 09:31 (samme dag, men tidligere).
        // Selv om denne ble mottatt og sendt to dager senere (mottattTidspunkt 2026-01-07),
        // regner systemet den som "eldre" fordi behandletTidspunkt < sykmelding-1 sitt behandletTidspunkt.
        // Resultatet er at sykmelding-2 klippes bort og det opprettes ingen ny søknad – dette er feilen.
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingId = "sykmelding-2",
                sykmeldingsperioder = heltSykmeldt(fom = fom, tom = tom),
                sykmeldingSkrevet = OffsetDateTime.parse("2026-01-05T09:31:24.445Z"),
                signaturDato = OffsetDateTime.parse("2026-01-07T08:32:18.152Z"),
                timestamp = OffsetDateTime.parse("2026-01-07T08:57:24.341Z"),
                syketilfelleStartDato = syketilfelleStartDato,
                arbeidsgiver = arbeidsgiver,
                mottattTidspunkt = OffsetDateTime.parse("2026-01-07T08:42:08Z"),
                kontaktDato = LocalDate.of(2026, 1, 5),
            ),
            forventaSoknader = 0,
        )

        val klippmetrikker = klippMetrikkRepository.findAll().toList()
        klippmetrikker shouldHaveSize 1
        klippmetrikker[0].soknadstatus shouldBeEqualTo "NY"
        klippmetrikker[0].variant shouldBeEqualTo "SYKMELDING_STARTER_FOR_SLUTTER_ETTER"
        klippmetrikker[0].klippet shouldBeEqualTo true

        // Feilen: søknaden fra tilfeldig-id (GRADERT 50%) er fortsatt den eneste, tilfeldig-id-2 fikk ingen søknad
        val soknaderViaRest = hentSoknaderMetadata(fnr)
        soknaderViaRest shouldHaveSize 1
        soknaderViaRest[0].id shouldBeEqualTo soknadFraForsteSykmelding.id
    }
}
