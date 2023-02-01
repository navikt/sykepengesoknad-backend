package no.nav.helse.flex.overlappendesykmeldinger

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.avbrytSoknad
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Sykmeldingstype
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.repository.KlippMetrikkRepository
import no.nav.helse.flex.repository.KlippVariant
import no.nav.helse.flex.repository.KlippetSykepengesoknadRepository
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.FERIE_V2
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.util.DatoUtil
import no.nav.helse.flex.util.OBJECT_MAPPER
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.awaitility.Awaitility
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class OverlapperFor : BaseTestClass() {

    @Autowired
    private lateinit var klippetSykepengesoknadRepository: KlippetSykepengesoknadRepository

    @Autowired
    private lateinit var klippMetrikkRepository: KlippMetrikkRepository

    fun String?.tilSoknadsperioder(): List<Soknadsperiode>? {
        return if (this == null) null
        else OBJECT_MAPPER.readValue(this)
    }

    private final val basisdato = LocalDate.now()

    @BeforeEach
    fun opprydding() {
        databaseReset.resetDatabase()
    }

    @Test
    fun `Fremtidig arbeidstakersøknad starter før og slutter inni, klippes`() {
        val fnr = "33333333333"

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.plusDays(5),
                    tom = basisdato.plusDays(10),
                ),
            ),
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
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

        Awaitility.await().until { klippetSykepengesoknadRepository.findBySykmeldingUuid(nyesteSoknad.sykmeldingId!!) != null }

        val klipp = klippetSykepengesoknadRepository.findBySykmeldingUuid(nyesteSoknad.sykmeldingId!!)!!
        klipp.sykepengesoknadUuid shouldBeEqualTo klippetSoknad.id
        klipp.sykmeldingUuid shouldBeEqualTo nyesteSoknad.sykmeldingId
        klipp.klippVariant shouldBeEqualTo KlippVariant.SOKNAD_STARTER_FOR_SLUTTER_INNI
        klipp.periodeFor.tilSoknadsperioder() shouldBeEqualTo listOf(
            Soknadsperiode(
                fom = basisdato.plusDays(5),
                tom = basisdato.plusDays(10),
                grad = 100,
                sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG,
            )
        )
        klipp.periodeEtter.tilSoknadsperioder() shouldBeEqualTo listOf(
            Soknadsperiode(
                fom = basisdato.plusDays(8),
                tom = basisdato.plusDays(10),
                grad = 100,
                sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG,
            )
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
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato,
                    tom = basisdato.plusDays(10),
                ),
            ),
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
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
        klippetSoknad.soknadPerioder!![0].fom shouldBeEqualTo basisdato.plusDays(8)
        klippetSoknad.soknadPerioder!![0].tom shouldBeEqualTo basisdato.plusDays(10)

        val nyesteSoknad = hentetViaRest[1]
        nyesteSoknad.fom shouldBeEqualTo basisdato
        nyesteSoknad.tom shouldBeEqualTo basisdato.plusDays(7)
        nyesteSoknad.soknadPerioder!! shouldHaveSize 1
        nyesteSoknad.soknadPerioder!![0].fom shouldBeEqualTo basisdato
        nyesteSoknad.soknadPerioder!![0].tom shouldBeEqualTo basisdato.plusDays(7)

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
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.minusDays(5),
                    tom = basisdato.minusDays(1),
                ),
            ),
        )
        val overlappendeSoknad = sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.minusDays(10),
                    tom = basisdato.minusDays(2),
                ),
            ),
        ).first()

        overlappendeSoknad.fom shouldBeEqualTo basisdato.minusDays(10)
        overlappendeSoknad.tom shouldBeEqualTo basisdato.minusDays(2)
        overlappendeSoknad.status shouldBeEqualTo SoknadsstatusDTO.NY

        val klippetSoknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first { it.id != overlappendeSoknad.id }.id,
            fnr = fnr
        )

        klippetSoknad.fom shouldBeEqualTo basisdato.minusDays(1)
        klippetSoknad.tom shouldBeEqualTo basisdato.minusDays(1)
        klippetSoknad.status shouldBeEqualTo RSSoknadstatus.NY
        klippetSoknad.sporsmal!!.first { it.tag == FERIE_V2 }.sporsmalstekst shouldBeEqualTo "Tok du ut feriedager i tidsrommet ${
        DatoUtil.formatterPeriode(
            klippetSoknad.fom!!,
            klippetSoknad.tom!!
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
    fun `Avbrutt arbeidstakersøknad starter før og slutter inni, klippes`() {
        val fnr = "77777777777"
        val soknad = sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
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

        val overlappendeSoknad = sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.minusDays(15),
                    tom = basisdato.minusDays(5),
                ),
            ),
        ).first()

        overlappendeSoknad.fom shouldBeEqualTo basisdato.minusDays(15)
        overlappendeSoknad.tom shouldBeEqualTo basisdato.minusDays(5)
        overlappendeSoknad.status shouldBeEqualTo SoknadsstatusDTO.NY

        val klippetSoknad = hentSoknad(soknad.id, fnr)
        klippetSoknad.fom shouldBeEqualTo basisdato.minusDays(4)
        klippetSoknad.tom shouldBeEqualTo basisdato.minusDays(1)
        klippetSoknad.status shouldBeEqualTo RSSoknadstatus.AVBRUTT
        klippetSoknad.sporsmal!!.first { it.tag == FERIE_V2 }.sporsmalstekst shouldBeEqualTo "Tok du ut feriedager i tidsrommet ${
        DatoUtil.formatterPeriode(
            klippetSoknad.fom!!,
            klippetSoknad.tom!!
        )
        }?"
    }
}
