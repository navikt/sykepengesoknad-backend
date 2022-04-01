package no.nav.syfo.overlappendesykmeldinger

import no.nav.syfo.BaseTestClass
import no.nav.syfo.client.narmesteleder.Forskuttering
import no.nav.syfo.hentSoknader
import no.nav.syfo.mockArbeidsgiverForskutterer
import no.nav.syfo.ventPåRecords
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class OverlapperFor : BaseTestClass() {

    private final val basisdato = LocalDate.now()

    @BeforeEach
    fun setUp() {
        mockArbeidsgiverForskutterer(Forskuttering.JA)
    }

    @Test
    @Order(1)
    fun `Overlapper før`() {
        val fnr = "33333333333"
        sendArbeidstakerSykmelding(
            fom = basisdato.plusDays(5),
            tom = basisdato.plusDays(10),
            fnr = fnr
        )
        sendArbeidstakerSykmelding(
            fom = basisdato,
            tom = basisdato.plusDays(7),
            fnr = fnr
        )

        val hentetViaRest = hentSoknader(fnr)
        hentetViaRest shouldHaveSize 2

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
    }
}
