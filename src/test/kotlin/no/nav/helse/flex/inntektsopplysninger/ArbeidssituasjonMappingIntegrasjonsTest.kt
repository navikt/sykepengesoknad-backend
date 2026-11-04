package no.nav.helse.flex.inntektsopplysninger

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSArbeidssituasjon
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidssituasjonDTO
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.SporsmalSvar
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ArbeidssituasjonMappingIntegrasjonsTest : FellesTestOppsett() {
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

        hentSoknader(fnr).first().arbeidssituasjon shouldBe RSArbeidssituasjon.ARBEIDSTAKER

        soknader shouldHaveSize 1
        val soknad = soknader.first()
        soknad.arbeidssituasjon shouldBe ArbeidssituasjonDTO.ARBEIDSTAKER

        sykepengesoknadRepository.findBySykepengesoknadUuid(soknad.id)!!.arbeidssituasjon shouldBe Arbeidssituasjon.ARBEIDSTAKER
    }
}
