package no.nav.helse.flex.frisktilarbeid

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class FriskTilArbeidSoknadIntegrationTest : FellesTestOppsett() {
    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var friskTilArbeidRepository: FriskTilArbeidRepository

    @Autowired
    private lateinit var soknadProducer: SoknadProducer

    private val fnr = "11111111111"

    @BeforeEach
    @AfterEach
    fun slettDatabase() {
        databaseReset.resetDatabase()
    }

    @Test
    fun `Lagrer vedtakskoblingen og returnerer den i søknad og metadata`() {
        val soknad = lagSoknadMedVedtak()

        val lagret = sykepengesoknadDAO.lagreSykepengesoknad(soknad)

        lagret.friskTilArbeidVedtakId shouldBe soknad.friskTilArbeidVedtakId
        hentSoknaderMetadata(fnr).single().also {
            it.id shouldBe soknad.id
            it.friskTilArbeidVedtakId shouldBe soknad.friskTilArbeidVedtakId
        }
        hentSoknader(fnr).single().also {
            it.id shouldBe soknad.id
            it.friskTilArbeidVedtakId shouldBe soknad.friskTilArbeidVedtakId
            it.fom shouldBe soknad.fom
            it.tom shouldBe soknad.tom
        }
    }

    @Test
    fun `Publiserer søknad med vedtakskobling på Kafka`() {
        val soknad = lagSoknadMedVedtak()
        sykepengesoknadDAO.lagreSykepengesoknad(soknad)

        soknadProducer.soknadEvent(soknad)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().single().also {
            it.id shouldBe soknad.id
            it.fnr shouldBe fnr
            it.status shouldBe SoknadsstatusDTO.FREMTIDIG
            it.type shouldBe SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING
            it.friskTilArbeidVedtakId shouldBe soknad.friskTilArbeidVedtakId
        }
    }

    private fun lagSoknadMedVedtak(): Sykepengesoknad {
        val vedtak =
            friskTilArbeidRepository.save(
                lagFriskTilArbeidVedtakDbRecord(lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET))
                    .copy(behandletStatus = BehandletStatus.NY),
            )
        return lagFriskTilArbeidSoknad(
            fnr = fnr,
            friskTilArbeidVedtakId = vedtak.id.shouldNotBeNull(),
            soknadStatus = Soknadstatus.FREMTIDIG,
            fom = vedtak.fom,
            tom = vedtak.tom,
        ).copy(sykmeldingId = null, startSykeforlop = null, forstegangssoknad = null)
    }
}
