package no.nav.helse.flex.soknadsopprettelse

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

class SoknadIdTest {
    private val sykmeldingId: String = UUID.randomUUID().toString()
    private val dato: LocalDate = LocalDate.of(2022, 1, 1)
    private val eventTimestamp: OffsetDateTime = OffsetDateTime.of(dato, LocalTime.of(10, 10), ZoneOffset.UTC)

    @Test
    fun `skap søknad id gir samme resultat for samme sykmeldingKafkaMessage`() {
        val soknadIdEn = skapSoknadsId(dato, dato.plusDays(1), sykmeldingId, eventTimestamp)
        val soknadIdTo = skapSoknadsId(dato, dato.plusDays(1), sykmeldingId, eventTimestamp)

        soknadIdEn shouldBe soknadIdTo
    }

    @Test
    fun `skap søknad id gir ulikt resultat når tom er forskjellig`() {
        val soknadIdEn = skapSoknadsId(dato, dato.plusDays(1), sykmeldingId, eventTimestamp)
        val soknadIdTo = skapSoknadsId(dato, dato, sykmeldingId, eventTimestamp)

        soknadIdEn shouldNotBe soknadIdTo
    }

    @Test
    fun `skap søknad id gir ulikt resultat når timestamp er forskjellig`() {
        val soknadIdEn = skapSoknadsId(dato, dato, sykmeldingId, eventTimestamp)

        val soknadIdTo = skapSoknadsId(dato, dato, sykmeldingId, eventTimestamp.plusDays(1))

        soknadIdEn shouldNotBe soknadIdTo
    }
}
