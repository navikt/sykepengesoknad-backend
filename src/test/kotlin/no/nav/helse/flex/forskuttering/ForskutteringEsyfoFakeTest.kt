package no.nav.helse.flex.forskuttering

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.fakes.ForskutteringEsyfoOffsetRepositoryFake
import no.nav.helse.flex.fakes.ForskutteringEsyfoRepositoryFake
import no.nav.helse.flex.util.serialisertTilString
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

class ForskutteringEsyfoFakeTest : FakesTestOppsett() {
    @Autowired lateinit var listener: NarmestelederEsyfoListener

    @Autowired lateinit var repository: ForskutteringEsyfoRepositoryFake

    @Autowired lateinit var checkpoint: ForskutteringEsyfoCheckpoint

    @Autowired lateinit var offsetRepository: ForskutteringEsyfoOffsetRepositoryFake

    @AfterEach
    fun `Rydd fake-data etter hver test`() {
        repository.deleteAll()
        offsetRepository.deleteAll()
    }

    @Test
    fun `Lagrer og oppdaterer samme relasjon i en batch uten database`() {
        val melding = getNarmesteLederLeesah(UUID.randomUUID())
        var kvitteringer = 0
        listener.listen(
            listOf(
                record(0, melding.narmesteLederId, melding.serialisertTilString()),
                record(1, melding.narmesteLederId, melding.copy(arbeidsgiverForskutterer = false).serialisertTilString()),
            ),
        ) { kvitteringer++ }

        repository.count() shouldBe 1L
        repository.findByNarmesteLederId(melding.narmesteLederId).shouldNotBeNull().arbeidsgiverForskutterer shouldBe false
        checkpoint.lasOgHentOffset(NARMESTELEDER_ESYFO_LEESAH_TOPIC, 0) shouldBe 1L
        kvitteringer shouldBe 1
    }

    @Test
    fun `Tombstone beholder checkpoint og eldre offset gjenoppretter ikke slettet relasjon`() {
        val melding = getNarmesteLederLeesah(UUID.randomUUID())
        val opprett = record(0, melding.narmesteLederId, melding.serialisertTilString())
        listener.listen(listOf(opprett, record(1, melding.narmesteLederId, null))) { }

        listener.listen(listOf(opprett)) { }

        repository.findByNarmesteLederId(melding.narmesteLederId).shouldBeNull()
        checkpoint.lasOgHentOffset(NARMESTELEDER_ESYFO_LEESAH_TOPIC, 0) shouldBe 1L
    }

    @Test
    fun `Checkpoint er separat per partisjon`() {
        val første = getNarmesteLederLeesah(UUID.randomUUID())
        val andre = getNarmesteLederLeesah(UUID.randomUUID())

        listener.listen(
            listOf(
                record(5, første.narmesteLederId, første.serialisertTilString()),
                record(0, andre.narmesteLederId, andre.serialisertTilString(), partition = 1),
            ),
        ) { }

        repository.count() shouldBe 2L
        checkpoint.lasOgHentOffset(NARMESTELEDER_ESYFO_LEESAH_TOPIC, 0) shouldBe 5L
        checkpoint.lasOgHentOffset(NARMESTELEDER_ESYFO_LEESAH_TOPIC, 1) shouldBe 0L
    }

    private fun record(
        offset: Long,
        id: UUID,
        verdi: String?,
        partition: Int = 0,
    ) = ConsumerRecord(NARMESTELEDER_ESYFO_LEESAH_TOPIC, partition, offset, id.toString(), verdi)
}
