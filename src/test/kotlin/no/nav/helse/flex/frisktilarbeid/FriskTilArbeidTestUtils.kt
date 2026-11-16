package no.nav.helse.flex.frisktilarbeid

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.fakes.SoknadKafkaProducerFake
import no.nav.helse.flex.medlemskap.tilPostgresJson
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.awaitility.Awaitility.await
import org.awaitility.core.ConditionFactory
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

internal object FriskTilArbeidTestTid {
    val dato: LocalDate = LocalDate.of(2025, 4, 22)
    val tidspunkt: Instant = Instant.parse("2025-04-22T10:00:00Z")
    val statusAt: OffsetDateTime = OffsetDateTime.parse("2025-03-10T00:01:00Z")
}

internal fun ventPaFriskTilArbeid(beskrivelse: String): ConditionFactory =
    await(beskrivelse)
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(50))

internal fun forventSlettedeSoknaderForVedtak(
    vedtakId: String,
    forventedeStartdatoer: List<LocalDate>,
) {
    val slettede = SoknadKafkaProducerFake.records.map { it.value() }.filter { it.status == SoknadsstatusDTO.SLETTET }
    slettede.shouldHaveSize(forventedeStartdatoer.size)
    slettede.forEach { it.friskTilArbeidVedtakId shouldBe vedtakId }
    slettede.map { it.fom.shouldNotBeNull() }.sorted() shouldBe forventedeStartdatoer
}

internal fun FriskTilArbeidRepository.forventVedtakLagretFraKafkaMelding(melding: ConsumerRecord<String, String>) {
    val vedtak = melding.value().tilFriskTilArbeidVedtakStatus()
    val lagret = findAll().toList().shouldHaveSize(1).single()
    lagret.key shouldBe melding.key()
    lagret.vedtak
        .shouldNotBeNull()
        .value
        .shouldNotBeNull()
        .tilFriskTilArbeidVedtakStatus() shouldBe vedtak
    lagret.behandletStatus shouldBe BehandletStatus.NY
    lagret.ignorerArbeidssokerregister shouldBe null
}

internal fun String.asProducerRecordKey(): String = UUID.nameUUIDFromBytes(this.toByteArray()).toString()

internal fun lagFriskTilArbeidVedtakStatus(
    fnr: String,
    status: Status,
    vedtaksperiode: Periode = Periode(fom = FriskTilArbeidTestTid.dato, tom = FriskTilArbeidTestTid.dato.plusDays(13)),
): FriskTilArbeidVedtakStatus =
    FriskTilArbeidVedtakStatus(
        uuid = UUID.randomUUID().toString(),
        personident = fnr,
        begrunnelse = "Begrunnelse",
        fom = vedtaksperiode.fom,
        tom = vedtaksperiode.tom,
        status = status,
        statusAt = FriskTilArbeidTestTid.statusAt,
        statusBy = "Test",
    )

internal fun lagFriskTilArbeidVedtakDbRecord(friskTilArbeidVedtak: FriskTilArbeidVedtakStatus): FriskTilArbeidVedtakDbRecord {
    val friskTilArbeidVedtakDbRecord =
        FriskTilArbeidVedtakDbRecord(
            vedtakUuid = friskTilArbeidVedtak.uuid,
            key = friskTilArbeidVedtak.personident.asProducerRecordKey(),
            opprettet = FriskTilArbeidTestTid.tidspunkt,
            fnr = friskTilArbeidVedtak.personident,
            fom = friskTilArbeidVedtak.fom,
            tom = friskTilArbeidVedtak.tom,
            vedtak = friskTilArbeidVedtak.tilPostgresJson(),
            behandletStatus = BehandletStatus.BEHANDLET,
            ignorerArbeidssokerregister = null,
        )
    return friskTilArbeidVedtakDbRecord
}

internal fun lagFriskTilArbeidSoknad(
    fnr: String,
    friskTilArbeidVedtakId: String,
    soknadStatus: Soknadstatus,
    fom: LocalDate,
    tom: LocalDate,
): Sykepengesoknad {
    val soknad =
        Sykepengesoknad(
            id = UUID.randomUUID().toString(),
            fnr = fnr,
            soknadstype = Soknadstype.FRISKMELDT_TIL_ARBEIDSFORMIDLING,
            status = soknadStatus,
            opprettet = FriskTilArbeidTestTid.tidspunkt,
            startSykeforlop = fom,
            fom = fom,
            tom = tom,
            arbeidssituasjon = null,
            arbeidsgiverOrgnummer = null,
            arbeidsgiverNavn = null,
            sykmeldingId = UUID.randomUUID().toString(),
            sykmeldingSkrevet = null,
            soknadPerioder = emptyList(),
            egenmeldtSykmelding = null,
            sporsmal = emptyList(),
            utenlandskSykmelding = false,
            egenmeldingsdagerFraSykmelding = null,
            forstegangssoknad = false,
            friskTilArbeidVedtakId = friskTilArbeidVedtakId,
        )
    return soknad
}
