package no.nav.helse.flex.medlemskap

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.util.serialisertTilString
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBe
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

/**
 * Tester at vi kan lagre og hente spørsmål om medlemskap fra databasen.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MedlemskapVurderingRepositoryTest : FellesTestOppsett() {
    @Autowired
    private lateinit var medlemskapVurderingRepository: MedlemskapVurderingRepository

    @AfterEach
    fun slettFraDatabase() {
        medlemskapVurderingRepository.deleteAll()
    }

    private val sykepengesoknadId = UUID.randomUUID().toString()
    private val fnr = "11111111111"

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
                fnr = fnr,
                fom = fom,
                tom = tom,
                svartype = "UAVKLART",
                sporsmal = listOf("En", "To").tilPostgresJson(),
                sykepengesoknadId = sykepengesoknadId,
            ),
        )

        val dbRecords = medlemskapVurderingRepository.findAll() shouldHaveSize 1
        val dbRecord = dbRecords.first()

        dbRecord.fnr `should be equal to` "11111111111"
        dbRecord.fom `should be equal to` fom
        dbRecord.tom `should be equal to` tom
        dbRecord.timestamp shouldNotBe null
        dbRecord.svartid `should be equal to` svartid
        dbRecord.svartype `should be equal to` "UAVKLART"
        dbRecord.sporsmal!!.value `should be equal to` (listOf("En", "To").serialisertTilString())
        dbRecord.sykepengesoknadId `should be equal to` sykepengesoknadId
        dbRecord.kjentOppholdstillatelse shouldBe null
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
                fnr = fnr,
                fom = fom,
                tom = tom,
                svartype = "JA",
                sporsmal = emptyList<String>().tilPostgresJson(),
                sykepengesoknadId = sykepengesoknadId,
            ),
        )

        val dbRecords = medlemskapVurderingRepository.findAll() shouldHaveSize 1
        val dbRecord = dbRecords.first()

        dbRecord.fnr `should be equal to` "11111111111"
        dbRecord.fom `should be equal to` fom
        dbRecord.tom `should be equal to` tom
        dbRecord.timestamp shouldNotBe null
        dbRecord.svartid `should be equal to` svartid
        dbRecord.svartype `should be equal to` "JA"
        dbRecord.sporsmal!!.value.`should be equal to`(emptyList<String>().serialisertTilString())
        dbRecord.sykepengesoknadId `should be equal to` sykepengesoknadId
        dbRecord.kjentOppholdstillatelse shouldBe null
    }

    @Test
    fun `Lagrer null når liste med spørsmål er tom`() {
        val fom = LocalDate.of(2023, 1, 1)
        val tom = LocalDate.of(2023, 1, 1)
        val timestamp = LocalDateTime.of(2023, 1, 1, 1, 1, 1).toInstant(ZoneOffset.UTC)

        val svartid = 1000L
        medlemskapVurderingRepository.save(
            MedlemskapVurderingDbRecord(
                timestamp = timestamp,
                svartid = svartid,
                fnr = fnr,
                fom = fom,
                tom = tom,
                svartype = "JA",
                sykepengesoknadId = sykepengesoknadId,
            ),
        )

        val dbRecords = medlemskapVurderingRepository.findAll() shouldHaveSize 1
        val dbRecord = dbRecords.first()

        dbRecord.fnr `should be equal to` "11111111111"
        dbRecord.fom `should be equal to` fom
        dbRecord.tom `should be equal to` tom
        dbRecord.timestamp shouldNotBe null
        dbRecord.svartid `should be equal to` svartid
        dbRecord.svartype `should be equal to` "JA"
        dbRecord.sporsmal `should be equal to` null
        dbRecord.sykepengesoknadId `should be equal to` sykepengesoknadId
        dbRecord.kjentOppholdstillatelse shouldBe null
    }

    @Test
    fun `Lagrer kjentOppholdstillatelse med fom og tom`() {
        val fom = LocalDate.of(2023, 1, 1)
        val tom = LocalDate.of(2023, 1, 1)
        val timestamp = LocalDateTime.of(2023, 1, 1, 1, 1, 1).toInstant(ZoneOffset.UTC)

        val svartid = 1000L
        medlemskapVurderingRepository.save(
            MedlemskapVurderingDbRecord(
                timestamp = timestamp,
                svartid = svartid,
                fnr = fnr,
                fom = fom,
                tom = tom,
                svartype = "JA",
                sykepengesoknadId = sykepengesoknadId,
                kjentOppholdstillatelse =
                    KjentOppholdstillatelse(
                        LocalDate.of(2024, 1, 1),
                        LocalDate.of(2024, 1, 31),
                    ).tilPostgresJson(),
            ),
        )

        val dbRecords = medlemskapVurderingRepository.findAll() shouldHaveSize 1
        val dbRecord = dbRecords.first()

        dbRecord.fnr `should be equal to` "11111111111"
        dbRecord.fom `should be equal to` fom
        dbRecord.tom `should be equal to` tom
        dbRecord.timestamp shouldNotBe null
        dbRecord.svartid `should be equal to` svartid
        dbRecord.svartype `should be equal to` "JA"
        dbRecord.sporsmal `should be equal to` null
        dbRecord.sykepengesoknadId `should be equal to` sykepengesoknadId
        dbRecord.kjentOppholdstillatelse!!.value `should be equal to`
            KjentOppholdstillatelse(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 31),
            ).serialisertTilString()
    }

    @Test
    fun `Lagrer kjentOppholdstillatelse med fom men ikke tom`() {
        val fom = LocalDate.of(2023, 1, 1)
        val tom = LocalDate.of(2023, 1, 1)
        val timestamp = LocalDateTime.of(2023, 1, 1, 1, 1, 1).toInstant(ZoneOffset.UTC)

        val svartid = 1000L
        medlemskapVurderingRepository.save(
            MedlemskapVurderingDbRecord(
                timestamp = timestamp,
                svartid = svartid,
                fnr = fnr,
                fom = fom,
                tom = tom,
                svartype = "JA",
                sykepengesoknadId = sykepengesoknadId,
                kjentOppholdstillatelse = KjentOppholdstillatelse(LocalDate.of(2024, 1, 1)).tilPostgresJson(),
            ),
        )

        val dbRecords = medlemskapVurderingRepository.findAll() shouldHaveSize 1
        val dbRecord = dbRecords.first()

        dbRecord.fnr `should be equal to` "11111111111"
        dbRecord.fom `should be equal to` fom
        dbRecord.tom `should be equal to` tom
        dbRecord.timestamp shouldNotBe null
        dbRecord.svartid `should be equal to` svartid
        dbRecord.svartype `should be equal to` "JA"
        dbRecord.sporsmal `should be equal to` null
        dbRecord.sykepengesoknadId `should be equal to` sykepengesoknadId
        dbRecord.kjentOppholdstillatelse!!.value `should be equal to`
            KjentOppholdstillatelse(
                LocalDate.of(2024, 1, 1),
            ).serialisertTilString()
    }
}
