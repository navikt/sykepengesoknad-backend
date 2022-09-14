package no.nav.helse.flex.overlappendesykmeldinger

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.client.narmesteleder.Forskuttering
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.mockArbeidsgiverForskutterer
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class OverlapperFullstendig : BaseTestClass() {

    private final val basisdato = LocalDate.now()
    private final val fnr = "11111111111"

    @BeforeEach
    fun setUp() {
        databaseReset.resetDatabase()
        mockArbeidsgiverForskutterer(Forskuttering.JA)
    }

    @Test
    fun `Overlapper eksakt, sletter den overlappede søknaden`() {
        sendArbeidstakerSykmelding(
            fom = basisdato.plusDays(5),
            tom = basisdato.plusDays(10),
            fnr = fnr
        )
        sendArbeidstakerSykmelding(
            fom = basisdato.plusDays(5),
            tom = basisdato.plusDays(10),
            fnr = fnr
        )

        val hentetViaRest = hentSoknader(fnr)
        hentetViaRest shouldHaveSize 1

        hentetViaRest[0].fom shouldBeEqualTo basisdato.plusDays(5)
        hentetViaRest[0].tom shouldBeEqualTo basisdato.plusDays(10)

        val meldingerPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 3).tilSoknader()

        meldingerPaKafka[0].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[0].fom shouldBeEqualTo basisdato.plusDays(5)
        meldingerPaKafka[0].tom shouldBeEqualTo basisdato.plusDays(10)

        meldingerPaKafka[1].status shouldBeEqualTo SoknadsstatusDTO.SLETTET
        meldingerPaKafka[1].fom shouldBeEqualTo basisdato.plusDays(5)
        meldingerPaKafka[1].tom shouldBeEqualTo basisdato.plusDays(10)

        meldingerPaKafka[2].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[2].fom shouldBeEqualTo basisdato.plusDays(5)
        meldingerPaKafka[2].tom shouldBeEqualTo basisdato.plusDays(10)
    }

    @Test
    fun `Overlapper fullstendig`() {
        sendArbeidstakerSykmelding(
            fom = basisdato.plusDays(5),
            tom = basisdato.plusDays(10),
            fnr = fnr
        )
        sendArbeidstakerSykmelding(
            fom = basisdato,
            tom = basisdato.plusDays(15),
            fnr = fnr
        )

        val hentetViaRest = hentSoknader(fnr)
        hentetViaRest shouldHaveSize 1

        hentetViaRest[0].fom shouldBeEqualTo basisdato
        hentetViaRest[0].tom shouldBeEqualTo basisdato.plusDays(15)

        val meldingerPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 3).tilSoknader()

        meldingerPaKafka[0].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[0].fom shouldBeEqualTo basisdato.plusDays(5)
        meldingerPaKafka[0].tom shouldBeEqualTo basisdato.plusDays(10)

        meldingerPaKafka[1].status shouldBeEqualTo SoknadsstatusDTO.SLETTET
        meldingerPaKafka[1].fom shouldBeEqualTo basisdato.plusDays(5)
        meldingerPaKafka[1].tom shouldBeEqualTo basisdato.plusDays(10)

        meldingerPaKafka[2].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[2].fom shouldBeEqualTo basisdato
        meldingerPaKafka[2].tom shouldBeEqualTo basisdato.plusDays(15)
    }

    @Test
    fun `Overlapper med samme fom og etter tom`() {
        sendArbeidstakerSykmelding(
            fom = basisdato,
            tom = basisdato.plusDays(5),
            fnr = fnr
        )
        sendArbeidstakerSykmelding(
            fom = basisdato,
            tom = basisdato.plusDays(10),
            fnr = fnr
        )

        val hentetViaRest = hentSoknader(fnr)
        hentetViaRest shouldHaveSize 1

        hentetViaRest[0].fom shouldBeEqualTo basisdato
        hentetViaRest[0].tom shouldBeEqualTo basisdato.plusDays(10)

        val meldingerPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 3).tilSoknader()

        meldingerPaKafka[0].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[0].fom shouldBeEqualTo basisdato
        meldingerPaKafka[0].tom shouldBeEqualTo basisdato.plusDays(5)

        meldingerPaKafka[1].status shouldBeEqualTo SoknadsstatusDTO.SLETTET
        meldingerPaKafka[1].fom shouldBeEqualTo basisdato
        meldingerPaKafka[1].tom shouldBeEqualTo basisdato.plusDays(5)

        meldingerPaKafka[2].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[2].fom shouldBeEqualTo basisdato
        meldingerPaKafka[2].tom shouldBeEqualTo basisdato.plusDays(10)
    }
}
