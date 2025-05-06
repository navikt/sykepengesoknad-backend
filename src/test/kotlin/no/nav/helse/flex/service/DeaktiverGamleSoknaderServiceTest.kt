package no.nav.helse.flex.service

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.medlemskap.MedlemskapVurderingDbRecord
import no.nav.helse.flex.medlemskap.MedlemskapVurderingRepository
import no.nav.helse.flex.mock.opprettNySoknad
import no.nav.helse.flex.repository.SvarDAO
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadOppholdUtland
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.util.flattenSporsmal
import no.nav.helse.flex.util.serialisertTilString
import no.nav.helse.flex.util.tilOsloInstant
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

private const val FNR = "12345784312"

class DeaktiverGamleSoknaderServiceTest : FellesTestOppsett() {
    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var svarDao: SvarDAO

    @Autowired
    private lateinit var medlemskapVurderingRepository: MedlemskapVurderingRepository

    @Autowired
    private lateinit var publiserUtgaatteSoknader: PubliserUtgaatteSoknader

    @Autowired
    private lateinit var deaktiverGamleSoknaderService: DeaktiverGamleSoknaderService

    @BeforeEach
    @AfterEach
    fun nullstillDatabase() {
        sykepengesoknadDAO.nullstillSoknader(FNR)
    }

    @Test
    fun `Tom database inkluderer ingen søknader å deaktivere`() {
        deaktiverGamleSoknaderService.deaktiverSoknader() `should be equal to` 0
        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 0
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0, duration = Duration.ofSeconds(1))
    }

    @Test
    fun `En ny arbeidstakersøknad blir deaktivert`() {
        val nySoknad =
            opprettSoknad(Soknadstatus.NY)
                .also { sykepengesoknadDAO.lagreSykepengesoknad(it) }

        val sendtSoknad =
            nySoknad
                .copy(status = Soknadstatus.SENDT, id = UUID.randomUUID().toString())
                .also { sykepengesoknadDAO.lagreSykepengesoknad(it) }

        deaktiverGamleSoknaderService.deaktiverSoknader() `should be equal to` 1
        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 1

        sykepengesoknadDAO.finnSykepengesoknad(nySoknad.id).status `should be equal to` Soknadstatus.UTGATT
        sykepengesoknadDAO.finnSykepengesoknad(sendtSoknad.id).status `should be equal to` Soknadstatus.SENDT

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first().let {
            it.status `should be equal to` SoknadsstatusDTO.UTGAATT
            it.sporsmal `should be equal to` emptyList()
        }

        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 0
    }

    @Test
    fun `En avbrutt arbeidstaker blir deaktivert`() {
        val nySoknad = opprettSoknad(Soknadstatus.AVBRUTT).also { sykepengesoknadDAO.lagreSykepengesoknad(it) }

        val sendtSoknad =
            nySoknad
                .copy(status = Soknadstatus.SENDT, id = UUID.randomUUID().toString())
                .also { sykepengesoknadDAO.lagreSykepengesoknad(it) }

        deaktiverGamleSoknaderService.deaktiverSoknader() `should be equal to` 1
        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 1

        sykepengesoknadDAO.finnSykepengesoknad(nySoknad.id).status `should be equal to` Soknadstatus.UTGATT
        sykepengesoknadDAO.finnSykepengesoknad(sendtSoknad.id).status `should be equal to` Soknadstatus.SENDT

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first().let {
            it.status `should be equal to` SoknadsstatusDTO.UTGAATT
            it.sporsmal `should be equal to` emptyList()
        }

        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 0
    }

    @Test
    fun `En ny soknad om opphold utland blir deaktivert`() {
        val soknadUtland =
            settOppSoknadOppholdUtland(FNR)
                .copy(
                    opprettet =
                        LocalDateTime
                            .now()
                            .minusMonths(4)
                            .minusDays(1)
                            .tilOsloInstant(),
                    status = Soknadstatus.NY,
                ).also { sykepengesoknadDAO.lagreSykepengesoknad(it) }

        val sendtSoknad = soknadUtland.copy(status = Soknadstatus.SENDT, id = UUID.randomUUID().toString())
        sykepengesoknadDAO.lagreSykepengesoknad(sendtSoknad)

        deaktiverGamleSoknaderService.deaktiverSoknader() `should be equal to` 1
        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 1

        sykepengesoknadDAO.finnSykepengesoknad(soknadUtland.id).status `should be equal to` Soknadstatus.UTGATT
        sykepengesoknadDAO.finnSykepengesoknad(sendtSoknad.id).status `should be equal to` Soknadstatus.SENDT

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first().let {
            it.status `should be equal to` SoknadsstatusDTO.UTGAATT
            it.sporsmal `should be equal to` emptyList()
        }

        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 0
    }

    @Test
    fun `Spørsmål og Svar blir slettet fra en besvart søknad som deaktiveres`() {
        val avbruttSoknad = opprettSoknad(Soknadstatus.AVBRUTT).also { sykepengesoknadDAO.lagreSykepengesoknad(it) }

        val idPaaSporsmal = besvarStandardSporsmal(avbruttSoknad)

        deaktiverGamleSoknaderService.deaktiverSoknader() `should be equal to` 1
        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 1

        sykepengesoknadDAO.finnSykepengesoknad(avbruttSoknad.id).sporsmal.size `should be equal to` 0
        svarDao.finnSvar(idPaaSporsmal.toSet()).size `should be equal to` 0

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first().let {
            it.status `should be equal to` SoknadsstatusDTO.UTGAATT
            it.sporsmal `should be equal to` emptyList()
        }

        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 0
    }

    @Test
    fun `Vurdering av medlemskap fra LovMe blir slettet når søknad deaktiveres`() {
        val avbruttSoknad =
            opprettSoknad(Soknadstatus.AVBRUTT).also {
                sykepengesoknadDAO.lagreSykepengesoknad(it)
                lagreMedlemskapsVurdering(it)
            }

        deaktiverGamleSoknaderService.deaktiverSoknader() `should be equal to` 1
        medlemskapVurderingRepository.findBySykepengesoknadIdAndFomAndTom(
            avbruttSoknad.id,
            avbruttSoknad.fom!!,
            avbruttSoknad.tom!!,
        ) `should be equal to` null
    }

    private fun opprettSoknad(soknadStatus: Soknadstatus): Sykepengesoknad =
        opprettNySoknad().copy(
            status = soknadStatus,
            fnr = FNR,
            tom = LocalDate.now().minusMonths(4).minusDays(1),
            opprettet =
                LocalDate
                    .now()
                    .minusMonths(4)
                    .minusDays(1)
                    .atStartOfDay()
                    .tilOsloInstant(),
        )

    private fun besvarStandardSporsmal(soknad: Sykepengesoknad): List<String> {
        val idPaaSporsmal =
            sykepengesoknadDAO
                .finnSykepengesoknad(soknad.id)
                .sporsmal
                .flattenSporsmal()
                .also {
                    lagreSvar(it, "ANSVARSERKLARING", "JA")
                    lagreSvar(it, "FRISKMELDT", "JA")
                    lagreSvar(it, "ANDRE_INNTEKTSKILDER", "NEI")
                    lagreSvar(it, "OPPHOLD_UTENFOR_EOS", "JA")
                    lagreSvar(
                        it,
                        "OPPHOLD_UTENFOR_EOS_NAR",
                        Periode(LocalDate.now(), LocalDate.now()).serialisertTilString(),
                    )
                }.mapNotNull { it.id }
        return idPaaSporsmal
    }

    private fun lagreSvar(
        alleSporsmal: List<Sporsmal>,
        tag: String,
        svar: String,
    ) = svarDao.lagreSvar(alleSporsmal.find { it.tag == tag }?.id!!, Svar(null, svar))

    private fun lagreMedlemskapsVurdering(avbruttSoknad: Sykepengesoknad) {
        medlemskapVurderingRepository.save(
            MedlemskapVurderingDbRecord(
                timestamp = Instant.now(),
                svartid = 10L,
                fnr = avbruttSoknad.fnr,
                fom = avbruttSoknad.fom!!,
                tom = avbruttSoknad.tom!!,
                svartype = "JA",
                sykepengesoknadId = avbruttSoknad.id,
            ),
        )
    }

    private fun finnMedlemskapsVurdering(avbruttSoknad: Sykepengesoknad): MedlemskapVurderingDbRecord? =
        medlemskapVurderingRepository.findBySykepengesoknadIdAndFomAndTom(
            avbruttSoknad.id,
            avbruttSoknad.fom!!,
            avbruttSoknad.tom!!,
        )
}
