package no.nav.helse.flex.overlappendesykmeldinger

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.lang.Exception
import java.time.LocalDate
import kotlin.concurrent.thread

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ConcurrentKlipp : BaseTestClass() {
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
    fun `Klipper fom og tom samtidig`() {
        // TODO: Sett opp dette på samme måte som vi gjorde i flex-inntektsmelding-status
        val threads = mutableListOf<Thread>()
        thread {
            val farge = "\u001B[32m"
            for (i in 1..20) {
                try {
                    val soknad = hentSoknaderMetadata(fnr)[0]
                    println("${farge}klippSoknadTom $i start")
                    sykepengesoknadDAO.klippSoknadTom(
                        sykepengesoknadUuid = soknad.id,
                        nyTom = soknad.tom!!.minusDays(1),
                        tom = soknad.tom!!,
                        fom = soknad.fom!!
                    )
                    println("${farge}klippSoknadTom $i slutt")
                } catch (e: Exception) {
                    println("${farge}klippSoknadTom $i feilet, ${e.message}")
                }
            }
        }.also { threads.add(it) }
        thread {
            val farge = "\u001B[33m"
            for (i in 1..20) {
                try {
                    val soknad = hentSoknaderMetadata(fnr)[0]
                    println("${farge}klippSoknadFom $i start")
                    sykepengesoknadDAO.klippSoknadFom(
                        sykepengesoknadUuid = soknad.id,
                        nyFom = soknad.fom!!.plusDays(1),
                        fom = soknad.fom!!,
                        tom = soknad.tom!!
                    )
                    println("${farge}klippSoknadFom $i slutt")
                } catch (e: Exception) {
                    println("${farge}klippSoknadFom $i feilet, ${e.message}")
                }
            }
        }.also { threads.add(it) }

        threads.forEach { it.join() }
    }
}
