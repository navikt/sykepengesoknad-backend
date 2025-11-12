package no.nav.helse.flex.kafka.consumer

import no.nav.helse.flex.sykepengesoknad.kafka.*
import no.nav.helse.flex.util.objectMapper
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SjekkSykepengesoknadFelterConsumerTest {
    @Test
    fun `burde sette soknad felter`() {
        val soknad = lagTestSykepengesoknadDTO(opprettet = LocalDateTime.parse("2020-01-01T12:00:00"))
        val konsument = SjekkSykepengesoknadFelterConsumer()
        konsument.listen(ConsumerRecord<String, String?>("topic", 0, 0L, soknad.id, objectMapper.writeValueAsString(soknad)))
        konsument.felterVedSoknadstype().shouldHaveSize(1)
        konsument.felterVedSoknadstype()[soknad.type].shouldNotBeNull().shouldHaveSize(32)
    }

    @Test
    fun `burde akkumulere soknad felter`() {
        val soknad = lagTestSykepengesoknadDTO(opprettet = LocalDateTime.parse("2020-01-01T12:00:00"))
        val konsument = SjekkSykepengesoknadFelterConsumer()
        konsument.listen(ConsumerRecord<String, String?>("topic", 0, 0L, soknad.id, objectMapper.writeValueAsString(soknad)))
        konsument.listen(ConsumerRecord<String, String?>("topic", 0, 0L, soknad.id, objectMapper.writeValueAsString(soknad)))
        konsument.felterVedSoknadstype().shouldHaveSize(1)
        konsument.felterVedSoknadstype()[soknad.type].shouldNotBeNull().values.forEach {
            it.shouldBeEqualTo(2)
        }
    }

    @Test
    fun `burde logge resultat etter opprettet 2025-11-10`() {
        val soknad1 = lagTestSykepengesoknadDTO(opprettet = LocalDateTime.parse("2025-11-01T00:00:00"))
        val soknad2 = lagTestSykepengesoknadDTO(opprettet = LocalDateTime.parse("2025-11-11T00:00:00"))
        val konsument = SjekkSykepengesoknadFelterConsumer()
        konsument.listen(ConsumerRecord<String, String?>("topic", 0, 0L, soknad1.id, objectMapper.writeValueAsString(soknad1)))
        konsument.resultatErLogget().shouldBeFalse()
        konsument.listen(ConsumerRecord<String, String?>("topic", 0, 0L, soknad2.id, objectMapper.writeValueAsString(soknad2)))
        konsument.resultatErLogget().shouldBeTrue()
    }
}

private fun lagTestSykepengesoknadDTO(opprettet: LocalDateTime) =
    SykepengesoknadDTO(
        id = "test-id",
        type = SoknadstypeDTO.ARBEIDSTAKERE,
        status = SoknadsstatusDTO.NY,
        fnr = "12345678901",
        sykmeldingId = "sykmelding-1",
        arbeidsgiver = ArbeidsgiverDTO(navn = "Bedrift AS", orgnummer = "999888777"),
        arbeidssituasjon = ArbeidssituasjonDTO.ARBEIDSTAKER,
        opprettet = opprettet,
        opprinneligSendt = opprettet.minusDays(1),
        korrigerer = null,
        korrigertAv = null,
        soktUtenlandsopphold = false,
        arbeidsgiverForskutterer = null,
        fom = opprettet.plusDays(1).toLocalDate(),
        tom = opprettet.plusDays(10).toLocalDate(),
        dodsdato = null,
        startSyketilfelle = opprettet.toLocalDate(),
        arbeidGjenopptatt = null,
        friskmeldt = null,
        sykmeldingSkrevet = opprettet,
        sendtNav = opprettet.plusDays(12),
        sendtArbeidsgiver = null,
        egenmeldinger = null,
        fravarForSykmeldingen = null,
        papirsykmeldinger = emptyList(),
        fravar = null,
        andreInntektskilder = emptyList(),
        soknadsperioder =
            listOf(
                SoknadsperiodeDTO(
                    fom = opprettet.plusDays(1).toLocalDate(),
                    tom = opprettet.plusDays(10).toLocalDate(),
                    sykmeldingsgrad = 100,
                    sykmeldingstype = SykmeldingstypeDTO.AKTIVITET_IKKE_MULIG,
                    faktiskGrad = null,
                    faktiskTimer = null,
                    avtaltTimer = 37.5,
                ),
            ),
        sporsmal = emptyList(),
        avsendertype = AvsendertypeDTO.BRUKER,
        ettersending = false,
        mottaker = MottakerDTO.NAV,
        egenmeldtSykmelding = false,
        yrkesskade = false,
        arbeidUtenforNorge = false,
        harRedusertVenteperiode = false,
        behandlingsdager = emptyList(),
        permitteringer = emptyList(),
        merknaderFraSykmelding = listOf(MerknadDTO(type = "UGYLDIG_TILBAKEDATERING", beskrivelse = "Test")),
        egenmeldingsdagerFraSykmelding = emptyList(),
        merknader = null,
        sendTilGosys = null,
        utenlandskSykmelding = false,
        medlemskapVurdering = null,
        forstegangssoknad = true,
        tidligereArbeidsgiverOrgnummer = null,
        fiskerBlad = null,
        inntektFraNyttArbeidsforhold = null,
        selvstendigNaringsdrivende = null,
        friskTilArbeidVedtakId = null,
        friskTilArbeidVedtakPeriode = null,
        fortsattArbeidssoker = null,
        inntektUnderveis = null,
        ignorerArbeidssokerregister = null,
    )
