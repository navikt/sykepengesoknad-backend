package no.nav.helse.flex.util

import no.nav.helse.flex.config.JacksonConfig
import no.nav.helse.flex.domain.SelvstendigNaringsdrivendeInfo
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.module.kotlin.readValue

class ObjectMapperTest {
    private val jsonUtenPrimitiveFelter = """{"roller":[]}"""

    @Test
    fun `objectMapper gir standardverdi for manglende primitive felter`() {
        val info = objectMapper.readValue<SelvstendigNaringsdrivendeInfo>(jsonUtenPrimitiveFelter)

        info.erBarnepasser `should be equal to` false
        info.brukerHarOppgittForsikring `should be equal to` false
    }

    @Test
    fun `Spring sin JsonMapper gir standardverdi for manglende primitive felter`() {
        val builder = JsonMapper.builder().addModule(kotlinModule())
        JacksonConfig().flexJsonMapperBuilderCustomizer().customize(builder)

        val info = builder.build().readValue<SelvstendigNaringsdrivendeInfo>(jsonUtenPrimitiveFelter)

        info.erBarnepasser `should be equal to` false
    }
}
