package no.nav.helse.flex.overlappendesykmeldinger

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class OverlapperFor : BaseTestClass() {

    private final val basisdato = LocalDate.now()

    @Test
    fun `Fremtidig arbeidstakersøknad starter før og slutter inni, klippes`() {
        val fnr = "33333333333"
        sendArbeidstakerSykmelding(
            fom = basisdato.plusDays(5),
            tom = basisdato.plusDays(10),
            fnr = fnr
        )
        sendArbeidstakerSykmelding(
            fom = basisdato,
            tom = basisdato.plusDays(7),
            fnr = fnr
        )

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)

        val hentetViaRest = hentSoknader(fnr)
        hentetViaRest shouldHaveSize 2

        val klippetSoknad = hentetViaRest[0]
        klippetSoknad.fom shouldBeEqualTo basisdato.plusDays(8)
        klippetSoknad.tom shouldBeEqualTo basisdato.plusDays(10)

        val nyesteSoknad = hentetViaRest[1]
        nyesteSoknad.fom shouldBeEqualTo basisdato
        nyesteSoknad.tom shouldBeEqualTo basisdato.plusDays(7)
    }

    @Test
    fun `Fremtidig arbeidstakersøknad starter samtidig og slutter inni, klippes`() {
        val fnr = "44444444444"
        sendArbeidstakerSykmelding(
            fom = basisdato,
            tom = basisdato.plusDays(10),
            fnr = fnr
        )
        sendArbeidstakerSykmelding(
            fom = basisdato,
            tom = basisdato.plusDays(7),
            fnr = fnr
        )

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)

        val hentetViaRest = hentSoknader(fnr)
        hentetViaRest shouldHaveSize 2

        val klippetSoknad = hentetViaRest[0]
        klippetSoknad.fom shouldBeEqualTo basisdato.plusDays(8)
        klippetSoknad.tom shouldBeEqualTo basisdato.plusDays(10)
        klippetSoknad.soknadPerioder!! shouldHaveSize 1
        klippetSoknad.soknadPerioder!![0].fom shouldBeEqualTo basisdato.plusDays(8)
        klippetSoknad.soknadPerioder!![0].tom shouldBeEqualTo basisdato.plusDays(10)

        val nyesteSoknad = hentetViaRest[1]
        nyesteSoknad.fom shouldBeEqualTo basisdato
        nyesteSoknad.tom shouldBeEqualTo basisdato.plusDays(7)
        nyesteSoknad.soknadPerioder!! shouldHaveSize 1
        nyesteSoknad.soknadPerioder!![0].fom shouldBeEqualTo basisdato
        nyesteSoknad.soknadPerioder!![0].tom shouldBeEqualTo basisdato.plusDays(7)
    }

    @Test
    fun `Ny arbeidstakersøknad starter samtidig og slutter inni, klipper sykmelding`() {
        val fnr = "66666666666"

        sendArbeidstakerSykmelding(
            fom = basisdato.minusDays(5),
            tom = basisdato.minusDays(1),
            fnr = fnr
        )

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)

        sendArbeidstakerSykmelding(
            fom = basisdato.minusDays(10),
            tom = basisdato.minusDays(2),
            fnr = fnr
        )

        val soknad = sykepengesoknadKafkaConsumer
            .ventPåRecords(antall = 1)
            .tilSoknader()
            .first()

        soknad.fom shouldBeEqualTo basisdato.minusDays(10)
        soknad.tom shouldBeEqualTo basisdato.minusDays(6)
    }
}
