package no.nav.helse.flex.repository

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadOppholdUtland
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SoknadLagrerTest : BaseTestClass() {

    @Autowired
    private lateinit var soknadLagrer: SoknadLagrer

    @AfterEach
    fun `Vi resetter databasen etter hver test`() {
        databaseReset.resetDatabase()
    }

    @Test
    fun `Vi håndterer duplikater`() {
        val soknad = settOppSoknadOppholdUtland("12323234231")
        soknadLagrer.lagreSoknad(soknad)
        soknadLagrer.lagreSoknad(soknad)
    }
}
