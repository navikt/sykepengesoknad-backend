package no.nav.helse.flex.frisktilarbeid

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.medlemskap.tilPostgresJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class FriskTilArbeidRepositoryTest : FellesTestOppsett() {
    @Autowired
    private lateinit var friskTilArbeidRepository: FriskTilArbeidRepository

    @BeforeEach
    fun slettFraDatabase() {
        databaseReset.resetDatabase()
    }

    @Test
    fun `Lagrer og henter vedtak med innhold og behandlingsstatus`() {
        val vedtak = lagFriskTilArbeidVedtakStatus("11111111111", Status.FATTET)
        val lagret = friskTilArbeidRepository.save(vedtak.tilDbRecord(ignorerArbeidssokerregister = true))

        val hentet = friskTilArbeidRepository.findAll().single()

        hentet.id.shouldNotBeNull() shouldBe lagret.id
        hentet.vedtakUuid shouldBe vedtak.uuid
        hentet.key shouldBe vedtak.personident.asProducerRecordKey()
        hentet.fnr shouldBe vedtak.personident
        hentet.fom shouldBe vedtak.fom
        hentet.tom shouldBe vedtak.tom
        hentet.opprettet shouldBe FriskTilArbeidTestTid.tidspunkt
        hentet.behandletStatus shouldBe BehandletStatus.NY
        hentet.behandletTidspunkt shouldBe null
        hentet.ignorerArbeidssokerregister shouldBe true
        hentet.vedtak
            .shouldNotBeNull()
            .value
            .shouldNotBeNull()
            .tilFriskTilArbeidVedtakStatus() shouldBe vedtak
    }

    @Test
    fun `Henter eldste ubehandlede vedtak først og begrenser antallet`() {
        val nyeste =
            lagreVedtak(
                fnr = "33333333333",
                opprettet = FriskTilArbeidTestTid.tidspunkt.plusSeconds(2),
                ignorerArbeidssokerregister = false,
            )
        val eldste = lagreVedtak(fnr = "11111111111", opprettet = FriskTilArbeidTestTid.tidspunkt)
        val mellomste =
            lagreVedtak(
                fnr = "22222222222",
                opprettet = FriskTilArbeidTestTid.tidspunkt.plusSeconds(1),
                ignorerArbeidssokerregister = true,
            )

        friskTilArbeidRepository.finnVedtakSomSkalBehandles(1).map { it.id } shouldBe listOf(eldste.id)
        friskTilArbeidRepository.finnVedtakSomSkalBehandles(2).map { it.id } shouldBe listOf(eldste.id, mellomste.id)
        val alle = friskTilArbeidRepository.finnVedtakSomSkalBehandles(3)
        alle.map { it.id } shouldBe listOf(eldste.id, mellomste.id, nyeste.id)
        alle.map { it.ignorerArbeidssokerregister } shouldBe listOf(null, true, false)
        friskTilArbeidRepository.finnVedtakSomSkalBehandles(0).shouldBeEmpty()
    }

    @ParameterizedTest(name = "Henter ikke vedtak med status {0}")
    @EnumSource(value = BehandletStatus::class, names = ["NY"], mode = EnumSource.Mode.EXCLUDE)
    fun `Henter bare vedtak med status NY`(status: BehandletStatus) {
        lagreVedtak(fnr = "11111111111", behandletStatus = status)

        friskTilArbeidRepository.finnVedtakSomSkalBehandles(10).shouldBeEmpty()
    }

    private fun lagreVedtak(
        fnr: String,
        opprettet: Instant = FriskTilArbeidTestTid.tidspunkt,
        ignorerArbeidssokerregister: Boolean? = null,
        behandletStatus: BehandletStatus = BehandletStatus.NY,
    ): FriskTilArbeidVedtakDbRecord =
        friskTilArbeidRepository.save(
            lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET)
                .tilDbRecord(opprettet, ignorerArbeidssokerregister)
                .copy(behandletStatus = behandletStatus),
        )

    private fun FriskTilArbeidVedtakStatus.tilDbRecord(
        opprettet: Instant = FriskTilArbeidTestTid.tidspunkt,
        ignorerArbeidssokerregister: Boolean? = null,
    ): FriskTilArbeidVedtakDbRecord =
        FriskTilArbeidVedtakDbRecord(
            vedtakUuid = uuid,
            key = personident.asProducerRecordKey(),
            opprettet = opprettet,
            fnr = personident,
            fom = fom,
            tom = tom,
            vedtak = tilPostgresJson(),
            behandletStatus = BehandletStatus.NY,
            ignorerArbeidssokerregister = ignorerArbeidssokerregister,
        )
}
