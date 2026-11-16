package no.nav.helse.flex.frisktilarbeid

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.fakes.SoknadKafkaProducerFake
import no.nav.helse.flex.repository.SykepengesoknadRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired

abstract class FriskTilArbeidServiceTestBase : FakesTestOppsett() {
    @Autowired
    protected lateinit var friskTilArbeidService: FriskTilArbeidService

    @Autowired
    protected lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    protected val fnr = "11111111111"

    @BeforeEach
    @AfterEach
    fun slettDataOgKafkaHendelser() {
        sykepengesoknadRepository.deleteAll()
        friskTilArbeidRepository.deleteAll()
        SoknadKafkaProducerFake.records.clear()
    }

    protected fun lagreVedtak(
        periode: Periode = Periode(FriskTilArbeidTestTid.dato, FriskTilArbeidTestTid.dato.plusDays(13)),
        personident: String = fnr,
    ): String {
        val vedtak = lagFriskTilArbeidVedtakStatus(personident, Status.FATTET, periode)
        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            vedtakStatusMelding(vedtak, personident),
        )
        return friskTilArbeidRepository
            .findAll()
            .single {
                it.vedtak
                    .shouldNotBeNull()
                    .value
                    .shouldNotBeNull()
                    .tilFriskTilArbeidVedtakStatus()
                    .uuid == vedtak.uuid
            }.id
            .shouldNotBeNull()
    }

    protected fun vedtak(vedtakId: String): FriskTilArbeidVedtakDbRecord = friskTilArbeidRepository.findById(vedtakId).orElseThrow()

    protected fun vedtakStatusMelding(
        vedtakStatus: FriskTilArbeidVedtakStatus,
        personident: String = fnr,
        ignorerArbeidssokerregister: Boolean? = null,
    ) = FriskTilArbeidVedtakStatusKafkaMelding(
        key = personident.asProducerRecordKey(),
        friskTilArbeidVedtakStatus = vedtakStatus,
        ignorerArbeidssokerregister = ignorerArbeidssokerregister,
    )

    protected fun forventIngenSoknaderEllerKafkaHendelser() {
        sykepengesoknadRepository.findAll().shouldBeEmpty()
        SoknadKafkaProducerFake.records.shouldBeEmpty()
    }

    protected fun behandleVedtak() = friskTilArbeidService.behandleFriskTilArbeidVedtakStatus(BEHANDLE_ANTALL_FRISK_TIL_ARBEID_VEDTAK)
}
