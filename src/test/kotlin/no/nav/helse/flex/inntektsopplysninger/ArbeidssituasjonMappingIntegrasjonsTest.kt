package no.nav.helse.flex.inntektsopplysninger

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSArbeidssituasjon
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.hentProduserteRecords
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidssituasjonDTO
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.SporsmalSvar
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ArbeidssituasjonMappingIntegrasjonsTest : FellesTestOppsett() {
    @BeforeAll
    fun konfigurerUnleash() {
        fakeUnleash.resetAll()
    }

    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.hentProduserteRecords()
    }

    private val fom = LocalDate.of(2023, 1, 1)
    private val tom = LocalDate.of(2023, 1, 30)

    @Test
    fun `Næringsdrivende er fisker`() {
        val fnr = "99999999001"

        val soknader =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.FISKER,
                    fnr = fnr,
                ),
            )

        soknader shouldHaveSize 1
        val soknad = soknader.first()
        soknad.arbeidssituasjon shouldBeEqualTo ArbeidssituasjonDTO.FISKER

        hentSoknader(fnr).first().arbeidssituasjon shouldBeEqualTo RSArbeidssituasjon.FISKER

        sykepengesoknadRepository.findBySykepengesoknadUuid(soknad.id)!!.arbeidssituasjon shouldBeEqualTo Arbeidssituasjon.FISKER
    }

    @Test
    fun `Næringsdrivende er jordbruker`() {
        val fnr = "99999999002"

        val soknader =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.JORDBRUKER,
                    fnr = fnr,
                ),
            )

        soknader shouldHaveSize 1
        val soknad = soknader.first()
        soknad.arbeidssituasjon shouldBeEqualTo ArbeidssituasjonDTO.JORDBRUKER

        hentSoknader(fnr).first().arbeidssituasjon shouldBeEqualTo RSArbeidssituasjon.JORDBRUKER

        sykepengesoknadRepository.findBySykepengesoknadUuid(soknad.id)!!.arbeidssituasjon shouldBeEqualTo Arbeidssituasjon.JORDBRUKER
    }

    @Test
    fun `Arbeidstaker er fisker`() {
        val fnr = "99999999003"

        val sykmeldingKafkaMessage =
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                fnr = fnr,
            )

        val soknader =
            sendSykmelding(
                sykmeldingKafkaMessage.copy(
                    event =
                        sykmeldingKafkaMessage.event.copy(
                            brukerSvar =
                                sykmeldingKafkaMessage.event.brukerSvar!!.copy(
                                    arbeidssituasjon =
                                        SporsmalSvar(
                                            "Arbeidssituasjon",
                                            no.nav.syfo.sykmelding.kafka.model.Arbeidssituasjon.FISKER,
                                        ),
                                ),
                        ),
                ),
            )

        hentSoknader(fnr).first().arbeidssituasjon shouldBeEqualTo RSArbeidssituasjon.ARBEIDSTAKER

        soknader shouldHaveSize 1
        val soknad = soknader.first()
        soknad.arbeidssituasjon shouldBeEqualTo ArbeidssituasjonDTO.ARBEIDSTAKER

        sykepengesoknadRepository.findBySykepengesoknadUuid(soknad.id)!!.arbeidssituasjon shouldBeEqualTo Arbeidssituasjon.ARBEIDSTAKER
    }
}
