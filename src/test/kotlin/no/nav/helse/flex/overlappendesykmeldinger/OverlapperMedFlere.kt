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
import no.nav.helse.flex.repository.SoknadLagrer
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.util.*

class OverlapperMedFlere : BaseTestClass() {

    @Autowired
    private lateinit var soknadLagrer: SoknadLagrer

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    private lateinit var overlappTester: OverlappTester

    @BeforeAll
    fun init() {
        overlappTester = OverlappTester(
            soknadLagrer = soknadLagrer,
            sykepengesoknadDAO = sykepengesoknadDAO,
            baseTestClass = this,
            skalPrintePerioder = true,
        )
    }

    @AfterEach
    fun clenUp() {
        databaseReset.resetDatabase()
        do {
            val cr = sykepengesoknadKafkaConsumer.hentProduserteRecords()
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
    }

    @Test
    fun `Overlapper i midten av en lang rekke med nye søknader`() {
        overlappTester.testKlipp(
            forventaSoknadPaKafka = 2,
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
                            tom = LocalDate.of(2022, 1, 31),
                            grad = 100,
                            sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG
                        )
                    )
                ),
                Soknad(
                    status = Soknadstatus.NY,
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
                    status = Soknadstatus.NY,
                    soknadPerioder = listOf(
                        Soknadsperiode(
                            fom = LocalDate.of(2022, 3, 1),
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

    private class OverlappTester(
        private val soknadLagrer: SoknadLagrer,
        private val sykepengesoknadDAO: SykepengesoknadDAO,
        private val baseTestClass: BaseTestClass,
        private val skalPrintePerioder: Boolean = false,
    ) {
        data class Soknad(
            val soknadPerioder: List<Soknadsperiode>,
            val status: Soknadstatus = Soknadstatus.FREMTIDIG,
            val traceId: String = UUID.randomUUID().toString()
        )

        fun testKlipp(
            eksisterendeSoknader: List<Soknad>,
            overlappendeSoknad: Soknad,
            dagensDato: LocalDate,
            forventetResultat: List<Soknad>,
            forventaSoknadPaKafka: Int,
        ) {
            eksisterendeSoknader.forEach { lagreEksisterendeSoknad(it) }

            riktigStatusUtIfraDagensDato(dagensDato)

            baseTestClass.sendSykmelding(
                sykmeldingKafkaMessage(
                    fnr = "fnr",
                    sykmeldingId = overlappendeSoknad.traceId,
                    sykmeldingsperioder = heltSykmeldt(
                        fom = overlappendeSoknad.soknadPerioder.minOf { it.fom },
                        tom = overlappendeSoknad.soknadPerioder.maxOf { it.tom },
                    ),
                ),
                forventaSoknader = forventaSoknadPaKafka,
            )

            riktigStatusUtIfraDagensDato(dagensDato)

            resultatetErSomForventet(
                eksisterendeSoknader,
                overlappendeSoknad,
                forventetResultat,
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
                    sykmeldingSkrevet = now,
                    soknadPerioder = soknad.soknadPerioder,
                    arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    arbeidsgiverOrgnummer = "123454543",
                    arbeidsgiverNavn = "Butikken",
                    utenlandskSykmelding = false,
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
                .map { Soknad(soknadPerioder = it.soknadPerioder!!, status = it.status, traceId = it.sykmeldingId!!) }

            if (skalPrintePerioder) {
                printPerioder(eksisterende, overlappendeSoknad, forventetResultat, faktiskResultat)
            }

            forventetResultat.map { it.copy(traceId = "") } shouldBeEqualTo faktiskResultat.map { it.copy(traceId = "") }
        }

        private fun printPerioder(
            eksisterende: List<Soknad>,
            overlappendeSoknad: Soknad,
            forventet: List<Soknad>,
            faktisk: List<Soknad>,
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
