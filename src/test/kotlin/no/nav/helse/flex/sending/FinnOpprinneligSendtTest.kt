package no.nav.helse.flex.sending

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Avsendertype
import no.nav.helse.flex.domain.Opprinnelse
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.repository.SykepengesoknadDbRecord
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.*

class FinnOpprinneligSendtTest {
    val lengesiden = Instant.now().minusSeconds(5000)
    val endaLengreSiden = Instant.now().minusSeconds(10000)

    @Test
    fun `finner opprinnelig sendt basert tidligere overlappende`() {
        val denViSender = skapDbRecord(sendt = Instant.now())
        listOf(
            denViSender,
            skapDbRecord(sendt = lengesiden),
        ).finnOpprinneligSendt(denViSender) `should be equal to` lengesiden
    }

    @Test
    fun `finner opprinnelig sendt basert på flere tidligere overlappende`() {
        val denViSender = skapDbRecord(sendt = Instant.now())
        listOf(
            denViSender,
            skapDbRecord(sendt = lengesiden),
            skapDbRecord(sendt = endaLengreSiden),
        ).finnOpprinneligSendt(denViSender) `should be equal to` endaLengreSiden
    }

    @Test
    fun `finner ingen opprinnelig sendt når det ikke er noen tidligere overlappende`() {
        val denViSender = skapDbRecord(sendt = Instant.now())
        listOf(
            denViSender,
        ).finnOpprinneligSendt(denViSender).shouldBeNull()
    }

    fun skapDbRecord(
        sendt: Instant? = null,
        korrigerer: String? = null,
        sykepengesoknadUuid: String = UUID.randomUUID().toString(),
        fom: LocalDate = LocalDate.now().minusDays(4),
        tom: LocalDate = LocalDate.now(),
    ): SykepengesoknadDbRecord =
        SykepengesoknadDbRecord(
            id = UUID.randomUUID().toString(),
            sykepengesoknadUuid = sykepengesoknadUuid,
            fnr = "bla",
            soknadstype = Soknadstype.ANNET_ARBEIDSFORHOLD,
            status = Soknadstatus.SENDT,
            opprettet = Instant.now(),
            avbruttDato = LocalDate.now(),
            sendtNav = null,
            korrigerer = korrigerer,
            korrigertAv = "sdfsdf",
            opprinnelse = Opprinnelse.SYKEPENGESOKNAD_BACKEND,
            avsendertype = Avsendertype.BRUKER,
            sykmeldingUuid = UUID.randomUUID().toString(),
            fom = fom,
            tom = tom,
            startSykeforlop = LocalDate.MIN,
            sykmeldingSkrevet = Instant.now(),
            sykmeldingSignaturDato = Instant.now(),
            sendtArbeidsgiver = null,
            arbeidsgiverOrgnummer = "12345",
            arbeidsgiverNavn = "1243123",
            arbeidssituasjon = Arbeidssituasjon.ANNET,
            egenmeldtSykmelding = null,
            merknaderFraSykmelding = null,
            utenlandskSykmelding = false,
            egenmeldingsdagerFraSykmelding = null,
            sendt = sendt,
            forstegangssoknad = false,
            aktivertDato = null,
        )
}
