package no.nav.helse.flex.frisktilarbeid

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.aktivering.AktiveringJob
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.fakes.SoknadKafkaProducerFake
import no.nav.helse.flex.frisktilarbeid.sporsmal.sendFriskTilArbeidVedtak
import no.nav.helse.flex.frisktilarbeid.sporsmal.ventPaFriskTilArbeidSoknader
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.testutil.SoknadBesvarer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate

class FremtidigeSoknaderFjernesVedIkkeLengreFriskmeldtTest : FakesTestOppsett() {
    @Autowired
    private lateinit var friskTilArbeidService: FriskTilArbeidService

    @Autowired
    private lateinit var aktiveringJob: AktiveringJob

    @Autowired
    private lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    private val fnr = "11111111199"

    @BeforeEach
    @AfterEach
    fun nullstillTestdata() {
        sykepengesoknadRepository.deleteAll()
        friskTilArbeidRepository.deleteAll()
        SoknadKafkaProducerFake.records.clear()
    }

    @Test
    fun `Innsending med avregistrering sletter resterende søknader og avslutter vedtaket`() {
        val iDag = FriskTilArbeidTestTid.dato
        val vedtakFom = iDag.minusDays(15)
        val soknad =
            opprettVedtakMedEnAktivOgTreFremtidigeSoknader(
                fom = vedtakFom,
                tom = iDag.plusDays(35),
                aktiveringsdato = iDag,
            )
        val vedtakId = soknad.friskTilArbeidVedtakId.shouldNotBeNull()
        check(friskTilArbeidRepository.findById(vedtakId).get().behandletStatus == BehandletStatus.BEHANDLET)
        val forInnsending = Instant.now()

        sendSoknadMedAvregistrering(soknad, avregistrertDato = vedtakFom.plusDays(2))

        forventSlettedeSoknaderForVedtak(
            vedtakId,
            forventedeStartdatoer = listOf(vedtakFom.plusDays(14), vedtakFom.plusDays(28), vedtakFom.plusDays(42)),
        )
        hentSoknader(fnr).single().also {
            it.id shouldBe soknad.id
            it.status shouldBe RSSoknadstatus.SENDT
            it.fom shouldBe vedtakFom
        }
        friskTilArbeidRepository.findById(vedtakId).get().avsluttetTidspunkt.shouldNotBeNull().also {
            it.isBefore(forInnsending) shouldBe false
            it.isAfter(Instant.now()) shouldBe false
        }
    }

    private fun opprettVedtakMedEnAktivOgTreFremtidigeSoknader(
        fom: LocalDate,
        tom: LocalDate,
        aktiveringsdato: LocalDate,
    ): RSSykepengesoknad {
        sendFriskTilArbeidVedtak(fnr, fom, tom)
        friskTilArbeidService.behandleFriskTilArbeidVedtakStatus(BEHANDLE_ANTALL_FRISK_TIL_ARBEID_VEDTAK)
        aktiveringJob.bestillAktivering(aktiveringsdato)
        return ventPaFriskTilArbeidSoknader(
            fnr,
            listOf(RSSoknadstatus.NY, RSSoknadstatus.FREMTIDIG, RSSoknadstatus.FREMTIDIG, RSSoknadstatus.FREMTIDIG),
        ).first()
    }

    private fun sendSoknadMedAvregistrering(
        soknad: RSSykepengesoknad,
        avregistrertDato: LocalDate,
    ) {
        SoknadBesvarer(soknad, this, fnr)
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_NEI, "CHECKED", false)
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT, "NEI", false)
            .besvarSporsmal(
                FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_AVREGISTRERT_NAR,
                avregistrertDato.toString(),
                true,
                mutert = true,
            ).besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(FTA_INNTEKT_UNDERVEIS, "NEI")
            .besvarSporsmal(FTA_REISE_TIL_UTLANDET, "NEI")
            .oppsummering()
            .sendSoknad()
    }
}
