package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.medlemskap.tilPostgresJson
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

/**
 * Tester at vi kan lagre og hente vedtak om friskmeldt til arbeidsformidling fra databasen.
 */
class FriskTilArbeidRepositoryTest : FellesTestOppsett() {
    @Autowired
    private lateinit var friskTilArbeidRepository: FriskTilArbeidRepository

    @AfterEach
    fun slettFraDatabase() {
        friskTilArbeidRepository.deleteAll()
    }

    private val uuid = UUID.randomUUID().toString()
    private val fnr = "11111111111"

    @Test
    fun `Lagrer og henter`() {
        val timestamp = Instant.now()
        val fom = LocalDate.now()
        val tom = LocalDate.now()
        val statusAt = OffsetDateTime.now()

        val friskTilArbeidVedtakStatus =
            FriskTilArbeidVedtakStatus(
                uuid = uuid,
                personident = fnr,
                begrunnelse = "Begrunnelse",
                fom = fom,
                tom = tom,
                status = Status.FATTET,
                statusAt = statusAt,
                statusBy = "Test",
            )

        friskTilArbeidRepository.save(
            FriskTilArbeidDbRecord(
                timestamp = timestamp,
                fnr = friskTilArbeidVedtakStatus.personident,
                fom = friskTilArbeidVedtakStatus.fom,
                tom = friskTilArbeidVedtakStatus.tom,
                begrunnelse = friskTilArbeidVedtakStatus.begrunnelse,
                vedtakStatus = friskTilArbeidVedtakStatus.tilPostgresJson(),
                status = BehandletStatus.NY,
            ),
        )

        val dbRecords = friskTilArbeidRepository.findAll() shouldHaveSize 1
        val dbRecord = dbRecords.first()

        dbRecord.id shouldNotBe null
        dbRecord.fnr `should be equal to` fnr
        dbRecord.fom `should be equal to` fom
        dbRecord.tom `should be equal to` tom
        dbRecord.begrunnelse `should be equal to` "Begrunnelse"
        dbRecord.status `should be equal to` BehandletStatus.NY

        val lagretJson = objectMapper.readTree(dbRecord.vedtakStatus!!.value)
        val vedtakStatusJson = objectMapper.readTree(friskTilArbeidVedtakStatus.serialisertTilString())
        lagretJson `should be equal to` vedtakStatusJson
    }
}
