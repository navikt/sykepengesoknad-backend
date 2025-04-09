package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.medlemskap.tilPostgresJson
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBe
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
        dbRecord.vedtakUuid `should be equal to` verdierForLagring.uuid
        dbRecord.key `should be equal to` verdierForLagring.key
        dbRecord.fnr `should be equal to` verdierForLagring.vedtakStatus.personident
        dbRecord.fom `should be equal to` verdierForLagring.vedtakStatus.fom
        dbRecord.tom `should be equal to` verdierForLagring.vedtakStatus.tom
        dbRecord.behandletStatus `should be equal to` BehandletStatus.NY
        dbRecord.behandletTidspunkt `should be equal to` null
        dbRecord.ignorerArbeidssokerregister `should be equal to` true

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
                it.vedtakStatus.tilDbRecord(it.uuid, it.key, ignorerArbeidssokerregister = true),
            )
        }

        lagVerdierForLagring("33333333333").also {
            friskTilArbeidRepository.save(
                it.vedtakStatus.tilDbRecord(it.uuid, it.key, ignorerArbeidssokerregister = false),
            )
        }

        friskTilArbeidRepository.finnVedtakSomSkalBehandles(1).also {
            it.size `should be equal to` 1
            it[0].fnr `should be equal to` "11111111111"
            it[0].ignorerArbeidssokerregister `should be equal to` null
        }

        friskTilArbeidRepository.finnVedtakSomSkalBehandles(2).also {
            it.size `should be equal to` 2
            it[0].fnr `should be equal to` "11111111111"
            it[1].fnr `should be equal to` "22222222222"
            it[1].ignorerArbeidssokerregister `should be equal to` true
        }

        friskTilArbeidRepository.finnVedtakSomSkalBehandles(3).also {
            it.size `should be equal to` 3
            it[0].fnr `should be equal to` "11111111111"
            it[1].fnr `should be equal to` "22222222222"
            it[2].fnr `should be equal to` "33333333333"
            it[2].ignorerArbeidssokerregister `should be equal to` false
        }
    }

    private fun lagVerdierForLagring(
        fnr: String,
        ignorerArbeidssokerregister: Boolean = false,
    ): VerdierForLagring {
        return VerdierForLagring(
            uuid = UUID.randomUUID().toString(),
            key = fnr.asProducerRecordKey(),
            vedtakStatus = lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET),
            ignorerArbeidssokerregister = ignorerArbeidssokerregister,
        )
    }

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
