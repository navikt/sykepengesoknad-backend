package no.nav.helse.flex.forskuttering

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.forskuttering.domain.Forskuttering
import no.nav.helse.flex.forskuttering.domain.ForskutteringEsyfo
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.julesoknad.JulesoknadCronJob
import no.nav.helse.flex.repository.JulesoknadkandidatDAO
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.service.MottakerAvSoknadService
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class ForskutteringLesereTest : FellesTestOppsett() {
    @Autowired lateinit var gammel: ForskutteringRepository

    @Autowired lateinit var esyfo: ForskutteringEsyfoRepository

    @Autowired lateinit var mottakerAvSoknadService: MottakerAvSoknadService

    @Autowired lateinit var julesoknadCronJob: JulesoknadCronJob

    @Autowired lateinit var julesoknadkandidatDAO: JulesoknadkandidatDAO

    private val fnr = "12345678946"
    private val orgnummer = "999999995"
    private val lederId = UUID.randomUUID()
    private val fom = LocalDate.of(LocalDate.now().year + 1, 12, 1)

    @BeforeEach
    fun `Rydd databasen før hver test`() {
        databaseReset.resetDatabase()
        gammel.deleteAll()
        esyfo.deleteAll()
        flexSyketilfelleMockRestServiceServer.reset()
    }

    @Nested
    inner class MottakerAvSoknad {
        @Test
        fun `Bruker eksisterende forskuttering selv om eSyfo har motsatt verdi`() {
            lagreForskuttering(true)
            lagreEsyfoForskuttering(false)

            mottakerAvSoknadService.arbeidsgiverForskutterer(soknad()) shouldBe true
        }

        @Test
        fun `Ukjent forskuttering regnes fortsatt som forskuttering selv om eSyfo sier nei`() {
            lagreEsyfoForskuttering(false)

            mottakerAvSoknadService.arbeidsgiverForskutterer(soknad()) shouldBe true
        }
    }

    @Nested
    inner class Julesoknad {
        @Test
        fun `Aktiverer ikke søknaden når eksisterende tabell sier ja og eSyfo sier nei`() {
            lagreForskuttering(true)
            lagreEsyfoForskuttering(false)
            opprettJulesoknad()

            julesoknadCronJob.prosseserJulesoknadKandidater()

            val soknad = hentSoknaderMetadata(fnr).single()
            soknad.status shouldBe RSSoknadstatus.FREMTIDIG
            sykepengesoknadRepository.erAktivertJulesoknadKandidat(soknad.id) shouldBe false
            julesoknadkandidatDAO.hentJulesoknadkandidater() shouldHaveSize 1
        }

        @Test
        fun `Aktiverer ved ukjent forskuttering selv om eSyfo sier ja`() {
            lagreEsyfoForskuttering(true)
            opprettJulesoknad()

            julesoknadCronJob.prosseserJulesoknadKandidater()

            val soknad =
                await().atMost(Duration.ofSeconds(5)).until(
                    { hentSoknaderMetadata(fnr).single() },
                    { it.status == RSSoknadstatus.NY },
                )
            sykepengesoknadRepository.erAktivertJulesoknadKandidat(soknad.id) shouldBe true
            julesoknadkandidatDAO.hentJulesoknadkandidater().shouldBeEmpty()
            sykepengesoknadKafkaConsumer.ventPåRecords(1)
        }
    }

    private fun soknad() =
        Sykepengesoknad(
            id = UUID.randomUUID().toString(),
            fnr = fnr,
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            status = Soknadstatus.NY,
            opprettet = Instant.now(),
            sporsmal = emptyList(),
            fom = fom,
            tom = fom.plusDays(14),
            arbeidsgiverOrgnummer = orgnummer,
            utenlandskSykmelding = false,
        )

    private fun opprettJulesoknad() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = orgnummer, orgNavn = "Arbeidsgiver"),
                sykmeldingsperioder = heltSykmeldt(fom = fom, tom = fom.plusDays(14)),
            ),
        )
        julesoknadkandidatDAO.hentJulesoknadkandidater() shouldHaveSize 1
    }

    private fun lagreForskuttering(verdi: Boolean) {
        gammel.save(
            Forskuttering(
                oppdatert = Instant.now(),
                timestamp = Instant.now(),
                narmesteLederId = lederId,
                orgnummer = orgnummer,
                brukerFnr = fnr,
                aktivFom = fom,
                aktivTom = null,
                arbeidsgiverForskutterer = verdi,
            ),
        )
    }

    private fun lagreEsyfoForskuttering(verdi: Boolean) {
        esyfo.save(
            ForskutteringEsyfo(
                oppdatert = Instant.now(),
                timestamp = Instant.now(),
                narmesteLederId = lederId,
                orgnummer = orgnummer,
                brukerFnr = fnr,
                aktivFom = fom,
                aktivTom = null,
                arbeidsgiverForskutterer = verdi,
            ),
        )
    }
}
