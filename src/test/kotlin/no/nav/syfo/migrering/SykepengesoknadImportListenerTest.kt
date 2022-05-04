package no.nav.syfo.migrering

import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.BaseTestClass
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.repository.SykepengesoknadRepository
import no.nav.syfo.soknadsopprettelse.settOppSoknadArbeidstaker
import no.nav.syfo.soknadsopprettelse.settOppSoknadOppholdUtland
import no.nav.syfo.soknadsopprettelse.sorterSporsmal
import no.nav.syfo.util.serialisertTilString
import no.nav.syfo.util.tilOsloLocalDateTime
import org.amshove.kluent.`should be empty`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import skapSoknadMetadata
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SykepengesoknadImportListenerTest : BaseTestClass() {

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var sykepengesoknadImportListener: SykepengesoknadImportListener

    @Autowired
    private lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    @Autowired
    private lateinit var soknadKorrigertPatchListener: SoknadKorrigertPatchListener

    @AfterEach
    fun `Vi resetter databasen etter hver test`() {
        databaseReset.resetDatabase()
    }

    @Test
    fun `Vi håndterer tom liste`() {
        sykepengesoknadImportListener.handterSoknader(emptyList())
    }

    @Test
    fun `Vi håndterer en søknad`() {
        sykepengesoknadRepository.count() `should be equal to` 0
        val fnr = "1234534343"
        val soknad = settOppSoknadOppholdUtland(fnr)
        sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr)).`should be empty`()

        val soknadSomString = soknad.tilSykepengesoknadKafka().serialisertTilString()
        sykepengesoknadImportListener.handterSoknader(listOf(soknadSomString, soknadSomString))

        val soknader = sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr))
        soknader.shouldHaveSize(1)
        soknader.first().sorterSporsmal().utenSpmIdOgPavirkerAndreOgMs() `should be equal to` soknad.sorterSporsmal()
            .utenSpmIdOgPavirkerAndreOgMs()

        sykepengesoknadImportListener.handterSoknader(listOf(soknadSomString))
        sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr)).shouldHaveSize(1)
        sykepengesoknadRepository.count() `should be equal to` 1
    }

    @Test
    fun `Vi håndterer to søknader`() {
        sykepengesoknadRepository.count() `should be equal to` 0

        val fnr = "1234534344"

        val soknad = settOppSoknadOppholdUtland(fnr)
        val soknad2 = settOppSoknadArbeidstaker(
            soknadMetadata = skapSoknadMetadata(fnr = fnr),
            erForsteSoknadISykeforlop = true,
            tidligsteFomForSykmelding = LocalDate.now()
        )
        sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr)).`should be empty`()

        val soknadSomString = soknad.tilSykepengesoknadKafka().serialisertTilString()
        val soknad2SomString = soknad2.tilSykepengesoknadKafka().serialisertTilString()
        sykepengesoknadImportListener.handterSoknader(listOf(soknadSomString, soknad2SomString))

        val soknader = sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr))
        soknader.shouldHaveSize(2)
        soknader.first { it.soknadstype == Soknadstype.OPPHOLD_UTLAND }.sorterSporsmal()
            .utenSpmIdOgPavirkerAndreOgMs() `should be equal to` soknad.sorterSporsmal().utenSpmIdOgPavirkerAndreOgMs()
        soknader.first { it.soknadstype == Soknadstype.ARBEIDSTAKERE }.sorterSporsmal()
            .utenSpmIdOgPavirkerAndreOgMs() `should be equal to` soknad2.sorterSporsmal().utenSpmIdOgPavirkerAndreOgMs()

        sykepengesoknadImportListener.handterSoknader(listOf(soknadSomString))
        sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr)).shouldHaveSize(2)
        sykepengesoknadRepository.count() `should be equal to` 2
    }

    @Test
    fun `Vi håndterer en søknad som senere blir korrigert`() {
        sykepengesoknadRepository.count() `should be equal to` 0

        val fnr = "1234534343"
        val soknad = settOppSoknadOppholdUtland(fnr).copy(status = Soknadstatus.SENDT)
        sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr)).`should be empty`()

        val soknadSomString = soknad.tilSykepengesoknadKafka().serialisertTilString()
        sykepengesoknadImportListener.handterSoknader(listOf(soknadSomString, soknadSomString))

        val soknader = sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr))
        soknader.shouldHaveSize(1)

        sykepengesoknadRepository.count() `should be equal to` 1
        sykepengesoknadRepository.findBySykepengesoknadUuid(soknad.id)!!.status `should be equal to` Soknadstatus.SENDT

        val korrigering = SykepengesoknadDTO(
            fnr = fnr,
            id = UUID.randomUUID().toString(),
            status = SoknadsstatusDTO.SENDT,
            type = SoknadstypeDTO.OPPHOLD_UTLAND,
            korrigerer = soknad.id,
        )
        soknadKorrigertPatchListener.handterSoknader(
            listOf(
                korrigering.serialisertTilString()
            )
        )
        sykepengesoknadRepository.count() `should be equal to` 1
        sykepengesoknadRepository.findBySykepengesoknadUuid(soknad.id)!!.status `should be equal to` Soknadstatus.KORRIGERT
        sykepengesoknadRepository.findBySykepengesoknadUuid(soknad.id)!!.korrigertAv `should be equal to` korrigering.id
    }
}

fun Sykepengesoknad.tilSykepengesoknadKafka(): SykepengesoknadKafka {
    return SykepengesoknadKafka(
        id = id,
        fnr = fnr,
        soknadstype = soknadstype,
        status = status,
        opprettet = opprettet?.tilOsloLocalDateTime(),
        avbruttDato = avbruttDato,
        sendtNav = sendtNav?.tilOsloLocalDateTime(),
        korrigerer = korrigerer,
        korrigertAv = korrigertAv,
        sporsmal = sporsmal,
        opprinnelse = opprinnelse,
        avsendertype = avsendertype,
        sykmeldingId = sykmeldingId,
        fom = fom,
        tom = tom,
        startSykeforlop = startSykeforlop,
        sykmeldingSkrevet = sykmeldingSkrevet?.tilOsloLocalDateTime(),
        soknadPerioder = soknadPerioder,
        sendtArbeidsgiver = sendtArbeidsgiver?.tilOsloLocalDateTime(),
        arbeidsgiverOrgnummer = arbeidsgiverOrgnummer,
        arbeidsgiverNavn = arbeidsgiverNavn,
        arbeidssituasjon = arbeidssituasjon,
        egenmeldtSykmelding = egenmeldtSykmelding,
        merknaderFraSykmelding = merknaderFraSykmelding,
        avbruttFeilinfo = avbruttFeilinfo,
    )
}

fun Sykepengesoknad.utenSpmIdOgPavirkerAndreOgMs(): Sykepengesoknad {
    var soknaden = this
    val sporsmal = this.alleSporsmalOgUndersporsmal()
    sporsmal.forEach {
        soknaden = soknaden.replaceSporsmal(it.copy(id = null, pavirkerAndreSporsmal = false))
    }
    return soknaden.fjernMs()
}

fun Sykepengesoknad.fjernMs(): Sykepengesoknad = this.copy(
    opprettet = opprettet?.truncatedTo(ChronoUnit.SECONDS),
    sykmeldingSkrevet = sykmeldingSkrevet?.truncatedTo(ChronoUnit.SECONDS),
    sendtNav = sendtNav?.truncatedTo(ChronoUnit.SECONDS),
    sendtArbeidsgiver = sendtArbeidsgiver?.truncatedTo(ChronoUnit.SECONDS),
)
