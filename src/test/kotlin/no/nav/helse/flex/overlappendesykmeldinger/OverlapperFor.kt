package no.nav.helse.flex.overlappendesykmeldinger

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Sykmeldingstype
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.repository.KlippMetrikkRepository
import no.nav.helse.flex.repository.KlippVariant
import no.nav.helse.flex.repository.KlippetSykepengesoknadRepository
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.util.OBJECT_MAPPER
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.awaitility.Awaitility
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
        klippMetrikkRepository.deleteAll()
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
        klippMetrikkRepository.deleteAll()
    }

    @Test
    fun `Ny arbeidstakersøknad starter samtidig og slutter inni, klipper sykmelding`() {
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
        val soknad = sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.minusDays(10),
                    tom = basisdato.minusDays(2),
                ),
            ),
        ).first()

        soknad.fom shouldBeEqualTo basisdato.minusDays(10)
        soknad.tom shouldBeEqualTo basisdato.minusDays(6)

        val klippmetrikker = klippMetrikkRepository.findAll().toList().sortedBy { it.variant }
        klippmetrikker shouldHaveSize 2

        klippmetrikker[0].soknadstatus `should be equal to` "NY"
        klippmetrikker[0].variant `should be equal to` "SOKNAD_STARTER_FOR_SLUTTER_INNI"
        klippmetrikker[0].endringIUforegrad `should be equal to` "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet `should be equal to` false

        klippmetrikker[1].soknadstatus `should be equal to` "NY"
        klippmetrikker[1].variant `should be equal to` "SYKMELDING_STARTER_INNI_SLUTTER_ETTER"
        klippmetrikker[1].endringIUforegrad `should be equal to` "SAMME_UFØREGRAD"
        klippmetrikker[1].klippet `should be equal to` true
        klippMetrikkRepository.deleteAll()
    }
}
