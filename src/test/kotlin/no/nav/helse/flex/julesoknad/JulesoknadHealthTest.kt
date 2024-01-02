package no.nav.helse.flex.julesoknad

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus.*
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.forskuttering.ForskutteringRepository
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.ReadinessState
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.MethodName::class)
class JulesoknadHealthTest : BaseTestClass() {
    @Autowired
    private lateinit var prosesserJulesoknadkandidat: JulesoknadCronJob

    @Autowired
    private lateinit var forskutteringRepository: ForskutteringRepository

    @Autowired
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    private final val fnr = "123456789"

    private final val nesteÅr = LocalDate.now().plusYears(1).year

    @BeforeEach
    fun setUp() {
        forskutteringRepository.deleteAll()
        flexSyketilfelleMockRestServiceServer.reset()
        databaseReset.resetDatabase()

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(nesteÅr, 12, 1),
                        tom = LocalDate.of(nesteÅr, 12, 15),
                    ),
            ),
        )
    }

    @Test
    fun `1 - Stanser når health ikke er ok`() {
        AvailabilityChangeEvent.publish(
            applicationEventPublisher,
            this,
            ReadinessState.REFUSING_TRAFFIC,
        )

        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        val soknader = hentSoknaderMetadata(fnr)
        soknader.shouldHaveSize(1)
        soknader.first().status.shouldBeEqualTo(FREMTIDIG)
    }

    @Test
    fun `2 - Fortsetter når healt er ok igjen`() {
        AvailabilityChangeEvent.publish(
            applicationEventPublisher,
            this,
            ReadinessState.ACCEPTING_TRAFFIC,
        )

        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)

        val soknader = hentSoknaderMetadata(fnr)
        soknader.shouldHaveSize(1)
        soknader.first().status.shouldBeEqualTo(NY)
    }
}
