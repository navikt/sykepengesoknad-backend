package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.domain.Soknadstatus.FREMTIDIG
import no.nav.helse.flex.domain.Soknadstatus.NY
import no.nav.helse.flex.domain.Soknadstatus.SENDT
import no.nav.helse.flex.fakes.SoknadKafkaProducerFake
import no.nav.helse.flex.frisktilarbeid.BehandletStatus.BEHANDLET
import no.nav.helse.flex.frisktilarbeid.sporsmal.sendFriskTilArbeidVedtak
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.repository.SykepengesoknadDbRecord
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.sendStartMelding
import no.nav.helse.flex.sendStoppMelding
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING
import no.nav.helse.flex.soknadsopprettelse.FTA_INNTEKT_UNDERVEIS
import no.nav.helse.flex.soknadsopprettelse.FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT
import no.nav.helse.flex.soknadsopprettelse.FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_AVREGISTRERT_NAR
import no.nav.helse.flex.soknadsopprettelse.FTA_JOBBSITUASJONEN_DIN_NEI
import no.nav.helse.flex.soknadsopprettelse.FTA_REISE_TIL_UTLANDET
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.util.osloZone
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be equal to`
import org.amshove.kluent.`should start with`
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldContainAll
import org.amshove.kluent.shouldContainSame
import org.amshove.kluent.shouldNotBeNull
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

class StartMeldingTest : FakesTestOppsett() {
    @Autowired
    private lateinit var friskTilArbeidCronJob: FriskTilArbeidCronJob

    @Autowired
    private lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    private val fnr = "11111111122"

    // Vedtaket fører til fire søknadsperioder på 14 dager: én som er passert (NY), én som pågår og to fremtidige (FREMTIDIG).
    private val tidspunkt = LocalDate.now()
    private val vedtakFom = tidspunkt.minusDays(15)
    private val vedtakTom = tidspunkt.plusDays(35)

    private val forsteSoknadFom = vedtakFom
    private val andreSoknadFom = vedtakFom.plusDays(14)
    private val tredjeSoknadFom = vedtakFom.plusDays(28)
    private val fjerdeSoknadFom = vedtakFom.plusDays(42)

    // STOPP sletter søknader med fom etter stopp-datoen, dvs. tredje og fjerde søknadsperioden.
    private val stoppTidspunkt = tidspunkt.tilOsloInstant()
    private val startTidspunkt = tidspunkt.plusDays(1).tilOsloInstant()

    @BeforeEach
    fun setup() {
        sykepengesoknadRepository.deleteAll()
        friskTilArbeidRepository.deleteAll()
    }

    @Test
    fun `Start-melding gjenoppretter slettede søknader i en vedtaksperiode`() {
        // Arrange
        `Gitt at et vedtak er mottatt og søknader opprettet`()
        `Og en stopp-melding har slettet søknader`(stoppTidspunkt)

        // Act
        `Motta og prosesser en start-melding`()

        // Assert
        hentVedtak().also {
            it.behandletStatus `should be equal to` BEHANDLET
            it.avsluttetTidspunkt `should be equal to` null
        }

        val soknader = hentSoknader()
        soknader.map { it.fom to it.status } `should be equal to`
            listOf(
                forsteSoknadFom to NY,
                andreSoknadFom to FREMTIDIG,
                tredjeSoknadFom to FREMTIDIG,
                fjerdeSoknadFom to FREMTIDIG,
            )

        val gjenopprettedeSoknadIder = soknader.filter { it.fom!! > tidspunkt }.map { it.sykepengesoknadUuid }
        `Så er det publisert FREMTIDIG-event for`(gjenopprettedeSoknadIder)
    }

    @Test
    fun `Start-melding gjenoppretter alle søknader når stopp-melding har slettet alle`() {
        // Arrange
        `Gitt at et vedtak er mottatt og søknader opprettet`()
        val slettedeSoknadIder = `Og en stopp-melding har slettet søknader`(vedtakFom.minusDays(1).tilOsloInstant())
        hentSoknader().shouldBeEmpty()

        // Act
        `Motta og prosesser en start-melding`()

        // Assert
        `Og søknader med passert tom er aktivert`()

        hentVedtak().avsluttetTidspunkt `should be equal to` null

        val soknader = hentSoknader()
        soknader.map { it.fom to it.status } `should be equal to`
            listOf(
                forsteSoknadFom to NY,
                andreSoknadFom to FREMTIDIG,
                tredjeSoknadFom to FREMTIDIG,
                fjerdeSoknadFom to FREMTIDIG,
            )
        soknader.map { it.sykepengesoknadUuid }.intersect(slettedeSoknadIder.toSet()).shouldBeEmpty()
    }

    @Test
    fun `Søknader i en gjenopprettet vedtaksperiode får ny id`() {
        // Arrange
        `Gitt at et vedtak er mottatt og søknader opprettet`()

        val forsteSlettedeIder = `Og en stopp-melding har slettet søknader`(stoppTidspunkt)

        // Act
        `Motta og prosesser en start-melding`()

        val andreSlettedeIder = `Og en stopp-melding har slettet søknader`(stoppTidspunkt)
        `Motta og prosesser en start-melding`()

        // Assert
        val gjenopprettedeSoknadIder =
            hentSoknader().filter { it.fom!! > tidspunkt }.map { it.sykepengesoknadUuid }

        gjenopprettedeSoknadIder.size `should be equal to` 2
        andreSlettedeIder `should not be equal to` forsteSlettedeIder
        gjenopprettedeSoknadIder.intersect((forsteSlettedeIder + andreSlettedeIder).toSet()).shouldBeEmpty()
    }

    @Test
    fun `Det kastes IllegalStateException hvis avsluttetTidspunkt er null`() {
        // Arrange
        `Gitt at et vedtak er mottatt og søknader opprettet`()

        // Act
        val e = assertThrows<IllegalStateException> { `Motta og prosesser en start-melding`() }

        val vedtak = hentVedtak()
        e.message!! `should start with` "Kan ikke gjenopprette vedtaksperiode: ${vedtak.id} når den aldri har blitt avsluttet."
    }

    @Test
    fun `Det kastes IllegalStateException hvis behandletStatus ikke er BEHANDLET`() {
        // Arrange
        `Gitt at et vedtak er mottatt og søknader opprettet`()
        `Og vedtaket er avsluttet men ikke behandlet`()

        // Act
        val e = assertThrows<IllegalStateException> { `Motta og prosesser en start-melding`() }

        // Assert
        val vedtak = hentVedtak()
        e.message!! `should start with` "Kan ikke gjenopprette vedtaksperiode: ${vedtak.id} som har status ${vedtak.behandletStatus}."
    }

    @Test
    fun `Start-melding har ingen effekt dersom bruker har svart at de ikke fortsatt er friskmeldt`() {
        // Arrange
        `Gitt at et vedtak er mottatt og søknader opprettet`()
        `Og en stopp-melding har slettet søknader`(stoppTidspunkt)
        `Og bruker svarer at de ikke fortsatt er friskmeldt`(hentNySoknad())

        // Act
        `Motta og prosesser en start-melding`()

        // Assert
        hentVedtak().also {
            it.behandletStatus `should be equal to` BEHANDLET
            it.avsluttetTidspunkt `should be equal to` stoppTidspunkt
        }
        hentSoknader().map { it.fom to it.status } `should be equal to` listOf(forsteSoknadFom to SENDT)
    }

    @Test
    fun `Start-melding har ingen effekt dersom bruker har svart nei i en eldre søknad enn den nyeste`() {
        // Arrange
        `Gitt at et vedtak er mottatt og søknader opprettet`()
        `Og bruker svarer at de ikke fortsatt er friskmeldt`(hentNySoknad())
        `Og vedtaket blir manuelt rebehandlet`()

        val soknader = hentSoknader()
        check(soknader.last().status == FREMTIDIG)

        // Act
        `Motta og prosesser en start-melding`()

        // Assert
        val avsluttetTidspunkt = hentVedtak().avsluttetTidspunkt.shouldNotBeNull()
        hentVedtak().also {
            it.behandletStatus `should be equal to` BEHANDLET
            it.avsluttetTidspunkt `should be equal to` avsluttetTidspunkt
        }
        hentSoknader() shouldContainSame soknader
    }

    @Test
    fun `Det kastes IllegalStateException hvis melding gjelder ukjent vedtak`() {
        // Arrange
        `Gitt at et vedtak er mottatt og søknader opprettet`()
        val vedtak = hentVedtak()
        val soknader = hentSoknader()
        val ukjentVedtaksperiodeId = UUID.randomUUID().toString()

        // Act
        val e =
            assertThrows<IllegalStateException> {
                sendStartMelding(ukjentVedtaksperiodeId, fnr, startTidspunkt)
            }

        // Assert
        e.message `should be equal to` "Fant ikke vedtak for START-melding med vedtaksperiode: $ukjentVedtaksperiodeId."
        hentVedtak() `should be equal to` vedtak
        hentSoknader() shouldContainSame soknader
    }

    @Test
    fun `Det kastes IllegalStateException når melding gjelder vedtak som ignorerer arbeidssøkerregisteret`() {
        // Arrange
        `Gitt at et vedtak er mottatt og søknader opprettet`()
        `Og vedtaket ignorerer arbeidssøkerregisteret`()
        val vedtakFor = hentVedtak()
        val soknaderFor = hentSoknader()

        // Act
        val e =
            assertThrows<IllegalStateException> {
                sendStoppMelding(vedtakFor.id!!, fnr, stoppTidspunkt)
            }

        // Assert
        e.message `should be equal to`
            "Kan ikke behandle STOPP-melding for vedtaksperiode: ${vedtakFor.id} " +
            "siden vedtaket er satt til å ignorere arbeidssøkerregisteret."
        hentVedtak() `should be equal to` vedtakFor
        hentSoknader() shouldContainSame soknaderFor
    }

    private fun `Gitt at et vedtak er mottatt og søknader opprettet`(
        fom: LocalDate = vedtakFom,
        tom: LocalDate = vedtakTom,
    ) {
        sendFriskTilArbeidVedtak(fnr, fom, tom)
        friskTilArbeidCronJob.behandleFriskTilArbeidVedtak()
        `Og søknader med passert tom er aktivert`()

        check(hentVedtak().behandletStatus == BEHANDLET)
        check(hentSoknader().size == 4)
    }

    private fun `Og søknader med passert tom er aktivert`() {
        await().until { hentSoknader().filter { it.tom!! < tidspunkt }.all { it.status == NY } }
    }

    private fun `Og en stopp-melding har slettet søknader`(tidspunkt: Instant): List<String> {
        val soknaderFor = hentSoknader().map { it.sykepengesoknadUuid }

        sendStoppMelding(hentVedtak().id!!, fnr, tidspunkt)

        val slettedeSoknadIder = soknaderFor - hentSoknader().map { it.sykepengesoknadUuid }.toSet()
        check(slettedeSoknadIder.isNotEmpty())
        return slettedeSoknadIder
    }

    private fun `Og vedtaket er avsluttet men ikke behandlet`() {
        friskTilArbeidRepository.save(
            hentVedtak().copy(avsluttetTidspunkt = stoppTidspunkt, behandletStatus = BehandletStatus.NY),
        )
    }

    private fun `Og vedtaket blir manuelt rebehandlet`() {
        friskTilArbeidRepository.save(hentVedtak().copy(behandletStatus = BehandletStatus.NY))
        friskTilArbeidCronJob.behandleFriskTilArbeidVedtak()
        check(hentSoknader().size == 4)
    }

    private fun `Og vedtaket ignorerer arbeidssøkerregisteret`() {
        friskTilArbeidRepository.save(hentVedtak().copy(ignorerArbeidssokerregister = true))
    }

    private fun `Og bruker svarer at de ikke fortsatt er friskmeldt`(soknad: RSSykepengesoknad) {
        SoknadBesvarer(rSSykepengesoknad = soknad, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_NEI, "CHECKED", false)
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT, "NEI", false)
            .besvarSporsmal(
                FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_AVREGISTRERT_NAR,
                soknad.fom!!.plusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE),
                true,
                mutert = true,
            ).besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(FTA_INNTEKT_UNDERVEIS, "NEI")
            .besvarSporsmal(FTA_REISE_TIL_UTLANDET, "NEI")
            .oppsummering()
            .sendSoknad()
    }

    private fun `Motta og prosesser en start-melding`() {
        sendStartMelding(hentVedtak().id!!, fnr, startTidspunkt)
        friskTilArbeidCronJob.behandleFriskTilArbeidVedtak()
    }

    private fun `Så er det publisert FREMTIDIG-event for`(soknadIder: List<String>) {
        SoknadKafkaProducerFake.records
            .map { it.value() }
            .filter { it.status == SoknadsstatusDTO.FREMTIDIG }
            .map { it.id }
            .shouldContainAll(soknadIder)
    }

    private fun hentVedtak(): FriskTilArbeidVedtakDbRecord = friskTilArbeidRepository.findByFnrIn(listOf(fnr)).single()

    private fun hentNySoknad(): RSSykepengesoknad = hentSoknader(fnr).single { it.status == RSSoknadstatus.NY }

    private fun hentSoknader(): List<SykepengesoknadDbRecord> =
        sykepengesoknadRepository.findByFriskTilArbeidVedtakId(hentVedtak().id!!).sortedBy { it.fom }

    private fun LocalDate.tilOsloInstant(): Instant = atStartOfDay(osloZone).toInstant()
}
