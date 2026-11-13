package no.nav.helse.flex.julesoknad

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.repository.JulesoknadkandidatDAO
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.ReadinessState
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID

@TestMethodOrder(MethodOrderer.MethodName::class)
class JulesoknadHealthTest : FellesTestOppsett() {
    @Autowired
    private lateinit var prosesserJulesoknadkandidat: JulesoknadCronJob

    @Autowired
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    @Autowired
    private lateinit var julesoknadkandidatDAO: JulesoknadkandidatDAO

    @BeforeEach
    fun setUp() {
        flexSyketilfelleMockRestServiceServer.reset()
        databaseReset.resetDatabase()

        julesoknadkandidatDAO.lagreJulesoknadkandidat(UUID.randomUUID().toString())
    }

    @Test
    fun `1 - Stanser opprydding når health ikke er ok`() {
        AvailabilityChangeEvent.publish(
            applicationEventPublisher,
            this,
            ReadinessState.REFUSING_TRAFFIC,
        )

        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        julesoknadkandidatDAO.hentJulesoknadkandidater() shouldHaveSize 1
    }

    @Test
    fun `2 - Fortsetter opprydding når health er ok igjen`() {
        AvailabilityChangeEvent.publish(
            applicationEventPublisher,
            this,
            ReadinessState.ACCEPTING_TRAFFIC,
        )

        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()
        julesoknadkandidatDAO.hentJulesoknadkandidater().shouldBeEmpty()
    }
}
