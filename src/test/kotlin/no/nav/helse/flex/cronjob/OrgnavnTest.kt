package no.nav.helse.flex.cronjob

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Avsendertype
import no.nav.helse.flex.domain.Opprinnelse
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.repository.SykepengesoknadDbRecord
import no.nav.helse.flex.repository.SykepengesoknadRepository
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.util.*

internal class OrgnavnTest : BaseTestClass() {

    @Autowired
    private lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    private val fnr = "84930294320"
    private val orgNr = "12345678"

    private val soknad = SykepengesoknadDbRecord(
        fom = LocalDate.now(),
        tom = LocalDate.now(),
        arbeidsgiverNavn = "Nyeste orgnavn",
        arbeidsgiverOrgnummer = orgNr,
        sykepengesoknadUuid = UUID.randomUUID().toString(),
        fnr = fnr,
        soknadstype = Soknadstype.ARBEIDSTAKERE,
        status = Soknadstatus.SENDT,
        opprinnelse = Opprinnelse.SYKEPENGESOKNAD_BACKEND,
        opprettet = Instant.now(),
        avbruttDato = null,
        sendtNav = Instant.now(),
        sendtArbeidsgiver = Instant.now(),
        korrigerer = null,
        korrigertAv = null,
        avsendertype = Avsendertype.SYSTEM,
        sykmeldingUuid = UUID.randomUUID().toString(),
        startSykeforlop = LocalDate.now(),
        sykmeldingSkrevet = Instant.now(),
        arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
        egenmeldtSykmelding = false,
        merknaderFraSykmelding = null,
        avbruttFeilinfo = false,
    )

    @BeforeAll
    fun setup() {
        sykepengesoknadRepository.deleteAll()

        sykepengesoknadRepository.save(
            soknad.copy(
                fom = LocalDate.now(),
                tom = LocalDate.now(),
                arbeidsgiverOrgnummer = orgNr,
                arbeidsgiverNavn = "Nyeste orgnavn",
                sykepengesoknadUuid = UUID.randomUUID().toString()
            )
        )
        sykepengesoknadRepository.save(
            soknad.copy(
                fom = LocalDate.now().minusDays(5),
                tom = LocalDate.now().minusDays(5),
                arbeidsgiverOrgnummer = orgNr,
                arbeidsgiverNavn = "Nyere orgnavn",
                sykepengesoknadUuid = UUID.randomUUID().toString()
            )
        )
        sykepengesoknadRepository.save(
            soknad.copy(
                fom = LocalDate.now().minusDays(10),
                tom = LocalDate.now().minusDays(10),
                arbeidsgiverOrgnummer = orgNr,
                arbeidsgiverNavn = "Gammelt orgnavn",
                sykepengesoknadUuid = UUID.randomUUID().toString()
            )
        )

        sykepengesoknadRepository.save(
            soknad.copy(
                fom = LocalDate.now(),
                tom = LocalDate.now(),
                arbeidsgiverOrgnummer = "11111111",
                arbeidsgiverNavn = "Annen bedrift AS",
                sykepengesoknadUuid = UUID.randomUUID().toString()
            )
        )
    }

    @Test
    fun `Henter siste orgnavn i fra databasen`() {
        val organisasjoner = sykepengesoknadRepository.findLatestOrgnavn()

        organisasjoner.shouldHaveSize(2)

        val orgMedNyttNavn = organisasjoner.first { it.first == orgNr }
        orgMedNyttNavn.first.shouldBeEqualTo(orgNr)
        orgMedNyttNavn.second.shouldBeEqualTo("Nyeste orgnavn")

        val annenOrg = organisasjoner.first { it.first != orgNr }
        annenOrg.first.shouldBeEqualTo("11111111")
        annenOrg.second.shouldBeEqualTo("Annen bedrift AS")
    }
}
