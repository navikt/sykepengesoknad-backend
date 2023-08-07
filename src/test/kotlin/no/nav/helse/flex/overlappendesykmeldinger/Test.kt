package no.nav.helse.flex.overlappendesykmeldinger

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.lang.Exception
import java.time.LocalDate
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
            val farge = "\u001B[32m"
            for (i in 1..20) {
                try {
                    println("${farge}klippSoknadTom $i start")
                    sykepengesoknadDAO.klippSoknadTom(
                        sykepengesoknadUuid = soknad.id,
                        nyTom = soknad.tom!!.minusDays(1),
                        tom = soknad.tom!!,
                        fom = soknad.fom!!
                    )
                    println("${farge}klippSoknadTom $i slutt")
                } catch (e: Exception) {
                    println("${farge}klippSoknadTom $i feilet")
                }
            }
        }.also { threads.add(it) }
        thread {
            val farge = "\u001B[33m"
            for (i in 1..20) {
                try {
                    println("${farge}klippSoknadFom $i start")
                    sykepengesoknadDAO.klippSoknadFom(
                        sykepengesoknadUuid = soknad.id,
                        nyFom = soknad.fom!!.plusDays(1),
                        fom = soknad.fom!!,
                        tom = soknad.tom!!
                    )
                    println("${farge}klippSoknadFom $i slutt")
                } catch (e: Exception) {
                    println("${farge}klippSoknadFom $i feilet")
                }
            }
        }.also { threads.add(it) }

        threads.forEach { it.join() }
    }
}
