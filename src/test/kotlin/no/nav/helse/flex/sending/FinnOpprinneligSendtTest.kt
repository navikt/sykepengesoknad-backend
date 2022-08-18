package no.nav.helse.flex.sending

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Avsendertype
import no.nav.helse.flex.domain.Opprinnelse
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.repository.SykepengesoknadDbRecord
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.util.*

class FinnOpprinneligSendtTest {

    val lengesiden = Instant.now().minusSeconds(5000)
    val ikkeSålengesiden = Instant.now().minusSeconds(2000)

    @Test
    fun `velger eldste sendt dato`() {
        listOf(
            skapDbRecord(sykepengesoknadUuid = "1", korrigerer = "2", sendtNav = Instant.now()),
            skapDbRecord(sykepengesoknadUuid = "2", korrigerer = "3", sendtNav = Instant.now()),
            skapDbRecord(sykepengesoknadUuid = "3", sendtNav = lengesiden, sendtArbeidsgiver = ikkeSålengesiden)
        ).finnOpprinneligSendt("1") `should be equal to` lengesiden
    }

    @Test
    fun `velger sendt nav når kun den finnes`() {
        listOf(
            skapDbRecord(sykepengesoknadUuid = "1", korrigerer = "3", sendtNav = Instant.now()),
            skapDbRecord(sykepengesoknadUuid = "3", sendtNav = ikkeSålengesiden)
        ).finnOpprinneligSendt("1") `should be equal to` ikkeSålengesiden
    }

    @Test
    fun `velger sendt arbeidsgiver når kun den finnes`() {
        listOf(
            skapDbRecord(sykepengesoknadUuid = "1", korrigerer = "2", sendtNav = Instant.now()),
            skapDbRecord(sykepengesoknadUuid = "2", korrigerer = "3", sendtNav = Instant.now()),
            skapDbRecord(sykepengesoknadUuid = "3", sendtArbeidsgiver = ikkeSålengesiden)
        ).finnOpprinneligSendt("1") `should be equal to` ikkeSålengesiden
    }

    @Test
    fun `kaster feil når sendt tidspunkt mangler`() {

        val exception = assertThrows<RuntimeException> {
            listOf(
                skapDbRecord(sykepengesoknadUuid = "1", korrigerer = "2", sendtNav = Instant.now()),
                skapDbRecord(sykepengesoknadUuid = "2", korrigerer = "3", sendtNav = Instant.now()),
                skapDbRecord(sykepengesoknadUuid = "3")
            ).finnOpprinneligSendt("1")
        }

        exception.message `should be equal to` "3 er korrigert med status SENDT, men ingen sendt datoer"
    }

    @Test
    fun `kaster feil når sendt korrigerer mangler`() {

        val exception = assertThrows<RuntimeException> {
            listOf(
                skapDbRecord(sykepengesoknadUuid = "1", korrigerer = "2", sendtNav = Instant.now()),
                skapDbRecord(sykepengesoknadUuid = "2", korrigerer = "3", sendtNav = Instant.now()),
                skapDbRecord(sykepengesoknadUuid = "3")
            ).finnOpprinneligSendt("666")
        }

        exception.message `should be equal to` "Forventa å finne søknad med id 666"
    }

    fun skapDbRecord(
        sendtNav: Instant? = null,
        sendtArbeidsgiver: Instant? = null,
        korrigerer: String? = null,
        sykepengesoknadUuid: String = UUID.randomUUID().toString()
    ): SykepengesoknadDbRecord {
        return SykepengesoknadDbRecord(
            id = UUID.randomUUID().toString(),
            sykepengesoknadUuid = sykepengesoknadUuid,
            fnr = "bla",
            soknadstype = Soknadstype.ANNET_ARBEIDSFORHOLD,
            status = Soknadstatus.SENDT,
            opprettet = Instant.now(),
            avbruttDato = LocalDate.now(),
            sendtNav = sendtNav,
            korrigerer = korrigerer,
            korrigertAv = "sdfsdf",
            opprinnelse = Opprinnelse.SYKEPENGESOKNAD_BACKEND,
            avsendertype = Avsendertype.BRUKER,
            sykmeldingUuid = UUID.randomUUID().toString(),
            fom = LocalDate.EPOCH,
            tom = LocalDate.EPOCH,
            startSykeforlop = LocalDate.EPOCH,
            sykmeldingSkrevet = Instant.now(),
            sendtArbeidsgiver = sendtArbeidsgiver,
            arbeidsgiverOrgnummer = "12345",
            arbeidsgiverNavn = "1243123",
            arbeidssituasjon = Arbeidssituasjon.ANNET,
            egenmeldtSykmelding = null,
            merknaderFraSykmelding = null,
            avbruttFeilinfo = null,
        )
    }
}
