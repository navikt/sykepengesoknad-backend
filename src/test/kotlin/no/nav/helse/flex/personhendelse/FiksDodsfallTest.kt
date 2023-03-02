package no.nav.helse.flex.personhendelse

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.domain.*
import no.nav.helse.flex.repository.DodsmeldingDAO
import no.nav.helse.flex.repository.SykepengesoknadDbRecord
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.util.*

class FiksDodsfallTest : BaseTestClass() {

    @Autowired
    private lateinit var dodsmeldingDAO: DodsmeldingDAO

    @Autowired
    private lateinit var fiksDodsfall: FiksDodsfall

    @Test
    fun `Patcher inn gammel dødsfall`() {
        dodsmeldingDAO.fnrMedToUkerGammelDodsmelding().shouldBeEmpty()

        fiksDodsfall.fiksDodsfall()
        dodsmeldingDAO.fnrMedToUkerGammelDodsmelding().shouldBeEmpty()

        sykepengesoknadRepository.save(
            SykepengesoknadDbRecord(
                id = null,
                sykepengesoknadUuid = "a6b38fb8-3efc-3609-a242-0b2a1837a3e3",
                fnr = "12345",
                soknadstype = Soknadstype.ANNET_ARBEIDSFORHOLD,
                status = Soknadstatus.SENDT,
                opprettet = Instant.now(),
                avbruttDato = LocalDate.now(),
                sendtNav = null,
                korrigerer = null,
                korrigertAv = "sdfsdf",
                opprinnelse = Opprinnelse.SYKEPENGESOKNAD_BACKEND,
                avsendertype = Avsendertype.BRUKER,
                sykmeldingUuid = UUID.randomUUID().toString(),
                fom = LocalDate.EPOCH,
                tom = LocalDate.EPOCH,
                startSykeforlop = LocalDate.EPOCH,
                sykmeldingSkrevet = Instant.now(),
                sendtArbeidsgiver = null,
                arbeidsgiverOrgnummer = "12345",
                arbeidsgiverNavn = "1243123",
                arbeidssituasjon = Arbeidssituasjon.ANNET,
                egenmeldtSykmelding = null,
                merknaderFraSykmelding = null,
                avbruttFeilinfo = null,
                utenlandskSykmelding = false,
            )
        )
        fiksDodsfall.fiksDodsfall()
        fiksDodsfall.fiksDodsfall()
        fiksDodsfall.fiksDodsfall()

        dodsmeldingDAO.fnrMedToUkerGammelDodsmelding().shouldHaveSize(1)
        dodsmeldingDAO.fnrMedToUkerGammelDodsmelding().first().fnr.shouldBeEqualTo("12345")
        dodsmeldingDAO.fnrMedToUkerGammelDodsmelding().first().dodsdato.shouldBeEqualTo(LocalDate.of(2022, 11, 17))
    }
}
