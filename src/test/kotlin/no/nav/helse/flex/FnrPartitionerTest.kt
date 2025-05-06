package no.nav.helse.flex

import no.nav.helse.flex.kafka.FnrPartitioner
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test

private const val ANTALL_PARTISJONER = 10
private const val REPETER_TEST_ANTALL_GANGER = 3

class FnrPartitionerTest {
    @Test
    fun `Alle meldinger med samme fnr skal sendes på samme partisjon`() {
        val syntetiskeFnr = hentSyntetiskeFnr()
        val partisjoneringsResultat: HashMap<String, MutableList<Int>?> = hashMapOf()

        repeat(REPETER_TEST_ANTALL_GANGER) {
            syntetiskeFnr.forEach {
                FnrPartitioner
                    .kalkulerPartisjon(it.toByteArray(), ANTALL_PARTISJONER)
                    .also { partisjon ->
                        partisjoneringsResultat.getOrPut(it) { mutableListOf() }?.add(partisjon)
                    }
            }
        }

        // Alle fnr skal ha blitt partisjonert til samme partisjon.
        partisjoneringsResultat.forEach {
            it.value!! shouldHaveSize REPETER_TEST_ANTALL_GANGER
            it.value!!.toSet().size shouldBe 1
        }

        // Alle partisjoner skal ha blitt brukt.
        partisjoneringsResultat
            .flatMap { listOf(it.value!!.first()) }
            .groupBy { it }
            .mapValues { it.value.size }
            .shouldHaveSize(ANTALL_PARTISJONER)
    }

    private fun hentSyntetiskeFnr() =
        this.javaClass.classLoader
            .getResourceAsStream("syntetiske-fnr.txt")!!
            .bufferedReader()
            .readLines()
}
