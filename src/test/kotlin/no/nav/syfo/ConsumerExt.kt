package no.nav.syfo

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.juridiskvurdering.JuridiskVurderingKafkaDto
import no.nav.syfo.testutil.SubsumsjonAssertions.assertSubsumsjonsmelding
import no.nav.syfo.util.OBJECT_MAPPER
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.awaitility.Awaitility.await
import java.time.Duration

fun <K, V> Consumer<K, V>.subscribeHvisIkkeSubscribed(vararg topics: String) {
    if (this.subscription().isEmpty()) {
        this.subscribe(listOf(*topics))
    }
}

fun <K, V> Consumer<K, V>.hentProduserteRecords(duration: Duration = Duration.ofMillis(100)): List<ConsumerRecord<K, V>> {
    return this.poll(duration).also {
        this.commitSync()
    }.iterator().asSequence().toList()
}

fun <K, V> Consumer<K, V>.ventPåRecords(
    antall: Int,
    duration: Duration = Duration.ofMillis(1000),
): List<ConsumerRecord<K, V>> {

    val factory = if (antall == 0) {
        // Må vente fullt ut, ikke opp til en tid siden vi vil se at ingen blir produsert
        await().during(duration)
    } else {
        await().atMost(duration)
    }

    val alle = ArrayList<ConsumerRecord<K, V>>()
    factory.until {
        alle.addAll(this.hentProduserteRecords())
        alle.size == antall
    }
    return alle
}

fun List<ConsumerRecord<String, String>>.tilSoknader(): List<SykepengesoknadDTO> {
    return this.map { it.value().tilTilSykepengesoknad() }
}

fun List<ConsumerRecord<String, String>>.tilJuridiskVurdering(): List<JuridiskVurderingKafkaDto> {
    return this
        .map {
            assertSubsumsjonsmelding(it.value())
            it.value()
        }
        .map { OBJECT_MAPPER.readValue(it) }
}

private fun String.tilTilSykepengesoknad(): SykepengesoknadDTO {
    return OBJECT_MAPPER.readValue(this)
}
