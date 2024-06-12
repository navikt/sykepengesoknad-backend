package no.nav.helse.flex.medlemskap

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSporsmal
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.controller.domain.sykepengesoknad.flatten
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.medIndex
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_MEDLEMSKAP_SPORSMAL
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS
import no.nav.helse.flex.util.DatoUtil
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBe
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

/**
 * Tester at spørsmål om medlemskap blir opprettet og besvart som forventet, inkludert at
 * bruker legger til eller sletter ekstra underspørsmål (perioder).
 *
 * @see MedlemskapSyketilfelleIntegrationTest for testing av forskjellige scenario relatert
 *     til når det stilles medlemskapspørsmål.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MedlemskapSporsmalIntegrationTest : FellesTestOppsett() {
    @Autowired
    private lateinit var medlemskapVurderingRepository: MedlemskapVurderingRepository

    @BeforeAll
    fun konfigurerUnleash() {
        fakeUnleash.resetAll()
        fakeUnleash.enable(UNLEASH_CONTEXT_MEDLEMSKAP_SPORSMAL, UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS)
    }

    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.hentProduserteRecords()
    }

    private val fnr = "31111111111"
    private val fom = LocalDate.of(2023, 1, 1)
    private val tom = LocalDate.of(2023, 1, 7)

    @Test
    @Order(1)
    fun `Oppretter arbeidstakersøknad med status NY`() {
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.UAVKLART,
                    sporsmal =
                        listOf(
                            MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
                            MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE,
                            MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_EØS_OMRÅDE,
                            MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_NORGE,
                        ),
                    kjentOppholdstillatelse =
                        KjentOppholdstillatelse(
                            fom = fom.minusMonths(2),
                            tom = tom.plusMonths(2),
                        ),
                ).serialisertTilString(),
            ),
        )

        val soknader =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    fnr = fnr,
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = fom,
                            tom = tom,
                        ),
                ),
            )

        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.ARBEIDSTAKERE)
        assertThat(soknader.last().status).isEqualTo(SoknadsstatusDTO.NY)
        assertThat(soknader.last().medlemskapVurdering).isEqualTo("UAVKLART")
    }

    @Test
    @Order(2)
    fun `Verifiser at søknaden har spørsmål som forventet`() {
        val soknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )

        assertThat(soknad.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                FERIE_V2,
                PERMISJON_V2,
                medIndex(ARBEID_UNDERVEIS_100_PROSENT, 0),
                ANDRE_INNTEKTSKILDER_V2,
                MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE,
                MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE,
                MEDLEMSKAP_OPPHOLD_UTENFOR_EOS,
                OPPHOLD_UTENFOR_EOS,
                MEDLEMSKAP_OPPHOLDSTILLATELSE_V2,
                TIL_SLUTT,
            ),
        )
    }

    @Test
    @Order(2)
    fun `Response fra LovMe medlemskapvurdering er lagret i databasen`() {
        val medlemskapVurderingDbRecords = medlemskapVurderingRepository.findAll() shouldHaveSize 1
        val medlemskapVurdering = medlemskapVurderingDbRecords.first()

        medlemskapVurdering.fnr `should be equal to` fnr
        medlemskapVurdering.svartype `should be equal to` MedlemskapVurderingSvarType.UAVKLART.toString()
        medlemskapVurdering.sporsmal!!.value `should be equal to`
            listOf(
                MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
                MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE,
                MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_EØS_OMRÅDE,
                MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_NORGE,
            ).serialisertTilString()
        medlemskapVurdering.hentKjentOppholdstillatelse() shouldNotBe null
    }

    @Test
    @Order(2)
    fun `Soknad returneres med kjentOppholdstillatelse`() {
        val soknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )

        soknad.kjentOppholdstillatelse!!.fom shouldBeEqualTo fom.minusMonths(2)
        soknad.kjentOppholdstillatelse!!.tom shouldBeEqualTo tom.plusMonths(2)
    }

    @Test
    @Order(2)
    fun `Spørsmål er riktig sortert`() {
        val soknad = hentSoknadMedStatusNy()

        val index = soknad.sporsmal!!.indexOfFirst { it.tag == ANDRE_INNTEKTSKILDER_V2 }
        index shouldBeEqualTo 5

        soknad.sporsmal!![index].tag shouldBeEqualTo ANDRE_INNTEKTSKILDER_V2
        soknad.sporsmal!![index + 1].tag shouldBeEqualTo MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE
        soknad.sporsmal!![index + 2].tag shouldBeEqualTo MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE
        soknad.sporsmal!![index + 3].tag shouldBeEqualTo MEDLEMSKAP_OPPHOLD_UTENFOR_EOS
        soknad.sporsmal!![index + 4].tag shouldBeEqualTo OPPHOLD_UTENFOR_EOS
        soknad.sporsmal!![index + 5].tag shouldBeEqualTo MEDLEMSKAP_OPPHOLDSTILLATELSE_V2
        soknad.sporsmal!![index + 6].tag shouldBeEqualTo TIL_SLUTT
    }

    @Test
    @Order(3)
    fun `Besvar medlemskapspørsmål om oppholdstillatelse`() {
        val soknad = hentSoknadMedStatusNy()

        SoknadBesvarer(rSSykepengesoknad = soknad, mockMvc = this, fnr = fnr)
            .besvarSporsmal(
                tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_V2,
                svar = "JA",
                ferdigBesvart = false,
            )
            .besvarSporsmal(
                tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_VEDTAKSDATO,
                svar = soknad.fom.toString(),
                ferdigBesvart = false,
            )
            .besvarSporsmal(
                tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_PERIODE,
                svar =
                    DatoUtil.periodeTilJson(
                        fom = soknad.tom!!.minusDays(25),
                        tom = soknad.tom!!.minusDays(5),
                    ),
            )
    }

    @Test
    @Order(3)
    fun `Besvar medlemskapspørsmål om Arbeid Utenfor Norge med to perioder`() {
        val soknadId = hentSoknadMedStatusNy().id

        hentSoknadSomKanBesvares().let {
            val (soknad, soknadBesvarer) = it
            besvarMedlemskapArbeidUtenforNorge(
                soknadBesvarer = soknadBesvarer,
                soknad = soknad,
                index = 0,
            )
        }

        leggTilUndersporsmal(soknadId, MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE)

        hentSoknadSomKanBesvares().let {
            val (soknad, soknadBesvarer) = it
            besvarMedlemskapArbeidUtenforNorge(
                soknadBesvarer = soknadBesvarer,
                soknad = soknad,
                index = 1,
            )
        }

        val lagretSoknad =
            hentSoknad(
                soknadId = soknadId,
                fnr = fnr,
            )
        lagretSoknad.sporsmal!!.first {
            it.tag == MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE
        }.undersporsmal shouldHaveSize 2
    }

    @Test
    @Order(3)
    fun `Besvar medlemskapspørsmål om opphold utenfor Norge med to perioder`() {
        val soknadId = hentSoknadMedStatusNy().id

        hentSoknadSomKanBesvares().let {
            val (soknad, soknadBesvarer) = it
            besvarMedlemskapOppholdUtenforNorge(
                soknadBesvarer = soknadBesvarer,
                soknad = soknad,
                index = 0,
            )
        }

        leggTilUndersporsmal(soknadId, MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE)

        hentSoknadSomKanBesvares().let {
            val (soknad, soknadBesvarer) = it
            besvarMedlemskapOppholdUtenforNorge(
                soknadBesvarer = soknadBesvarer,
                soknad = soknad,
                index = 1,
            )
        }

        val lagretSoknad =
            hentSoknad(
                soknadId = soknadId,
                fnr = fnr,
            )
        lagretSoknad.sporsmal!!.first {
            it.tag == MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE
        }.undersporsmal shouldHaveSize 2
    }

    @Test
    @Order(3)
    fun `Besvar medlemskapspørsmål om opphold utenfor EØS med to perioder`() {
        val soknadId = hentSoknadMedStatusNy().id

        hentSoknadSomKanBesvares().let {
            val (soknad, soknadBesvarer) = it
            besvarMedlemskapOppholdUtenforEos(
                soknadBesvarer = soknadBesvarer,
                soknad = soknad,
                index = 0,
            )
        }

        leggTilUndersporsmal(soknadId, MEDLEMSKAP_OPPHOLD_UTENFOR_EOS)

        hentSoknadSomKanBesvares().let {
            val (soknad, soknadBesvarer) = it
            besvarMedlemskapOppholdUtenforEos(
                soknadBesvarer = soknadBesvarer,
                soknad = soknad,
                index = 1,
            )
        }

        val lagretSoknad =
            hentSoknad(
                soknadId = soknadId,
                fnr = fnr,
            )
        lagretSoknad.sporsmal!!.first {
            it.tag == MEDLEMSKAP_OPPHOLD_UTENFOR_EOS
        }.undersporsmal shouldHaveSize 2
    }

    @Test
    @Order(4)
    fun `Grenseverdier på spørsmål følger perioden til søknaden`() {
        val soknad = hentSoknadMedStatusNy()

        soknad.getSporsmalMedTag("MEDLEMSKAP_OPPHOLDSTILLATELSE_VEDTAKSDATO").let {
            it.min shouldBeEqualTo tom.minusYears(10).format(ISO_LOCAL_DATE)
            it.max shouldBeEqualTo tom.format(ISO_LOCAL_DATE)
        }
        soknad.getSporsmalMedTag("MEDLEMSKAP_OPPHOLDSTILLATELSE_PERIODE").let {
            it.min shouldBeEqualTo tom.minusYears(10).format(ISO_LOCAL_DATE)
            it.max shouldBeEqualTo tom.plusYears(10).format(ISO_LOCAL_DATE)
        }

        val arbeidUtenforNorgePeriodeEn = soknad.getSporsmalMedTag("MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_NAAR_0")
        val arbeidUtenforNorgePeriodeTo = soknad.getSporsmalMedTag("MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_NAAR_1")
        arbeidUtenforNorgePeriodeEn.min shouldBeEqualTo tom.minusYears(10).format(ISO_LOCAL_DATE)
        arbeidUtenforNorgePeriodeEn.max shouldBeEqualTo tom.plusYears(1).format(ISO_LOCAL_DATE)
        arbeidUtenforNorgePeriodeEn.min shouldBeEqualTo arbeidUtenforNorgePeriodeTo.min
        arbeidUtenforNorgePeriodeEn.max shouldBeEqualTo arbeidUtenforNorgePeriodeTo.max

        val oppholdUtenforNorgePeriodeEn = soknad.getSporsmalMedTag("MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_NAAR_0")
        val oppholdUtenforNorgePeriodeTo = soknad.getSporsmalMedTag("MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_NAAR_1")
        oppholdUtenforNorgePeriodeEn.min shouldBeEqualTo tom.minusYears(2).format(ISO_LOCAL_DATE)
        oppholdUtenforNorgePeriodeEn.max shouldBeEqualTo tom.format(ISO_LOCAL_DATE)
        oppholdUtenforNorgePeriodeEn.min shouldBeEqualTo oppholdUtenforNorgePeriodeTo.min
        oppholdUtenforNorgePeriodeEn.max shouldBeEqualTo oppholdUtenforNorgePeriodeTo.max

        val oppholdUtenforEOSPeriodeEn = soknad.getSporsmalMedTag("MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_NAAR_0")
        val oppholdUtenforEOSPeriodeTo = soknad.getSporsmalMedTag("MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_NAAR_1")
        oppholdUtenforEOSPeriodeEn.min shouldBeEqualTo tom.minusYears(2).format(ISO_LOCAL_DATE)
        oppholdUtenforEOSPeriodeEn.max shouldBeEqualTo tom.format(ISO_LOCAL_DATE)
        oppholdUtenforEOSPeriodeEn.min shouldBeEqualTo oppholdUtenforEOSPeriodeTo.min
        oppholdUtenforEOSPeriodeEn.max shouldBeEqualTo oppholdUtenforEOSPeriodeTo.max
    }

    @Test
    @Order(5)
    fun `Slett underspørsmål på medlemskapspørsmål om arbeid utenfor Norge`() {
        val soknadId = hentSoknadMedStatusNy().id
        val hovedsporsmalFor =
            hentSoknad(soknadId, fnr).sporsmal!!.first { it.tag == MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE }

        slettUndersporsmal(
            fnr = fnr,
            soknadId = soknadId,
            sporsmalId = hovedsporsmalFor.id!!,
            undersporsmalId = hovedsporsmalFor.undersporsmal[1].id!!,
        )

        val hovedsporsmalEtter =
            hentSoknad(soknadId, fnr).sporsmal!!.first { it.tag == MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE }
        hovedsporsmalEtter.undersporsmal shouldHaveSize 1
        hovedsporsmalEtter.undersporsmal[0].tag shouldBeEqualTo hovedsporsmalFor.undersporsmal[0].tag

        val (utenIdFor, utenIdEtter) = fjernIdFraHovedsporsmal(hovedsporsmalEtter, hovedsporsmalFor)
        utenIdEtter shouldBeEqualTo utenIdFor
    }

    @Test
    @Order(5)
    fun `Slett underspørsmål på medlemskapspørsmål om opphold utenfor Norge`() {
        val soknadId = hentSoknadMedStatusNy().id
        val hovedsporsmalFor =
            hentSoknad(soknadId, fnr).sporsmal!!.first { it.tag == MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE }

        slettUndersporsmal(
            fnr = fnr,
            soknadId = soknadId,
            sporsmalId = hovedsporsmalFor.id!!,
            undersporsmalId = hovedsporsmalFor.undersporsmal[1].id!!,
        )

        val hovedsporsmalEtter =
            hentSoknad(soknadId, fnr).sporsmal!!.first { it.tag == MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE }
        hovedsporsmalEtter.undersporsmal shouldHaveSize 1
        hovedsporsmalEtter.undersporsmal[0].tag shouldBeEqualTo hovedsporsmalFor.undersporsmal[0].tag

        val (utenIdFor, utenIdEtter) = fjernIdFraHovedsporsmal(hovedsporsmalEtter, hovedsporsmalFor)
        utenIdEtter shouldBeEqualTo utenIdFor
    }

    @Test
    @Order(5)
    fun `Slett underspørsmål på medlemskapspørsmål om opphold utenfor EØS`() {
        val soknadId = hentSoknadMedStatusNy().id
        val hovedsporsmalFor =
            hentSoknad(soknadId, fnr).sporsmal!!.first { it.tag == MEDLEMSKAP_OPPHOLD_UTENFOR_EOS }

        slettUndersporsmal(
            fnr = fnr,
            soknadId = soknadId,
            sporsmalId = hovedsporsmalFor.id!!,
            undersporsmalId = hovedsporsmalFor.undersporsmal[1].id!!,
        )

        val hovedsporsmalEtter =
            hentSoknad(soknadId, fnr).sporsmal!!.first { it.tag == MEDLEMSKAP_OPPHOLD_UTENFOR_EOS }
        hovedsporsmalEtter.undersporsmal shouldHaveSize 1
        hovedsporsmalEtter.undersporsmal[0].tag shouldBeEqualTo hovedsporsmalFor.undersporsmal[0].tag

        val (utenIdFor, utenIdEtter) = fjernIdFraHovedsporsmal(hovedsporsmalEtter, hovedsporsmalFor)
        utenIdEtter shouldBeEqualTo utenIdFor
    }

    @Test
    @Order(6)
    fun `Besvar arbeidstakerspørsmål og send søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()

        hentSoknadSomKanBesvares().let {
            val (_, soknadBesvarer) = it
            besvarArbeidstakerSporsmal(soknadBesvarer)
            val sendtSoknad =
                soknadBesvarer
                    .besvarSporsmal(tag = "TIL_SLUTT", svar = "Svar 1", ferdigBesvart = false)
                    .besvarSporsmal(tag = BEKREFT_OPPLYSNINGER, svar = "CHECKED")
                    .sendSoknad()
            sendtSoknad.status shouldBeEqualTo RSSoknadstatus.SENDT
        }

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader shouldHaveSize 1
        val kafkaSoknad = kafkaSoknader.first()
        kafkaSoknad.status shouldBeEqualTo SoknadsstatusDTO.SENDT

        // Spørsmålene som omhandler medlemskap blir ikke mappet om til eget felt i SykepengesoknadDTO så vi trenger
        // bare å sjekke at spørsmålene er med.
        kafkaSoknad.sporsmal!!.any { it.tag == MEDLEMSKAP_OPPHOLDSTILLATELSE } shouldBeEqualTo false
        kafkaSoknad.sporsmal!!.any { it.tag == MEDLEMSKAP_OPPHOLDSTILLATELSE_V2 } shouldBeEqualTo true
        kafkaSoknad.sporsmal!!.any { it.tag == MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE } shouldBeEqualTo true
        kafkaSoknad.sporsmal!!.any { it.tag == MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE } shouldBeEqualTo true
        kafkaSoknad.sporsmal!!.any { it.tag == MEDLEMSKAP_OPPHOLD_UTENFOR_EOS } shouldBeEqualTo true
        kafkaSoknad.medlemskapVurdering shouldBeEqualTo "UAVKLART"
    }

    @Test
    @Order(7)
    fun `Korrigert søknad har duplisert medlemskapVurdering`() {
        val soknad = hentSoknader(fnr).first()
        val korrigerendeSoknad = korrigerSoknad(soknad.id, fnr)

        mockFlexSyketilfelleArbeidsgiverperiode(andreKorrigerteRessurser = soknad.id)

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = korrigerendeSoknad, mockMvc = this, fnr = fnr)
                .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
                .besvarSporsmal(tag = "TIL_SLUTT", svar = "Svar 2", ferdigBesvart = false)
                .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader shouldHaveSize 1
        val kafkaSoknad = kafkaSoknader.first()

        assertThat(
            medlemskapVurderingRepository.findBySykepengesoknadIdAndFomAndTom(
                sendtSoknad.id,
                sendtSoknad.fom!!,
                sendtSoknad.tom!!,
            ),
        ).isNotNull

        kafkaSoknad.id shouldBeEqualTo sendtSoknad.id
        kafkaSoknad.status shouldBeEqualTo SoknadsstatusDTO.SENDT
        kafkaSoknad.medlemskapVurdering shouldBeEqualTo "UAVKLART"
    }

    private fun hentSoknadMedStatusNy(): RSSykepengesoknad {
        return hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
            fnr = fnr,
        )
    }

    private fun leggTilUndersporsmal(
        soknadId: String,
        tag: String,
    ) {
        val lagretSoknad =
            hentSoknad(
                soknadId = soknadId,
                fnr = fnr,
            )

        val hovedsporsmal = lagretSoknad.sporsmal!!.first { it.tag == tag }
        leggTilUndersporsmal(fnr, soknadId, hovedsporsmal.id!!)
    }

    private fun hentSoknadSomKanBesvares(): Pair<RSSykepengesoknad, SoknadBesvarer> {
        val soknad = hentSoknadMedStatusNy()
        val soknadBesvarer = SoknadBesvarer(rSSykepengesoknad = soknad, mockMvc = this, fnr = fnr)
        return Pair(soknad, soknadBesvarer)
    }

    private fun fjernIdFraHovedsporsmal(
        hovedsporsmalEtter: RSSporsmal,
        hovedsporsmalFor: RSSporsmal,
    ): Pair<List<RSSporsmal>, List<RSSporsmal>> {
        val flattenEtter = listOf(hovedsporsmalEtter).flatten()
        val utenIdEtter = flattenEtter.utenId()
        val flattenFor =
            listOf(hovedsporsmalFor.copy(undersporsmal = listOf(hovedsporsmalFor.undersporsmal[0]))).flatten()
        val utenIdFor = flattenFor.utenId()

        return utenIdFor to utenIdEtter
    }

    private fun besvarArbeidstakerSporsmal(soknadBesvarer: SoknadBesvarer) =
        soknadBesvarer
            .besvarSporsmal(tag = ANSVARSERKLARING, svar = "CHECKED")
            .besvarSporsmal(tag = TILBAKE_I_ARBEID, svar = "NEI")
            .besvarSporsmal(tag = FERIE_V2, svar = "NEI")
            .besvarSporsmal(tag = PERMISJON_V2, svar = "NEI")
            .besvarSporsmal(tag = OPPHOLD_UTENFOR_EOS, svar = "NEI")
            .besvarSporsmal(tag = medIndex(ARBEID_UNDERVEIS_100_PROSENT, 0), svar = "NEI")
            .besvarSporsmal(tag = ANDRE_INNTEKTSKILDER_V2, svar = "NEI")

    private fun besvarMedlemskapArbeidUtenforNorge(
        soknadBesvarer: SoknadBesvarer,
        soknad: RSSykepengesoknad,
        index: Int,
    ) {
        soknadBesvarer
            .besvarSporsmal(
                tag = MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE,
                svar = "JA",
                ferdigBesvart = false,
            )
            .besvarSporsmal(
                tag = medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_ARBEIDSGIVER, index),
                svar = medIndex("Arbeidsgiver", index),
                ferdigBesvart = false,
            )
            .besvarSporsmal(
                tag = medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_HVOR, index),
                svar = medIndex("Land", index),
                ferdigBesvart = false,
            )
            .besvarSporsmal(
                tag = medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_NAAR, index),
                svar =
                    DatoUtil.periodeTilJson(
                        fom = soknad.tom!!.minusDays(25),
                        tom = soknad.tom!!.minusDays(5),
                    ),
            )
    }

    private fun besvarMedlemskapOppholdUtenforNorge(
        soknadBesvarer: SoknadBesvarer,
        soknad: RSSykepengesoknad,
        index: Int,
    ) {
        soknadBesvarer
            .besvarSporsmal(tag = MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE, svar = "JA", ferdigBesvart = false)
            .besvarSporsmal(
                tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_HVOR, index),
                svar = "Land",
                ferdigBesvart = false,
            )
            .besvarSporsmal(
                tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE_FERIE, index),
                svar = "CHECKED",
                ferdigBesvart = false,
            )
            .besvarSporsmal(
                tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_NAAR, index),
                svar =
                    DatoUtil.periodeTilJson(
                        fom = soknad.tom!!.minusDays(25),
                        tom = soknad.tom!!.minusDays(5),
                    ),
            )
    }

    private fun besvarMedlemskapOppholdUtenforEos(
        soknadBesvarer: SoknadBesvarer,
        soknad: RSSykepengesoknad,
        index: Int,
    ) {
        soknadBesvarer
            .besvarSporsmal(tag = MEDLEMSKAP_OPPHOLD_UTENFOR_EOS, svar = "JA", ferdigBesvart = false)
            .besvarSporsmal(
                tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_HVOR, index),
                svar = "Land",
                ferdigBesvart = false,
            )
            .besvarSporsmal(
                tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE_ANNET, index),
                svar = "CHECKED",
                ferdigBesvart = false,
            )
            .besvarSporsmal(
                tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE_ANNET_FRITEKST, index),
                svar = "Handletur",
                ferdigBesvart = false,
            )
            .besvarSporsmal(
                tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_NAAR, index),
                svar =
                    DatoUtil.periodeTilJson(
                        fom = soknad.tom!!.minusDays(25),
                        tom = soknad.tom!!.minusDays(5),
                    ),
            )
    }

    private fun List<RSSporsmal>.utenId(): List<RSSporsmal> {
        return this.map { sporsmal ->
            sporsmal.copy(
                id = "",
                undersporsmal = sporsmal.undersporsmal.utenId(),
                svar = sporsmal.svar.map { it.copy(id = "") },
            )
        }
    }
}
