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

    @Test
    fun `Lagrer og henter vedtak`() {
        val friskTilArbeidVedtakStatus = lagFriskTilArbeidVedtakStatus("11111111111")

        friskTilArbeidRepository.save(
            friskTilArbeidVedtakStatus.tilDbRecord(Instant.now()),
        )

        val dbRecords = friskTilArbeidRepository.findAll() shouldHaveSize 1
        val dbRecord = dbRecords.first()

        dbRecord.id shouldNotBe null
        dbRecord.fnr `should be equal to` friskTilArbeidVedtakStatus.personident
        dbRecord.fom `should be equal to` friskTilArbeidVedtakStatus.fom
        dbRecord.tom `should be equal to` friskTilArbeidVedtakStatus.tom
        dbRecord.begrunnelse `should be equal to` "Begrunnelse"
        dbRecord.status `should be equal to` BehandletStatus.NY

        val lagretJson = objectMapper.readTree(dbRecord.vedtakStatus!!.value)
        val vedtakStatusJson = objectMapper.readTree(friskTilArbeidVedtakStatus.serialisertTilString())
        lagretJson `should be equal to` vedtakStatusJson
    }

    @Test
    fun `Finner vedtak som skal behandles`() {
        friskTilArbeidRepository.save(lagFriskTilArbeidVedtakStatus("11111111111").tilDbRecord(Instant.now()))
        friskTilArbeidRepository.save(lagFriskTilArbeidVedtakStatus("22222222222").tilDbRecord(Instant.now()))

        val ettVedtak = friskTilArbeidRepository.finnVedtakSomSkalBehandles(1)
        ettVedtak.size `should be equal to` 1
        ettVedtak.first().fnr `should be equal to` "11111111111"

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

    private fun lagFriskTilArbeidVedtakStatus(fnr: String): FriskTilArbeidVedtakStatus =
        FriskTilArbeidVedtakStatus(
            uuid = UUID.randomUUID().toString(),
            personident = fnr,
            begrunnelse = "Begrunnelse",
            fom = LocalDate.now(),
            tom = LocalDate.now(),
            status = Status.FATTET,
            statusAt = OffsetDateTime.now(),
            statusBy = "Test",
        )

    private fun FriskTilArbeidVedtakStatus.tilDbRecord(timestamp: Instant): FriskTilArbeidDbRecord =
        FriskTilArbeidDbRecord(
            timestamp = timestamp,
            fnr = this.personident,
            fom = this.fom,
            tom = this.tom,
            begrunnelse = this.begrunnelse,
            vedtakStatus = this.tilPostgresJson(),
            status = BehandletStatus.NY,
        )
}
