package no.nav.helse.flex.frisktilarbeid.sporsmal

import no.nav.helse.flex.*
import no.nav.helse.flex.aktivering.SoknadAktivering
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.frisktilarbeid.*
import no.nav.helse.flex.frisktilarbeid.asProducerRecordKey
import no.nav.helse.flex.frisktilarbeid.lagFriskTilArbeidVedtakStatus
import no.nav.helse.flex.kafka.FRISKTILARBEID_TOPIC
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.util.serialisertTilString
import org.amshove.kluent.`should be equal to`
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FriskTilArbeidIntegrationMedSporsmalTest() : FakesTestOppsett() {
    @Autowired
    lateinit var friskTilArbeidConsumer: FriskTilArbeidConsumer

    @Autowired
    lateinit var friskTilArbeidRepository: FriskTilArbeidRepository

    @Autowired
    lateinit var friskTilArbeidService: FriskTilArbeidService

    @Autowired
    lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    @Autowired
    lateinit var aktivering: SoknadAktivering

    private val fnr = "11111111111"

    @Test
    @Order(1)
    fun `Mottar og lagrer VedtakStatusRecord med status FATTET`() {
        // To 14-dagersperioder og én 7-dagersperiode.
        val vedtaksperiode =
            Periode(
                fom = LocalDate.of(2025, 2, 3),
                tom = LocalDate.of(2025, 3, 9),
            )
        val fnr = fnr
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

        vedtakSomSkalBehandles.size `should be equal to` 1
        vedtakSomSkalBehandles.first().also {
            it.fnr `should be equal to` fnr
            it.behandletStatus `should be equal to` BehandletStatus.NY
            it.fom `should be equal to` vedtaksperiode.fom
            it.tom `should be equal to` vedtaksperiode.tom
        }
    }

    @Test
    @Order(2)
    fun `Oppretter søknad fra med status FREMTIDIG`() {
        friskTilArbeidService.behandleFriskTilArbeidVedtakStatus(1)

        friskTilArbeidRepository.finnVedtakSomSkalBehandles(1).size `should be equal to` 0

        val friskTilArbeidDbRecord =
            friskTilArbeidRepository.findAll().first().also {
                it.behandletStatus `should be equal to` BehandletStatus.BEHANDLET
            }

        sykepengesoknadRepository.findByFriskTilArbeidVedtakId(friskTilArbeidDbRecord.id!!).also {
            it.size `should be equal to` 3
            it.forEach {
                it.status `should be equal to` Soknadstatus.FREMTIDIG
                it.friskTilArbeidVedtakId `should be equal to` friskTilArbeidDbRecord.id
            }
        }
    }

    @Test
    @Order(3)
    fun `Aktiver den første søknaden`() {
        val eldsteSoknad = sykepengesoknadRepository.findAll().minByOrNull { it.fom!! }!!
        aktivering.aktiverSoknad(eldsteSoknad.sykepengesoknadUuid)

        val soknader = hentSoknader(fnr)
        soknader.size `should be equal to` 3
    }

    @Test
    @Order(4)
    fun `Sortering av tags er riktig`() {
        val soknad = hentSoknader(fnr).first { it.status == RSSoknadstatus.NY }
        soknad.sporsmal!!.map { it.tag } `should be equal to`
            listOf(
                ANSVARSERKLARING,
                FTA_JOBBSITUASJONEN_DIN,
                FTA_INNTEKT_UNDERVEIS,
                FTA_REISE_TIL_UTLANDET,
                TIL_SLUTT,
            )
    }

    @Test
    @Order(5)
    fun `Besvar alt`() {
        val soknad = hentSoknader(fnr).first { it.status == RSSoknadstatus.NY }
        SoknadBesvarer(rSSykepengesoknad = soknad, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_NEI, "CHECKED", false)
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_FORTSATT_ARBEIDSSOKER, "JA", true)
            .besvarSporsmal(FTA_INNTEKT_UNDERVEIS, "NEI")
            .besvarSporsmal(FTA_REISE_TIL_UTLANDET, "NEI")
            .oppsummering()
            .also {
                assertThat(it.muterteSoknaden).isFalse()
            }
            .sendSoknad()
    }
}
