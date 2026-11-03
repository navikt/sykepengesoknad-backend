package no.nav.helse.flex.medlemskap

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.medlemskap.KjentOppholdstillatelse
import no.nav.helse.flex.medlemskap.MedlemskapVurderingDbRecord
import no.nav.helse.flex.medlemskap.MedlemskapVurderingRepository
import no.nav.helse.flex.medlemskap.tilPostgresJson
import no.nav.helse.flex.util.serialisertTilString
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

        dbRecord.fnr shouldBe "11111111111"
        dbRecord.fom shouldBe fom
        dbRecord.tom shouldBe tom
        dbRecord.timestamp shouldNotBe null
        dbRecord.svartid shouldBe svartid
        dbRecord.svartype shouldBe "UAVKLART"
        dbRecord.sporsmal!!.value shouldBe (listOf("En", "To").serialisertTilString())
        dbRecord.sykepengesoknadId shouldBe sykepengesoknadId
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

        dbRecord.fnr shouldBe "11111111111"
        dbRecord.fom shouldBe fom
        dbRecord.tom shouldBe tom
        dbRecord.timestamp shouldNotBe null
        dbRecord.svartid shouldBe svartid
        dbRecord.svartype shouldBe "JA"
        dbRecord.sporsmal!!.value.shouldBe(emptyList<String>().serialisertTilString())
        dbRecord.sykepengesoknadId shouldBe sykepengesoknadId
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

        dbRecord.fnr shouldBe "11111111111"
        dbRecord.fom shouldBe fom
        dbRecord.tom shouldBe tom
        dbRecord.timestamp shouldNotBe null
        dbRecord.svartid shouldBe svartid
        dbRecord.svartype shouldBe "JA"
        dbRecord.sporsmal shouldBe null
        dbRecord.sykepengesoknadId shouldBe sykepengesoknadId
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

        dbRecord.fnr shouldBe "11111111111"
        dbRecord.fom shouldBe fom
        dbRecord.tom shouldBe tom
        dbRecord.timestamp shouldNotBe null
        dbRecord.svartid shouldBe svartid
        dbRecord.svartype shouldBe "JA"
        dbRecord.sporsmal shouldBe null
        dbRecord.sykepengesoknadId shouldBe sykepengesoknadId
        dbRecord.kjentOppholdstillatelse!!.value shouldBe
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

        dbRecord.fnr shouldBe "11111111111"
        dbRecord.fom shouldBe fom
        dbRecord.tom shouldBe tom
        dbRecord.timestamp shouldNotBe null
        dbRecord.svartid shouldBe svartid
        dbRecord.svartype shouldBe "JA"
        dbRecord.sporsmal shouldBe null
        dbRecord.sykepengesoknadId shouldBe sykepengesoknadId
        dbRecord.kjentOppholdstillatelse!!.value shouldBe
            KjentOppholdstillatelse(
                LocalDate.of(2024, 1, 1),
            ).serialisertTilString()
    }
}
