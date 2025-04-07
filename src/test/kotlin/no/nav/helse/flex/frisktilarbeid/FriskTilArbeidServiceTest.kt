package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.frisktilarbeid.BehandletStatus.*
import no.nav.helse.flex.mockdispatcher.skapArbeidssokerperiodeResponse
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class FriskTilArbeidServiceTest : FakesTestOppsett() {
    @Autowired
    private lateinit var fakeFriskTilArbeidRepository: FriskTilArbeidRepository

    @Autowired
    lateinit var friskTilArbeidService: FriskTilArbeidService

    @Autowired
    lateinit var friskTilArbeidCronJob: FriskTilArbeidCronJob

    @BeforeEach
    fun slettFraDatabase() {
        fakeFriskTilArbeidRepository.deleteAll()
    }

    private val fnr = "11111111111"
    private val key = fnr.asProducerRecordKey()

    @Test
    fun `Lagrer vedtak med status FATTET`() {
        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            FriskTilArbeidVedtakStatusKafkaMelding(
                key = key,
                friskTilArbeidVedtakStatus = lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET),
            ),
        )

        fakeFriskTilArbeidRepository.findAll().toList() shouldHaveSize 1
    }

    @Test
    fun `Lagrer ikke vedtak med status FERDIG_BEHANDLET`() {
        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            FriskTilArbeidVedtakStatusKafkaMelding(
                key = key,
                friskTilArbeidVedtakStatus = lagFriskTilArbeidVedtakStatus(fnr, Status.FERDIG_BEHANDLET),
            ),
        )

        fakeFriskTilArbeidRepository.findAll().toList() shouldHaveSize 0
    }

    @Test
    fun `Lagrer ikke vedtak med statusAt før tidspunktForOvertakelse norsk tid`() {
        val statusAt = OffsetDateTime.of(LocalDateTime.of(2025, 3, 9, 22, 59, 0), ZoneOffset.UTC)

        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            FriskTilArbeidVedtakStatusKafkaMelding(
                key = key,
                friskTilArbeidVedtakStatus =
                    lagFriskTilArbeidVedtakStatus(
                        fnr,
                        Status.FATTET,
                    ).copy(statusAt = statusAt),
            ),
        )

        fakeFriskTilArbeidRepository.findAll().toList() shouldHaveSize 0
    }

    @Test
    fun `Lagrer ikke vedtak med statusAt etter tidspunktForOvertakelse norsk tid`() {
        val statusAt = OffsetDateTime.of(LocalDateTime.of(2025, 3, 9, 23, 1, 0), ZoneOffset.UTC)

        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            FriskTilArbeidVedtakStatusKafkaMelding(
                key = key,
                friskTilArbeidVedtakStatus =
                    lagFriskTilArbeidVedtakStatus(
                        fnr,
                        Status.FATTET,
                    ).copy(statusAt = statusAt),
            ),
        )

        fakeFriskTilArbeidRepository.findAll().toList() shouldHaveSize 1
    }

    @Test
    fun `Lagrer to perioder som ikke overlapper`() {
        lagFriskTilArbeidVedtakStatus(
            Periode(
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 7),
            ),
        )

        lagFriskTilArbeidVedtakStatus(
            Periode(
                fom = LocalDate.of(2024, 1, 8),
                tom = LocalDate.of(2024, 1, 15),
            ),
        )

        fakeFriskTilArbeidRepository.findAll().toList() shouldHaveSize 2
    }

    @Test
    fun `Lagrer to overlappende perioder for to forskjellige brukere`() {
        lagFriskTilArbeidVedtakStatus(
            Periode(
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 7),
            ),
        )

        lagFriskTilArbeidVedtakStatus(
            Periode(
                fom = LocalDate.of(2024, 1, 5),
                tom = LocalDate.of(2024, 1, 13),
            ),
            personident = "22222222222",
        )

        fakeFriskTilArbeidRepository.findAll().toList() shouldHaveSize 2
    }

    @Test
    fun `Overlapp ved lagring av to vedtak med samme fom og tom`() {
        lagFriskTilArbeidVedtakStatus(
            Periode(
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 7),
            ),
        )
        friskTilArbeidCronJob.schedulertStartBehandlingAvFriskTilArbeidVedtakStatus()

        lagFriskTilArbeidVedtakStatus(
            Periode(
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 7),
            ),
        )
        friskTilArbeidCronJob.schedulertStartBehandlingAvFriskTilArbeidVedtakStatus()

        val vedtakFraDb = fakeFriskTilArbeidRepository.findAll().toList()
        vedtakFraDb shouldHaveSize 2
        vedtakFraDb.map { it.behandletStatus }.toSet() `should be equal to` setOf(BEHANDLET, OVERLAPP)
    }

    @Test
    fun `Overlapp ved lagring av nytt vedtak med fom før eksisterende vedtaks tom`() {
        lagFriskTilArbeidVedtakStatus(
            Periode(
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 7),
            ),
        )
        friskTilArbeidCronJob.schedulertStartBehandlingAvFriskTilArbeidVedtakStatus()

        lagFriskTilArbeidVedtakStatus(
            Periode(
                fom = LocalDate.of(2024, 1, 5),
                tom = LocalDate.of(2024, 1, 13),
            ),
        )
        friskTilArbeidCronJob.schedulertStartBehandlingAvFriskTilArbeidVedtakStatus()

        val vedtakFraDb = fakeFriskTilArbeidRepository.findAll().toList()
        vedtakFraDb shouldHaveSize 2
        vedtakFraDb.map { it.behandletStatus }.toSet() `should be equal to` setOf(BEHANDLET, OVERLAPP)
    }

    @Test
    fun `Overlapp ved lagring av nytt vedtak med tom etter eksisterende vedtaks fom`() {
        lagFriskTilArbeidVedtakStatus(
            Periode(
                fom = LocalDate.of(2024, 1, 5),
                tom = LocalDate.of(2024, 1, 13),
            ),
        )
        friskTilArbeidCronJob.schedulertStartBehandlingAvFriskTilArbeidVedtakStatus()

        lagFriskTilArbeidVedtakStatus(
            Periode(
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 7),
            ),
        )
        friskTilArbeidCronJob.schedulertStartBehandlingAvFriskTilArbeidVedtakStatus()

        val vedtakFraDb = fakeFriskTilArbeidRepository.findAll().toList()
        vedtakFraDb shouldHaveSize 2
        vedtakFraDb.map { it.behandletStatus }.toSet() `should be equal to` setOf(BEHANDLET, OVERLAPP)
    }

    @Test
    fun `Overlapp ved lagring av nytt vedtak med fom lik eksisterende vedtaks tom`() {
        lagFriskTilArbeidVedtakStatus(
            Periode(
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 7),
            ),
        )
        friskTilArbeidCronJob.schedulertStartBehandlingAvFriskTilArbeidVedtakStatus()

        lagFriskTilArbeidVedtakStatus(
            Periode(
                fom = LocalDate.of(2024, 1, 7),
                tom = LocalDate.of(2024, 1, 14),
            ),
        )
        friskTilArbeidCronJob.schedulertStartBehandlingAvFriskTilArbeidVedtakStatus()

        val vedtakFraDb = fakeFriskTilArbeidRepository.findAll().toList()
        vedtakFraDb shouldHaveSize 2
        vedtakFraDb.map { it.behandletStatus }.toSet() `should be equal to` setOf(BEHANDLET, OVERLAPP)
    }

    @Test
    fun `Overlapp ved lagring av nytt vedtak med tom lik eksisterende vedtaks fom`() {
        lagFriskTilArbeidVedtakStatus(
            Periode(
                fom = LocalDate.of(2024, 1, 7),
                tom = LocalDate.of(2024, 1, 14),
            ),
        )
        friskTilArbeidCronJob.schedulertStartBehandlingAvFriskTilArbeidVedtakStatus()

        lagFriskTilArbeidVedtakStatus(
            Periode(
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 7),
            ),
        )
        friskTilArbeidCronJob.schedulertStartBehandlingAvFriskTilArbeidVedtakStatus()

        val vedtakFraDb = fakeFriskTilArbeidRepository.findAll().toList()
        vedtakFraDb shouldHaveSize 2
        vedtakFraDb.map { it.behandletStatus }.toSet() `should be equal to` setOf(BEHANDLET, OVERLAPP)
    }

    @Test
    fun `Person er ikke i arbeidssøker registeret`() {
        FellesTestOppsett.arbeidssokerregisterMockDispatcher.enqueue(
            MockResponse().setBody("[]").setResponseCode(200),
        )

        lagFriskTilArbeidVedtakStatus(
            Periode(
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 7),
            ),
        )
        friskTilArbeidCronJob.schedulertStartBehandlingAvFriskTilArbeidVedtakStatus()

        val vedtakFraDb = fakeFriskTilArbeidRepository.findAll().toList()
        vedtakFraDb shouldHaveSize 1
        vedtakFraDb.map { it.behandletStatus }.toSet() `should be equal to` setOf(INGEN_ARBEIDSSOKERPERIODE)
    }

    @Test
    fun `Person er avsluttet i arbeidssøker registeret`() {
        FellesTestOppsett.arbeidssokerregisterMockDispatcher.enqueue(
            MockResponse().setBody(listOf(skapArbeidssokerperiodeResponse(avsluttet = true)).serialisertTilString()).setResponseCode(200),
        )

        lagFriskTilArbeidVedtakStatus(
            Periode(
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 7),
            ),
        )
        friskTilArbeidCronJob.schedulertStartBehandlingAvFriskTilArbeidVedtakStatus()

        val vedtakFraDb = fakeFriskTilArbeidRepository.findAll().toList()
        vedtakFraDb shouldHaveSize 1
        vedtakFraDb.map { it.behandletStatus }.toSet() `should be equal to` setOf(SISTE_ARBEIDSSOKERPERIODE_AVSLUTTET)
    }

    @Test
    fun `Behandler ett av to vedtak`() {
        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            FriskTilArbeidVedtakStatusKafkaMelding(
                key = "11111111111".asProducerRecordKey(),
                friskTilArbeidVedtakStatus = lagFriskTilArbeidVedtakStatus("11111111111", Status.FATTET),
            ),
        )

        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            FriskTilArbeidVedtakStatusKafkaMelding(
                key = "22222222222".asProducerRecordKey(),
                friskTilArbeidVedtakStatus = lagFriskTilArbeidVedtakStatus("22222222222", Status.FATTET),
            ),
        )

        friskTilArbeidService.behandleFriskTilArbeidVedtakStatus(1)

        fakeFriskTilArbeidRepository.findAll().toList().also {
            it.find { it.fnr == "11111111111" }?.behandletStatus `should be equal to` BEHANDLET
            it.find { it.fnr == "22222222222" }?.behandletStatus `should be equal to` NY
        }
    }

    @Test
    fun `Behandler et vedtak andre gang for å generer søknad for dag som mangler`() {
        val periode = Periode(fom = LocalDate.of(2025, 4, 7), tom = LocalDate.of(2025, 5, 4))

        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            FriskTilArbeidVedtakStatusKafkaMelding(
                key = "33333333333".asProducerRecordKey(),
                friskTilArbeidVedtakStatus = lagFriskTilArbeidVedtakStatus("33333333333", Status.FATTET, periode),
            ),
        )

        val soknader = friskTilArbeidService.behandleFriskTilArbeidVedtakStatus(1).sortedBy { it.fom }

        soknader shouldHaveSize 2
        soknader[0].fom `should be equal to` LocalDate.of(2025, 4, 7)
        soknader[0].tom `should be equal to` LocalDate.of(2025, 4, 20)
        soknader[1].fom `should be equal to` LocalDate.of(2025, 4, 21)
        soknader[1].tom `should be equal to` LocalDate.of(2025, 5, 4)

        // Utvider vedtaket med én dag og setter status tilbake til NY for å få generert søknaden som mangler.
        friskTilArbeidRepository.findByFnrIn(listOf("33333333333")).single().also {
            friskTilArbeidRepository.save(it.copy(tom = LocalDate.of(2025, 5, 5), behandletStatus = NY))
        }

        val result = friskTilArbeidService.behandleFriskTilArbeidVedtakStatus(1)
        result.size `should be equal to` 1
        result.first().fom `should be equal to` LocalDate.of(2025, 5, 5)
        result.first().tom `should be equal to` LocalDate.of(2025, 5, 5)
    }

    private fun lagFriskTilArbeidVedtakStatus(
        vedtaksperiode: Periode,
        personident: String = fnr,
    ) {
        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            FriskTilArbeidVedtakStatusKafkaMelding(
                key = personident.asProducerRecordKey(),
                friskTilArbeidVedtakStatus =
                    lagFriskTilArbeidVedtakStatus(
                        personident,
                        Status.FATTET,
                        vedtaksperiode,
                    ),
            ),
        )
    }
}
