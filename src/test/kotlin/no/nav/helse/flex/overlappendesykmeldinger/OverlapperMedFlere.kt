package no.nav.helse.flex.overlappendesykmeldinger

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.Sykmeldingstype
import no.nav.helse.flex.hentProduserteRecords
import no.nav.helse.flex.overlappendesykmeldinger.OverlapperMedFlere.OverlappTester.*
import no.nav.helse.flex.repository.KlippMetrikkRepository
import no.nav.helse.flex.repository.SoknadLagrer
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.testdata.gradertSykmeldt
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.util.serialisertTilString
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class OverlapperMedFlere : BaseTestClass() {

    @Autowired
    private lateinit var soknadLagrer: SoknadLagrer

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    private lateinit var overlappTester: OverlappTester

    @Autowired
    private lateinit var klippMetrikkRepository: KlippMetrikkRepository

    @BeforeAll
    fun init() {
        overlappTester = OverlappTester(
            soknadLagrer = soknadLagrer,
            sykepengesoknadDAO = sykepengesoknadDAO,
            baseTestClass = this,
            skalPrintePerioder = true
        )
    }

    @AfterEach
    fun clenUp() {
        databaseReset.resetDatabase()
        do {
            val cr = sykepengesoknadKafkaConsumer.hentProduserteRecords()
            if (cr.isNotEmpty()) {
                println("GJENSTÅENDE SØKNADER PÅ TOPIC ${cr.first().value().serialisertTilString()}")
            }
        } while (cr.isNotEmpty())
    }

    @Test
    fun `Overlapper i midten av en lang rekke med fremtidige søknader`() {
        overlappTester.testKlipp(
            forventaSoknadPaKafka = 4,
            eksisterendeSoknader = listOf(
                Soknad(
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 1, 1),
                            tom = LocalDate.of(2022, 1, 31),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                ),
                Soknad(
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 2, 1),
                            tom = LocalDate.of(2022, 2, 28),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                ),
                Soknad(
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 3, 1),
                            tom = LocalDate.of(2022, 3, 31),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                )
            ),
            overlappendeSoknad = Soknad(
                soknadPerioder = listOf(
                    Soknadsperiode(
                        fom = LocalDate.of(2022, 1, 15),
                        tom = LocalDate.of(2022, 3, 15),
                        grad = 100,
                        sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                    )
                )
            ),
            dagensDato = LocalDate.of(2022, 1, 1),
            forventetResultat = listOf(
                Soknad(
                    status = Soknadstatus.FREMTIDIG,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 1, 1),
                            tom = LocalDate.of(2022, 1, 14),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                ),
                Soknad(
                    status = Soknadstatus.FREMTIDIG,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 1, 15),
                            tom = LocalDate.of(2022, 2, 13),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                ),
                Soknad(
                    status = Soknadstatus.FREMTIDIG,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 2, 14),
                            tom = LocalDate.of(2022, 3, 15),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                ),
                Soknad(
                    status = Soknadstatus.FREMTIDIG,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 3, 16),
                            tom = LocalDate.of(2022, 3, 31),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                )
            )
        )
        val klippmetrikker = klippMetrikkRepository.findAll().toList().sortedBy { it.variant }
        klippmetrikker shouldHaveSize 3

        klippmetrikker[0].soknadstatus `should be equal to` "FREMTIDIG"
        klippmetrikker[0].variant `should be equal to` "SOKNAD_STARTER_FOR_SLUTTER_ETTER"
        klippmetrikker[0].endringIUforegrad `should be equal to` "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet `should be equal to` true
        klippmetrikker[1].soknadstatus `should be equal to` "FREMTIDIG"
        klippmetrikker[1].variant `should be equal to` "SOKNAD_STARTER_FOR_SLUTTER_INNI"
        klippmetrikker[1].endringIUforegrad `should be equal to` "SAMME_UFØREGRAD"
        klippmetrikker[1].klippet `should be equal to` true
        klippmetrikker[2].soknadstatus `should be equal to` "FREMTIDIG"
        klippmetrikker[2].variant `should be equal to` "SOKNAD_STARTER_INNI_SLUTTER_ETTER"
        klippmetrikker[2].endringIUforegrad `should be equal to` "SAMME_UFØREGRAD"
        klippmetrikker[2].klippet `should be equal to` true
    }

    @Test
    fun `Overlapper i midten av en lang rekke med nye søknader`() {
        overlappTester.testKlipp(
            forventaSoknadPaKafka = 5,
            eksisterendeSoknader = listOf(
                Soknad(
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 1, 1),
                            tom = LocalDate.of(2022, 1, 31),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                ),
                Soknad(
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 2, 1),
                            tom = LocalDate.of(2022, 2, 28),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                ),
                Soknad(
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 3, 1),
                            tom = LocalDate.of(2022, 3, 31),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                )
            ),
            overlappendeSoknad = Soknad(
                soknadPerioder = listOf(
                    Soknadsperiode(
                        fom = LocalDate.of(2022, 1, 15),
                        tom = LocalDate.of(2022, 3, 15),
                        grad = 100,
                        sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                    )
                )
            ),
            dagensDato = LocalDate.of(2022, 4, 1),
            forventetResultat = listOf(
                Soknad(
                    status = Soknadstatus.NY,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 1, 1),
                            tom = LocalDate.of(2022, 1, 14),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                ),
                Soknad(
                    status = Soknadstatus.NY,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 1, 15),
                            tom = LocalDate.of(2022, 2, 13),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                ),
                Soknad(
                    status = Soknadstatus.NY,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 2, 14),
                            tom = LocalDate.of(2022, 3, 15),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                ),
                Soknad(
                    status = Soknadstatus.NY,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 3, 16),
                            tom = LocalDate.of(2022, 3, 31),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `Overlapper i midten av en lang rekke fremtidige soknader med gap i midten`() {
        overlappTester.testKlipp(
            forventaSoknadPaKafka = 5,
            eksisterendeSoknader = listOf(
                Soknad(
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 1, 1),
                            tom = LocalDate.of(2022, 1, 15),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                ),
                Soknad(
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 1, 16),
                            tom = LocalDate.of(2022, 1, 20),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                ),
                Soknad(
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 2, 1),
                            tom = LocalDate.of(2022, 2, 15),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                ),
                Soknad(
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 2, 16),
                            tom = LocalDate.of(2022, 2, 28),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                )
            ),
            overlappendeSoknad = Soknad(
                soknadPerioder = listOf(
                    Soknadsperiode(
                        fom = LocalDate.of(2022, 1, 10),
                        tom = LocalDate.of(2022, 2, 20),
                        grad = 100,
                        sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                    )
                )
            ),
            dagensDato = LocalDate.of(2022, 1, 1),
            forventetResultat = listOf(
                Soknad(
                    status = Soknadstatus.FREMTIDIG,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 1, 1),
                            tom = LocalDate.of(2022, 1, 9),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                ),
                Soknad(
                    status = Soknadstatus.FREMTIDIG,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 1, 10),
                            tom = LocalDate.of(2022, 1, 30),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                ),
                Soknad(
                    status = Soknadstatus.FREMTIDIG,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 1, 31),
                            tom = LocalDate.of(2022, 2, 20),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                ),
                Soknad(
                    status = Soknadstatus.FREMTIDIG,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 2, 21),
                            tom = LocalDate.of(2022, 2, 28),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `Overlapper i midten og første av 3 deler aktiveres nå`() {
        overlappTester.testKlipp(
            dagensDato = LocalDate.of(2022, 1, 10),
            eksisterendeSoknader = listOf(
                Soknad(
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 1, 1),
                            tom = LocalDate.of(2022, 1, 30),
                            grad = 50,
                            sykmeldingstype = Sykmeldingstype.GRADERT
                        )
                    )
                )
            ),
            overlappendeSoknad = Soknad(
                soknadPerioder = listOf(
                    Soknadsperiode(
                        fom = LocalDate.of(2022, 1, 10),
                        tom = LocalDate.of(2022, 1, 20),
                        grad = 100,
                        sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                    )
                )
            ),
            forventetResultat = listOf(
                Soknad(
                    status = Soknadstatus.NY,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 1, 1),
                            tom = LocalDate.of(2022, 1, 9),
                            grad = 50,
                            sykmeldingstype = Sykmeldingstype.GRADERT
                        )
                    )
                ),
                Soknad(
                    status = Soknadstatus.FREMTIDIG,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 1, 10),
                            tom = LocalDate.of(2022, 1, 20),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                ),
                Soknad(
                    status = Soknadstatus.FREMTIDIG,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 1, 21),
                            tom = LocalDate.of(2022, 1, 30),
                            grad = 50,
                            sykmeldingstype = Sykmeldingstype.GRADERT
                        )
                    )
                )
            ),
            forventaSoknadPaKafka = 2
        )
    }

    @Test
    fun `Overlapper i midten av en lang rekke med sendte soknader`() {
        overlappTester.testKlipp(
            forventaSoknadPaKafka = 3,
            eksisterendeSoknader = listOf(
                Soknad(
                    status = Soknadstatus.SENDT,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 1, 1),
                            tom = LocalDate.of(2022, 1, 31),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                ),
                Soknad(
                    status = Soknadstatus.SENDT,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 2, 1),
                            tom = LocalDate.of(2022, 2, 28),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                ),
                Soknad(
                    status = Soknadstatus.SENDT,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 3, 1),
                            tom = LocalDate.of(2022, 3, 31),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                )
            ),
            overlappendeSoknad = Soknad(
                soknadPerioder = listOf(
                    Soknadsperiode(
                        fom = LocalDate.of(2022, 1, 15),
                        tom = LocalDate.of(2022, 3, 15),
                        grad = 50,
                        sykmeldingstype = Sykmeldingstype.GRADERT
                    )
                )
            ),
            dagensDato = LocalDate.of(2022, 4, 1),
            forventetResultat = listOf(
                Soknad(
                    status = Soknadstatus.SENDT,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 1, 1),
                            tom = LocalDate.of(2022, 1, 31),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                ),
                Soknad(
                    status = Soknadstatus.SENDT,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 2, 1),
                            tom = LocalDate.of(2022, 2, 28),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                ),
                Soknad(
                    status = Soknadstatus.SENDT,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 3, 1),
                            tom = LocalDate.of(2022, 3, 31),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                ),
                Soknad(
                    status = Soknadstatus.NY,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 1, 15),
                            tom = LocalDate.of(2022, 1, 31),
                            grad = 50,
                            sykmeldingstype = Sykmeldingstype.GRADERT
                        )
                    )
                ),
                Soknad(
                    status = Soknadstatus.NY,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 2, 1),
                            tom = LocalDate.of(2022, 2, 28),
                            grad = 50,
                            sykmeldingstype = Sykmeldingstype.GRADERT
                        )
                    )
                ),
                Soknad(
                    status = Soknadstatus.NY,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 3, 1),
                            tom = LocalDate.of(2022, 3, 15),
                            grad = 50,
                            sykmeldingstype = Sykmeldingstype.GRADERT
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `Eldre sykmelding overlapper både fullstendig og delvis`() {
        val sykmeldingSkrevet = OffsetDateTime.now()
        val overlappendeSoknad = Soknad(
            sykmeldingSkrevet = sykmeldingSkrevet.minusDays(1),
            soknadPerioder = listOf(
                Soknadsperiode(
                    fom = LocalDate.of(2022, 1, 1),
                    tom = LocalDate.of(2022, 1, 10),
                    grad = 100,
                    sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                )
            )
        )

        overlappTester.testKlipp(
            dagensDato = LocalDate.of(2022, 1, 1),
            eksisterendeSoknader = listOf(
                Soknad(
                    sykmeldingSkrevet = sykmeldingSkrevet,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 1, 1),
                            tom = LocalDate.of(2022, 1, 10),
                            grad = 50,
                            sykmeldingstype = Sykmeldingstype.GRADERT
                        )
                    )
                ),
                Soknad(
                    sykmeldingSkrevet = sykmeldingSkrevet,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 1, 5),
                            tom = LocalDate.of(2022, 1, 20),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                )
            ),
            overlappendeSoknad = overlappendeSoknad,
            forventetResultat = listOf(
                Soknad(
                    status = Soknadstatus.FREMTIDIG,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 1, 1),
                            tom = LocalDate.of(2022, 1, 10),
                            grad = 50,
                            sykmeldingstype = Sykmeldingstype.GRADERT
                        )
                    )
                ),
                Soknad(
                    status = Soknadstatus.FREMTIDIG,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 1, 5),
                            tom = LocalDate.of(2022, 1, 20),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                )
            ),
            forventaSoknadPaKafka = 0
        )

        // Siden den forventer 0 søknader på kafka så må vi dobbeltsjekke at den ikke har feilet på noe
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = "fnr",
                sykmeldingsperioder = heltSykmeldt(
                    fom = overlappendeSoknad.soknadPerioder.minOf { it.fom }.plusYears(1),
                    tom = overlappendeSoknad.soknadPerioder.maxOf { it.tom }.plusYears(1)
                )
            ),
            forventaSoknader = 1
        )
    }

    private class OverlappTester(
        private val soknadLagrer: SoknadLagrer,
        private val sykepengesoknadDAO: SykepengesoknadDAO,
        private val baseTestClass: BaseTestClass,
        private val skalPrintePerioder: Boolean = false
    ) {
        data class Soknad(
            val soknadPerioder: List<Soknadsperiode>,
            val status: Soknadstatus = Soknadstatus.FREMTIDIG,
            val traceId: String = UUID.randomUUID().toString(),
            val sykmeldingSkrevet: OffsetDateTime? = null
        )

        fun testKlipp(
            eksisterendeSoknader: List<Soknad>,
            overlappendeSoknad: Soknad,
            dagensDato: LocalDate,
            forventetResultat: List<Soknad>,
            forventaSoknadPaKafka: Int
        ) {
            eksisterendeSoknader.forEach { lagreEksisterendeSoknad(it) }

            riktigStatusUtIfraDagensDato(dagensDato)

            baseTestClass.sendSykmelding(
                sykmeldingKafkaMessage(
                    fnr = "fnr",
                    sykmeldingId = overlappendeSoknad.traceId,
                    sykmeldingSkrevet = overlappendeSoknad.sykmeldingSkrevet ?: OffsetDateTime.now(),
                    sykmeldingsperioder = if (overlappendeSoknad.soknadPerioder.any { it.grad != 100 }) {
                        gradertSykmeldt(
                            fom = overlappendeSoknad.soknadPerioder.minOf { it.fom },
                            tom = overlappendeSoknad.soknadPerioder.maxOf { it.tom }
                        )
                    } else {
                        heltSykmeldt(
                            fom = overlappendeSoknad.soknadPerioder.minOf { it.fom },
                            tom = overlappendeSoknad.soknadPerioder.maxOf { it.tom }
                        )
                    }
                ),
                forventaSoknader = forventaSoknadPaKafka
            )

            riktigStatusUtIfraDagensDato(dagensDato)

            resultatetErSomForventet(
                eksisterendeSoknader,
                overlappendeSoknad,
                forventetResultat
            )
        }

        private fun lagreEksisterendeSoknad(soknad: Soknad) {
            val now = Instant.now()

            soknadLagrer.lagreSoknad(
                Sykepengesoknad(
                    id = UUID.randomUUID().toString(),
                    sykmeldingId = soknad.traceId,
                    fnr = "fnr",
                    soknadstype = Soknadstype.ARBEIDSTAKERE,
                    status = soknad.status,
                    opprettet = now,
                    sporsmal = emptyList(),
                    fom = soknad.soknadPerioder.minOf { it.fom },
                    tom = soknad.soknadPerioder.maxOf { it.tom },
                    startSykeforlop = soknad.soknadPerioder.minOf { it.fom },
                    sykmeldingSkrevet = soknad.sykmeldingSkrevet?.toInstant() ?: now,
                    soknadPerioder = soknad.soknadPerioder,
                    arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    arbeidsgiverOrgnummer = "123454543",
                    arbeidsgiverNavn = "Butikken",
                    utenlandskSykmelding = false,
                    egenmeldingsdagerFraSykmelding = null
                )
            )
        }

        private fun riktigStatusUtIfraDagensDato(dagensDato: LocalDate) {
            sykepengesoknadDAO.finnSykepengesoknader(listOf("fnr"))
                .forEach {
                    if (it.status !in listOf(Soknadstatus.FREMTIDIG, Soknadstatus.NY)) return@forEach

                    val riktigStatus = if (it.tom!!.isBefore(dagensDato)) Soknadstatus.NY else Soknadstatus.FREMTIDIG
                    sykepengesoknadDAO.oppdaterStatus(it.copy(status = riktigStatus))
                }
        }

        private fun resultatetErSomForventet(
            eksisterende: List<Soknad>,
            overlappendeSoknad: Soknad,
            forventet: List<Soknad>
        ) {
            val forventetResultat = forventet.sortedBy { it.soknadPerioder.minOf { p -> p.fom } }

            val faktiskResultat = sykepengesoknadDAO.finnSykepengesoknader(listOf("fnr"))
                .sortedBy { it.fom }
                .map {
                    Soknad(
                        soknadPerioder = it.soknadPerioder!!,
                        status = it.status,
                        traceId = it.sykmeldingId!!,
                        sykmeldingSkrevet = null
                    )
                }

            if (skalPrintePerioder) {
                printPerioder(eksisterende, overlappendeSoknad, forventetResultat, faktiskResultat)
            }

            forventetResultat.map {
                it.copy(
                    traceId = "",
                    sykmeldingSkrevet = null
                )
            } shouldBeEqualTo faktiskResultat.map {
                it.copy(
                    traceId = "",
                    sykmeldingSkrevet = null
                )
            }
        }

        private fun printPerioder(
            eksisterende: List<Soknad>,
            overlappendeSoknad: Soknad,
            forventet: List<Soknad>,
            faktisk: List<Soknad>
        ) {
            val perioder = forventet.flatMap { it.soknadPerioder } + faktisk.flatMap { it.soknadPerioder }
            val minFom = perioder.minOf { it.fom }
            val maxTom = perioder.maxOf { it.tom }
            val traceMap = (eksisterende + listOf(overlappendeSoknad)).map { it.traceId }

            fun print(soknader: List<Soknad>) {
                soknader.forEach { sok ->
                    val farge = 32 + traceMap.indexOf(sok.traceId)
                    var tall = '1'

                    sok.soknadPerioder.forEach { p ->
                        var periodeLinje = ""

                        minFom.datesUntil(maxTom).forEach {
                            periodeLinje += if (it in p.fom..p.tom) "\u001B[${farge}m$tall" else ' '
                        }

                        tall++
                        println(periodeLinje + "  ${sok.status}")
                    }
                }
            }

            println("Farge = soknad")
            println("Tall = soknad periode nummer")
            println("Overlappende soknad:")
            print(listOf(overlappendeSoknad))
            println("Eksisterende soknader:")
            print(eksisterende)
            println("Faktisk resultat:")
            print(faktisk)
            println("Forventet resultat:")
            print(forventet)
        }
    }
}
