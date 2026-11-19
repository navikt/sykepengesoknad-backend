package no.nav.helse.flex.outbox

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration

class OutboxBackoffTest {
    @Test
    fun `ventetiden starter på ett sekund og dobles til maks 30 minutter`() {
        (1..13).map { beregnNesteOutboxForsøk(it).seconds } shouldBe
            listOf(1L, 2L, 4L, 8L, 16L, 32L, 64L, 128L, 256L, 512L, 1024L, 1800L, 1800L)
    }

    @Test
    fun `ventetiden holder seg på maks ved svært mange forsøk`() {
        beregnNesteOutboxForsøk(10_000) shouldBe Duration.ofMinutes(30)
    }
}
