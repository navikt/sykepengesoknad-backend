package no.nav.helse.flex.kafka

import com.nhaarman.mockitokotlin2.*
import no.nav.helse.flex.domain.Soknadstype.*
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.mapper.SykepengesoknadTilSykepengesoknadDTOMapper
import no.nav.helse.flex.juridiskvurdering.JuridiskVurderingKafkaProducer
import no.nav.helse.flex.kafka.producer.AivenKafkaProducer
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.kafka.producer.finnDagerSpartFordiJobbetMerEnnSoknadTilsier
import no.nav.helse.flex.kafka.producer.finnDagerSpartVedArbeidGjenopptattTidligere
import no.nav.helse.flex.mock.gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal
import no.nav.helse.flex.mock.opprettSendtSoknad
import no.nav.helse.flex.mock.opprettSendtSoknadForArbeidsledige
import no.nav.helse.flex.repository.RedusertVenteperiodeRepository
import no.nav.helse.flex.soknadsopprettelse.ANDRE_INNTEKTSKILDER
import no.nav.helse.flex.soknadsopprettelse.FRISKMELDT
import no.nav.helse.flex.soknadsopprettelse.FRISKMELDT_START
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.testutil.besvarsporsmal
import no.nav.helse.flex.util.Metrikk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDate.now
import java.time.format.DateTimeFormatter
import java.util.Arrays.asList

class SoknadProducerTest {

    private val kafkaProducer: AivenKafkaProducer = mock()

    private val metrikk = mock(Metrikk::class.java)

    private val juridiskVurderingKafkaProducer = mock(JuridiskVurderingKafkaProducer::class.java)

    private val redusertVenteperiodeRepository = mock(RedusertVenteperiodeRepository::class.java)

    private val mapper = SykepengesoknadTilSykepengesoknadDTOMapper(juridiskVurderingKafkaProducer, redusertVenteperiodeRepository)

    private val soknadProducer = SoknadProducer(kafkaProducer, metrikk, mapper)

    @Test
    fun soknadOpprettetArbeidstakere() {
        val sykepengesoknad = gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal()

        soknadProducer.soknadEvent(sykepengesoknad, null, false)
        val captor: KArgumentCaptor<SykepengesoknadDTO> = argumentCaptor()

        verify(kafkaProducer).produserMelding(captor.capture())
        val (id, _, status) = captor.firstValue

        assertThat(id).isEqualTo(sykepengesoknad.id)
        assertThat(status).isEqualTo(SoknadsstatusDTO.FREMTIDIG)
    }

    @Test
    fun soknadOpprettetArbeidstakereFeilVedSending() {
        whenever(kafkaProducer.produserMelding(any())).thenThrow(java.lang.RuntimeException("Heisann"))

        val sykepengesoknad = gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal()

        assertThatThrownBy { soknadProducer.soknadEvent(sykepengesoknad, null, false) }
            .isInstanceOf(RuntimeException::class.java)
    }

    @Test
    fun soknadSendtArbeidstakereFeilVedSending() {
        whenever(kafkaProducer.produserMelding(any())).thenThrow(java.lang.RuntimeException("Oops"))

        val sykepengesoknad = gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal()

        assertThatThrownBy { soknadProducer.soknadEvent(sykepengesoknad, null, false) }
            .isInstanceOf(RuntimeException::class.java)
    }

    @Test
    fun ingenDagerSpartHvisArbeidIkkeErGjenopptattTidligere() {
        val soknadsperioder = listOf(soknadsperiodeDTO(20, 5, 100))

        val dagerSpart = finnDagerSpartVedArbeidGjenopptattTidligere(null, soknadsperioder)

        assertThat(dagerSpart).isEqualTo(0.0)
    }

    @Test
    fun enDagSpartHvisArbeidGjenopptattSammeDagSomTom() {
        val soknadsperioder = listOf(soknadsperiodeDTO(20, 5, 100))

        val dagerSpart = finnDagerSpartVedArbeidGjenopptattTidligere(now().minusDays(5), soknadsperioder)

        assertThat(dagerSpart).isEqualTo(1.0)
    }

    @Test
    fun femDagerSpartHvisArbeidGjenopptattFireDagerForTom() {
        val soknadsperioder = listOf(soknadsperiodeDTO(20, 5, 100))

        val dagerSpart = finnDagerSpartVedArbeidGjenopptattTidligere(now().minusDays(9), soknadsperioder)

        assertThat(dagerSpart).isEqualTo(5.0)
    }

    @Test
    fun alleDagerSpartHvisArbeidGjenopptattSammeDagSomFom() {
        val soknadsperioder = listOf(soknadsperiodeDTO(20, 5, 100))

        val dagerSpart = finnDagerSpartVedArbeidGjenopptattTidligere(now().minusDays(20), soknadsperioder)

        assertThat(dagerSpart).isEqualTo(16.0)
    }

    @Test
    fun antallDagerSpartHvisArbeidGjenopptattFireDagerForTomForGradertSykmelding() {
        val soknadsperioder = listOf(soknadsperiodeDTO(20, 5, 50))

        val dagerSpart = finnDagerSpartVedArbeidGjenopptattTidligere(now().minusDays(9), soknadsperioder)

        assertThat(dagerSpart).isEqualTo(2.5)
    }

    @Test
    fun antallDagerSpartHvisArbeidGjenopptattFireDagerForTomForGradertSykmeldingOgToPerioder() {
        val soknadsperioder = asList(
            soknadsperiodeDTO(20, 14, 100),
            soknadsperiodeDTO(14, 5, 50)
        )

        val dagerSpart = finnDagerSpartVedArbeidGjenopptattTidligere(now().minusDays(9), soknadsperioder)

        assertThat(dagerSpart).isEqualTo(2.5)
    }

    @Test
    fun antallDagerSpartHvisArbeidGjenopptattIForstePeriodeForSykmeldingMedToPerioder() {
        val soknadsperioder = asList(
            soknadsperiodeDTO(20, 14, 100),
            soknadsperiodeDTO(14, 5, 50)
        )

        val dagerSpart = finnDagerSpartVedArbeidGjenopptattTidligere(now().minusDays(16), soknadsperioder)

        assertThat(dagerSpart).isEqualTo(9.0)
    }

    @Test
    fun ingenDagerSpartHvisIkkeJobbetMerEnnSoknadTilsier() {
        val soknadsperioder = listOf(soknadsperiodeDTO(20, 5, 100))

        val dagerSpart = finnDagerSpartFordiJobbetMerEnnSoknadTilsier(now().minusDays(9), soknadsperioder)

        assertThat(dagerSpart).isEqualTo(0.0)
    }

    @Test
    fun treDagerSpartFordiJobbetMerEnnSoknadTilsier() {
        val soknadsperioder = listOf(soknadsperiodeDTO(20, 6, 100, 20))

        val dagerSpart = finnDagerSpartFordiJobbetMerEnnSoknadTilsier(null, soknadsperioder)

        assertThat(dagerSpart).isEqualTo(3.0)
    }

    @Test
    fun antallDagerSpartFordiJobbetMerEnnSoknadTilsierOgKomTilbakeTidligere() {
        val soknadsperioder = listOf(soknadsperiodeDTO(20, 6, 100, 20))

        val dagerSpart = finnDagerSpartFordiJobbetMerEnnSoknadTilsier(now().minusDays(9), soknadsperioder)

        // Dette blir riktig fordi finnDagerSpartFordiJobbetMerEnnSoknadTilsier kun ser på innsparing ift gradering -
        // innsparing pga at man er tidligere tilbake i jobb håndteres av finnDagerSpartVedArbeidGjenopptattTidligere
        assertThat(dagerSpart).isEqualTo(2.4)
    }

    @Test
    fun antallDagerSpartFordiJobbetMerEnnSoknadTilsierOgToPerioder() {
        val soknadsperioder = asList(
            soknadsperiodeDTO(20, 14, 100),
            soknadsperiodeDTO(14, 5, 80, 50)
        )

        val dagerSpart = finnDagerSpartFordiJobbetMerEnnSoknadTilsier(null, soknadsperioder)

        assertThat(dagerSpart).isEqualTo(3.0)
    }

    @Test
    fun metrikkTellerForsteSoknadISyketilfelle() {
        val sykepengesoknad = opprettSendtSoknad()

        soknadProducer.soknadEvent(sykepengesoknad, null, false)

        verify(metrikk, times(1)).tellForsteSoknadISyketilfelle(ArgumentMatchers.anyString())
        verify(metrikk, times(0)).tellForlengelseSoknadISyketilfelle(ArgumentMatchers.anyString())
    }

    @Test
    fun metrikkTellerForlengelseAvSyketilfelle() {
        var sykepengesoknad = opprettSendtSoknad()
        sykepengesoknad = sykepengesoknad.copy(startSykeforlop = sykepengesoknad.startSykeforlop!!.minusDays(7))

        soknadProducer.soknadEvent(sykepengesoknad, null, false)

        verify(metrikk, times(1)).tellForlengelseSoknadISyketilfelle(ArgumentMatchers.anyString())
        verify(metrikk, times(0)).tellForsteSoknadISyketilfelle(ArgumentMatchers.anyString())
    }

    @Test
    fun metrikkTellerDagerFraAktiveringTilInnsending() {
        val sykepengesoknad = opprettSendtSoknad()

        soknadProducer.soknadEvent(sykepengesoknad, null, false)

        val tom = sykepengesoknad.tom
        verify(metrikk, times(1)).tellDagerFraAktiveringTilInnsending(ARBEIDSTAKERE.name, dagerSiden(tom!!))
        verify(metrikk, times(0)).tellDagerFraAktiveringTilInnsending(ARBEIDSLEDIG.name, dagerSiden(tom))
        verify(metrikk, times(0)).tellDagerFraAktiveringTilInnsending(SELVSTENDIGE_OG_FRILANSERE.name, dagerSiden(tom))
        verify(metrikk, times(0)).tellDagerFraAktiveringTilInnsending(OPPHOLD_UTLAND.name, dagerSiden(tom))
    }

    @Test
    fun metrikkTellerArbeidstakerMedFlereInntektskilder() {
        val sykepengesoknad = opprettSendtSoknad()

        soknadProducer.soknadEvent(sykepengesoknad, null, false)

        verify(metrikk, times(1)).tellSoknadMedFlereInntektsKilder()
    }

    @Test
    fun arbeidtakereUtenAndreInntektskilderTellesIkkeOpp() {
        val sykepengesoknad = opprettSendtSoknad().replaceSporsmal(
            Sporsmal(tag = ANDRE_INNTEKTSKILDER, svartype = Svartype.JA_NEI, svar = listOf(Svar(null, "NEI")))
        )

        soknadProducer.soknadEvent(sykepengesoknad, null, false)

        verify(metrikk, never()).tellSoknadMedFlereInntektsKilder()
    }

    @Test
    fun antallFriskmeldteDagerForArbeidsledige() {
        var sykepengesoknad = opprettSendtSoknadForArbeidsledige().copy(fnr = "12345")

        sykepengesoknad = sykepengesoknad.besvarsporsmal(tag = FRISKMELDT, svar = "NEI")
        sykepengesoknad = sykepengesoknad.besvarsporsmal(tag = FRISKMELDT_START, svar = sykepengesoknad.tom!!.minusDays(5).format(DateTimeFormatter.ISO_LOCAL_DATE))

        soknadProducer.soknadEvent(sykepengesoknad, null, false)

        verify(metrikk, times(1)).tellAntallFriskmeldteDagerForArbeidsledige(6L)
    }

    @Test
    fun tellerFriskmeldingPaaTOMDatosomEnDag() {
        var sykepengesoknad = opprettSendtSoknadForArbeidsledige().copy(fnr = "12345")

        sykepengesoknad = sykepengesoknad.besvarsporsmal(tag = FRISKMELDT, svar = "NEI")
        sykepengesoknad = sykepengesoknad.besvarsporsmal(tag = FRISKMELDT_START, svar = sykepengesoknad.tom!!.format(DateTimeFormatter.ISO_LOCAL_DATE))

        soknadProducer.soknadEvent(sykepengesoknad, null, false)

        verify(metrikk, times(1)).tellAntallFriskmeldteDagerForArbeidsledige(1L)
    }

    @Test
    fun tellerIkkeFriskmeldteDagerForArbeidsledigeHvisDetIkkeErBesvart() {
        var sykepengesoknad = opprettSendtSoknadForArbeidsledige().copy(fnr = "12345")

        sykepengesoknad = sykepengesoknad.besvarsporsmal(tag = FRISKMELDT, svar = "JA")

        soknadProducer.soknadEvent(sykepengesoknad, null, false)

        verify(metrikk, never()).tellAntallFriskmeldteDagerForArbeidsledige(ArgumentMatchers.anyLong())
    }

    private fun soknadsperiodeDTO(fom: Int, tom: Int, grad: Int): SoknadsperiodeDTO {
        return SoknadsperiodeDTO(
            now().minusDays(fom.toLong()),
            now().minusDays(tom.toLong()),
            grad, null, null, null, null, null
        )
    }

    private fun soknadsperiodeDTO(fom: Int, tom: Int, grad: Int, faktiskGrad: Int): SoknadsperiodeDTO {
        return SoknadsperiodeDTO(
            now().minusDays(fom.toLong()),
            now().minusDays(tom.toLong()),
            grad,
            faktiskGrad, null, null, null, null
        )
    }

    private fun dagerSiden(tom: LocalDate): Long {
        return Duration.between(tom.atStartOfDay(), now().atStartOfDay()).toDays()
    }
}
