package no.nav.helse.flex.nyttarbeidsforhold

import no.nav.helse.flex.*
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import org.amshove.kluent.*
import org.junit.jupiter.api.*
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class NyttArbeidsforholdInnenforAgpTest : FellesTestOppsett() {
    val fnr = "22222220001"
    final val basisdato = LocalDate.of(2024, 9, 15)

    @BeforeAll
    fun konfigurerUnleash() {
        fakeUnleash.resetAll()
        fakeUnleash.enable("sykepengesoknad-backend-tilkommen-inntekt")
    }

    @Test
    @Order(1)
    fun `Arbeidstakersøknader opprettes for en ti dagers sykmelding`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(11),
                        tom = basisdato,
                    ),
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "123454543", orgNavn = "MATBUTIKKEN AS"),
            ),
        )
    }

    @Test
    @Order(2)
    fun `Har ikke nytt arbeidsforhold spørsmål`() {
        val soknaden = hentSoknader(fnr = fnr).first()

        soknaden.sporsmal!!.find {
            it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG
        }.shouldBeNull()

        soknaden.sporsmal!!.map { it.tag } `should be equal to`
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                FERIE_V2,
                PERMISJON_V2,
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                ANDRE_INNTEKTSKILDER_V2,
                OPPHOLD_UTENFOR_EOS,
                TIL_SLUTT,
            )
    }
}
