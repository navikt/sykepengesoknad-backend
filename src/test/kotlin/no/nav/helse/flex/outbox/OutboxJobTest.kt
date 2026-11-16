package no.nav.helse.flex.outbox

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

class OutboxJobTest : FellesTestOppsett() {
    @Autowired
    private lateinit var outboxService: OutboxServiceImpl

    @Autowired
    private lateinit var outboxDbRepository: OutboxDbRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    @AfterEach
    fun ryddOutbox() {
        outboxDbRepository.deleteAll()
    }

    @Test
    fun `prosesserer alle usendte rader for flere fnr i opprettet-rekkefølge per fnr`() {
        val soknader =
            listOf("11111111111", "22222222222", "11111111111", "22222222222").mapIndexed { index, fnr ->
                SykepengesoknadDTO(
                    id = UUID.randomUUID().toString(),
                    type = SoknadstypeDTO.ARBEIDSTAKERE,
                    status = if (index < 2) SoknadsstatusDTO.NY else SoknadsstatusDTO.SENDT,
                    fnr = fnr,
                )
            }

        val transactionTemplate = TransactionTemplate(transactionManager)
        soknader.forEach { soknad ->
            transactionTemplate.executeWithoutResult { outboxService.outboxSykepengesoknad(soknad) }
        }

        OutboxJob(outboxService).kjørJobb()

        outboxDbRepository.findAll().toList().also { rader ->
            rader shouldHaveSize 4
            rader.all { it.sendtTimestamp != null } shouldBe true
        }

        val mottatt = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 4).tilSoknader()
        mottatt.filter { it.fnr == "11111111111" }.map { it.id } shouldBe listOf(soknader[0].id, soknader[2].id)
        mottatt.filter { it.fnr == "22222222222" }.map { it.id } shouldBe listOf(soknader[1].id, soknader[3].id)
    }
}
