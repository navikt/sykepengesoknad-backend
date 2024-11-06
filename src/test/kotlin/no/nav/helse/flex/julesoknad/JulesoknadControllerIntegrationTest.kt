package no.nav.helse.flex.julesoknad

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus.FREMTIDIG
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus.NY
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.hentProduserteRecords
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import java.time.Duration
import java.time.LocalDate

class JulesoknadControllerIntegrationTest : FellesTestOppsett() {
    @Autowired
    private lateinit var prosesserJulesoknadkandidat: JulesoknadCronJob

    private final val fnr = "123456789"

    private final val nesteÅr = LocalDate.now().plusYears(1).year

    @BeforeEach
    fun setUp() {
        flexSyketilfelleMockRestServiceServer.reset()
        databaseReset.resetDatabase()
    }

    @AfterEach
    fun hentKafkaSoknader() {
        sykepengesoknadKafkaConsumer.hentProduserteRecords()
    }

    @Test
    fun `15 dagers søknad blir markert som julesøknad`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(nesteÅr, 12, 1),
                        tom = LocalDate.of(nesteÅr, 12, 15),
                    ),
            ),
        )

        hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr,
        ).julesoknad shouldBe false

        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        await().atMost(Duration.ofSeconds(5)).until(
            { hentSoknaderMetadata(fnr) },
            { (it.size == 1 && it.first().status == NY) },
        )

        hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr,
        ).julesoknad shouldBe true
    }

    @Test
    fun `14 dagers søknad blir ikke markert som julesøknad`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(nesteÅr, 12, 1),
                        tom = LocalDate.of(nesteÅr, 12, 14),
                    ),
            ),
        )

        hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr,
        ).julesoknad shouldBe false

        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        val soknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        soknad.julesoknad shouldBe false
        soknad.status shouldBeEqualTo FREMTIDIG
    }
}
