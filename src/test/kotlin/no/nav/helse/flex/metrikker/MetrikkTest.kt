package no.nav.helse.flex.metrikker

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.util.Metrikk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@TestMethodOrder(MethodOrderer.MethodName::class)
class MetrikkTest : BaseTestClass() {
    @Autowired
    private lateinit var metrikk: Metrikk

    @Autowired
    private lateinit var registry: MeterRegistry

    fun MockMvc.metrikker(): List<String> =
        this
            .perform(get("/internal/prometheus"))
            .andExpect(status().isOk).andReturn().response.contentAsString.split("\n")

    @Test
    fun `1 - teller en sendt søknad`() {
        metrikk.tellSoknadSendt(Soknadstype.ARBEIDSTAKERE)
        val sendtArbeidstakerLinjer =
            mockMvc.metrikker().filter { it.contains("syfosoknad_soknad_sendt") }.filter { it.contains("soknadstype=\"ARBEIDSTAKERE\"") }
        assertThat(sendtArbeidstakerLinjer).hasSize(1)

        val count = registry.counter("syfosoknad_soknad_sendt", Tags.of("type", "info", "soknadstype", "ARBEIDSTAKERE")).count()

        val counter = sendtArbeidstakerLinjer.first { !it.startsWith("#") }
        assertThat(counter).isEqualTo("syfosoknad_soknad_sendt_total{soknadstype=\"ARBEIDSTAKERE\",type=\"info\",} $count")
    }

    @Test
    fun `2 - teller enda en sendt søknad`() {
        metrikk.tellSoknadSendt(Soknadstype.ARBEIDSTAKERE)
        val sendtArbeidstakerLinjer =
            mockMvc.metrikker().filter { it.contains("syfosoknad_soknad_sendt") }.filter { it.contains("soknadstype=\"ARBEIDSTAKERE\"") }
        assertThat(sendtArbeidstakerLinjer).hasSize(1)

        val count = registry.counter("syfosoknad_soknad_sendt", Tags.of("type", "info", "soknadstype", "ARBEIDSTAKERE")).count()

        val counter = sendtArbeidstakerLinjer.first { !it.startsWith("#") }
        assertThat(counter).isEqualTo("syfosoknad_soknad_sendt_total{soknadstype=\"ARBEIDSTAKERE\",type=\"info\",} $count")
    }
}
