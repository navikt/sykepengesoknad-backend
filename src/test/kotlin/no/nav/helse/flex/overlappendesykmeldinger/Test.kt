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
import kotlin.concurrent.thread

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Test : BaseTestClass() {
    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    private final val basisdato = LocalDate.now()
    private val fnr = "11555555555"

    @Test
    @Order(1)
    fun `Fremtidig arbeidstakersøknad opprettes for en sykmelding`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.minusDays(1),
                    tom = basisdato.plusDays(15)
                )
            )
        )
    }

    @Test
    @Order(2)
    fun `Tester concurrency`() {
        val soknad = hentSoknaderMetadata(fnr)[0]
        soknad.soknadstype shouldBeEqualTo RSSoknadstype.ARBEIDSTAKERE
        soknad.status shouldBeEqualTo RSSoknadstatus.FREMTIDIG

        val threads = mutableListOf<Thread>()
        thread {
            for (i in 1..20) {
                println("klippSoknadTom $i start")
                sykepengesoknadDAO.klippSoknadTom(
                    sykepengesoknadUuid = soknad.id,
                    klipp = soknad.tom!!.minusDays(1)
                )
                println("klippSoknadTom $i slutt")
            }
        }.also { threads.add(it) }
        thread {
            for (i in 1..20) {
                println("klippSoknadFom $i start")
                sykepengesoknadDAO.klippSoknadFom(
                    sykepengesoknadUuid = soknad.id,
                    klipp = soknad.fom!!.plusDays(1)
                )
                println("klippSoknadFom $i slutt")
            }
        }.also { threads.add(it) }

        threads.forEach { it.join() }
    }
}
