package no.nav.helse.flex.overlappendesykmeldinger

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidsgiverperiode
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.repository.KlippMetrikkRepository
import no.nav.helse.flex.soknadsopprettelse.ARBEID_UNDERVEIS_100_PROSENT
import no.nav.helse.flex.soknadsopprettelse.FERIE_V2
import no.nav.helse.flex.soknadsopprettelse.JOBBET_DU_GRADERT
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.gradertSykmeldt
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
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
    val sykmeldingId = "1234"

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
        klippmetrikker[0].soknadstatus shouldBe "FREMTIDIG"
        klippmetrikker[0].variant shouldBe "SOKNAD_STARTER_INNI_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad shouldBe "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet shouldBe true

        val hentetViaRest = hentSoknaderMetadata(fnr).sortedBy { it.fom }
        hentetViaRest shouldHaveSize 3

        val soknadKlippStart = hentetViaRest[0]
        soknadKlippStart.fom shouldBe basisDato.plusDays(1)
        soknadKlippStart.tom shouldBe basisDato.plusDays(9)
        hentSoknad(soknadKlippStart.id, fnr).klippet shouldBe true

        val soknadSomOverlappet = hentetViaRest[1]
        soknadSomOverlappet.fom shouldBe basisDato.plusDays(10)
        soknadSomOverlappet.tom shouldBe basisDato.plusDays(20)
        hentSoknad(soknadSomOverlappet.id, fnr).klippet shouldBe false

        val soknadKlippSlutt = hentetViaRest[2]
        soknadKlippSlutt.fom shouldBe basisDato.plusDays(21)
        soknadKlippSlutt.tom shouldBe basisDato.plusDays(30)
        hentSoknad(soknadKlippSlutt.id, fnr).klippet shouldBe true
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
        klippmetrikker[0].soknadstatus shouldBe "NY"
        klippmetrikker[0].variant shouldBe "SOKNAD_STARTER_INNI_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad shouldBe "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet shouldBe true

        val hentetViaRest = hentSoknaderMetadata(fnr).sortedBy { it.fom }
        hentetViaRest shouldHaveSize 3

        val forsteSoknad =
            hentSoknad(
                soknadId = hentetViaRest[0].id,
                fnr = fnr,
            )
        forsteSoknad.status shouldBe RSSoknadstatus.NY
        forsteSoknad.fom shouldBe basisDato.minusDays(30)
        forsteSoknad.tom shouldBe basisDato.minusDays(21)
        hentSoknad(forsteSoknad.id, fnr).klippet shouldBe true

        val periodeSpmSok1 =
            forsteSoknad
                .getSporsmalMedTagOrNull(FERIE_V2)
                ?.undersporsmal
                ?.first()
        periodeSpmSok1?.min shouldBe basisDato.minusDays(30).toString()
        periodeSpmSok1?.max shouldBe basisDato.minusDays(21).toString()

        val soknadSomOverlappet = hentetViaRest[1]
        soknadSomOverlappet.status shouldBe RSSoknadstatus.NY
        soknadSomOverlappet.fom shouldBe basisDato.minusDays(20)
        soknadSomOverlappet.tom shouldBe basisDato.minusDays(10)
        hentSoknad(soknadSomOverlappet.id, fnr).klippet shouldBe false

        val sisteSoknad =
            hentSoknad(
                soknadId = hentetViaRest[2].id,
                fnr = fnr,
            )
        sisteSoknad.status shouldBe RSSoknadstatus.NY
        sisteSoknad.fom shouldBe basisDato.minusDays(9)
        sisteSoknad.tom shouldBe basisDato.minusDays(1)
        hentSoknad(sisteSoknad.id, fnr).klippet shouldBe true

        val periodeSpmSok3 =
            sisteSoknad
                .getSporsmalMedTagOrNull(FERIE_V2)
                ?.undersporsmal
                ?.first()
        periodeSpmSok3?.min shouldBe basisDato.minusDays(9).toString()
        periodeSpmSok3?.max shouldBe basisDato.minusDays(1).toString()
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
        klippmetrikker[0].soknadstatus shouldBe "FREMTIDIG"
        klippmetrikker[0].variant shouldBe "SYKMELDING_STARTER_FOR_SLUTTER_ETTER"
        klippmetrikker[0].endringIUforegrad shouldBe "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet shouldBe true

        val soknader = hentSoknader(fnr)
        soknader shouldHaveSize 1
        soknader[0].fom shouldBe basisDato.minusDays(10)
        soknader[0].tom shouldBe basisDato.plusDays(10)
    }

    @Test
    fun `Ny logikk basert på signaturDato erstatter søknad når signaturDato er nyere`() {
        val soknadFraSykmelding1 =
            sendSykmelding(
                gradertSykmelding50Prosent(
                    sykmeldingSkrevet = OffsetDateTime.parse("2026-01-05T15:55:02.661Z"),
                    signaturDato = OffsetDateTime.parse("2026-01-05T14:56:22.802Z"),
                ),
            ).single()
                .also {
                    it.status shouldBe SoknadsstatusDTO.NY
                    it.soknadsperioder!!.single().grad shouldBe 50
                }

        val soknaderFraSykmelding2 =
            sendSykmelding(
                sykmelding100Prosent(
                    sykmeldingId = sykmeldingId,
                    sykmeldingSkrevet = OffsetDateTime.parse("2026-01-05T09:31:24.445Z"),
                    signaturDato = OffsetDateTime.parse("2026-01-07T08:32:18.152Z"),
                ),
                forventaSoknader = 2,
            )

        soknaderFraSykmelding2.first { it.status == SoknadsstatusDTO.SLETTET }.also {
            it.id shouldBe soknadFraSykmelding1.id
            it.soknadsperioder!!.single().grad shouldBe 50
        }

        val nySoknad =
            soknaderFraSykmelding2.first { it.status == SoknadsstatusDTO.NY }.also {
                it.sykmeldingId shouldBe sykmeldingId
                it.soknadsperioder!!.single().grad shouldBe 100
            }

        val soknaderViaRest = hentSoknaderMetadata(fnr)
        soknaderViaRest shouldHaveSize 1
        soknaderViaRest.first().id shouldBe nySoknad.id
    }

    @Test
    fun `Første sykmelding mangler signaturDato, klipp baseres på sykmeldingSkrevet`() {
        val soknadFraSykmelding1 =
            sendSykmelding(
                gradertSykmelding50Prosent(
                    sykmeldingSkrevet = OffsetDateTime.parse("2026-01-05T10:00:00.000Z"),
                    signaturDato = null,
                ),
            ).single()
                .also {
                    it.status shouldBe SoknadsstatusDTO.NY
                    it.soknadsperioder!!.single().grad shouldBe 50
                }

        val soknaderFraSykmelding2 =
            sendSykmelding(
                sykmelding100Prosent(
                    sykmeldingId = sykmeldingId,
                    sykmeldingSkrevet = OffsetDateTime.parse("2026-01-06T10:00:00.000Z"),
                    signaturDato = OffsetDateTime.parse("2026-01-07T08:32:18.152Z"),
                ),
                forventaSoknader = 2,
            )

        soknaderFraSykmelding2.first { it.status == SoknadsstatusDTO.SLETTET }.also {
            it.id shouldBe soknadFraSykmelding1.id
            it.soknadsperioder!!.single().grad shouldBe 50
        }

        val nySoknad =
            soknaderFraSykmelding2.first { it.status == SoknadsstatusDTO.NY }.also {
                it.sykmeldingId shouldBe sykmeldingId
                it.soknadsperioder!!.single().grad shouldBe 100
            }

        val soknaderViaRest = hentSoknaderMetadata(fnr)
        soknaderViaRest shouldHaveSize 1
        soknaderViaRest.first().id shouldBe nySoknad.id
    }

    @Test
    fun `Andre sykmelding mangler signaturDato, klipp baseres på sykmeldingSkrevet`() {
        val soknadFraSykmelding1 =
            sendSykmelding(
                gradertSykmelding50Prosent(
                    sykmeldingSkrevet = OffsetDateTime.parse("2026-01-04T10:00:00.000Z"),
                    signaturDato = OffsetDateTime.parse("2026-01-05T14:56:22.802Z"),
                ),
            ).single()
                .also {
                    it.status shouldBe SoknadsstatusDTO.NY
                    it.soknadsperioder!!.single().grad shouldBe 50
                }

        val soknaderFraSykmelding2 =
            sendSykmelding(
                sykmelding100Prosent(
                    sykmeldingId = sykmeldingId,
                    sykmeldingSkrevet = OffsetDateTime.parse("2026-01-07T10:00:00.000Z"),
                    signaturDato = null,
                ),
                forventaSoknader = 2,
            )

        soknaderFraSykmelding2.first { it.status == SoknadsstatusDTO.SLETTET }.also {
            it.id shouldBe soknadFraSykmelding1.id
            it.soknadsperioder!!.single().grad shouldBe 50
        }

        val nySoknad =
            soknaderFraSykmelding2.first { it.status == SoknadsstatusDTO.NY }.also {
                it.sykmeldingId shouldBe sykmeldingId
                it.soknadsperioder!!.single().grad shouldBe 100
            }

        val soknaderViaRest = hentSoknaderMetadata(fnr)
        soknaderViaRest shouldHaveSize 1
        soknaderViaRest.first().id shouldBe nySoknad.id
    }

    @Test
    fun `Nyere sykmelding etter sendt soknad oppretter ny soknad uten å erstatte sendt`() {
        val førsteSoknad =
            sendSykmelding(
                gradertSykmelding50Prosent(
                    sykmeldingSkrevet = OffsetDateTime.parse("2026-01-05T15:55:02.661Z"),
                    signaturDato = OffsetDateTime.parse("2026-01-05T14:56:22.802Z"),
                ),
            ).single()

        val førsteSoknadHentet = hentSoknad(førsteSoknad.id, fnr)

        mockFlexSyketilfelleArbeidsgiverperiode(
            arbeidsgiverperiode =
                Arbeidsgiverperiode(
                    antallBrukteDager = 9,
                    oppbruktArbeidsgiverperiode = true,
                    arbeidsgiverPeriode = Periode(fom = førsteSoknadHentet.fom!!, tom = førsteSoknadHentet.tom!!),
                ),
        )

        SoknadBesvarer(førsteSoknadHentet, this@OverlapperInni, fnr)
            .standardSvar(ekskludert = listOf(ARBEID_UNDERVEIS_100_PROSENT))
            .besvarSporsmal(JOBBET_DU_GRADERT + "0", "NEI")
            .sendSoknad()

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)

        sendSykmelding(
            sykmelding100Prosent(
                sykmeldingSkrevet = OffsetDateTime.parse("2026-01-05T09:31:24.445Z"),
                signaturDato = OffsetDateTime.parse("2026-01-07T08:32:18.152Z"),
            ),
        ).single()
            .also {
                it.status shouldBe SoknadsstatusDTO.NY
            }

        val soknaderViaRest = hentSoknaderMetadata(fnr)
        soknaderViaRest shouldHaveSize 2
        soknaderViaRest.count { it.status == RSSoknadstatus.SENDT } shouldBe 1
        soknaderViaRest.count { it.status == RSSoknadstatus.NY } shouldBe 1
        soknaderViaRest.any { it.id == førsteSoknad.id } shouldBe true

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 3)
    }

    fun gradertSykmelding50Prosent(
        sykmeldingId: String = "sykmelding-1",
        sykmeldingSkrevet: OffsetDateTime = OffsetDateTime.parse("2026-01-05T15:55:02.661Z"),
        signaturDato: OffsetDateTime? = OffsetDateTime.parse("2026-01-05T14:56:22.802Z"),
    ): SykmeldingKafkaMessageDTO =
        sykmeldingKafkaMessage(
            fnr = fnr,
            sykmeldingId = sykmeldingId,
            sykmeldingsperioder = gradertSykmeldt(fom = LocalDate.of(2026, 1, 3), tom = LocalDate.of(2026, 1, 18), grad = 50),
            sykmeldingSkrevet = sykmeldingSkrevet,
            signaturDato = signaturDato,
            timestamp = OffsetDateTime.parse("2026-01-05T15:23:25.982Z"),
            syketilfelleStartDato = LocalDate.of(2026, 1, 1),
            arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "123456789", orgNavn = "Arbeidsgiver AS"),
            mottattTidspunkt = OffsetDateTime.parse("2026-01-05T15:05:09Z"),
            kontaktDato = LocalDate.of(2026, 1, 2),
        )

    fun sykmelding100Prosent(
        sykmeldingId: String = "sykmelding-2",
        sykmeldingSkrevet: OffsetDateTime = OffsetDateTime.parse("2026-01-05T09:31:24.445Z"),
        signaturDato: OffsetDateTime? = OffsetDateTime.parse("2026-01-07T08:32:18.152Z"),
    ): SykmeldingKafkaMessageDTO =
        sykmeldingKafkaMessage(
            fnr = fnr,
            sykmeldingId = sykmeldingId,
            sykmeldingsperioder = heltSykmeldt(fom = LocalDate.of(2026, 1, 3), tom = LocalDate.of(2026, 1, 18)),
            sykmeldingSkrevet = sykmeldingSkrevet,
            signaturDato = signaturDato,
            timestamp = OffsetDateTime.parse("2026-01-07T08:57:24.341Z"),
            syketilfelleStartDato = LocalDate.of(2026, 1, 1),
            arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "123456789", orgNavn = "Arbeidsgiver AS"),
            mottattTidspunkt = OffsetDateTime.parse("2026-01-07T08:42:08Z"),
            kontaktDato = LocalDate.of(2026, 1, 5),
        )
}
