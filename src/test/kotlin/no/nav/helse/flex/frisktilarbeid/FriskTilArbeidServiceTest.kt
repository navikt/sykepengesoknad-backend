package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.domain.Periode
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.*

class FriskTilArbeidServiceTest : FakesTestOppsett() {
    @Autowired
    private lateinit var fakeFriskTilArbeidRepository: FriskTilArbeidRepository

    @Autowired
    lateinit var friskTilArbeidService: FriskTilArbeidService

    @BeforeEach
    fun setup() {
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
    fun `Feiler ved lagring av to vedtak med samme fom og tom`() {
        lagFriskTilArbeidVedtakStatus(
            Periode(
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 7),
            ),
        )

        assertThrows<FriskTilArbeidVedtakStatusException> {
            lagFriskTilArbeidVedtakStatus(
                Periode(
                    fom = LocalDate.of(2024, 1, 1),
                    tom = LocalDate.of(2024, 1, 7),
                ),
            )
        }
    }

    @Test
    fun `Feiler ved lagring av nytt vedtak med fom før eksisterende vedtaks tom`() {
        lagFriskTilArbeidVedtakStatus(
            Periode(
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 7),
            ),
        )

        assertThrows<FriskTilArbeidVedtakStatusException> {
            lagFriskTilArbeidVedtakStatus(
                Periode(
                    fom = LocalDate.of(2024, 1, 5),
                    tom = LocalDate.of(2024, 1, 13),
                ),
            )
        }
    }

    @Test
    fun `Feiler ved lagring av nytt vedtak med tom etter eksisterende vedtaks fom`() {
        lagFriskTilArbeidVedtakStatus(
            Periode(
                fom = LocalDate.of(2024, 1, 5),
                tom = LocalDate.of(2024, 1, 13),
            ),
        )

        assertThrows<FriskTilArbeidVedtakStatusException> {
            lagFriskTilArbeidVedtakStatus(
                Periode(
                    fom = LocalDate.of(2024, 1, 1),
                    tom = LocalDate.of(2024, 1, 7),
                ),
            )
        }
    }

    @Test
    fun `Feiler ved lagring av nytt vedtak med fom lik eksisterende vedtaks tom`() {
        lagFriskTilArbeidVedtakStatus(
            Periode(
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 7),
            ),
        )

        assertThrows<FriskTilArbeidVedtakStatusException> {
            lagFriskTilArbeidVedtakStatus(
                Periode(
                    fom = LocalDate.of(2024, 1, 7),
                    tom = LocalDate.of(2024, 1, 14),
                ),
            )
        }
    }

    @Test
    fun `Feiler ved lagring av nytt vedtak med tom lik eksisterende vedtaks fom`() {
        lagFriskTilArbeidVedtakStatus(
            Periode(
                fom = LocalDate.of(2024, 1, 7),
                tom = LocalDate.of(2024, 1, 14),
            ),
        )

        assertThrows<FriskTilArbeidVedtakStatusException> {
            lagFriskTilArbeidVedtakStatus(
                Periode(
                    fom = LocalDate.of(2024, 1, 1),
                    tom = LocalDate.of(2024, 1, 7),
                ),
            )
        }
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
            it.find { it.fnr == "11111111111" }?.behandletStatus `should be equal to` BehandletStatus.BEHANDLET
            it.find { it.fnr == "22222222222" }?.behandletStatus `should be equal to` BehandletStatus.NY
        }
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
