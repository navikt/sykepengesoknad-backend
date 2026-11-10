package no.nav.helse.flex.forskuttering

import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.aktivering.AktiveringProducer
import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.forskuttering.domain.Forskuttering
import no.nav.helse.flex.forskuttering.domain.ForskutteringEsyfo
import no.nav.helse.flex.julesoknad.ProsesserJulesoknadkandidat
import no.nav.helse.flex.juridiskvurdering.JuridiskVurderingKafkaProducer
import no.nav.helse.flex.repository.JulesoknadkandidatDAO
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadDbRecord
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.service.MottakerAvSoknadService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class ForskutteringLesereTest : FellesTestOppsett() {
    @Autowired lateinit var gammel: ForskutteringRepository

    @Autowired lateinit var esyfo: ForskutteringEsyfoRepository

    @Test
    fun `begge produksjonslesere bruker eksisterende tabell selv om eSyfo har andre data`() {
        val fnr = "12345678946"
        val orgnummer = "999999995"
        val lederId = UUID.randomUUID()
        val nå = Instant.now()
        val fom = LocalDate.now().plusDays(1)
        gammel.save(
            Forskuttering(
                oppdatert = nå,
                timestamp = nå,
                narmesteLederId = lederId,
                orgnummer = orgnummer,
                brukerFnr = fnr,
                aktivFom = fom,
                aktivTom = null,
                arbeidsgiverForskutterer = true,
            ),
        )
        esyfo.save(
            ForskutteringEsyfo(
                oppdatert = nå,
                timestamp = nå,
                narmesteLederId = lederId,
                orgnummer = orgnummer,
                brukerFnr = fnr,
                aktivFom = fom,
                aktivTom = null,
                arbeidsgiverForskutterer = false,
            ),
        )

        val soknad =
            Sykepengesoknad(
                id = UUID.randomUUID().toString(),
                fnr = fnr,
                soknadstype = Soknadstype.ARBEIDSTAKERE,
                status = Soknadstatus.NY,
                opprettet = nå,
                sporsmal = emptyList(),
                fom = fom,
                tom = fom.plusDays(14),
                arbeidsgiverOrgnummer = orgnummer,
                utenlandskSykmelding = false,
            )

        val mottaker =
            MottakerAvSoknadService(
                Mockito.mock(SykepengesoknadDAO::class.java),
                Mockito.mock(FlexSyketilfelleClient::class.java),
                Mockito.mock(IdentService::class.java),
                Mockito.mock(JuridiskVurderingKafkaProducer::class.java),
                gammel,
            )
        mottaker.arbeidsgiverForskutterer(soknad) shouldBe true

        val julesoknadDAO = Mockito.mock(JulesoknadkandidatDAO::class.java)
        val soknadDAO = Mockito.mock(SykepengesoknadDAO::class.java)
        val soknadRepository = Mockito.mock(SykepengesoknadRepository::class.java)
        val aktivering = Mockito.mock(AktiveringProducer::class.java)
        val ident = Mockito.mock(IdentService::class.java)
        val dbRecord = Mockito.mock(SykepengesoknadDbRecord::class.java)
        val soknadUuid = UUID.randomUUID().toString()
        Mockito.`when`(dbRecord.id).thenReturn("db-id")
        Mockito.`when`(dbRecord.sykepengesoknadUuid).thenReturn(soknadUuid)
        Mockito.`when`(dbRecord.status).thenReturn(Soknadstatus.FREMTIDIG)
        Mockito.`when`(dbRecord.arbeidssituasjon).thenReturn(Arbeidssituasjon.ARBEIDSTAKER)
        Mockito.`when`(dbRecord.arbeidsgiverOrgnummer).thenReturn(orgnummer)
        Mockito.`when`(dbRecord.fnr).thenReturn(fnr)
        Mockito.`when`(dbRecord.fom).thenReturn(fom)
        Mockito.`when`(soknadRepository.findBySykepengesoknadUuid(soknadUuid)).thenReturn(dbRecord)
        Mockito.`when`(ident.hentFolkeregisterIdenterMedHistorikkForFnr(fnr)).thenReturn(FolkeregisterIdenter(fnr, emptyList()))
        val kandidat = JulesoknadkandidatDAO.Julesoknadkandidat("kandidat-id", soknadUuid)

        val prosesser = ProsesserJulesoknadkandidat(julesoknadDAO, soknadDAO, soknadRepository, gammel, aktivering, ident)

        prosesser.prosseserJulesoknadKandidat(kandidat)
        Mockito.verify(soknadRepository, Mockito.never()).settErAktivertJulesoknadKandidat("db-id")

        esyfo.save(
            ForskutteringEsyfo(
                oppdatert = nå,
                timestamp = nå,
                narmesteLederId = UUID.randomUUID(),
                orgnummer = "999999996",
                brukerFnr = fnr,
                aktivFom = fom,
                aktivTom = null,
                arbeidsgiverForskutterer = true,
            ),
        )
        mottaker.arbeidsgiverForskutterer(soknad.copy(arbeidsgiverOrgnummer = "999999996")) shouldBe true
        Mockito.clearInvocations(soknadRepository)
        Mockito.`when`(dbRecord.arbeidsgiverOrgnummer).thenReturn("999999996")
        prosesser.prosseserJulesoknadKandidat(kandidat)
        Mockito.verify(soknadRepository).settErAktivertJulesoknadKandidat("db-id")
    }
}
