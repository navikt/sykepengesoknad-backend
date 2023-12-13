package no.nav.helse.flex

import no.nav.helse.flex.cronjob.ReistetilskuddFixCronjob
import no.nav.helse.flex.mock.opprettNySoknadReisetilskudd
import no.nav.helse.flex.repository.SoknadLagrer
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class ReistilskuddBuggFixTest : BaseTestClass() {

    @Autowired
    lateinit var reisetilskuddFixCronjob: ReistetilskuddFixCronjob

    @Autowired
    lateinit var soknadLagrer: SoknadLagrer

    @Test
    fun `kjører ok`() {
        soknadLagrer.lagreSoknad(opprettNySoknadReisetilskudd(LocalDate.of(2023, 11, 25), false))
        soknadLagrer.lagreSoknad(opprettNySoknadReisetilskudd(LocalDate.of(2023, 11, 25), true))

        reisetilskuddFixCronjob.fixReistilskuddImpl() `should be equal to` 1
        reisetilskuddFixCronjob.fixReistilskuddImpl() `should be equal to` 0
    }
}
