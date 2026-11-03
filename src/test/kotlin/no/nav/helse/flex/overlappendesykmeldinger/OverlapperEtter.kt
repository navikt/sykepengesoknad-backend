package no.nav.helse.flex.overlappendesykmeldinger

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.helse.flex.FellesTestOppsett
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
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.behandlingsdager
import no.nav.helse.flex.testdata.gradertSykmeldt
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.reisetilskudd
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.util.formatterPeriode
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.ventPåRecords
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class OverlapperEtter : FellesTestOppsett() {
    @Autowired
    private lateinit var aktiveringJob: AktiveringJob

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    lateinit var klippMetrikkRepository: KlippMetrikkRepository

    @Autowired
    private lateinit var klippetSykepengesoknadRepository: KlippetSykepengesoknadRepository

    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.ventPåRecords(12)
    }

    fun String?.tilSoknadsperioder(): List<Soknadsperiode>? =
        if (this == null) {
            null
        } else {
            objectMapper.readValue(this)
        }

    private final val basisdato = LocalDate.now()

    private val fnr = "12345678900"

    @Test
    @Order(1)
    fun `Fremtidig arbeidstakersøknad opprettes for en sykmelding`() {
        val kafkaSoknader =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = basisdato.minusDays(1),
                            tom = basisdato.plusDays(15),
                        ),
                ),
            )
        kafkaSoknader[0].status shouldBe SoknadsstatusDTO.FREMTIDIG

        val hentetViaRest = hentSoknaderMetadata(fnr)
        hentetViaRest shouldHaveSize 1
        hentetViaRest[0].soknadstype shouldBe RSSoknadstype.ARBEIDSTAKERE
        hentetViaRest[0].status shouldBe RSSoknadstatus.FREMTIDIG
    }

    @Test
    @Order(2)
    fun `Fremtidig arbeidstakersøknad opprettes for en overlappende sykmelding i scenario 1`() {
        val kafkaSoknader =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = basisdato,
                            tom = basisdato.plusDays(15),
                        ),
                ),
                oppfolgingsdato = basisdato.minusDays(1),
                forventaSoknader = 2,
            )

        kafkaSoknader[0].status shouldBe SoknadsstatusDTO.FREMTIDIG
        kafkaSoknader[0].fom shouldBe basisdato
        kafkaSoknader[0].tom shouldBe basisdato.plusDays(15)

        kafkaSoknader[1].status shouldBe SoknadsstatusDTO.NY
        kafkaSoknader[1].fom shouldBe basisdato.minusDays(1)
        kafkaSoknader[1].tom shouldBe basisdato.minusDays(1)

        val soknaderMetadata = hentSoknaderMetadata(fnr)
        soknaderMetadata shouldHaveSize 2

        val forsteSoknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )

        forsteSoknad.soknadstype shouldBe RSSoknadstype.ARBEIDSTAKERE
        forsteSoknad.status shouldBe RSSoknadstatus.NY
        forsteSoknad.fom shouldBe basisdato.minusDays(1)
        forsteSoknad.tom shouldBe basisdato.minusDays(1)
        val forsteSoknadSpmFinnes = forsteSoknad.getSporsmalMedTagOrNull(TILBAKE_I_ARBEID)
        forsteSoknadSpmFinnes shouldNotBe null
        val periodeSpmSok1 =
            forsteSoknad
                .getSporsmalMedTagOrNull(FERIE_V2)
                ?.undersporsmal
                ?.first()
        periodeSpmSok1?.min shouldBe basisdato.minusDays(1).toString()
        periodeSpmSok1?.max shouldBe basisdato.minusDays(1).toString()

        val fremtidigSoknad =
            hentSoknad(
                soknadId = soknaderMetadata.last().id,
                fnr = fnr,
            )
        fremtidigSoknad.soknadstype shouldBe RSSoknadstype.ARBEIDSTAKERE
        fremtidigSoknad.status shouldBe RSSoknadstatus.FREMTIDIG
        fremtidigSoknad.fom shouldBe basisdato
        fremtidigSoknad.tom shouldBe basisdato.plusDays(15)

        val klippmetrikker = klippMetrikkRepository.findAll().toList()
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus shouldBe "FREMTIDIG"
        klippmetrikker[0].variant shouldBe "SOKNAD_STARTER_INNI_SLUTTER_ETTER"
        klippmetrikker[0].endringIUforegrad shouldBe "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet shouldBe true
    }

    @Test
    @Order(3)
    fun `Søknadene aktiveres og får spørsmål tilpasset klippingen`() {
        aktiveringJob.bestillAktivering(basisdato.plusDays(16))

        sykepengesoknadKafkaConsumer
            .ventPåRecords(antall = 1)
            .tilSoknader()
            .first()
            .status shouldBe SoknadsstatusDTO.NY

        val soknaderMetadata = hentSoknaderMetadata(fnr)
        soknaderMetadata shouldHaveSize 2

        val forsteSoknad =
            hentSoknad(
                soknadId = soknaderMetadata.first().id,
                fnr = fnr,
            )

        forsteSoknad.soknadstype shouldBe RSSoknadstype.ARBEIDSTAKERE
        forsteSoknad.status shouldBe RSSoknadstatus.NY
        forsteSoknad.fom shouldBe basisdato.minusDays(1)
        forsteSoknad.tom shouldBe basisdato.minusDays(1)
        val forsteSoknadSpmFinnes = forsteSoknad.getSporsmalMedTagOrNull(TILBAKE_I_ARBEID)
        forsteSoknadSpmFinnes shouldNotBe null
        val periodeSpmSok1 =
            forsteSoknad
                .getSporsmalMedTagOrNull(FERIE_V2)
                ?.undersporsmal
                ?.first()
        periodeSpmSok1?.min shouldBe basisdato.minusDays(1).toString()
        periodeSpmSok1?.max shouldBe basisdato.minusDays(1).toString()

        val andreSoknad =
            hentSoknad(
                soknadId = soknaderMetadata.last().id,
                fnr = fnr,
            )
        andreSoknad.soknadstype shouldBe RSSoknadstype.ARBEIDSTAKERE
        andreSoknad.status shouldBe RSSoknadstatus.NY
        andreSoknad.fom shouldBe basisdato
        andreSoknad.tom shouldBe basisdato.plusDays(15)
        val periodeSpmSok2 =
            andreSoknad
                .getSporsmalMedTagOrNull(FERIE_V2)
                ?.undersporsmal
                ?.first()
        periodeSpmSok2?.min shouldBe basisdato.toString()
        periodeSpmSok2?.max shouldBe basisdato.plusDays(15).toString()
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
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato,
                        tom = basisdato.plusDays(15),
                    ),
            ),
        )

        aktiveringJob.bestillAktivering(basisdato.plusDays(16))

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)

        val soknaderMetadata = hentSoknaderMetadata(fnr)
        soknaderMetadata shouldHaveSize 1

        val rsSoknad =
            hentSoknad(
                soknadId = soknaderMetadata.first().id,
                fnr = fnr,
            )

        mockFlexSyketilfelleArbeidsgiverperiode(
            arbeidsgiverperiode =
                Arbeidsgiverperiode(
                    antallBrukteDager = 16,
                    oppbruktArbeidsgiverperiode = true,
                    arbeidsgiverPeriode = Periode(fom = rsSoknad.fom!!, tom = rsSoknad.tom!!),
                ),
        )

        SoknadBesvarer(rsSoknad, this, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(TILBAKE_I_ARBEID, "NEI")
            .besvarSporsmal(PERMISJON_V2, "NEI")
            .besvarSporsmal(FERIE_V2, "NEI")
            .besvarSporsmal(OPPHOLD_UTENFOR_EOS, "NEI")
            .besvarSporsmal(ARBEID_UNDERVEIS_100_PROSENT + '0', "NEI")
            .besvarSporsmal(ANDRE_INNTEKTSKILDER_V2, "NEI")
            .oppsummering()
            .sendSoknad()

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    @Order(11)
    fun `Overlappende sykmelding med samme grad blir klippet`() {
        val soknad =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = basisdato.plusDays(10),
                            tom = basisdato.plusDays(20),
                        ),
                ),
            ).first()

        soknad.fom shouldBe basisdato.plusDays(16)
        soknad.tom shouldBe basisdato.plusDays(20)

        await().until { klippetSykepengesoknadRepository.findBySykmeldingUuid(soknad.sykmeldingId!!) != null }

        val klipp = klippetSykepengesoknadRepository.findBySykmeldingUuid(soknad.sykmeldingId!!)!!
        klipp.sykepengesoknadUuid shouldNotBe soknad.id
        klipp.sykmeldingUuid shouldBe soknad.sykmeldingId
        klipp.klippVariant shouldBe KlippVariant.SYKMELDING_STARTER_FOR_SLUTTER_INNI
        klipp.periodeFor.tilSoknadsperioder() shouldBe
            listOf(
                Soknadsperiode(
                    fom = basisdato.plusDays(10),
                    tom = basisdato.plusDays(20),
                    grad = 100,
                    sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG,
                ),
            )
        klipp.periodeEtter.tilSoknadsperioder() shouldBe
            listOf(
                Soknadsperiode(
                    fom = basisdato.plusDays(16),
                    tom = basisdato.plusDays(20),
                    grad = 100,
                    sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG,
                ),
            )

        val klippmetrikker = klippMetrikkRepository.findAll().toList()
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus shouldBe "SENDT"
        klippmetrikker[0].variant shouldBe "SYKMELDING_STARTER_FOR_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad shouldBe "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet shouldBe true
    }

    @Test
    @Order(12)
    fun `Database tømmes`() {
        databaseReset.resetDatabase()
    }

    @Test
    @Order(20)
    fun `Brukeren har en ny søknad`() {
        val soknad =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = basisdato.minusDays(15),
                            tom = basisdato.minusDays(1),
                        ),
                ),
            ).first()

        soknad.status shouldBe SoknadsstatusDTO.NY
    }

    @Test
    @Order(21)
    fun `Overlappende sykmelding med forskjellig grad blir klippet`() {
        val soknad =
            sendSykmelding(
                forventaSoknader = 2,
                sykmeldingKafkaMessage =
                    sykmeldingKafkaMessage(
                        fnr = fnr,
                        sykmeldingsperioder =
                            gradertSykmeldt(
                                fom = basisdato.minusDays(10),
                                tom = basisdato.plusDays(5),
                                grad = 50,
                            ),
                    ),
            ).last()

        soknad.fom shouldBe basisdato.minusDays(10)
        soknad.tom shouldBe basisdato.plusDays(5)

        val klippmetrikker = klippMetrikkRepository.findAll().toList().sortedBy { it.variant }
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus shouldBe "NY"
        klippmetrikker[0].variant shouldBe "SOKNAD_STARTER_INNI_SLUTTER_ETTER"
        klippmetrikker[0].endringIUforegrad shouldBe "LAVERE_UFØREGRAD"
        klippmetrikker[0].klippet shouldBe true
    }

    @Test
    @Order(22)
    fun `DB tømmes første gang`() {
        databaseReset.resetDatabase()
    }

    @Test
    @Order(30)
    fun `Bruker har en ny søknad og svarer på noen spørsmål`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(15),
                        tom = basisdato.minusDays(5),
                    ),
            ),
        )

        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = fnr,
            )

        SoknadBesvarer(soknaden, this, fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
            .besvarSporsmal(tag = "FERIE_V2", svar = "JA", ferdigBesvart = false)
            .besvarSporsmal(tag = "FERIE_NAR_V2", svar = """{"fom":"${basisdato.minusDays(12)}","tom":"${basisdato.minusDays(5)}"}""")
    }

    @Test
    @Order(31)
    fun `Overlappende sykmelding klipper NY søknad og oppdaterer spørsmål`() {
        val overlappendeSykmeldingId =
            sendSykmelding(
                forventaSoknader = 2,
                sykmeldingKafkaMessage =
                    sykmeldingKafkaMessage(
                        fnr = fnr,
                        sykmeldingsperioder =
                            heltSykmeldt(
                                fom = basisdato.minusDays(10),
                                tom = basisdato.minusDays(1),
                            ),
                    ),
            ).last().sykmeldingId

        val soknaderMetadata = hentSoknaderMetadata(fnr)
        soknaderMetadata shouldHaveSize 2

        val overlappendeSoknad = soknaderMetadata.first { it.sykmeldingId == overlappendeSykmeldingId }
        overlappendeSoknad.fom shouldBe basisdato.minusDays(10)
        overlappendeSoknad.tom shouldBe basisdato.minusDays(1)

        val klippetSoknad =
            hentSoknad(
                soknadId = soknaderMetadata.first { it.sykmeldingId != overlappendeSykmeldingId }.id,
                fnr = fnr,
            )

        klippetSoknad.soknadstype shouldBe RSSoknadstype.ARBEIDSTAKERE
        klippetSoknad.status shouldBe RSSoknadstatus.NY
        klippetSoknad.fom shouldBe basisdato.minusDays(15)
        klippetSoknad.tom shouldBe basisdato.minusDays(11)

        klippetSoknad.sporsmal!!.all { it.svar.isEmpty() } shouldBe true

        val periodeSpm = klippetSoknad.getSporsmalMedTag(FERIE_V2)
        periodeSpm.sporsmalstekst shouldBe "Tok du ut feriedager i tidsrommet ${
            formatterPeriode(
                klippetSoknad.fom!!,
                klippetSoknad.tom!!,
            )
        }?"
        periodeSpm.svar.size shouldBe 0
    }

    @Test
    @Order(32)
    fun `DB tømmes andre gang`() {
        databaseReset.resetDatabase()
    }

    @Test
    @Order(40)
    fun `Bruker har 2 innsendte søknader som overlapper fullstendig`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato,
                        tom = basisdato.plusDays(15),
                    ),
            ),
        )
        aktiveringJob.bestillAktivering(basisdato.plusDays(16))

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)

        val rsSoknader = hentSoknaderMetadata(fnr).shouldHaveSize(1)
        val rsSoknad =
            hentSoknad(
                soknadId = rsSoknader.first().id,
                fnr = fnr,
            )

        mockFlexSyketilfelleArbeidsgiverperiode(
            arbeidsgiverperiode =
                Arbeidsgiverperiode(
                    antallBrukteDager = 16,
                    oppbruktArbeidsgiverperiode = true,
                    arbeidsgiverPeriode = Periode(fom = rsSoknad.fom!!, tom = rsSoknad.tom!!),
                ),
        )

        SoknadBesvarer(rsSoknad, this, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(TILBAKE_I_ARBEID, "NEI")
            .besvarSporsmal(PERMISJON_V2, "NEI")
            .besvarSporsmal(FERIE_V2, "NEI")
            .besvarSporsmal(OPPHOLD_UTENFOR_EOS, "NEI")
            .besvarSporsmal(ARBEID_UNDERVEIS_100_PROSENT + '0', "NEI")
            .besvarSporsmal(ANDRE_INNTEKTSKILDER_V2, "NEI")
            .oppsummering()
            .sendSoknad()

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)

        val identiskSoknad =
            sykepengesoknadDAO
                .finnSykepengesoknad(rsSoknad.id)
                .copy(
                    id = UUID.randomUUID().toString(),
                    sykmeldingId = UUID.randomUUID().toString(),
                )
        sykepengesoknadDAO.lagreSykepengesoknad(identiskSoknad)

        val klippmetrikker = klippMetrikkRepository.findAll().toList().sortedBy { it.variant }
        klippmetrikker shouldHaveSize 0
    }

    @Test
    @Order(41)
    fun `Sykmelding overlapper med tom på de to identiske søknadene`() {
        val soknad =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = basisdato.plusDays(15),
                            tom = basisdato.plusDays(20),
                        ),
                ),
            ).first()

        soknad.fom shouldBe basisdato.plusDays(16)
        soknad.tom shouldBe basisdato.plusDays(20)

        val klippmetrikker = klippMetrikkRepository.findAll().toList().sortedBy { it.variant }
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus shouldBe "SENDT"
        klippmetrikker[0].variant shouldBe "SYKMELDING_STARTER_FOR_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad shouldBe "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet shouldBe true
    }

    @Test
    @Order(43)
    fun `DB tømmes tredje gang`() {
        databaseReset.resetDatabase()
    }

    @Test
    @Order(50)
    fun `Ny behandlingsdager sykmelding klippes ikke`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(15),
                        tom = basisdato.minusDays(5),
                    ),
            ),
        )

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    behandlingsdager(
                        fom = basisdato.minusDays(10),
                        tom = basisdato.minusDays(1),
                        behandlingsdager = 2,
                    ),
            ),
        )

        val soknader = sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr))

        soknader[0].soknadstype shouldBe Soknadstype.ARBEIDSTAKERE
        soknader[0].fom shouldBe basisdato.minusDays(15)
        soknader[0].tom shouldBe basisdato.minusDays(5)
        soknader[0].status shouldBe Soknadstatus.NY

        soknader[1].soknadstype shouldBe Soknadstype.BEHANDLINGSDAGER
        soknader[1].fom shouldBe basisdato.minusDays(10)
        soknader[1].tom shouldBe basisdato.minusDays(1)
        soknader[1].status shouldBe Soknadstatus.NY

        databaseReset.resetDatabase()
        klippMetrikkRepository.count() shouldBe 0
    }

    @Test
    @Order(60)
    fun `Fremtidig behandlingsdager sykmelding klippes ikke`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato,
                        tom = basisdato.plusDays(10),
                    ),
            ),
        )

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    behandlingsdager(
                        fom = basisdato.plusDays(5),
                        tom = basisdato.plusDays(15),
                        behandlingsdager = 2,
                    ),
            ),
        )

        val soknader = sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr))

        soknader[0].fom shouldBe basisdato
        soknader[0].tom shouldBe basisdato.plusDays(10)
        soknader[0].soknadstype shouldBe Soknadstype.ARBEIDSTAKERE
        soknader[0].status shouldBe Soknadstatus.FREMTIDIG

        soknader[1].fom shouldBe basisdato.plusDays(5)
        soknader[1].tom shouldBe basisdato.plusDays(15)
        soknader[1].soknadstype shouldBe Soknadstype.BEHANDLINGSDAGER
        soknader[1].status shouldBe Soknadstatus.FREMTIDIG

        databaseReset.resetDatabase()
        klippMetrikkRepository.count() shouldBe 0
    }

    @Test
    @Order(70)
    fun `Ny reisetilskudd sykmelding klippes ikke`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(15),
                        tom = basisdato.minusDays(5),
                    ),
            ),
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    reisetilskudd(
                        fom = basisdato.minusDays(10),
                        tom = basisdato.minusDays(1),
                    ),
            ),
        )

        val soknader = sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr))

        soknader[0].fom shouldBe basisdato.minusDays(15)
        soknader[0].tom shouldBe basisdato.minusDays(5)
        soknader[0].soknadstype shouldBe Soknadstype.ARBEIDSTAKERE
        soknader[0].status shouldBe Soknadstatus.NY

        soknader[1].fom shouldBe basisdato.minusDays(10)
        soknader[1].tom shouldBe basisdato.minusDays(1)
        soknader[1].soknadstype shouldBe Soknadstype.REISETILSKUDD
        soknader[1].status shouldBe Soknadstatus.NY

        databaseReset.resetDatabase()
        klippMetrikkRepository.count() shouldBe 0
    }

    @Test
    @Order(80)
    fun `Fremtidig reisetiskudd sykmelding klipper klippes ikke`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato,
                        tom = basisdato.plusDays(10),
                    ),
            ),
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    reisetilskudd(
                        fom = basisdato.plusDays(5),
                        tom = basisdato.plusDays(15),
                    ),
            ),
        )

        val soknader = sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr))

        soknader[0].fom shouldBe basisdato
        soknader[0].tom shouldBe basisdato.plusDays(10)
        soknader[0].soknadstype shouldBe Soknadstype.ARBEIDSTAKERE
        soknader[0].status shouldBe Soknadstatus.FREMTIDIG

        soknader[1].fom shouldBe basisdato.plusDays(5)
        soknader[1].tom shouldBe basisdato.plusDays(15)
        soknader[1].soknadstype shouldBe Soknadstype.REISETILSKUDD
        soknader[1].status shouldBe Soknadstatus.FREMTIDIG

        databaseReset.resetDatabase()
        klippMetrikkRepository.count() shouldBe 0
    }

    @Test
    @Order(90)
    fun `Sender inn en arbeidstaker søknad med grad 50`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    gradertSykmeldt(
                        fom = basisdato.minusDays(20),
                        tom = basisdato.minusDays(1),
                        grad = 50,
                    ),
            ),
        )

        val soknaderMetadata = hentSoknaderMetadata(fnr)
        soknaderMetadata shouldHaveSize 1
        soknaderMetadata.first().status shouldBe RSSoknadstatus.NY

        val rsSoknad =
            hentSoknad(
                soknadId = soknaderMetadata.first().id,
                fnr = fnr,
            )

        mockFlexSyketilfelleArbeidsgiverperiode(
            arbeidsgiverperiode =
                Arbeidsgiverperiode(
                    antallBrukteDager = 19,
                    oppbruktArbeidsgiverperiode = true,
                    arbeidsgiverPeriode = Periode(fom = rsSoknad.fom!!, tom = rsSoknad.tom!!),
                ),
        )

        SoknadBesvarer(rsSoknad, this, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(TILBAKE_I_ARBEID, "NEI")
            .besvarSporsmal(PERMISJON_V2, "NEI")
            .besvarSporsmal(FERIE_V2, "NEI")
            .besvarSporsmal(OPPHOLD_UTENFOR_EOS, "NEI")
            .besvarSporsmal(JOBBET_DU_GRADERT + '0', "NEI")
            .besvarSporsmal(ANDRE_INNTEKTSKILDER_V2, "NEI")
            .oppsummering()
            .sendSoknad()

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    @Order(91)
    fun `Overlappende sykmelding med forskjellig grad blir splittet`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    gradertSykmeldt(
                        fom = basisdato.minusDays(5),
                        tom = basisdato,
                        grad = 80,
                    ),
            ),
            forventaSoknader = 2,
        )

        val soknaderMetadata = hentSoknaderMetadata(fnr)
        soknaderMetadata shouldHaveSize 3

        soknaderMetadata[0].fom shouldBe basisdato.minusDays(20)
        soknaderMetadata[0].tom shouldBe basisdato.minusDays(1)
        soknaderMetadata[0].status shouldBe RSSoknadstatus.SENDT

        soknaderMetadata[1].fom shouldBe basisdato.minusDays(5)
        soknaderMetadata[1].tom shouldBe basisdato.minusDays(1)
        soknaderMetadata[1].status shouldBe RSSoknadstatus.NY

        soknaderMetadata[2].fom shouldBe basisdato
        soknaderMetadata[2].tom shouldBe basisdato
        soknaderMetadata[2].status shouldBe RSSoknadstatus.FREMTIDIG

        val klippmetrikker = klippMetrikkRepository.findAll().toList()
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus shouldBe "SENDT"
        klippmetrikker[0].variant shouldBe "SYKMELDING_STARTER_FOR_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad shouldBe "VET_IKKE"
        klippmetrikker[0].klippet shouldBe true
    }

    @Test
    @Order(92)
    fun `Reset data`() {
        databaseReset.resetDatabase()
        klippMetrikkRepository.count() shouldBe 0
    }

    @Test
    @Order(100)
    fun `Sender inn en kort arbeidstaker søknad`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    gradertSykmeldt(
                        fom = basisdato.minusDays(5),
                        tom = basisdato.minusDays(1),
                    ),
            ),
        )

        val soknaderMetadata = hentSoknaderMetadata(fnr)
        soknaderMetadata shouldHaveSize 1
        soknaderMetadata.first().status shouldBe RSSoknadstatus.NY

        val rsSoknad =
            hentSoknad(
                soknadId = soknaderMetadata.first().id,
                fnr = fnr,
            )

        mockFlexSyketilfelleArbeidsgiverperiode(
            arbeidsgiverperiode =
                Arbeidsgiverperiode(
                    antallBrukteDager = 4,
                    oppbruktArbeidsgiverperiode = true,
                    arbeidsgiverPeriode = Periode(fom = rsSoknad.fom!!, tom = rsSoknad.tom!!),
                ),
        )

        SoknadBesvarer(rsSoknad, this, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(TILBAKE_I_ARBEID, "NEI")
            .besvarSporsmal(PERMISJON_V2, "NEI")
            .besvarSporsmal(FERIE_V2, "NEI")
            .besvarSporsmal(OPPHOLD_UTENFOR_EOS, "NEI")
            .besvarSporsmal(JOBBET_DU_GRADERT + '0', "NEI")
            .besvarSporsmal(ANDRE_INNTEKTSKILDER_V2, "NEI")
            .oppsummering()
            .sendSoknad()

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    @Order(101)
    fun `Lang overlappende sykmelding med forskjellig grad splittes både pga overlapp og lengde`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    gradertSykmeldt(
                        fom = basisdato.minusDays(5),
                        tom = basisdato.plusDays(60),
                        grad = 80,
                    ),
            ),
            forventaSoknader = 3,
        )

        val soknaderMetadata = hentSoknaderMetadata(fnr)
        soknaderMetadata shouldHaveSize 4

        soknaderMetadata[0].fom shouldBe basisdato.minusDays(5)
        soknaderMetadata[0].tom shouldBe basisdato.minusDays(1)
        soknaderMetadata[0].status shouldBe RSSoknadstatus.SENDT

        soknaderMetadata[1].fom shouldBe basisdato.minusDays(5)
        soknaderMetadata[1].tom shouldBe basisdato.minusDays(1)
        soknaderMetadata[1].status shouldBe RSSoknadstatus.NY

        soknaderMetadata[2].fom shouldBe basisdato
        soknaderMetadata[2].tom shouldBe basisdato.plusDays(30)
        soknaderMetadata[2].status shouldBe RSSoknadstatus.FREMTIDIG

        soknaderMetadata[3].fom shouldBe basisdato.plusDays(31)
        soknaderMetadata[3].tom shouldBe basisdato.plusDays(60)
        soknaderMetadata[3].status shouldBe RSSoknadstatus.FREMTIDIG

        val klippmetrikker = klippMetrikkRepository.findAll().toList()
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus shouldBe "SENDT"
        klippmetrikker[0].variant shouldBe "SYKMELDING_STARTER_FOR_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad shouldBe "VET_IKKE"
        klippmetrikker[0].klippet shouldBe true
    }

    @Test
    @Order(102)
    fun `Reset testdata`() {
        databaseReset.resetDatabase()
        klippMetrikkRepository.count() shouldBe 0
    }

    @Test
    @Order(110)
    fun `Arbeidstakersøknad der eldre sykmelding sendes sist, klippes`() {
        val sykmeldingSkrevet = OffsetDateTime.now()

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(5),
                        tom = basisdato,
                    ),
                sykmeldingSkrevet = sykmeldingSkrevet,
            ),
        )
        val overlappendeSoknad =
            sendSykmelding(
                sykmeldingKafkaMessage =
                    sykmeldingKafkaMessage(
                        fnr = fnr,
                        sykmeldingsperioder =
                            heltSykmeldt(
                                fom = basisdato.minusDays(1),
                                tom = basisdato.plusDays(10),
                            ),
                        sykmeldingSkrevet = sykmeldingSkrevet.minusHours(1),
                    ),
            ).last()

        overlappendeSoknad.fom shouldBe basisdato.plusDays(1)
        overlappendeSoknad.tom shouldBe basisdato.plusDays(10)
        overlappendeSoknad.status shouldBe SoknadsstatusDTO.FREMTIDIG

        val klippmetrikker = klippMetrikkRepository.findAll().toList()
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus shouldBe "FREMTIDIG"
        klippmetrikker[0].variant shouldBe "SYKMELDING_STARTER_FOR_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad shouldBe "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet shouldBe true
    }

    @Test
    @Order(111)
    fun `Reset database data`() {
        databaseReset.resetDatabase()
        klippMetrikkRepository.count() shouldBe 0
    }
}
