package no.nav.helse.flex.arbeidsledig

import no.nav.helse.flex.*
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ArbeidsledigGapTest : FellesTestOppsett() {
    companion object {
        const val TIDLIGERE_ARBEIDSGIVER_ORGNR = "123456789"
    }

    @Test
    fun `søknad uten gap beholder tidligereArbeidsgiverOrgnummer`() {
        val fnr = "99887766551"
        val sykeforlopStart = LocalDate.of(2024, 3, 1)
        val soknadTom = LocalDate.of(2024, 3, 15)

        sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                fnr = fnr,
                tidligereArbeidsgiverOrgnummer = TIDLIGERE_ARBEIDSGIVER_ORGNR,
                sykmeldingsperioder = heltSykmeldt(fom = sykeforlopStart, tom = soknadTom),
            ),
            oppfolgingsdato = sykeforlopStart,
        )

        val andreSoknader =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                    fnr = fnr,
                    tidligereArbeidsgiverOrgnummer = TIDLIGERE_ARBEIDSGIVER_ORGNR,
                    sykmeldingsperioder = heltSykmeldt(fom = soknadTom.plusDays(1), tom = LocalDate.of(2024, 3, 31)),
                ),
                oppfolgingsdato = sykeforlopStart,
            )

        with(andreSoknader.first()) {
            type `should be equal to` SoknadstypeDTO.ARBEIDSLEDIG
            tidligereArbeidsgiverOrgnummer.shouldNotBeNull()
            tidligereArbeidsgiverOrgnummer `should be equal to` TIDLIGERE_ARBEIDSGIVER_ORGNR
            forstegangssoknad `should be equal to` false
        }
    }
}
