package no.nav.helse.flex.overlappendesykmeldinger

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
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
    }

    @Test
    fun `Overlapper eksakt, sletter den overlappede søknaden`() {
        val meldingerPaKafka = mutableListOf<SykepengesoknadDTO>()

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.plusDays(5),
                    tom = basisdato.plusDays(10),
                ),
            ),
        ).also { meldingerPaKafka.addAll(it) }
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.plusDays(5),
                    tom = basisdato.plusDays(10),
                ),
            ),
            forventaSoknader = 2,
        ).also { meldingerPaKafka.addAll(it) }

        meldingerPaKafka.shouldHaveSize(3)

        meldingerPaKafka[0].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[0].fom shouldBeEqualTo basisdato.plusDays(5)
        meldingerPaKafka[0].tom shouldBeEqualTo basisdato.plusDays(10)

        meldingerPaKafka[1].status shouldBeEqualTo SoknadsstatusDTO.SLETTET
        meldingerPaKafka[1].fom shouldBeEqualTo basisdato.plusDays(5)
        meldingerPaKafka[1].tom shouldBeEqualTo basisdato.plusDays(10)

        meldingerPaKafka[2].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[2].fom shouldBeEqualTo basisdato.plusDays(5)
        meldingerPaKafka[2].tom shouldBeEqualTo basisdato.plusDays(10)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        hentetViaRest shouldHaveSize 1

        hentetViaRest[0].fom shouldBeEqualTo basisdato.plusDays(5)
        hentetViaRest[0].tom shouldBeEqualTo basisdato.plusDays(10)
    }

    @Test
    fun `Overlapper fullstendig`() {
        val meldingerPaKafka = mutableListOf<SykepengesoknadDTO>()

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.plusDays(5),
                    tom = basisdato.plusDays(10),
                ),
            ),
        ).also { meldingerPaKafka.addAll(it) }
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato,
                    tom = basisdato.plusDays(15),
                ),
            ),
            forventaSoknader = 2,

        ).also { meldingerPaKafka.addAll(it) }

        meldingerPaKafka[0].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[0].fom shouldBeEqualTo basisdato.plusDays(5)
        meldingerPaKafka[0].tom shouldBeEqualTo basisdato.plusDays(10)

        meldingerPaKafka[1].status shouldBeEqualTo SoknadsstatusDTO.SLETTET
        meldingerPaKafka[1].fom shouldBeEqualTo basisdato.plusDays(5)
        meldingerPaKafka[1].tom shouldBeEqualTo basisdato.plusDays(10)

        meldingerPaKafka[2].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[2].fom shouldBeEqualTo basisdato
        meldingerPaKafka[2].tom shouldBeEqualTo basisdato.plusDays(15)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        hentetViaRest shouldHaveSize 1

        hentetViaRest[0].fom shouldBeEqualTo basisdato
        hentetViaRest[0].tom shouldBeEqualTo basisdato.plusDays(15)
    }

    @Test
    fun `Overlapper med samme fom og etter tom`() {
        val meldingerPaKafka = mutableListOf<SykepengesoknadDTO>()

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato,
                    tom = basisdato.plusDays(5),
                ),
            ),
        ).also { meldingerPaKafka.addAll(it) }
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato,
                    tom = basisdato.plusDays(10),
                ),
            ),
            forventaSoknader = 2,

        ).also { meldingerPaKafka.addAll(it) }

        meldingerPaKafka[0].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[0].fom shouldBeEqualTo basisdato
        meldingerPaKafka[0].tom shouldBeEqualTo basisdato.plusDays(5)

        meldingerPaKafka[1].status shouldBeEqualTo SoknadsstatusDTO.SLETTET
        meldingerPaKafka[1].fom shouldBeEqualTo basisdato
        meldingerPaKafka[1].tom shouldBeEqualTo basisdato.plusDays(5)

        meldingerPaKafka[2].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[2].fom shouldBeEqualTo basisdato
        meldingerPaKafka[2].tom shouldBeEqualTo basisdato.plusDays(10)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        hentetViaRest shouldHaveSize 1

        hentetViaRest[0].fom shouldBeEqualTo basisdato
        hentetViaRest[0].tom shouldBeEqualTo basisdato.plusDays(10)
    }
}
