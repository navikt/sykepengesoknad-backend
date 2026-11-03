package no.nav.helse.flex.frisktilarbeid.sporsmal

import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.frisktilarbeid.*
import no.nav.helse.flex.frisktilarbeid.asProducerRecordKey
import no.nav.helse.flex.frisktilarbeid.lagFriskTilArbeidVedtakStatus
import no.nav.helse.flex.kafka.FRISKTILARBEID_TOPIC
import no.nav.helse.flex.util.serialisertTilString
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.time.LocalDate

fun FakesTestOppsett.sendFriskTilArbeidVedtak(
    fnr: String,
    fom: LocalDate,
    tom: LocalDate,
): String {
    val vedtaksperiode =
        Periode(
            fom = fom,
            tom = tom,
        )
    val key = fnr.asProducerRecordKey()
    val friskTilArbeidVedtakStatus = lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET, vedtaksperiode)

    friskTilArbeidConsumer.listen(
        ConsumerRecord(
            FRISKTILARBEID_TOPIC,
            0,
            0,
            key,
            friskTilArbeidVedtakStatus.serialisertTilString(),
        ),
    ) { }

    val vedtakSomSkalBehandles = friskTilArbeidRepository.finnVedtakSomSkalBehandles(10)

    vedtakSomSkalBehandles.size shouldBe 1
    vedtakSomSkalBehandles.first().also {
        it.fnr shouldBe fnr
        it.behandletStatus shouldBe BehandletStatus.NY
        it.fom shouldBe vedtaksperiode.fom
        it.tom shouldBe vedtaksperiode.tom
    }
    return key
}
