package no.nav.helse.flex.overlappendesykmeldinger

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.aktivering.AktiveringJob
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.domain.Arbeidsgiverperiode
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykmeldingstype
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.repository.KlippMetrikkRepository
import no.nav.helse.flex.repository.KlippVariant
import no.nav.helse.flex.repository.KlippetSykepengesoknadRepository
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.ANDRE_INNTEKTSKILDER_V2
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING
import no.nav.helse.flex.soknadsopprettelse.ARBEID_UNDERVEIS_100_PROSENT
import no.nav.helse.flex.soknadsopprettelse.ARBEID_UTENFOR_NORGE
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER
import no.nav.helse.flex.soknadsopprettelse.FERIE_V2
import no.nav.helse.flex.soknadsopprettelse.JOBBET_DU_GRADERT
import no.nav.helse.flex.soknadsopprettelse.PERMISJON_V2
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_I_ARBEID
import no.nav.helse.flex.soknadsopprettelse.UTLAND_V2
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.behandingsdager
import no.nav.helse.flex.testdata.gradertSykmeldt
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.reisetilskudd
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.util.DatoUtil
import no.nav.helse.flex.util.OBJECT_MAPPER
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeEqualTo
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class OverlapperEtter : BaseTestClass() {

    @Autowired
    private lateinit var aktiveringJob: AktiveringJob

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    lateinit var klippMetrikkRepository: KlippMetrikkRepository

    @Autowired
    private lateinit var klippetSykepengesoknadRepository: KlippetSykepengesoknadRepository

    fun String?.tilSoknadsperioder(): List<Soknadsperiode>? {
        return if (this == null) {
            null
        } else {
            OBJECT_MAPPER.readValue(this)
        }
    }

    private final val basisdato = LocalDate.now()
    private val fnr = "11555555555"

    @Test
    @Order(1)
    fun `Fremtidig arbeidstakersøknad opprettes for en sykmelding`() {
        val kafkaSoknader = sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.minusDays(1),
                    tom = basisdato.plusDays(15)
                )
            )
        )
        kafkaSoknader[0].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG

        val hentetViaRest = hentSoknaderMetadata(fnr)
        hentetViaRest shouldHaveSize 1
        hentetViaRest[0].soknadstype shouldBeEqualTo RSSoknadstype.ARBEIDSTAKERE
        hentetViaRest[0].status shouldBeEqualTo RSSoknadstatus.FREMTIDIG
    }

    @Test
    @Order(2)
    fun `Fremtidig arbeidstakersøknad opprettes for en overlappende sykmelding i scenario 1`() {
        val kafkaSoknader = sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato,
                    tom = basisdato.plusDays(15)
                )
            ),
            oppfolgingsdato = basisdato.minusDays(1),
            forventaSoknader = 2
        )

        kafkaSoknader[0].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        kafkaSoknader[0].fom shouldBeEqualTo basisdato
        kafkaSoknader[0].tom shouldBeEqualTo basisdato.plusDays(15)

        kafkaSoknader[1].status shouldBeEqualTo SoknadsstatusDTO.NY
        kafkaSoknader[1].fom shouldBeEqualTo basisdato.minusDays(1)
        kafkaSoknader[1].tom shouldBeEqualTo basisdato.minusDays(1)

        val soknaderMetadata = hentSoknaderMetadata(fnr)
        soknaderMetadata shouldHaveSize 2

        val forsteSoknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        forsteSoknad.soknadstype shouldBeEqualTo RSSoknadstype.ARBEIDSTAKERE
        forsteSoknad.status shouldBeEqualTo RSSoknadstatus.NY
        forsteSoknad.fom shouldBeEqualTo basisdato.minusDays(1)
        forsteSoknad.tom shouldBeEqualTo basisdato.minusDays(1)
        val forsteSoknadSpmFinnes = forsteSoknad.sporsmal?.find { it.tag == ARBEID_UTENFOR_NORGE }
        forsteSoknadSpmFinnes shouldNotBeEqualTo null
        val periodeSpmSok1 = forsteSoknad.sporsmal
            ?.find { it.tag == FERIE_V2 }
            ?.undersporsmal
            ?.first()
        periodeSpmSok1?.min shouldBeEqualTo basisdato.minusDays(1).toString()
        periodeSpmSok1?.max shouldBeEqualTo basisdato.minusDays(1).toString()

        val fremtidigSoknad = hentSoknad(
            soknadId = soknaderMetadata.last().id,
            fnr = fnr
        )
        fremtidigSoknad.soknadstype shouldBeEqualTo RSSoknadstype.ARBEIDSTAKERE
        fremtidigSoknad.status shouldBeEqualTo RSSoknadstatus.FREMTIDIG
        fremtidigSoknad.fom shouldBeEqualTo basisdato
        fremtidigSoknad.tom shouldBeEqualTo basisdato.plusDays(15)

        val klippmetrikker = klippMetrikkRepository.findAll().toList()
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus `should be equal to` "FREMTIDIG"
        klippmetrikker[0].variant `should be equal to` "SOKNAD_STARTER_INNI_SLUTTER_ETTER"
        klippmetrikker[0].endringIUforegrad `should be equal to` "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet `should be equal to` true
    }

    @Test
    @Order(3)
    fun `Søknadene aktiveres og får spørsmål tilpasset klippingen`() {
        aktiveringJob.bestillAktivering(basisdato.plusDays(16))

        sykepengesoknadKafkaConsumer
            .ventPåRecords(antall = 1)
            .tilSoknader()
            .first()
            .status shouldBeEqualTo SoknadsstatusDTO.NY

        val soknaderMetadata = hentSoknaderMetadata(fnr)
        soknaderMetadata shouldHaveSize 2

        val forsteSoknad = hentSoknad(
            soknadId = soknaderMetadata.first().id,
            fnr = fnr
        )

        forsteSoknad.soknadstype shouldBeEqualTo RSSoknadstype.ARBEIDSTAKERE
        forsteSoknad.status shouldBeEqualTo RSSoknadstatus.NY
        forsteSoknad.fom shouldBeEqualTo basisdato.minusDays(1)
        forsteSoknad.tom shouldBeEqualTo basisdato.minusDays(1)
        val forsteSoknadSpmFinnes = forsteSoknad.sporsmal?.find { it.tag == ARBEID_UTENFOR_NORGE }
        forsteSoknadSpmFinnes shouldNotBeEqualTo null
        val periodeSpmSok1 = forsteSoknad.sporsmal
            ?.find { it.tag == FERIE_V2 }
            ?.undersporsmal
            ?.first()
        periodeSpmSok1?.min shouldBeEqualTo basisdato.minusDays(1).toString()
        periodeSpmSok1?.max shouldBeEqualTo basisdato.minusDays(1).toString()

        val andreSoknad = hentSoknad(
            soknadId = soknaderMetadata.last().id,
            fnr = fnr
        )
        andreSoknad.soknadstype shouldBeEqualTo RSSoknadstype.ARBEIDSTAKERE
        andreSoknad.status shouldBeEqualTo RSSoknadstatus.NY
        andreSoknad.fom shouldBeEqualTo basisdato
        andreSoknad.tom shouldBeEqualTo basisdato.plusDays(15)
        val finnesIkke = andreSoknad.sporsmal?.find { it.tag == ARBEID_UTENFOR_NORGE }
        finnesIkke shouldBeEqualTo null
        val periodeSpmSok2 = andreSoknad.sporsmal
            ?.find { it.tag == FERIE_V2 }
            ?.undersporsmal
            ?.first()
        periodeSpmSok2?.min shouldBeEqualTo basisdato.toString()
        periodeSpmSok2?.max shouldBeEqualTo basisdato.plusDays(15).toString()
    }

    @Test
    @Order(4)
    fun `Databasen tømmes`() {
        databaseReset.resetDatabase()
    }

    @Test
    @Order(10)
    fun `Brukeren sender inn en sykmelding`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato,
                    tom = basisdato.plusDays(15)
                )
            )
        )

        aktiveringJob.bestillAktivering(basisdato.plusDays(16))

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)

        val soknaderMetadata = hentSoknaderMetadata(fnr)
        soknaderMetadata shouldHaveSize 1

        val rsSoknad = hentSoknad(
            soknadId = soknaderMetadata.first().id,
            fnr = fnr
        )

        mockFlexSyketilfelleArbeidsgiverperiode(
            arbeidsgiverperiode = Arbeidsgiverperiode(
                antallBrukteDager = 16,
                oppbruktArbeidsgiverperiode = true,
                arbeidsgiverPeriode = Periode(fom = rsSoknad.fom!!, tom = rsSoknad.tom!!)
            )
        )

        SoknadBesvarer(rsSoknad, this, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "NEI")
            .besvarSporsmal(TILBAKE_I_ARBEID, "NEI")
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "NEI")
            .besvarSporsmal(PERMISJON_V2, "NEI")
            .besvarSporsmal(FERIE_V2, "NEI")
            .besvarSporsmal(UTLAND_V2, "NEI")
            .besvarSporsmal(ARBEID_UNDERVEIS_100_PROSENT + '0', "NEI")
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "NEI")
            .besvarSporsmal(ANDRE_INNTEKTSKILDER_V2, "NEI")
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
            .sendSoknad()

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    @Order(11)
    fun `Overlappende sykmelding med samme grad blir klippet`() {
        val soknad = sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.plusDays(10),
                    tom = basisdato.plusDays(20)
                )
            )
        ).first()

        soknad.fom shouldBeEqualTo basisdato.plusDays(16)
        soknad.tom shouldBeEqualTo basisdato.plusDays(20)

        await().until { klippetSykepengesoknadRepository.findBySykmeldingUuid(soknad.sykmeldingId!!) != null }

        val klipp = klippetSykepengesoknadRepository.findBySykmeldingUuid(soknad.sykmeldingId!!)!!
        klipp.sykepengesoknadUuid shouldNotBeEqualTo soknad.id
        klipp.sykmeldingUuid shouldBeEqualTo soknad.sykmeldingId
        klipp.klippVariant shouldBeEqualTo KlippVariant.SYKMELDING_STARTER_FOR_SLUTTER_INNI
        klipp.periodeFor.tilSoknadsperioder() shouldBeEqualTo listOf(
            Soknadsperiode(
                fom = basisdato.plusDays(10),
                tom = basisdato.plusDays(20),
                grad = 100,
                sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
            )
        )
        klipp.periodeEtter.tilSoknadsperioder() shouldBeEqualTo listOf(
            Soknadsperiode(
                fom = basisdato.plusDays(16),
                tom = basisdato.plusDays(20),
                grad = 100,
                sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
            )
        )

        val klippmetrikker = klippMetrikkRepository.findAll().toList()
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus `should be equal to` "SENDT"
        klippmetrikker[0].variant `should be equal to` "SYKMELDING_STARTER_FOR_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad `should be equal to` "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet `should be equal to` true
    }

    @Test
    @Order(12)
    fun `Database tømmes`() {
        databaseReset.resetDatabase()
    }

    @Test
    @Order(20)
    fun `Brukeren har en ny søknad`() {
        val soknad = sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.minusDays(15),
                    tom = basisdato.minusDays(1)
                )
            )
        ).first()

        soknad.status shouldBeEqualTo SoknadsstatusDTO.NY
    }

    @Test
    @Order(21)
    fun `Overlappende sykmelding med forskjellig grad blir klippet`() {
        val soknad = sendSykmelding(
            forventaSoknader = 2,
            sykmeldingKafkaMessage = sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = gradertSykmeldt(
                    fom = basisdato.minusDays(10),
                    tom = basisdato.plusDays(5),
                    grad = 50
                )
            )
        ).last()

        soknad.fom shouldBeEqualTo basisdato.minusDays(10)
        soknad.tom shouldBeEqualTo basisdato.plusDays(5)

        val klippmetrikker = klippMetrikkRepository.findAll().toList().sortedBy { it.variant }
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus `should be equal to` "NY"
        klippmetrikker[0].variant `should be equal to` "SOKNAD_STARTER_INNI_SLUTTER_ETTER"
        klippmetrikker[0].endringIUforegrad `should be equal to` "LAVERE_UFØREGRAD"
        klippmetrikker[0].klippet `should be equal to` true
    }

    @Test
    @Order(22)
    fun `DB tømmes`() {
        databaseReset.resetDatabase()
    }

    @Test
    @Order(30)
    fun `Bruker har en ny søknad og svarer på noen spørsmål`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.minusDays(15),
                    tom = basisdato.minusDays(5)
                )
            )
        )

        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
            fnr = fnr
        )

        SoknadBesvarer(soknaden, this, fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "ARBEID_UTENFOR_NORGE", svar = "NEI")
            .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
            .besvarSporsmal(tag = "FERIE_V2", svar = "JA", ferdigBesvart = false)
            .besvarSporsmal(tag = "FERIE_NAR_V2", svar = """{"fom":"${basisdato.minusDays(12)}","tom":"${basisdato.minusDays(5)}"}""")
    }

    @Test
    @Order(31)
    fun `Overlappende sykmelding klipper NY søknad og oppdaterer spørsmål`() {
        val overlappendeSykmeldingId = sendSykmelding(
            forventaSoknader = 2,
            sykmeldingKafkaMessage = sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.minusDays(10),
                    tom = basisdato.minusDays(1)
                )
            )
        ).last().sykmeldingId

        val soknaderMetadata = hentSoknaderMetadata(fnr)
        soknaderMetadata shouldHaveSize 2

        val overlappendeSoknad = soknaderMetadata.first { it.sykmeldingId == overlappendeSykmeldingId }
        overlappendeSoknad.fom shouldBeEqualTo basisdato.minusDays(10)
        overlappendeSoknad.tom shouldBeEqualTo basisdato.minusDays(1)

        val klippetSoknad = hentSoknad(
            soknadId = soknaderMetadata.first { it.sykmeldingId != overlappendeSykmeldingId }.id,
            fnr = fnr
        )

        klippetSoknad.soknadstype shouldBeEqualTo RSSoknadstype.ARBEIDSTAKERE
        klippetSoknad.status shouldBeEqualTo RSSoknadstatus.NY
        klippetSoknad.fom shouldBeEqualTo basisdato.minusDays(15)
        klippetSoknad.tom shouldBeEqualTo basisdato.minusDays(11)

        klippetSoknad.sporsmal!!.all { it.svar.isEmpty() } shouldBeEqualTo true

        val periodeSpm = klippetSoknad.sporsmal!!.first { it.tag == FERIE_V2 }
        periodeSpm.sporsmalstekst shouldBeEqualTo "Tok du ut feriedager i tidsrommet ${
        DatoUtil.formatterPeriode(
            klippetSoknad.fom!!,
            klippetSoknad.tom!!
        )
        }?"
        periodeSpm.svar.size shouldBeEqualTo 0
    }

    @Test
    @Order(32)
    fun `Db tømmes`() {
        databaseReset.resetDatabase()
    }

    @Test
    @Order(40)
    fun `Bruker har 2 innsendte søknader som overlapper fullstendig`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato,
                    tom = basisdato.plusDays(15)
                )
            )
        )
        aktiveringJob.bestillAktivering(basisdato.plusDays(16))

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)

        val rsSoknader = hentSoknaderMetadata(fnr).shouldHaveSize(1)
        val rsSoknad = hentSoknad(
            soknadId = rsSoknader.first().id,
            fnr = fnr
        )

        mockFlexSyketilfelleArbeidsgiverperiode(
            arbeidsgiverperiode = Arbeidsgiverperiode(
                antallBrukteDager = 16,
                oppbruktArbeidsgiverperiode = true,
                arbeidsgiverPeriode = Periode(fom = rsSoknad.fom!!, tom = rsSoknad.tom!!)
            )
        )

        SoknadBesvarer(rsSoknad, this, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "NEI")
            .besvarSporsmal(TILBAKE_I_ARBEID, "NEI")
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "NEI")
            .besvarSporsmal(PERMISJON_V2, "NEI")
            .besvarSporsmal(FERIE_V2, "NEI")
            .besvarSporsmal(UTLAND_V2, "NEI")
            .besvarSporsmal(ARBEID_UNDERVEIS_100_PROSENT + '0', "NEI")
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "NEI")
            .besvarSporsmal(ANDRE_INNTEKTSKILDER_V2, "NEI")
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
            .sendSoknad()

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)

        val identiskSoknad = sykepengesoknadDAO
            .finnSykepengesoknad(rsSoknad.id)
            .copy(
                id = UUID.randomUUID().toString(),
                sykmeldingId = UUID.randomUUID().toString()
            )
        sykepengesoknadDAO.lagreSykepengesoknad(identiskSoknad)

        val klippmetrikker = klippMetrikkRepository.findAll().toList().sortedBy { it.variant }
        klippmetrikker shouldHaveSize 0
    }

    @Test
    @Order(41)
    fun `Sykmelding overlapper med tom på de to identiske søknadene`() {
        val soknad = sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.plusDays(15),
                    tom = basisdato.plusDays(20)
                )
            )
        ).first()

        soknad.fom shouldBeEqualTo basisdato.plusDays(16)
        soknad.tom shouldBeEqualTo basisdato.plusDays(20)

        val klippmetrikker = klippMetrikkRepository.findAll().toList().sortedBy { it.variant }
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus `should be equal to` "SENDT"
        klippmetrikker[0].variant `should be equal to` "SYKMELDING_STARTER_FOR_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad `should be equal to` "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet `should be equal to` true
    }

    @Test
    @Order(43)
    fun `db tømmes`() {
        databaseReset.resetDatabase()
    }

    @Test
    @Order(50)
    fun `Ny behandlingsdager sykmelding klippes ikke`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.minusDays(15),
                    tom = basisdato.minusDays(5)
                )
            )
        )

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = behandingsdager(
                    fom = basisdato.minusDays(10),
                    tom = basisdato.minusDays(1),
                    behandlingsdager = 2
                )
            )
        )

        val soknader = sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr))

        soknader[0].soknadstype shouldBeEqualTo Soknadstype.ARBEIDSTAKERE
        soknader[0].fom shouldBeEqualTo basisdato.minusDays(15)
        soknader[0].tom shouldBeEqualTo basisdato.minusDays(5)
        soknader[0].status shouldBeEqualTo Soknadstatus.NY

        soknader[1].soknadstype shouldBeEqualTo Soknadstype.BEHANDLINGSDAGER
        soknader[1].fom shouldBeEqualTo basisdato.minusDays(10)
        soknader[1].tom shouldBeEqualTo basisdato.minusDays(1)
        soknader[1].status shouldBeEqualTo Soknadstatus.NY

        databaseReset.resetDatabase()
        klippMetrikkRepository.count() shouldBeEqualTo 0
    }

    @Test
    @Order(60)
    fun `Fremtidig behandlingsdager sykmelding klippes ikke`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato,
                    tom = basisdato.plusDays(10)
                )
            )
        )

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = behandingsdager(
                    fom = basisdato.plusDays(5),
                    tom = basisdato.plusDays(15),
                    behandlingsdager = 2
                )
            )
        )

        val soknader = sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr))

        soknader[0].fom shouldBeEqualTo basisdato
        soknader[0].tom shouldBeEqualTo basisdato.plusDays(10)
        soknader[0].soknadstype shouldBeEqualTo Soknadstype.ARBEIDSTAKERE
        soknader[0].status shouldBeEqualTo Soknadstatus.FREMTIDIG

        soknader[1].fom shouldBeEqualTo basisdato.plusDays(5)
        soknader[1].tom shouldBeEqualTo basisdato.plusDays(15)
        soknader[1].soknadstype shouldBeEqualTo Soknadstype.BEHANDLINGSDAGER
        soknader[1].status shouldBeEqualTo Soknadstatus.FREMTIDIG

        databaseReset.resetDatabase()
        klippMetrikkRepository.count() shouldBeEqualTo 0
    }

    @Test
    @Order(70)
    fun `Ny reisetilskudd sykmelding klippes ikke`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.minusDays(15),
                    tom = basisdato.minusDays(5)
                )
            )
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = reisetilskudd(
                    fom = basisdato.minusDays(10),
                    tom = basisdato.minusDays(1)
                )
            )
        )

        val soknader = sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr))

        soknader[0].fom shouldBeEqualTo basisdato.minusDays(15)
        soknader[0].tom shouldBeEqualTo basisdato.minusDays(5)
        soknader[0].soknadstype shouldBeEqualTo Soknadstype.ARBEIDSTAKERE
        soknader[0].status shouldBeEqualTo Soknadstatus.NY

        soknader[1].fom shouldBeEqualTo basisdato.minusDays(10)
        soknader[1].tom shouldBeEqualTo basisdato.minusDays(1)
        soknader[1].soknadstype shouldBeEqualTo Soknadstype.REISETILSKUDD
        soknader[1].status shouldBeEqualTo Soknadstatus.NY

        databaseReset.resetDatabase()
        klippMetrikkRepository.count() shouldBeEqualTo 0
    }

    @Test
    @Order(80)
    fun `Fremtidig reisetiskudd sykmelding klipper klippes ikke`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato,
                    tom = basisdato.plusDays(10)
                )
            )
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = reisetilskudd(
                    fom = basisdato.plusDays(5),
                    tom = basisdato.plusDays(15)
                )
            )
        )

        val soknader = sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr))

        soknader[0].fom shouldBeEqualTo basisdato
        soknader[0].tom shouldBeEqualTo basisdato.plusDays(10)
        soknader[0].soknadstype shouldBeEqualTo Soknadstype.ARBEIDSTAKERE
        soknader[0].status shouldBeEqualTo Soknadstatus.FREMTIDIG

        soknader[1].fom shouldBeEqualTo basisdato.plusDays(5)
        soknader[1].tom shouldBeEqualTo basisdato.plusDays(15)
        soknader[1].soknadstype shouldBeEqualTo Soknadstype.REISETILSKUDD
        soknader[1].status shouldBeEqualTo Soknadstatus.FREMTIDIG

        databaseReset.resetDatabase()
        klippMetrikkRepository.count() shouldBeEqualTo 0
    }

    @Test
    @Order(90)
    fun `Sender inn en arbeidstaker søknad med grad 50`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = gradertSykmeldt(
                    fom = basisdato.minusDays(20),
                    tom = basisdato.minusDays(1),
                    grad = 50
                )
            )
        )

        val soknaderMetadata = hentSoknaderMetadata(fnr)
        soknaderMetadata shouldHaveSize 1
        soknaderMetadata.first().status shouldBeEqualTo RSSoknadstatus.NY

        val rsSoknad = hentSoknad(
            soknadId = soknaderMetadata.first().id,
            fnr = fnr
        )

        mockFlexSyketilfelleArbeidsgiverperiode(
            arbeidsgiverperiode = Arbeidsgiverperiode(
                antallBrukteDager = 19,
                oppbruktArbeidsgiverperiode = true,
                arbeidsgiverPeriode = Periode(fom = rsSoknad.fom!!, tom = rsSoknad.tom!!)
            )
        )

        SoknadBesvarer(rsSoknad, this, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "NEI")
            .besvarSporsmal(TILBAKE_I_ARBEID, "NEI")
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "NEI")
            .besvarSporsmal(PERMISJON_V2, "NEI")
            .besvarSporsmal(FERIE_V2, "NEI")
            .besvarSporsmal(UTLAND_V2, "NEI")
            .besvarSporsmal(JOBBET_DU_GRADERT + '0', "NEI")
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "NEI")
            .besvarSporsmal(ANDRE_INNTEKTSKILDER_V2, "NEI")
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
            .sendSoknad()

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    @Order(91)
    fun `Overlappende sykmelding med forskjellig grad blir splittet`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = gradertSykmeldt(
                    fom = basisdato.minusDays(5),
                    tom = basisdato,
                    grad = 80
                )
            ),
            forventaSoknader = 2
        )

        val soknaderMetadata = hentSoknaderMetadata(fnr)
        soknaderMetadata shouldHaveSize 3

        soknaderMetadata[0].fom shouldBeEqualTo basisdato.minusDays(20)
        soknaderMetadata[0].tom shouldBeEqualTo basisdato.minusDays(1)
        soknaderMetadata[0].status shouldBeEqualTo RSSoknadstatus.SENDT

        soknaderMetadata[1].fom shouldBeEqualTo basisdato.minusDays(5)
        soknaderMetadata[1].tom shouldBeEqualTo basisdato.minusDays(1)
        soknaderMetadata[1].status shouldBeEqualTo RSSoknadstatus.NY

        soknaderMetadata[2].fom shouldBeEqualTo basisdato
        soknaderMetadata[2].tom shouldBeEqualTo basisdato
        soknaderMetadata[2].status shouldBeEqualTo RSSoknadstatus.FREMTIDIG

        val klippmetrikker = klippMetrikkRepository.findAll().toList()
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus `should be equal to` "SENDT"
        klippmetrikker[0].variant `should be equal to` "SYKMELDING_STARTER_FOR_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad `should be equal to` "VET_IKKE"
        klippmetrikker[0].klippet `should be equal to` true
    }

    @Test
    @Order(92)
    fun `Reset data`() {
        databaseReset.resetDatabase()
        klippMetrikkRepository.count() shouldBeEqualTo 0
    }

    @Test
    @Order(100)
    fun `Sender inn en kort arbeidstaker søknad`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = gradertSykmeldt(
                    fom = basisdato.minusDays(5),
                    tom = basisdato.minusDays(1)
                )
            )
        )

        val soknaderMetadata = hentSoknaderMetadata(fnr)
        soknaderMetadata shouldHaveSize 1
        soknaderMetadata.first().status shouldBeEqualTo RSSoknadstatus.NY

        val rsSoknad = hentSoknad(
            soknadId = soknaderMetadata.first().id,
            fnr = fnr
        )

        mockFlexSyketilfelleArbeidsgiverperiode(
            arbeidsgiverperiode = Arbeidsgiverperiode(
                antallBrukteDager = 4,
                oppbruktArbeidsgiverperiode = true,
                arbeidsgiverPeriode = Periode(fom = rsSoknad.fom!!, tom = rsSoknad.tom!!)
            )
        )

        SoknadBesvarer(rsSoknad, this, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "NEI")
            .besvarSporsmal(TILBAKE_I_ARBEID, "NEI")
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "NEI")
            .besvarSporsmal(PERMISJON_V2, "NEI")
            .besvarSporsmal(FERIE_V2, "NEI")
            .besvarSporsmal(UTLAND_V2, "NEI")
            .besvarSporsmal(JOBBET_DU_GRADERT + '0', "NEI")
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "NEI")
            .besvarSporsmal(ANDRE_INNTEKTSKILDER_V2, "NEI")
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
            .sendSoknad()

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    @Order(101)
    fun `Lang overlappende sykmelding med forskjellig grad splittes både pga overlapp og lengde`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = gradertSykmeldt(
                    fom = basisdato.minusDays(5),
                    tom = basisdato.plusDays(60),
                    grad = 80
                )
            ),
            forventaSoknader = 3
        )

        val soknaderMetadata = hentSoknaderMetadata(fnr)
        soknaderMetadata shouldHaveSize 4

        soknaderMetadata[0].fom shouldBeEqualTo basisdato.minusDays(5)
        soknaderMetadata[0].tom shouldBeEqualTo basisdato.minusDays(1)
        soknaderMetadata[0].status shouldBeEqualTo RSSoknadstatus.SENDT

        soknaderMetadata[1].fom shouldBeEqualTo basisdato.minusDays(5)
        soknaderMetadata[1].tom shouldBeEqualTo basisdato.minusDays(1)
        soknaderMetadata[1].status shouldBeEqualTo RSSoknadstatus.NY

        soknaderMetadata[2].fom shouldBeEqualTo basisdato
        soknaderMetadata[2].tom shouldBeEqualTo basisdato.plusDays(30)
        soknaderMetadata[2].status shouldBeEqualTo RSSoknadstatus.FREMTIDIG

        soknaderMetadata[3].fom shouldBeEqualTo basisdato.plusDays(31)
        soknaderMetadata[3].tom shouldBeEqualTo basisdato.plusDays(60)
        soknaderMetadata[3].status shouldBeEqualTo RSSoknadstatus.FREMTIDIG

        val klippmetrikker = klippMetrikkRepository.findAll().toList()
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus `should be equal to` "SENDT"
        klippmetrikker[0].variant `should be equal to` "SYKMELDING_STARTER_FOR_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad `should be equal to` "VET_IKKE"
        klippmetrikker[0].klippet `should be equal to` true
    }

    @Test
    @Order(102)
    fun `Reset testdata`() {
        databaseReset.resetDatabase()
        klippMetrikkRepository.count() shouldBeEqualTo 0
    }

    @Test
    @Order(110)
    fun `Arbeidstakersøknad der eldre sykmelding sendes sist, klippes`() {
        val sykmeldingSkrevet = OffsetDateTime.now()

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.minusDays(5),
                    tom = basisdato
                ),
                sykmeldingSkrevet = sykmeldingSkrevet
            )
        )
        val overlappendeSoknad = sendSykmelding(
            sykmeldingKafkaMessage = sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.minusDays(1),
                    tom = basisdato.plusDays(10)
                ),
                sykmeldingSkrevet = sykmeldingSkrevet.minusHours(1)
            )
        ).last()

        overlappendeSoknad.fom shouldBeEqualTo basisdato.plusDays(1)
        overlappendeSoknad.tom shouldBeEqualTo basisdato.plusDays(10)
        overlappendeSoknad.status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG

        val klippmetrikker = klippMetrikkRepository.findAll().toList()
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus `should be equal to` "FREMTIDIG"
        klippmetrikker[0].variant `should be equal to` "SYKMELDING_STARTER_FOR_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad `should be equal to` "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet `should be equal to` true
    }

    @Test
    @Order(111)
    fun `Reset database data`() {
        databaseReset.resetDatabase()
        klippMetrikkRepository.count() shouldBeEqualTo 0
    }
}
