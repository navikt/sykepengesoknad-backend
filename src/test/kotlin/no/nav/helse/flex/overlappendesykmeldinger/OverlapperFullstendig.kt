package no.nav.helse.flex.overlappendesykmeldinger

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidsgiverperiode
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.repository.KlippMetrikkRepository
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.ANDRE_INNTEKTSKILDER_V2
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING
import no.nav.helse.flex.soknadsopprettelse.ARBEID_UNDERVEIS_100_PROSENT
import no.nav.helse.flex.soknadsopprettelse.ARBEID_UTENFOR_NORGE
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER
import no.nav.helse.flex.soknadsopprettelse.FERIE_V2
import no.nav.helse.flex.soknadsopprettelse.FRAVAR_FOR_SYKMELDINGEN
import no.nav.helse.flex.soknadsopprettelse.PERMISJON_V2
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_I_ARBEID
import no.nav.helse.flex.soknadsopprettelse.UTLAND_V2
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

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class OverlapperFullstendig : BaseTestClass() {

    private final val basisdato = LocalDate.now()
    private final val fnr = "11111111111"

    @Autowired
    private lateinit var klippMetrikkRepository: KlippMetrikkRepository

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
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.plusDays(5),
                    tom = basisdato.plusDays(10)
                )
            )
        ).also { meldingerPaKafka.addAll(it) }
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.plusDays(5),
                    tom = basisdato.plusDays(10)
                )
            ),
            forventaSoknader = 2
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
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.plusDays(5),
                    tom = basisdato.plusDays(10)
                )
            )
        ).also { meldingerPaKafka.addAll(it) }
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato,
                    tom = basisdato.plusDays(15)
                )
            ),
            forventaSoknader = 2

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
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato,
                    tom = basisdato.plusDays(5)
                )
            )
        ).also { meldingerPaKafka.addAll(it) }
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato,
                    tom = basisdato.plusDays(10)
                )
            ),
            forventaSoknader = 2

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
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.minusDays(10),
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
                antallBrukteDager = 9,
                oppbruktArbeidsgiverperiode = true,
                arbeidsgiverPeriode = Periode(fom = rsSoknad.fom!!, tom = rsSoknad.tom!!)
            )
        )

        SoknadBesvarer(rsSoknad, this, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(FRAVAR_FOR_SYKMELDINGEN, "NEI")
            .besvarSporsmal(TILBAKE_I_ARBEID, "NEI")
            .besvarSporsmal(FRAVAR_FOR_SYKMELDINGEN, "NEI")
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

        sendSykmelding(
            sykmeldingKafkaMessage = sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = gradertSykmeldt(
                    fom = basisdato.minusDays(15),
                    tom = basisdato.plusDays(10),
                    grad = 50
                )
            ),
            forventaSoknader = 3
        )

        val soknader = hentSoknaderMetadata(fnr)
        soknader shouldHaveSize 4

        soknader[0].status shouldBeEqualTo RSSoknadstatus.SENDT
        soknader[0].fom shouldBeEqualTo basisdato.minusDays(10)
        soknader[0].tom shouldBeEqualTo basisdato.minusDays(1)

        soknader[2].status shouldBeEqualTo RSSoknadstatus.NY
        soknader[2].fom shouldBeEqualTo basisdato.minusDays(15)
        soknader[2].tom shouldBeEqualTo basisdato.minusDays(11)

        soknader[1].status shouldBeEqualTo RSSoknadstatus.NY
        soknader[1].fom shouldBeEqualTo basisdato.minusDays(10)
        soknader[1].tom shouldBeEqualTo basisdato.minusDays(1)

        soknader[3].status shouldBeEqualTo RSSoknadstatus.FREMTIDIG
        soknader[3].fom shouldBeEqualTo basisdato
        soknader[3].tom shouldBeEqualTo basisdato.plusDays(10)

        val klippmetrikker = klippMetrikkRepository.findAll().toList()
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus `should be equal to` "SENDT"
        klippmetrikker[0].variant `should be equal to` "SYKMELDING_STARTER_INNI_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad `should be equal to` "VET_IKKE"
        klippmetrikker[0].klippet `should be equal to` true
    }
}
