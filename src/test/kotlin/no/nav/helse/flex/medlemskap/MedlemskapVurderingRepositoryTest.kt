package no.nav.helse.flex.medlemskap

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.util.serialisertTilString
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MedlemskapVurderingRepositoryTest : BaseTestClass() {

    private val sykepengesoknadId = UUID.randomUUID().toString()

    @Autowired
    private lateinit var medlemskapVurderingRepository: MedlemskapVurderingRepository

    @AfterEach
    fun resetDatabase() {
        medlemskapVurderingRepository.deleteAll()
    }

    @Test
    fun `Lagrer og henter liste med spørsmål`() {
        val fom = LocalDate.of(2023, 1, 1)
        val tom = LocalDate.of(2023, 1, 1)
        val timestamp = LocalDateTime.of(2023, 1, 1, 1, 1, 1).toInstant(ZoneOffset.UTC)
        val svartid = 1000L

        medlemskapVurderingRepository.save(
            MedlemskapVurderingDbRecord(
                timestamp = timestamp,
                svartid = svartid,
                fnr = "fnr",
                fom = fom,
                tom = tom,
                svartype = "UAVKLART",
                sporsmal = listOf("En", "To").tilPostgresJson(),
                sykepengesoknadId = sykepengesoknadId
            )
        )

        val dbRecords = medlemskapVurderingRepository.findAll() shouldHaveSize 1
        val dbRecord = dbRecords.first()

        dbRecord.fnr `should be equal to` "fnr"
        dbRecord.fom `should be equal to` fom
        dbRecord.tom `should be equal to` tom
        dbRecord.timestamp shouldNotBe null
        dbRecord.svartid `should be equal to` svartid
        dbRecord.svartype `should be equal to` "UAVKLART"
        dbRecord.sporsmal.value!! `should be equal to` (listOf("En", "To").serialisertTilString())
        dbRecord.sykepengesoknadId `should be equal to` sykepengesoknadId
    }

    @Test
    fun `Lagrer og henter tom liste med spørsmål`() {
        val fom = LocalDate.of(2023, 1, 1)
        val tom = LocalDate.of(2023, 1, 1)
        val timestamp = LocalDateTime.of(2023, 1, 1, 1, 1, 1).toInstant(ZoneOffset.UTC)

        val svartid = 1000L
        medlemskapVurderingRepository.save(
            MedlemskapVurderingDbRecord(
                timestamp = timestamp,
                svartid = svartid,
                fnr = "fnr",
                fom = fom,
                tom = tom,
                svartype = "JA",
                sporsmal = emptyList<String>().tilPostgresJson(),
                sykepengesoknadId = sykepengesoknadId

            )
        )

        val dbRecords = medlemskapVurderingRepository.findAll() shouldHaveSize 1
        val dbRecord = dbRecords.first()

        dbRecord.fnr `should be equal to` "fnr"
        dbRecord.fom `should be equal to` fom
        dbRecord.tom `should be equal to` tom
        dbRecord.timestamp shouldNotBe null
        dbRecord.svartid `should be equal to` svartid
        dbRecord.svartype `should be equal to` "JA"
        dbRecord.sporsmal.value!!.`should be equal to`(emptyList<String>().serialisertTilString())
        dbRecord.sykepengesoknadId `should be equal to` sykepengesoknadId
    }
}
