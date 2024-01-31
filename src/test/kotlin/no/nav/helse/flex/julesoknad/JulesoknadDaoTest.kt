package no.nav.helse.flex.julesoknad

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.repository.JulesoknadkandidatDAO
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.util.*

@TestMethodOrder(MethodOrderer.MethodName::class)
class JulesoknadDaoTest : FellesTestOppsett() {
    @Autowired
    private lateinit var julesoknadkandidatDAO: JulesoknadkandidatDAO

    @Test
    fun `Takler duplikater`() {
        val uuid = UUID.randomUUID().toString()
        julesoknadkandidatDAO.lagreJulesoknadkandidat(uuid)
        julesoknadkandidatDAO.lagreJulesoknadkandidat(uuid)
    }
}
