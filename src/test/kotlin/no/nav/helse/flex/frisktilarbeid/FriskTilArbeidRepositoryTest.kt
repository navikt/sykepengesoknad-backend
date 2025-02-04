package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.medlemskap.tilPostgresJson
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.OffsetDateTime
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
            lagVerdierForLagring("11111111111").also {
                friskTilArbeidRepository.save(
                    it.vedtakStatus.tilDbRecord(it.uuid, it.key),
                )
            }

        val dbRecords = friskTilArbeidRepository.findAll() shouldHaveSize 1
        val dbRecord = dbRecords.first()

        dbRecord.id shouldNotBe null
        dbRecord.vedtakUuid `should be equal to` verdierForLagring.uuid
        dbRecord.key `should be equal to` verdierForLagring.key
        dbRecord.fnr `should be equal to` verdierForLagring.vedtakStatus.personident
        dbRecord.fom `should be equal to` verdierForLagring.vedtakStatus.fom
        dbRecord.tom `should be equal to` verdierForLagring.vedtakStatus.tom
        dbRecord.behandletStatus `should be equal to` BehandletStatus.NY
        dbRecord.behandletTidspunkt shouldBe null

        val lagretJson = objectMapper.readTree(dbRecord.vedtak!!.value)
        val vedtakStatusJson = objectMapper.readTree(verdierForLagring.vedtakStatus.serialisertTilString())
        lagretJson `should be equal to` vedtakStatusJson
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
                it.vedtakStatus.tilDbRecord(it.uuid, it.key),
            )
        }

        friskTilArbeidRepository.finnVedtakSomSkalBehandles(1).also {
            it.size `should be equal to` 1
            it[0].fnr `should be equal to` "11111111111"
        }

        friskTilArbeidRepository.finnVedtakSomSkalBehandles(2).also {
            it.size `should be equal to` 2
            it[0].fnr `should be equal to` "11111111111"
            it[1].fnr `should be equal to` "22222222222"
        }
    }

    private fun lagVerdierForLagring(
        fnr: String,
        fom: LocalDate? = LocalDate.now(),
        tom: LocalDate? = LocalDate.now(),
    ): VerdierForLagring {
        val uuid = UUID.randomUUID().toString()
        val key = fnr.asProducerRecordKey()
        val vedtakStatus = lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET, fom!! to tom!!)
        return VerdierForLagring(uuid, key, vedtakStatus)
    }

    private fun FriskTilArbeidVedtakStatus.tilDbRecord(
        vedtakUuid: String,
        key: String,
    ): FriskTilArbeidVedtakDbRecord =
        FriskTilArbeidVedtakDbRecord(
            vedtakUuid = vedtakUuid,
            key = key,
            opprettet = OffsetDateTime.now(),
            fnr = this.personident,
            fom = this.fom,
            tom = this.tom,
            vedtak = this.tilPostgresJson(),
            behandletStatus = BehandletStatus.NY,
        )

    private data class VerdierForLagring(
        val uuid: String,
        val key: String,
        val vedtakStatus: FriskTilArbeidVedtakStatus,
    )
}
