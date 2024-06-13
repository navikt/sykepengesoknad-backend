package no.nav.helse.flex.overlappendesykmeldinger

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidsgiverperiode
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.repository.KlippMetrikkRepository
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.testdata.gradertSykmeldt
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class OverlapperFullstendig : FellesTestOppsett() {
    private final val basisdato = LocalDate.now()
    private final val fnr = "11111111111"

    @Autowired
    private lateinit var klippMetrikkRepository: KlippMetrikkRepository

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @BeforeEach
    fun setUp() {
        databaseReset.resetDatabase()
    }

    @Test
    fun `Overlapper eksakt, sletter den overlappede søknaden`() {
        val meldingerPaKafka = mutableListOf<SykepengesoknadDTO>()

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.plusDays(5),
                        tom = basisdato.plusDays(10),
                    ),
            ),
        ).also { meldingerPaKafka.addAll(it) }
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.plusDays(5),
                        tom = basisdato.plusDays(10),
                    ),
            ),
            forventaSoknader = 2,
        ).also { meldingerPaKafka.addAll(it) }

        meldingerPaKafka.shouldHaveSize(3)

        meldingerPaKafka[0].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[0].fom shouldBeEqualTo basisdato.plusDays(5)
        meldingerPaKafka[0].tom shouldBeEqualTo basisdato.plusDays(10)

        meldingerPaKafka[1].status shouldBeEqualTo SoknadsstatusDTO.SLETTET
        meldingerPaKafka[1].fom shouldBeEqualTo basisdato.plusDays(5)
        meldingerPaKafka[1].tom shouldBeEqualTo basisdato.plusDays(10)

        meldingerPaKafka[2].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[2].fom shouldBeEqualTo basisdato.plusDays(5)
        meldingerPaKafka[2].tom shouldBeEqualTo basisdato.plusDays(10)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        hentetViaRest shouldHaveSize 1

        hentetViaRest[0].fom shouldBeEqualTo basisdato.plusDays(5)
        hentetViaRest[0].tom shouldBeEqualTo basisdato.plusDays(10)
        hentSoknad(hentetViaRest[0].id, fnr).klippet shouldBeEqualTo false

        val klippmetrikker = klippMetrikkRepository.findAll().toList().sortedBy { it.variant }
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus `should be equal to` "FREMTIDIG"
        klippmetrikker[0].variant `should be equal to` "SOKNAD_STARTER_FOR_SLUTTER_ETTER"
        klippmetrikker[0].endringIUforegrad `should be equal to` "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet `should be equal to` true
    }

    @Test
    fun `Overlapper fullstendig`() {
        val meldingerPaKafka = mutableListOf<SykepengesoknadDTO>()

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.plusDays(5),
                        tom = basisdato.plusDays(10),
                    ),
            ),
        ).also { meldingerPaKafka.addAll(it) }
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato,
                        tom = basisdato.plusDays(15),
                    ),
            ),
            forventaSoknader = 2,
        ).also { meldingerPaKafka.addAll(it) }

        meldingerPaKafka[0].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[0].fom shouldBeEqualTo basisdato.plusDays(5)
        meldingerPaKafka[0].tom shouldBeEqualTo basisdato.plusDays(10)

        meldingerPaKafka[1].status shouldBeEqualTo SoknadsstatusDTO.SLETTET
        meldingerPaKafka[1].fom shouldBeEqualTo basisdato.plusDays(5)
        meldingerPaKafka[1].tom shouldBeEqualTo basisdato.plusDays(10)

        meldingerPaKafka[2].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[2].fom shouldBeEqualTo basisdato
        meldingerPaKafka[2].tom shouldBeEqualTo basisdato.plusDays(15)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        hentetViaRest shouldHaveSize 1

        hentetViaRest[0].fom shouldBeEqualTo basisdato
        hentetViaRest[0].tom shouldBeEqualTo basisdato.plusDays(15)
        hentSoknad(hentetViaRest[0].id, fnr).klippet shouldBeEqualTo false

        val klippmetrikker = klippMetrikkRepository.findAll().toList().sortedBy { it.variant }
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus `should be equal to` "FREMTIDIG"
        klippmetrikker[0].variant `should be equal to` "SOKNAD_STARTER_FOR_SLUTTER_ETTER"
        klippmetrikker[0].endringIUforegrad `should be equal to` "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet `should be equal to` true
    }

    @Test
    fun `Overlapper med samme fom og etter tom`() {
        val meldingerPaKafka = mutableListOf<SykepengesoknadDTO>()

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato,
                        tom = basisdato.plusDays(5),
                    ),
            ),
        ).also { meldingerPaKafka.addAll(it) }
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato,
                        tom = basisdato.plusDays(10),
                    ),
            ),
            forventaSoknader = 2,
        ).also { meldingerPaKafka.addAll(it) }

        meldingerPaKafka[0].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[0].fom shouldBeEqualTo basisdato
        meldingerPaKafka[0].tom shouldBeEqualTo basisdato.plusDays(5)

        meldingerPaKafka[1].status shouldBeEqualTo SoknadsstatusDTO.SLETTET
        meldingerPaKafka[1].fom shouldBeEqualTo basisdato
        meldingerPaKafka[1].tom shouldBeEqualTo basisdato.plusDays(5)

        meldingerPaKafka[2].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[2].fom shouldBeEqualTo basisdato
        meldingerPaKafka[2].tom shouldBeEqualTo basisdato.plusDays(10)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        hentetViaRest shouldHaveSize 1

        hentetViaRest[0].fom shouldBeEqualTo basisdato
        hentetViaRest[0].tom shouldBeEqualTo basisdato.plusDays(10)
        hentSoknad(hentetViaRest[0].id, fnr).klippet shouldBeEqualTo false

        val klippmetrikker = klippMetrikkRepository.findAll().toList().sortedBy { it.variant }
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus `should be equal to` "FREMTIDIG"
        klippmetrikker[0].variant `should be equal to` "SOKNAD_STARTER_FOR_SLUTTER_ETTER"
        klippmetrikker[0].endringIUforegrad `should be equal to` "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet `should be equal to` true
    }

    @Test
    fun `Overlapper fullstendig med en sendt søknad og splittes opp i 3`() {
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
            .besvarSporsmal(TIL_SLUTT, "Jeg lover å ikke lyve!", ferdigBesvart = false)
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
            .sendSoknad()

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)

        sendSykmelding(
            sykmeldingKafkaMessage =
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder =
                        gradertSykmeldt(
                            fom = basisdato.minusDays(15),
                            tom = basisdato.plusDays(10),
                            grad = 50,
                        ),
                ),
            forventaSoknader = 3,
        )

        val soknader = hentSoknaderMetadata(fnr)
        soknader shouldHaveSize 4

        soknader[0].status shouldBeEqualTo RSSoknadstatus.SENDT
        soknader[0].fom shouldBeEqualTo basisdato.minusDays(10)
        soknader[0].tom shouldBeEqualTo basisdato.minusDays(1)
        hentSoknad(soknader[0].id, fnr).klippet shouldBeEqualTo false

        soknader[1].status shouldBeEqualTo RSSoknadstatus.NY
        soknader[1].fom shouldBeEqualTo basisdato.minusDays(15)
        soknader[1].tom shouldBeEqualTo basisdato.minusDays(11)
        hentSoknad(soknader[0].id, fnr).klippet shouldBeEqualTo false

        soknader[2].status shouldBeEqualTo RSSoknadstatus.NY
        soknader[2].fom shouldBeEqualTo basisdato.minusDays(10)
        soknader[2].tom shouldBeEqualTo basisdato.minusDays(1)
        hentSoknad(soknader[0].id, fnr).klippet shouldBeEqualTo false

        soknader[3].status shouldBeEqualTo RSSoknadstatus.FREMTIDIG
        soknader[3].fom shouldBeEqualTo basisdato
        soknader[3].tom shouldBeEqualTo basisdato.plusDays(10)
        hentSoknad(soknader[0].id, fnr).klippet shouldBeEqualTo false

        val klippmetrikker = klippMetrikkRepository.findAll().toList()
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus `should be equal to` "SENDT"
        klippmetrikker[0].variant `should be equal to` "SYKMELDING_STARTER_INNI_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad `should be equal to` "VET_IKKE"
        klippmetrikker[0].klippet `should be equal to` true
    }

    @Test
    fun `Eldre sykmelding overlapper fullstendig med fremtidig søknad, klippes`() {
        val sykmeldingSkrevet = OffsetDateTime.now()
        val meldingerPaKafka = mutableListOf<SykepengesoknadDTO>()

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(5),
                        tom = basisdato.plusDays(5),
                    ),
                sykmeldingSkrevet = sykmeldingSkrevet,
            ),
        ).also { meldingerPaKafka.addAll(it) }
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(10),
                        tom = basisdato.plusDays(10),
                    ),
                sykmeldingSkrevet = sykmeldingSkrevet.minusHours(1),
            ),
            forventaSoknader = 2,
        ).also { meldingerPaKafka.addAll(it) }

        meldingerPaKafka[0].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[0].fom shouldBeEqualTo basisdato.minusDays(5)
        meldingerPaKafka[0].tom shouldBeEqualTo basisdato.plusDays(5)

        meldingerPaKafka[2].status shouldBeEqualTo SoknadsstatusDTO.NY
        meldingerPaKafka[2].fom shouldBeEqualTo basisdato.minusDays(10)
        meldingerPaKafka[2].tom shouldBeEqualTo basisdato.minusDays(6)

        meldingerPaKafka[1].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[1].fom shouldBeEqualTo basisdato.plusDays(6)
        meldingerPaKafka[1].tom shouldBeEqualTo basisdato.plusDays(10)

        val klippmetrikker = klippMetrikkRepository.findAll().toList().sortedBy { it.variant }
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus `should be equal to` "FREMTIDIG"
        klippmetrikker[0].variant `should be equal to` "SYKMELDING_STARTER_INNI_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad `should be equal to` "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet `should be equal to` true
    }

    @Test
    fun `Eldre sykmelding med flere perioder overlapper fullstendig, klippes`() {
        val sykmeldingSkrevet = OffsetDateTime.now()
        val meldingerPaKafka = mutableListOf<SykepengesoknadDTO>()

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(5),
                        tom = basisdato.plusDays(5),
                    ),
                sykmeldingSkrevet = sykmeldingSkrevet,
            ),
        ).also { meldingerPaKafka.addAll(it) }
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(10),
                        tom = basisdato.minusDays(2),
                    ) +
                        gradertSykmeldt(
                            fom = basisdato.minusDays(1),
                            tom = basisdato.plusDays(1),
                        ) +
                        heltSykmeldt(
                            fom = basisdato.plusDays(2),
                            tom = basisdato.plusDays(10),
                        ),
                sykmeldingSkrevet = sykmeldingSkrevet.minusHours(1),
            ),
            forventaSoknader = 2,
        ).also { meldingerPaKafka.addAll(it) }

        meldingerPaKafka[0].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[0].fom shouldBeEqualTo basisdato.minusDays(5)
        meldingerPaKafka[0].tom shouldBeEqualTo basisdato.plusDays(5)

        meldingerPaKafka[2].status shouldBeEqualTo SoknadsstatusDTO.NY
        meldingerPaKafka[2].fom shouldBeEqualTo basisdato.minusDays(10)
        meldingerPaKafka[2].tom shouldBeEqualTo basisdato.minusDays(6)

        meldingerPaKafka[1].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[1].fom shouldBeEqualTo basisdato.plusDays(6)
        meldingerPaKafka[1].tom shouldBeEqualTo basisdato.plusDays(10)

        val klippmetrikker = klippMetrikkRepository.findAll().toList().sortedBy { it.variant }
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus `should be equal to` "FREMTIDIG"
        klippmetrikker[0].variant `should be equal to` "SYKMELDING_STARTER_INNI_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad `should be equal to` "FLERE_PERIODER"
        klippmetrikker[0].klippet `should be equal to` true
    }

    @Test
    fun `Eldre sykmelding overlapper fullstendig med 2 fremtidig søknad som har like perioder, klippes`() {
        val sykmeldingSkrevet = OffsetDateTime.now()

        val soknad =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = basisdato.minusDays(5),
                            tom = basisdato.plusDays(5),
                        ),
                    sykmeldingSkrevet = sykmeldingSkrevet,
                ),
            ).first()

        val identiskSoknad =
            sykepengesoknadDAO
                .finnSykepengesoknad(soknad.id)
                .copy(
                    id = UUID.randomUUID().toString(),
                    sykmeldingId = UUID.randomUUID().toString(),
                )
        sykepengesoknadDAO.lagreSykepengesoknad(identiskSoknad)

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(10),
                        tom = basisdato.plusDays(10),
                    ),
                sykmeldingSkrevet = sykmeldingSkrevet.minusHours(1),
            ),
            forventaSoknader = 2,
        )

        val soknader = hentSoknaderMetadata(fnr)
        soknader shouldHaveSize 4

        soknader[0].status shouldBeEqualTo RSSoknadstatus.FREMTIDIG
        soknader[0].fom shouldBeEqualTo basisdato.minusDays(5)
        soknader[0].tom shouldBeEqualTo basisdato.plusDays(5)

        soknader[1].status shouldBeEqualTo RSSoknadstatus.FREMTIDIG
        soknader[1].fom shouldBeEqualTo basisdato.minusDays(5)
        soknader[1].tom shouldBeEqualTo basisdato.plusDays(5)

        soknader[2].status shouldBeEqualTo RSSoknadstatus.NY
        soknader[2].fom shouldBeEqualTo basisdato.minusDays(10)
        soknader[2].tom shouldBeEqualTo basisdato.minusDays(6)

        soknader[3].status shouldBeEqualTo RSSoknadstatus.FREMTIDIG
        soknader[3].fom shouldBeEqualTo basisdato.plusDays(6)
        soknader[3].tom shouldBeEqualTo basisdato.plusDays(10)

        val klippmetrikker = klippMetrikkRepository.findAll().toList().sortedBy { it.variant }
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus `should be equal to` "FREMTIDIG"
        klippmetrikker[0].variant `should be equal to` "SYKMELDING_STARTER_INNI_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad `should be equal to` "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet `should be equal to` true
    }
}
