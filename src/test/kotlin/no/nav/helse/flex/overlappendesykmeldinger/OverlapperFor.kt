package no.nav.helse.flex.overlappendesykmeldinger

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.avbrytSoknad
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidsgiverperiode
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Sykmeldingstype
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.repository.KlippMetrikkRepository
import no.nav.helse.flex.repository.KlippVariant
import no.nav.helse.flex.repository.KlippetSykepengesoknadRepository
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.gradertSykmeldt
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.util.DatoUtil
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be null`
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.awaitility.Awaitility
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit.DAYS

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class OverlapperFor : FellesTestOppsett() {
    @Autowired
    private lateinit var klippetSykepengesoknadRepository: KlippetSykepengesoknadRepository

    @Autowired
    private lateinit var klippMetrikkRepository: KlippMetrikkRepository

    fun String?.tilSoknadsperioder(): List<Soknadsperiode>? =
        if (this == null) {
            null
        } else {
            objectMapper.readValue(this)
        }

    private final val basisdato = LocalDate.now()

    @BeforeEach
    fun opprydding() {
        databaseReset.resetDatabase()
        fakeUnleash.resetAll()
    }

    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.ventPåRecords(3)
    }

    @Test
    fun `Fremtidig arbeidstakersøknad starter før og slutter inni, klippes`() {
        val fnr = "33333333333"

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.plusDays(5),
                        tom = basisdato.plusDays(10),
                    ),
            ),
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato,
                        tom = basisdato.plusDays(7),
                    ),
            ),
        )

        val hentetViaRest = hentSoknaderMetadata(fnr)
        hentetViaRest shouldHaveSize 2

        val klippetSoknad = hentetViaRest[0]
        klippetSoknad.fom shouldBeEqualTo basisdato.plusDays(8)
        klippetSoknad.tom shouldBeEqualTo basisdato.plusDays(10)

        val nyesteSoknad = hentetViaRest[1]
        nyesteSoknad.fom shouldBeEqualTo basisdato
        nyesteSoknad.tom shouldBeEqualTo basisdato.plusDays(7)

        Awaitility
            .await()
            .until { klippetSykepengesoknadRepository.findBySykmeldingUuid(nyesteSoknad.sykmeldingId!!) != null }

        val klipp = klippetSykepengesoknadRepository.findBySykmeldingUuid(nyesteSoknad.sykmeldingId!!)!!
        klipp.sykepengesoknadUuid shouldBeEqualTo klippetSoknad.id
        klipp.sykmeldingUuid shouldBeEqualTo nyesteSoknad.sykmeldingId
        klipp.klippVariant shouldBeEqualTo KlippVariant.SOKNAD_STARTER_FOR_SLUTTER_INNI
        klipp.periodeFor.tilSoknadsperioder() shouldBeEqualTo
            listOf(
                Soknadsperiode(
                    fom = basisdato.plusDays(5),
                    tom = basisdato.plusDays(10),
                    grad = 100,
                    sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG,
                ),
            )
        klipp.periodeEtter.tilSoknadsperioder() shouldBeEqualTo
            listOf(
                Soknadsperiode(
                    fom = basisdato.plusDays(8),
                    tom = basisdato.plusDays(10),
                    grad = 100,
                    sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG,
                ),
            )
        val klippmetrikker = klippMetrikkRepository.findAll().toList().sortedBy { it.variant }
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus `should be equal to` "FREMTIDIG"
        klippmetrikker[0].variant `should be equal to` "SOKNAD_STARTER_FOR_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad `should be equal to` "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet `should be equal to` true
    }

    @Test
    fun `Fremtidig arbeidstakersøknad starter samtidig og slutter inni, klippes`() {
        val fnr = "44444444444"
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
                    heltSykmeldt(
                        fom = basisdato,
                        tom = basisdato.plusDays(7),
                    ),
            ),
        )

        val hentetViaRest = hentSoknaderMetadata(fnr)
        hentetViaRest shouldHaveSize 2

        val klippetSoknad = hentetViaRest[0]
        klippetSoknad.fom shouldBeEqualTo basisdato.plusDays(8)
        klippetSoknad.tom shouldBeEqualTo basisdato.plusDays(10)
        klippetSoknad.soknadPerioder!! shouldHaveSize 1
        klippetSoknad.soknadPerioder[0].fom shouldBeEqualTo basisdato.plusDays(8)
        klippetSoknad.soknadPerioder[0].tom shouldBeEqualTo basisdato.plusDays(10)

        val nyesteSoknad = hentetViaRest[1]
        nyesteSoknad.fom shouldBeEqualTo basisdato
        nyesteSoknad.tom shouldBeEqualTo basisdato.plusDays(7)
        nyesteSoknad.soknadPerioder!! shouldHaveSize 1
        nyesteSoknad.soknadPerioder[0].fom shouldBeEqualTo basisdato
        nyesteSoknad.soknadPerioder[0].tom shouldBeEqualTo basisdato.plusDays(7)

        val klippmetrikker = klippMetrikkRepository.findAll().toList().sortedBy { it.variant }
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus `should be equal to` "FREMTIDIG"
        klippmetrikker[0].variant `should be equal to` "SOKNAD_STARTER_FOR_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad `should be equal to` "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet `should be equal to` true
    }

    @Test
    fun `Ny arbeidstakersøknad starter før og slutter inni, klippes`() {
        val fnr = "66666666666"
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(5),
                        tom = basisdato.minusDays(1),
                    ),
            ),
        )
        val overlappendeSoknad =
            sendSykmelding(
                forventaSoknader = 2,
                sykmeldingKafkaMessage =
                    sykmeldingKafkaMessage(
                        fnr = fnr,
                        sykmeldingsperioder =
                            heltSykmeldt(
                                fom = basisdato.minusDays(10),
                                tom = basisdato.minusDays(2),
                            ),
                    ),
            ).last()

        overlappendeSoknad.fom shouldBeEqualTo basisdato.minusDays(10)
        overlappendeSoknad.tom shouldBeEqualTo basisdato.minusDays(2)
        overlappendeSoknad.status shouldBeEqualTo SoknadsstatusDTO.NY
        hentSoknad(overlappendeSoknad.id, fnr).klippet shouldBeEqualTo false

        val klippetSoknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.id != overlappendeSoknad.id }.id,
                fnr = fnr,
            )

        klippetSoknad.fom shouldBeEqualTo basisdato.minusDays(1)
        klippetSoknad.tom shouldBeEqualTo basisdato.minusDays(1)
        klippetSoknad.klippet shouldBeEqualTo true
        klippetSoknad.status shouldBeEqualTo RSSoknadstatus.NY
        klippetSoknad.sporsmal!!.first { it.tag == FERIE_V2 }.sporsmalstekst shouldBeEqualTo "Tok du ut feriedager i tidsrommet ${
            DatoUtil.formatterPeriode(
                klippetSoknad.fom!!,
                klippetSoknad.tom!!,
            )
        }?"

        val klippmetrikker = klippMetrikkRepository.findAll().toList().sortedBy { it.variant }
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus `should be equal to` "NY"
        klippmetrikker[0].variant `should be equal to` "SOKNAD_STARTER_FOR_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad `should be equal to` "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet `should be equal to` true
    }

    @Test
    fun `Ny arbeidstakersøknad starter før og slutter inni og eldre sykmelding sendes sist, klippes`() {
        val fnr = "66666677777"
        val sykmeldingSkrevet = OffsetDateTime.now()

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(5),
                        tom = basisdato.minusDays(1),
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
                                fom = basisdato.minusDays(10),
                                tom = basisdato.minusDays(2),
                            ),
                        sykmeldingSkrevet = sykmeldingSkrevet.minusHours(1),
                    ),
            ).last()

        overlappendeSoknad.fom shouldBeEqualTo basisdato.minusDays(10)
        overlappendeSoknad.tom shouldBeEqualTo basisdato.minusDays(6)
        overlappendeSoknad.status shouldBeEqualTo SoknadsstatusDTO.NY

        val klippmetrikker = klippMetrikkRepository.findAll().toList()
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus `should be equal to` "NY"
        klippmetrikker[0].variant `should be equal to` "SYKMELDING_STARTER_INNI_SLUTTER_ETTER"
        klippmetrikker[0].endringIUforegrad `should be equal to` "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet `should be equal to` true
    }

    @Test
    fun `Avbrutt arbeidstakersøknad starter før og slutter inni, klippes`() {
        val fnr = "77777777777"
        val soknad =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = basisdato.minusDays(10),
                            tom = basisdato.minusDays(1),
                        ),
                ),
            ).first()

        soknad.status shouldBeEqualTo SoknadsstatusDTO.NY
        soknad.fom shouldBeEqualTo basisdato.minusDays(10)
        soknad.tom shouldBeEqualTo basisdato.minusDays(1)

        avbrytSoknad(soknad.id, fnr)
        hentSoknad(soknad.id, fnr).status shouldBeEqualTo RSSoknadstatus.AVBRUTT
        sykepengesoknadKafkaConsumer.ventPåRecords(1)

        val overlappendeSoknad =
            sendSykmelding(
                forventaSoknader = 2,
                sykmeldingKafkaMessage =
                    sykmeldingKafkaMessage(
                        fnr = fnr,
                        sykmeldingsperioder =
                            heltSykmeldt(
                                fom = basisdato.minusDays(15),
                                tom = basisdato.minusDays(5),
                            ),
                    ),
            ).last()

        overlappendeSoknad.fom shouldBeEqualTo basisdato.minusDays(15)
        overlappendeSoknad.tom shouldBeEqualTo basisdato.minusDays(5)
        overlappendeSoknad.status shouldBeEqualTo SoknadsstatusDTO.NY
        hentSoknad(overlappendeSoknad.id, fnr).klippet shouldBeEqualTo false

        val klippetSoknad = hentSoknad(soknad.id, fnr)
        klippetSoknad.fom shouldBeEqualTo basisdato.minusDays(4)
        klippetSoknad.tom shouldBeEqualTo basisdato.minusDays(1)
        klippetSoknad.klippet shouldBeEqualTo true
        klippetSoknad.status shouldBeEqualTo RSSoknadstatus.AVBRUTT
        klippetSoknad.sporsmal!!.first { it.tag == FERIE_V2 }.sporsmalstekst shouldBeEqualTo "Tok du ut feriedager i tidsrommet ${
            DatoUtil.formatterPeriode(
                klippetSoknad.fom!!,
                klippetSoknad.tom!!,
            )
        }?"
    }

    @Test
    fun `Sendt arbeidstakersøknad starter før og slutter inni, splittes`() {
        val fnr = "88888888888"
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(10),
                        tom = basisdato.minusDays(1),
                    ),
            ),
        )

        val soknaderMetadata = hentSoknaderMetadata(fnr)
        soknaderMetadata shouldHaveSize 1
        soknaderMetadata.first().status shouldBeEqualTo RSSoknadstatus.NY

        val rsSoknad =
            hentSoknad(
                soknadId = soknaderMetadata.first().id,
                fnr = fnr,
            )

        mockFlexSyketilfelleArbeidsgiverperiode(
            arbeidsgiverperiode =
                Arbeidsgiverperiode(
                    antallBrukteDager = 9,
                    oppbruktArbeidsgiverperiode = true,
                    arbeidsgiverPeriode = Periode(fom = rsSoknad.fom!!, tom = rsSoknad.tom!!),
                ),
        )

        // Alle fnr som ikke er '31111111111', '31111111112' eller '31111111113' gjør  at MedlemskapMockDispatcher
        // svarer med status JA, så spørsmål om ARBEID_UTENFOR_NORGE vil ikke bli stilt.
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

        sendSykmelding(
            sykmeldingKafkaMessage =
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder =
                        gradertSykmeldt(
                            fom = basisdato.minusDays(15),
                            tom = basisdato.minusDays(5),
                            grad = 50,
                        ),
                ),
            forventaSoknader = 2,
        )

        val soknader = hentSoknaderMetadata(fnr)
        soknader shouldHaveSize 3

        soknader[0].status shouldBeEqualTo RSSoknadstatus.SENDT
        soknader[0].fom shouldBeEqualTo basisdato.minusDays(10)
        soknader[0].tom shouldBeEqualTo basisdato.minusDays(1)
        hentSoknad(soknader[0].id, fnr).klippet shouldBeEqualTo false

        soknader[1].status shouldBeEqualTo RSSoknadstatus.NY
        soknader[1].fom shouldBeEqualTo basisdato.minusDays(15)
        soknader[1].tom shouldBeEqualTo basisdato.minusDays(11)
        hentSoknad(soknader[1].id, fnr).klippet shouldBeEqualTo false

        soknader[2].status shouldBeEqualTo RSSoknadstatus.NY
        soknader[2].fom shouldBeEqualTo basisdato.minusDays(10)
        soknader[2].tom shouldBeEqualTo basisdato.minusDays(5)
        hentSoknad(soknader[2].id, fnr).klippet shouldBeEqualTo false

        val klippmetrikker = klippMetrikkRepository.findAll().toList()
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus `should be equal to` "SENDT"
        klippmetrikker[0].variant `should be equal to` "SYKMELDING_STARTER_INNI_SLUTTER_ETTER"
        klippmetrikker[0].endringIUforegrad `should be equal to` "VET_IKKE"
        klippmetrikker[0].klippet `should be equal to` true
    }

    @Test
    fun `Fremtidig arbeidstakersøknad med samme tidspunkt for sykmelding skrevet, klippes`() {
        val fnr = "99999999999"
        val sykmeldingSkrevet = OffsetDateTime.now().truncatedTo(DAYS)
        val signaturDato = OffsetDateTime.now()

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato,
                        tom = basisdato.plusDays(10),
                    ),
                sykmeldingSkrevet = sykmeldingSkrevet,
                signaturDato = signaturDato,
            ),
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato,
                        tom = basisdato.plusDays(7),
                    ),
                sykmeldingSkrevet = sykmeldingSkrevet,
                signaturDato = signaturDato.plusHours(1),
            ),
        )

        val hentetViaRest = hentSoknaderMetadata(fnr)
        hentetViaRest shouldHaveSize 2

        val klippetSoknad = hentetViaRest[0]
        klippetSoknad.fom shouldBeEqualTo basisdato.plusDays(8)
        klippetSoknad.tom shouldBeEqualTo basisdato.plusDays(10)
        klippetSoknad.soknadPerioder.`should not be null`().size `should be equal to` 1
        klippetSoknad.soknadPerioder.first().fom shouldBeEqualTo basisdato.plusDays(8)
        klippetSoknad.soknadPerioder.first().tom shouldBeEqualTo basisdato.plusDays(10)

        val nyesteSoknad = hentetViaRest[1]
        nyesteSoknad.fom shouldBeEqualTo basisdato
        nyesteSoknad.tom shouldBeEqualTo basisdato.plusDays(7)
        nyesteSoknad.soknadPerioder.`should not be null`().size `should be equal to` 1
        nyesteSoknad.soknadPerioder.first().fom shouldBeEqualTo basisdato
        nyesteSoknad.soknadPerioder.first().tom shouldBeEqualTo basisdato.plusDays(7)
    }
}
