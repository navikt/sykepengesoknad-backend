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
