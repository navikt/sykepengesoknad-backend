package no.nav.helse.flex.frisktilarbeid

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.medlemskap.tilPostgresJson
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.*

/**
 * Tester at vi kan lagre og hente vedtak om friskmeldt til arbeidsformidling fra databasen.
 */
class FriskTilArbeidRepositoryTest : FellesTestOppsett() {
    @Autowired
    private lateinit var friskTilArbeidRepository: FriskTilArbeidRepository

    @BeforeEach
    fun slettFraDatabase() {
        friskTilArbeidRepository.deleteAll()
    }

    @Test
    fun `Lagrer og henter vedtak`() {
        val verdierForLagring =
            lagVerdierForLagring(fnr = "11111111111", ignorerArbeidssokerregister = true).also {
                friskTilArbeidRepository.save(
                    it.vedtakStatus.tilDbRecord(it.uuid, it.key, it.ignorerArbeidssokerregister),
                )
            }

        val dbRecords = friskTilArbeidRepository.findAll() shouldHaveSize 1
        val dbRecord = dbRecords.first()

        dbRecord.id shouldNotBe null
        dbRecord.vedtakUuid shouldBe verdierForLagring.uuid
        dbRecord.key shouldBe verdierForLagring.key
        dbRecord.fnr shouldBe verdierForLagring.vedtakStatus.personident
        dbRecord.fom shouldBe verdierForLagring.vedtakStatus.fom
        dbRecord.tom shouldBe verdierForLagring.vedtakStatus.tom
        dbRecord.behandletStatus shouldBe BehandletStatus.NY
        dbRecord.behandletTidspunkt shouldBe null
        dbRecord.ignorerArbeidssokerregister shouldBe true

        val lagretJson = objectMapper.readTree(dbRecord.vedtak!!.value)
        val vedtakStatusJson = objectMapper.readTree(verdierForLagring.vedtakStatus.serialisertTilString())
        lagretJson shouldBe vedtakStatusJson
    }

    @Test
    fun `Finner vedtak som skal behandles`() {
        lagVerdierForLagring("11111111111").also {
            friskTilArbeidRepository.save(
                it.vedtakStatus.tilDbRecord(it.uuid, it.key),
            )
        }

        lagVerdierForLagring("22222222222").also {
            friskTilArbeidRepository.save(
                it.vedtakStatus.tilDbRecord(it.uuid, it.key, ignorerArbeidssokerregister = true),
            )
        }

        lagVerdierForLagring("33333333333").also {
            friskTilArbeidRepository.save(
                it.vedtakStatus.tilDbRecord(it.uuid, it.key, ignorerArbeidssokerregister = false),
            )
        }

        friskTilArbeidRepository.finnVedtakSomSkalBehandles(1).also {
            it.size shouldBe 1
            it[0].fnr shouldBe "11111111111"
            it[0].ignorerArbeidssokerregister shouldBe null
        }

        friskTilArbeidRepository.finnVedtakSomSkalBehandles(2).also {
            it.size shouldBe 2
            it[0].fnr shouldBe "11111111111"
            it[1].fnr shouldBe "22222222222"
            it[1].ignorerArbeidssokerregister shouldBe true
        }

        friskTilArbeidRepository.finnVedtakSomSkalBehandles(3).also {
            it.size shouldBe 3
            it[0].fnr shouldBe "11111111111"
            it[1].fnr shouldBe "22222222222"
            it[2].fnr shouldBe "33333333333"
            it[2].ignorerArbeidssokerregister shouldBe false
        }
    }

    private fun lagVerdierForLagring(
        fnr: String,
        ignorerArbeidssokerregister: Boolean = false,
    ): VerdierForLagring =
        VerdierForLagring(
            uuid = UUID.randomUUID().toString(),
            key = fnr.asProducerRecordKey(),
            vedtakStatus = lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET),
            ignorerArbeidssokerregister = ignorerArbeidssokerregister,
        )

    private fun FriskTilArbeidVedtakStatus.tilDbRecord(
        vedtakUuid: String,
        key: String,
        ignorerArbeidssokerregister: Boolean? = null,
    ): FriskTilArbeidVedtakDbRecord =
        FriskTilArbeidVedtakDbRecord(
            vedtakUuid = vedtakUuid,
            key = key,
            opprettet = Instant.now(),
            fnr = this.personident,
            fom = this.fom,
            tom = this.tom,
            vedtak = this.tilPostgresJson(),
            behandletStatus = BehandletStatus.NY,
            ignorerArbeidssokerregister = ignorerArbeidssokerregister,
        )

    private data class VerdierForLagring(
        val uuid: String,
        val key: String,
        val vedtakStatus: FriskTilArbeidVedtakStatus,
        val ignorerArbeidssokerregister: Boolean?,
    )
}
