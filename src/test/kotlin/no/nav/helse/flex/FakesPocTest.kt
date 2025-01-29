package no.nav.helse.flex

import no.nav.helse.flex.medlemskap.MedlemskapVurderingDbRecord
import no.nav.helse.flex.medlemskap.MedlemskapVurderingRepository
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate

class FakesPocTest : FakesTestOppsett() {
    @Autowired
    private lateinit var medlemskapVurderingRepository: MedlemskapVurderingRepository

    @Test
    fun `fake repo funker`() {
        medlemskapVurderingRepository.count() shouldBeEqualTo 0
        val medlemskapVurderingDbRecord =
            MedlemskapVurderingDbRecord(
                timestamp = Instant.now(),
                svartid = 2323,
                fnr = "123123",
                fom = LocalDate.now(),
                tom = LocalDate.now(),
                svartype = "sdfrsdf",
                sporsmal = null,
                sykepengesoknadId = "234234",
                kjentOppholdstillatelse = null,
            )
        val lagret =
            medlemskapVurderingRepository.save(
                medlemskapVurderingDbRecord,
            )

        medlemskapVurderingRepository.count() shouldBeEqualTo 1
        medlemskapVurderingRepository.findById(lagret.id!!).get()
            .copy(id = null) shouldBeEqualTo medlemskapVurderingDbRecord
    }
}
