package no.nav.helse.flex.nyttarbeidsforhold

import no.nav.helse.flex.*
import no.nav.helse.flex.aktivering.SoknadAktivering
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import org.amshove.kluent.*
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class NyttArbeidsforholdFellesOppsett : FellesTestOppsett() {
    val fnr = "22222220001"
    final val basisdato = LocalDate.now().minusDays(1)

    @Autowired
    lateinit var soknadAktivering: SoknadAktivering

    @BeforeAll
    fun konfigurerUnleash() {
        fakeUnleash.resetAll()
        fakeUnleash.enable("sykepengesoknad-backend-tilkommen-inntekt")
    }

    @Test
    @Order(1)
    fun `Arbeidstakersøknader opprettes for en lang sykmelding`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(20),
                        tom = basisdato,
                    ),
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "123454543", orgNavn = "MATBUTIKKEN AS"),
            ),
        )
    }
}
