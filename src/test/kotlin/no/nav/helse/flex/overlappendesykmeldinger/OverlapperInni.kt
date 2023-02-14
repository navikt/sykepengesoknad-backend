package no.nav.helse.flex.overlappendesykmeldinger

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.repository.KlippMetrikkRepository
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class OverlapperInni : BaseTestClass() {

    @Autowired
    private lateinit var klippMetrikkRepository: KlippMetrikkRepository

    val fnr = "44444444444"
    val basisDato = LocalDate.now()

    @AfterEach
    fun cleanUp() {
        databaseReset.resetDatabase()
    }

    @Test
    fun `Overlapper inni fremtidig søknad, klippes`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisDato.plusDays(1),
                    tom = basisDato.plusDays(30),
                ),
            ),
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisDato.plusDays(10),
                    tom = basisDato.plusDays(20),
                ),
            ),
        )

        val klippmetrikker = klippMetrikkRepository.findAll().toList()
        klippmetrikker shouldHaveSize 1
        klippmetrikker[0].soknadstatus shouldBeEqualTo "FREMTIDIG"
        klippmetrikker[0].variant shouldBeEqualTo "SOKNAD_STARTER_INNI_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad shouldBeEqualTo "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet shouldBeEqualTo true

        val hentetViaRest = hentSoknaderMetadata(fnr).sortedBy { it.fom }
        hentetViaRest shouldHaveSize 3

        val soknadKlippStart = hentetViaRest[0]
        soknadKlippStart.fom shouldBeEqualTo basisDato.plusDays(1)
        soknadKlippStart.tom shouldBeEqualTo basisDato.plusDays(9)

        val soknadSomOverlappet = hentetViaRest[1]
        soknadSomOverlappet.fom shouldBeEqualTo basisDato.plusDays(10)
        soknadSomOverlappet.tom shouldBeEqualTo basisDato.plusDays(20)

        val soknadKlippSlutt = hentetViaRest[2]
        soknadKlippSlutt.fom shouldBeEqualTo basisDato.plusDays(21)
        soknadKlippSlutt.tom shouldBeEqualTo basisDato.plusDays(30)
    }

    @Test
    fun `Overlapper inni ny søknad, klippes ikke`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisDato.minusDays(30),
                    tom = basisDato.minusDays(1),
                ),
            ),
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisDato.minusDays(20),
                    tom = basisDato.minusDays(10),
                ),
            ),
        )

        val klippmetrikker = klippMetrikkRepository.findAll().toList()
        klippmetrikker shouldHaveSize 1
        klippmetrikker[0].soknadstatus shouldBeEqualTo "NY"
        klippmetrikker[0].variant shouldBeEqualTo "SOKNAD_STARTER_INNI_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad shouldBeEqualTo "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet shouldBeEqualTo false

        val hentetViaRest = hentSoknaderMetadata(fnr).sortedBy { it.fom }
        hentetViaRest shouldHaveSize 2

        val forsteSoknad = hentetViaRest[0]
        forsteSoknad.status shouldBeEqualTo RSSoknadstatus.NY
        forsteSoknad.fom shouldBeEqualTo basisDato.minusDays(30)
        forsteSoknad.tom shouldBeEqualTo basisDato.minusDays(1)

        val soknadSomOverlappet = hentetViaRest[1]
        soknadSomOverlappet.status shouldBeEqualTo RSSoknadstatus.NY
        soknadSomOverlappet.fom shouldBeEqualTo basisDato.minusDays(20)
        soknadSomOverlappet.tom shouldBeEqualTo basisDato.minusDays(10)
    }
}
