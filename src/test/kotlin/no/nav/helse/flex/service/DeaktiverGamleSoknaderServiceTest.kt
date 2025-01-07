package no.nav.helse.flex.service

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Svar
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

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
        sykepengesoknadDAO.nullstillSoknader("12345784312")
    }

    @Test
    fun `Tom database inkluderer ingen søknader å deaktivere`() {
        val antall = deaktiverGamleSoknaderService.deaktiverSoknader()
        assertThat(antall).isEqualTo(0)

        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 0
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0, duration = Duration.ofSeconds(1))
    }

    @Test
    fun `En gammel arbeidstakersøknad blir deaktivert`() {
        val nySoknad =
            opprettNySoknad().copy(
                status = Soknadstatus.NY,
                fnr = "12345784312",
                tom = LocalDate.now().minusMonths(4).minusDays(1),
                opprettet = LocalDate.now().minusMonths(4).minusDays(1).atStartOfDay().tilOsloInstant(),
            )
        sykepengesoknadDAO.lagreSykepengesoknad(nySoknad)

        val sendtSoknad = nySoknad.copy(status = Soknadstatus.SENDT, id = UUID.randomUUID().toString())
        sykepengesoknadDAO.lagreSykepengesoknad(sendtSoknad)

        val antall = deaktiverGamleSoknaderService.deaktiverSoknader()
        assertThat(antall).isEqualTo(1)

        assertThat(sykepengesoknadDAO.finnSykepengesoknad(nySoknad.id).status).isEqualTo(Soknadstatus.UTGATT)
        assertThat(sykepengesoknadDAO.finnSykepengesoknad(sendtSoknad.id).status).isEqualTo(Soknadstatus.SENDT)

        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 1

        val soknadPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        soknadPaKafka.status `should be equal to` SoknadsstatusDTO.UTGAATT
        soknadPaKafka.sporsmal `should be equal to` emptyList()

        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 0
    }

    @Test
    fun `En avbrutt søknad blir deaktivert`() {
        val avbruttSoknad =
            opprettNySoknad().copy(
                status = Soknadstatus.AVBRUTT,
                fnr = "12345784312",
                tom = LocalDate.now().minusMonths(4).minusDays(1),
                opprettet = LocalDate.now().minusMonths(4).minusDays(1).atStartOfDay().tilOsloInstant(),
            )
        sykepengesoknadDAO.lagreSykepengesoknad(avbruttSoknad)

        val sendtSoknad = avbruttSoknad.copy(status = Soknadstatus.SENDT, id = UUID.randomUUID().toString())
        sykepengesoknadDAO.lagreSykepengesoknad(sendtSoknad)

        val antall = deaktiverGamleSoknaderService.deaktiverSoknader()
        assertThat(antall).isEqualTo(1)

        assertThat(sykepengesoknadDAO.finnSykepengesoknad(avbruttSoknad.id).status).isEqualTo(Soknadstatus.UTGATT)
        assertThat(sykepengesoknadDAO.finnSykepengesoknad(sendtSoknad.id).status).isEqualTo(Soknadstatus.SENDT)

        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 1

        val soknadPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        soknadPaKafka.status `should be equal to` SoknadsstatusDTO.UTGAATT
        soknadPaKafka.sporsmal `should be equal to` emptyList()

        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 0
    }

    @Test
    fun `En gammel utenlandssoknad blir deaktivert`() {
        val nySoknad =
            settOppSoknadOppholdUtland("12345784312")
                .copy(
                    opprettet = LocalDateTime.now().minusMonths(4).minusDays(1).tilOsloInstant(),
                    status = Soknadstatus.NY,
                )
        sykepengesoknadDAO.lagreSykepengesoknad(nySoknad)

        val sendtSoknad = nySoknad.copy(status = Soknadstatus.SENDT, id = UUID.randomUUID().toString())
        sykepengesoknadDAO.lagreSykepengesoknad(sendtSoknad)

        val antall = deaktiverGamleSoknaderService.deaktiverSoknader()
        assertThat(antall).isEqualTo(1)

        assertThat(sykepengesoknadDAO.finnSykepengesoknad(nySoknad.id).status).isEqualTo(Soknadstatus.UTGATT)
        assertThat(sykepengesoknadDAO.finnSykepengesoknad(sendtSoknad.id).status).isEqualTo(Soknadstatus.SENDT)

        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 1

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first().let {
            it.status `should be equal to` SoknadsstatusDTO.UTGAATT
            it.sporsmal `should be equal to` emptyList()
        }

        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 0
    }

    @Test
    fun `Utgåtte søknader blir publisert uten spørsmål`() {
        val nySoknad =
            opprettNySoknad().copy(
                status = Soknadstatus.NY,
                tom = LocalDate.now().minusMonths(4).minusDays(1),
                opprettet = LocalDate.now().minusMonths(4).minusDays(1).atStartOfDay().tilOsloInstant(),
            )
        sykepengesoknadDAO.lagreSykepengesoknad(nySoknad)

        val antall = deaktiverGamleSoknaderService.deaktiverSoknader()
        assertThat(antall).isEqualTo(1)

        assertThat(sykepengesoknadDAO.finnSykepengesoknad(nySoknad.id).status).isEqualTo(Soknadstatus.UTGATT)
        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 1

        val soknadPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        soknadPaKafka.status `should be equal to` SoknadsstatusDTO.UTGAATT
        soknadPaKafka.sporsmal `should be equal to` emptyList()

        soknadPaKafka.sporsmal?.size `should be equal to` 0
    }

    @Test
    fun `Svar blir slettet fra en delvis besvart utgått søknad`() {
        val avbruttSoknad =
            opprettNySoknad().copy(
                status = Soknadstatus.AVBRUTT,
                fnr = "12345784312",
                tom = LocalDate.now().minusMonths(4).minusDays(1),
                opprettet = LocalDate.now().minusMonths(4).minusDays(1).atStartOfDay().tilOsloInstant(),
            )
        sykepengesoknadDAO.lagreSykepengesoknad(avbruttSoknad)

        val alleSporsmal = sykepengesoknadDAO.finnSykepengesoknad(avbruttSoknad.id).sporsmal.flattenSporsmal()

        svarDao.lagreSvar(
            alleSporsmal.find { it.tag == "ANSVARSERKLARING" }?.id!!,
            Svar(null, "JA"),
        )

        svarDao.lagreSvar(
            alleSporsmal.find { it.tag == "FRISKMELDT" }?.id!!,
            Svar(null, "JA"),
        )

        svarDao.lagreSvar(
            alleSporsmal.find { it.tag == "ANDRE_INNTEKTSKILDER" }?.id!!,
            Svar(null, "NEI"),
        )

        svarDao.lagreSvar(
            alleSporsmal.find { it.tag == "OPPHOLD_UTENFOR_EOS" }?.id!!,
            Svar(null, "JA"),
        )

        svarDao.lagreSvar(
            alleSporsmal.find { it.tag == "OPPHOLD_UTENFOR_EOS_NAR" }?.id!!,
            Svar(null, Periode(LocalDate.now(), LocalDate.now()).serialisertTilString()),
        )

        val idPaaAlleSporsmal = alleSporsmal.mapNotNull { it.id }

        val antall = deaktiverGamleSoknaderService.deaktiverSoknader()
        assertThat(antall).isEqualTo(1)

        publiserUtgaatteSoknader.publiserUtgatteSoknader() `should be equal to` 1

        sykepengesoknadDAO.finnSykepengesoknad(avbruttSoknad.id).sporsmal.size `should be equal to` 0
        svarDao.finnSvar(idPaaAlleSporsmal.toSet()).size `should be equal to` 0
//

        val soknadPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        soknadPaKafka.status `should be equal to` SoknadsstatusDTO.UTGAATT
        soknadPaKafka.sporsmal `should be equal to` emptyList()

        // TODO: Trenger både ID og UUID, så returner et klasse wrapper et pair
    }

    @Test
    fun `Medlemskapsvurdering blir slettet fra en utgått søknad`() {
        val avbruttSoknad =
            opprettNySoknad().copy(
                status = Soknadstatus.AVBRUTT,
                fnr = "12345784312",
                tom = LocalDate.now().minusMonths(4).minusDays(1),
                opprettet = LocalDate.now().minusMonths(4).minusDays(1).atStartOfDay().tilOsloInstant(),
            )
        sykepengesoknadDAO.lagreSykepengesoknad(avbruttSoknad)

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

        val antall = deaktiverGamleSoknaderService.deaktiverSoknader()
        assertThat(antall).isEqualTo(1)

        medlemskapVurderingRepository.findBySykepengesoknadIdAndFomAndTom(
            avbruttSoknad.id,
            avbruttSoknad.fom,
            avbruttSoknad.tom,
        ) `should be equal to` null
    }
}
