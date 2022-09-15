package no.nav.helse.flex.overlappendesykmeldinger

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class OverlapperInni : BaseTestClass() {

    fun MockMvc.metrikker(): List<String> = this
        .perform(MockMvcRequestBuilders.get("/internal/prometheus"))
        .andExpect(MockMvcResultMatchers.status().isOk).andReturn().response.contentAsString.split("\n")

    @Test
    @Order(1)
    fun `Overlapper inni`() {
        val fnr = "44444444444"
        sendArbeidstakerSykmelding(
            fom = LocalDate.of(2025, 1, 6),
            tom = LocalDate.of(2025, 1, 19),
            fnr = fnr
        )
        sendArbeidstakerSykmelding(
            fom = LocalDate.of(2025, 1, 9),
            tom = LocalDate.of(2025, 1, 12),
            fnr = fnr
        )

        val hentetViaRest = hentSoknader(fnr)
        hentetViaRest shouldHaveSize 2

        mockMvc.metrikker()
            .filter { it.contains("overlapper_inni_perioder_total") }
            .first { !it.startsWith("#") }
            .shouldBeEqualTo("""overlapper_inni_perioder_total{perioder="3-4-7",type="info",} 1.0""")

        mockMvc.metrikker()
            .filter { it.contains("overlapper_inni_perioder_uten_helg_total") }
            .first { !it.startsWith("#") }
            .shouldBeEqualTo("""overlapper_inni_perioder_uten_helg_total{perioder="3-2-5",type="info",} 1.0""")

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
    }
}
