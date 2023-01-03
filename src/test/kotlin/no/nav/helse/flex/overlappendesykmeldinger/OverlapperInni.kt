package no.nav.helse.flex.overlappendesykmeldinger

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.repository.KlippMetrikkRepository
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.UUID

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class OverlapperInni : BaseTestClass() {

    @Autowired
    lateinit var klippMetrikkRepository: KlippMetrikkRepository

    @Test
    @Order(1)
    fun `Overlapper inni`() {
        klippMetrikkRepository.count() shouldBeEqualTo 0
        val fnr = "44444444444"
        val sykmeldingid2 = UUID.randomUUID().toString()
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2025, 1, 6),
                    tom = LocalDate.of(2025, 1, 19),
                ),
            ),
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingId = sykmeldingid2,
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2025, 1, 9),
                    tom = LocalDate.of(2025, 1, 12),
                ),
            ),
        )

        val hentetViaRest = hentSoknaderMetadata(fnr)
        hentetViaRest shouldHaveSize 2

        klippMetrikkRepository.count() shouldBeEqualTo 1
        val klippMetrikk = klippMetrikkRepository.findAll().iterator().next()

        klippMetrikk.soknadstatus `should be equal to` "FREMTIDIG"
        klippMetrikk.variant `should be equal to` "INNI"
        klippMetrikk.sykmeldingUuid `should be equal to` sykmeldingid2
    }
}
