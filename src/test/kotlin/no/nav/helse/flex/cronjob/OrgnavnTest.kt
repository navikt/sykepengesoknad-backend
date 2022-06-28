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
                sykepengesoknadUuid = "annen-uuid",
            )
        )
    }

    @Test
    fun `Henter siste orgnavn i fra databasen`() {
        val organisasjoner = sykepengesoknadRepository.findLatestOrgnavn()

        organisasjoner.shouldHaveSize(2)

        val orgMedNyttNavn = organisasjoner.first { it.arbeidsgiver_orgnummer == orgNr }
        orgMedNyttNavn.arbeidsgiver_orgnummer.shouldBeEqualTo(orgNr)
        orgMedNyttNavn.arbeidsgiver_navn.shouldBeEqualTo("Nyeste orgnavn")
        orgMedNyttNavn.sykepengesoknad_uuid.shouldBeEqualTo(soknad.sykepengesoknadUuid)

        val annenOrg = organisasjoner.first { it.arbeidsgiver_orgnummer != orgNr }
        annenOrg.arbeidsgiver_orgnummer.shouldBeEqualTo("11111111")
        annenOrg.arbeidsgiver_navn.shouldBeEqualTo("Annen bedrift AS")
        annenOrg.sykepengesoknad_uuid.shouldBeEqualTo("annen-uuid")
    }
}
