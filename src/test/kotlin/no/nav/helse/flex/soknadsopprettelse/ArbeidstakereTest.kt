@file:Suppress("DEPRECATION")

package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.mock.gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal
import no.nav.helse.flex.mock.opprettNySoknadMock
import no.nav.helse.flex.soknadsopprettelse.oppdateringhelpers.finnGyldigDatoSvar
import no.nav.helse.flex.soknadsopprettelse.oppdateringhelpers.skapOppdaterteSoknadsperioder
import no.nav.helse.flex.util.DatoUtil
import no.nav.helse.flex.util.PeriodeMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@ExtendWith(MockitoExtension::class)
class ArbeidstakereTest {

    @Test
    fun utenFriskmeldingErOppdaterteSoknadsperioderUendret() {
        var sykepengesoknad = opprettNySoknadMock()
        sykepengesoknad =
            sykepengesoknad.replaceSporsmal(sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).copy(svar = emptyList()))
        val oppdaterteSoknadsperioder = getOppdaterteSoknadsperioder(sykepengesoknad)

        assertThat(oppdaterteSoknadsperioder).isEqualTo(sykepengesoknad.soknadPerioder)
    }

    @Test
    fun periodeKuttesVedFriskmelding() {
        val periode1Fom = LocalDate.now().plusDays(5)
        val periode1Tom = LocalDate.now().plusDays(15)
        val periode2Fom = LocalDate.now().plusDays(16)
        val periode2Tom = LocalDate.now().plusDays(25)
        val friskmeldtDato = LocalDate.now().plusDays(19)
        val periode2TomOppdatert = LocalDate.now().plusDays(18)

        var sykepengesoknad = opprettNySoknadMock().copy(
            soknadPerioder = listOf(
                Soknadsperiode(fom = periode1Fom, tom = periode1Tom, grad = 100, sykmeldingstype = null),
                Soknadsperiode(fom = periode2Fom, tom = periode2Tom, grad = 100, sykmeldingstype = null)
            )
        )
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).copy(
                min = null,
                max = null,
                svar = listOf(
                    Svar(
                        null,
                        verdi = friskmeldtDato.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        avgittAv = null
                    )
                )
            )
        )
        val oppdaterteSoknadsperioder = getOppdaterteSoknadsperioder(sykepengesoknad)

        assertThat(oppdaterteSoknadsperioder).hasSize(2)
        assertThat(oppdaterteSoknadsperioder[0].fom).isEqualTo(periode1Fom)
        assertThat(oppdaterteSoknadsperioder[0].tom).isEqualTo(periode1Tom)
        assertThat(oppdaterteSoknadsperioder[1].fom).isEqualTo(periode2Fom)
        assertThat(oppdaterteSoknadsperioder[1].tom).isEqualTo(periode2TomOppdatert)
    }

    @Test
    fun periodeFjernesVedFriskmelding() {
        val periode1Fom = LocalDate.now().plusDays(5)
        val periode1Tom = LocalDate.now().plusDays(15)
        val periode2Fom = LocalDate.now().plusDays(16)
        val periode2Tom = LocalDate.now().plusDays(25)
        val friskmeldtDato = LocalDate.now().plusDays(10)
        val periode1TomOppdatert = LocalDate.now().plusDays(9)

        var sykepengesoknad = opprettNySoknadMock().copy(
            soknadPerioder = listOf(
                Soknadsperiode(periode1Fom, periode1Tom, grad = 100, sykmeldingstype = null),
                Soknadsperiode(periode2Fom, periode2Tom, grad = 100, sykmeldingstype = null)
            )
        )
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).copy(
                min = null,
                max = null,
                svar = listOf(Svar(null, friskmeldtDato.format(DateTimeFormatter.ISO_LOCAL_DATE)))
            )
        )
        val oppdaterteSoknadsperioder = getOppdaterteSoknadsperioder(sykepengesoknad)

        assertThat(oppdaterteSoknadsperioder).hasSize(1)
        assertThat(oppdaterteSoknadsperioder[0].fom).isEqualTo(periode1Fom)
        assertThat(oppdaterteSoknadsperioder[0].tom).isEqualTo(periode1TomOppdatert)
    }

    @Test
    fun feriePermisjonUtlandFjernesVedFriskmelding() {
        val fom = LocalDate.now().plusDays(5)
        val tom = LocalDate.now().plusDays(15)

        var sykepengesoknad = opprettNySoknadMock().copy(
            fom = fom,
            tom = tom,
            soknadPerioder = listOf(Soknadsperiode(fom, tom, grad = 100, sykmeldingstype = null))
        )
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).copy(
                min = fom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                max = tom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                svar = listOf(Svar(null, fom.format(DateTimeFormatter.ISO_LOCAL_DATE), null))
            )
        )
        val oppdatertSoknad: Sykepengesoknad = oppdaterMedSvarPaArbeidGjenopptattArbeidstaker(sykepengesoknad)

        assertThat(oppdatertSoknad.getOptionalSporsmalMedTag(FERIE_V2)).isNotPresent
        assertThat(oppdatertSoknad.getOptionalSporsmalMedTag(PERMISJON_V2)).isNotPresent
        assertThat(oppdatertSoknad.getOptionalSporsmalMedTag(UTLAND_V2)).isNotPresent
    }

    @Test
    fun feriePermisjonUtlandFjernesVedFriskmeldingNarFeriesporsmalErUndersporsmal() {
        val fom = LocalDate.now().plusDays(5)
        val tom = LocalDate.now().plusDays(15)

        var sykepengesoknad = gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal().copy(
            fom = fom,
            tom = tom,
            soknadPerioder = listOf(Soknadsperiode(fom, tom, 100, null))
        )
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).copy(
                min = fom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                max = tom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                svar = listOf(Svar(null, fom.format(DateTimeFormatter.ISO_LOCAL_DATE), null))
            )
        )
        val oppdatertSoknad: Sykepengesoknad = oppdaterMedSvarPaArbeidGjenopptattArbeidstaker(sykepengesoknad)

        assertThat(oppdatertSoknad.getOptionalSporsmalMedTag(FERIE_PERMISJON_UTLAND)).isNotPresent
    }

    @Test
    fun ferieFjernesVedFriskmelding() {
        val fom = LocalDate.now().plusDays(5)
        val tom = LocalDate.now().plusDays(15)

        var sykepengesoknad = opprettNySoknadMock().copy(
            fom = fom,
            tom = tom,
            soknadPerioder = listOf(Soknadsperiode(fom, tom, 100, null))
        )
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).copy(
                min = fom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                max = tom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                svar = listOf(Svar(null, fom.format(DateTimeFormatter.ISO_LOCAL_DATE), null))
            )
        )
        val oppdatertSoknad: Sykepengesoknad = oppdaterMedSvarPaArbeidGjenopptattArbeidstaker(sykepengesoknad)

        assertThat(oppdatertSoknad.getOptionalSporsmalMedTag(FERIE_V2)).isNotPresent
    }

    @Test
    fun feriePermisjonUtlandKuttesTilEnDagMedFeriesporsmalSomUndersporsmal() {
        val fom = LocalDate.now().minusDays(25)
        val tom = LocalDate.now().minusDays(17)

        var sykepengesoknad = gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal().copy(
            fom = fom,
            tom = tom
        )
        val arbeidGjenopptattDato = LocalDate.now().minusDays(24)
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).copy(
                min = null,
                max = null,
                svar = listOf(Svar(null, arbeidGjenopptattDato.format(DateTimeFormatter.ISO_LOCAL_DATE), null))
            )
        )
        val oppdatertMin =
            oppdaterMedSvarPaArbeidGjenopptattArbeidstaker(sykepengesoknad).getSporsmalMedTag(FERIE_NAR).min
        val oppdatertMax =
            oppdaterMedSvarPaArbeidGjenopptattArbeidstaker(sykepengesoknad).getSporsmalMedTag(FERIE_NAR).max

        assertThat(oppdatertMin).isEqualTo(oppdatertMax)
        assertThat(oppdatertMax).isEqualTo(fom.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }

    @Test
    fun ferieKuttesTilEnDag() {
        val fom = LocalDate.now().minusDays(25)
        val tom = LocalDate.now().minusDays(17)
        var sykepengesoknad = opprettNySoknadMock().copy(
            fom = fom,
            tom = tom
        )
        val arbeidGjenopptattDato = LocalDate.now().minusDays(24)
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).copy(
                min = null,
                max = null,
                svar = listOf(Svar(null, arbeidGjenopptattDato.format(DateTimeFormatter.ISO_LOCAL_DATE), null))
            )
        )
        val oppdatertMin =
            oppdaterMedSvarPaArbeidGjenopptattArbeidstaker(sykepengesoknad).getSporsmalMedTag(FERIE_NAR_V2).min
        val oppdatertMax =
            oppdaterMedSvarPaArbeidGjenopptattArbeidstaker(sykepengesoknad).getSporsmalMedTag(FERIE_NAR_V2).max

        assertThat(oppdatertMin).isEqualTo(oppdatertMax)
        assertThat(oppdatertMax).isEqualTo(fom.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }

    @Test
    fun utdanningFjernesVedFriskmelding() {
        val fom = LocalDate.now().plusDays(5)
        val tom = LocalDate.now().plusDays(15)
        var sykepengesoknad = gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal().copy(
            fom = fom,
            tom = tom,
            soknadPerioder = listOf(Soknadsperiode(fom, tom, 100, null))
        )
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).copy(
                min = fom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                max = tom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                svar = listOf(Svar(null, fom.format(DateTimeFormatter.ISO_LOCAL_DATE), null))
            )
        )
        val oppdatertSoknad: Sykepengesoknad = oppdaterMedSvarPaArbeidGjenopptattArbeidstaker(sykepengesoknad)

        assertThat(oppdatertSoknad.getOptionalSporsmalMedTag(UTDANNING)).isNotPresent
    }

    @Test
    fun utenFriskmeldingErFeriesporsmalSomUndersporsmalUendret() {
        var sykepengesoknad = gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal()
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).copy(
                min = null,
                max = null,
                svar = emptyList()
            )
        )
        val max = sykepengesoknad.getSporsmalMedTag(FERIE_NAR).max
        val oppdatertMax =
            oppdaterMedSvarPaArbeidGjenopptattArbeidstaker(sykepengesoknad).getSporsmalMedTag(FERIE_NAR).max

        assertThat(max).isEqualTo(oppdatertMax)
    }

    @Test
    fun utenFriskmeldingErFeriesporsmalUendret() {
        var sykepengesoknad = opprettNySoknadMock()
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).copy(
                min = null,
                max = null,
                svar = emptyList()
            )
        )
        val max = sykepengesoknad.getSporsmalMedTag(FERIE_NAR_V2).max
        val oppdatertMax =
            oppdaterMedSvarPaArbeidGjenopptattArbeidstaker(sykepengesoknad).getSporsmalMedTag(FERIE_NAR_V2).max

        assertThat(max).isEqualTo(oppdatertMax)
    }

    @Test
    fun ferieperiodeKuttesVedFriskmeldingNarFeriesporsmalErUndersporsmal() {
        val periodeFom = LocalDate.now().minusDays(25)
        val periodeTom = LocalDate.now().minusDays(17)

        var sykepengesoknad = gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal().copy(
            fom = periodeFom,
            tom = periodeTom
        )
        val arbeidGjenopptattDato = LocalDate.now().minusDays(23)
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).copy(
                min = null,
                max = null,
                svar = listOf(Svar(null, arbeidGjenopptattDato.format(DateTimeFormatter.ISO_LOCAL_DATE), null))
            )
        )
        val oppdatertMax =
            oppdaterMedSvarPaArbeidGjenopptattArbeidstaker(sykepengesoknad).getSporsmalMedTag(FERIE_NAR).max

        assertThat(oppdatertMax).isEqualTo(arbeidGjenopptattDato.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE))
    }

    @Test
    fun ferieperiodeKuttesVedFriskmelding() {
        val periodeFom = LocalDate.now().minusDays(25)
        val periodeTom = LocalDate.now().minusDays(17)

        var sykepengesoknad = opprettNySoknadMock().copy(
            fom = periodeFom,
            tom = periodeTom
        )
        val arbeidGjenopptattDato = LocalDate.now().minusDays(23)
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).copy(
                min = null,
                max = null,
                svar = listOf(Svar(null, arbeidGjenopptattDato.format(DateTimeFormatter.ISO_LOCAL_DATE), null))
            )
        )
        val oppdatertMax =
            oppdaterMedSvarPaArbeidGjenopptattArbeidstaker(sykepengesoknad).getSporsmalMedTag(FERIE_NAR_V2).max

        assertThat(oppdatertMax).isEqualTo(arbeidGjenopptattDato.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE))
    }

    @Test
    fun utenFriskmeldingErUtdanningssporsmalUendret() {
        var sykepengesoknad = opprettNySoknadMock()
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).copy(
                min = null,
                max = null,
                svar = emptyList()
            )
        )
        val max = sykepengesoknad.getSporsmalMedTag(UTDANNING_START).max
        val oppdatertMax =
            oppdaterMedSvarPaArbeidGjenopptattArbeidstaker(sykepengesoknad).getSporsmalMedTag(UTDANNING_START).max

        assertThat(max).isEqualTo(oppdatertMax)
    }

    @Test
    fun utdanningsperiodeKuttesVedFriskmelding() {
        val periodeFom = LocalDate.now().minusDays(25)
        val periodeTom = LocalDate.now().minusDays(17)
        var sykepengesoknad = opprettNySoknadMock().copy(
            fom = periodeFom,
            tom = periodeTom
        )
        val arbeidGjenopptattDato = LocalDate.now().minusDays(23)
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).copy(
                min = null,
                max = null,
                svar = listOf(Svar(null, arbeidGjenopptattDato.format(DateTimeFormatter.ISO_LOCAL_DATE), null))
            )
        )
        val oppdatertMax =
            oppdaterMedSvarPaArbeidGjenopptattArbeidstaker(sykepengesoknad).getSporsmalMedTag(UTDANNING_START).max

        assertThat(oppdatertMax).isEqualTo(arbeidGjenopptattDato.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE))
    }

    @Test
    fun leggerIkkeTilSporsmalOmABeholdeSykepenger() {
        val sykepengesoknad = opprettNySoknadMock()
        assertThat(sykepengesoknad.getOptionalSporsmalMedTag(UTLANDSOPPHOLD_SOKT_SYKEPENGER)).isNotPresent
    }

    @Test
    fun leggerIkkeTilSporsmalHvisUtlandsoppholdErIHelg() {
        val nesteLordag = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
        val nesteSondag = nesteLordag.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))

        var sykepengesoknad = opprettNySoknadMock()
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(UTLAND_NAR_V2).copy(
                min = null,
                max = null,
                svar = listOf(Svar(null, DatoUtil.periodeTilJson(nesteLordag, nesteSondag), null))
            )
        )
        val oppdatertSoknad: Sykepengesoknad = oppdaterMedSvarPaUtlandsopphold(sykepengesoknad)

        assertThat(oppdatertSoknad.getOptionalSporsmalMedTag(UTLANDSOPPHOLD_SOKT_SYKEPENGER)).isNotPresent
    }

    @Test
    fun leggerIkkeTilSporsmalHvisUtlandsoppholdErIFerie() {
        val nesteMandag = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val nesteFredag = nesteMandag.with(TemporalAdjusters.next(DayOfWeek.FRIDAY))

        var sykepengesoknad = opprettNySoknadMock()
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(FERIE_NAR_V2).copy(
                min = null,
                max = null,
                svar = listOf(Svar(null, DatoUtil.periodeTilJson(nesteMandag, nesteFredag), null))
            )
        )
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(UTLAND_NAR_V2).copy(
                min = null,
                max = null,
                svar = listOf(Svar(null, DatoUtil.periodeTilJson(nesteMandag, nesteFredag), null))
            )
        )
        val oppdatertSoknad: Sykepengesoknad = oppdaterMedSvarPaUtlandsopphold(sykepengesoknad)

        assertThat(oppdatertSoknad.getOptionalSporsmalMedTag(UTLANDSOPPHOLD_SOKT_SYKEPENGER)).isNotPresent
    }

    @Test
    fun leggerIkkeTilSporsmalHvisUtlandsoppholdErIFerieSoknadMedFerieSomUndersporsmal() {
        val nesteMandag = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val nesteFredag = nesteMandag.with(TemporalAdjusters.next(DayOfWeek.FRIDAY))

        var sykepengesoknad = gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal()
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(FERIE_NAR).copy(
                min = null,
                max = null,
                svar = listOf(Svar(null, DatoUtil.periodeTilJson(nesteMandag, nesteFredag), null))
            )
        )
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(UTLAND_NAR).copy(
                min = null,
                max = null,
                svar = listOf(Svar(null, DatoUtil.periodeTilJson(nesteMandag, nesteFredag), null))
            )
        )
        val oppdatertSoknad: Sykepengesoknad = oppdaterMedSvarPaUtlandsopphold(sykepengesoknad)

        assertThat(oppdatertSoknad.getOptionalSporsmalMedTag(UTLANDSOPPHOLD_SOKT_SYKEPENGER)).isNotPresent
    }

    @Test
    fun leggerTilSporsmalHvisUtlandsoppholdErUtenforHelgOgFerie() {
        val nesteMandag = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val nesteFredag = nesteMandag.with(TemporalAdjusters.next(DayOfWeek.FRIDAY))

        var sykepengesoknad = opprettNySoknadMock()
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(FERIE_NAR_V2).copy(
                min = null,
                max = null,
                svar = emptyList()
            )
        )
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(UTLAND_NAR_V2).copy(
                min = null,
                max = null,
                svar = listOf(Svar(null, DatoUtil.periodeTilJson(nesteMandag, nesteFredag), null))
            )
        )
        val oppdatertSoknad: Sykepengesoknad = oppdaterMedSvarPaUtlandsopphold(sykepengesoknad)

        assertThat(oppdatertSoknad.getOptionalSporsmalMedTag(UTLANDSOPPHOLD_SOKT_SYKEPENGER)).isPresent
    }

    @Test
    fun leggerTilSporsmalHvisUtlandsoppholdErUtenforHelgOgFerieFeriesporsmalSomUndersporsmal() {
        val nesteMandag = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val nesteFredag = nesteMandag.with(TemporalAdjusters.next(DayOfWeek.FRIDAY))

        var sykepengesoknad = gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal()
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(FERIE_NAR).copy(
                min = null,
                max = null,
                svar = emptyList()
            )
        )
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(UTLAND_NAR).copy(
                min = null,
                max = null,
                svar = listOf(Svar(null, DatoUtil.periodeTilJson(nesteMandag, nesteFredag), null))
            )
        )
        val oppdatertSoknad: Sykepengesoknad = oppdaterMedSvarPaUtlandsopphold(sykepengesoknad)

        assertThat(oppdatertSoknad.getOptionalSporsmalMedTag(UTLANDSOPPHOLD_SOKT_SYKEPENGER)).isPresent
    }

    @Test
    fun sortererSporsmalISykepengesoknad() {
        val ekstraGradertsporsmal = Sporsmal(
            tag = JOBBET_DU_GRADERT + 4,
            svartype = Svartype.JA_NEI
        )
        val nyttSporsmalForan = opprettNySoknadMock().sporsmal.toMutableList()
        nyttSporsmalForan.add(0, ekstraGradertsporsmal)
        val nyttSporsmalBak = opprettNySoknadMock().sporsmal.toMutableList()
        nyttSporsmalBak.add(ekstraGradertsporsmal)
        nyttSporsmalForan.sortBy { sporsmal -> sporsmal.fellesPlasseringSporsmal() }
        nyttSporsmalBak.sortBy { sporsmal -> sporsmal.fellesPlasseringSporsmal() }

        assertThat(nyttSporsmalForan.map { it.tag })
            .isEqualTo(nyttSporsmalBak.map { it.tag })
    }

    @Test
    fun parserISODato() {
        val isoDato = LocalDate.now().minusDays(19).format(DateTimeFormatter.ISO_LOCAL_DATE)

        var sykepengesoknad = gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal()
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).copy(
                min = null,
                max = null,
                svar = listOf(Svar(null, isoDato, null))
            )
        )
        val gyldigArbeidGjenopptattsvar = getGyldigArbeidGjenopptattsvar(sykepengesoknad)

        assertThat(gyldigArbeidGjenopptattsvar).isEqualTo(LocalDate.now().minusDays(19))
    }

    @Test
    fun parserDatoPaSporsmalsformat() {
        val isoDato = LocalDate.now().minusDays(19).format(PeriodeMapper.sporsmalstekstFormat)

        var sykepengesoknad = opprettNySoknadMock()
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).copy(
                min = null,
                max = null,
                svar = listOf(Svar(null, isoDato, null))
            )
        )
        val gyldigArbeidGjenopptattsvar = getGyldigArbeidGjenopptattsvar(sykepengesoknad)

        assertThat(gyldigArbeidGjenopptattsvar).isEqualTo(LocalDate.now().minusDays(19))
    }

    @Test
    fun feilFormatGirNull() {
        val isoDato = "02.02.20__"

        var sykepengesoknad = opprettNySoknadMock()
        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag(TILBAKE_NAR).copy(
                min = null,
                max = null,
                svar = listOf(Svar(null, isoDato, null))
            )
        )
        val gyldigArbeidGjenopptattsvar = getGyldigArbeidGjenopptattsvar(sykepengesoknad)

        assertThat(gyldigArbeidGjenopptattsvar).isNull()
    }

    private fun getGyldigArbeidGjenopptattsvar(sykepengesoknad: Sykepengesoknad): LocalDate? {
        return sykepengesoknad.finnGyldigDatoSvar(TILBAKE_I_ARBEID, TILBAKE_NAR)
    }

    private fun getOppdaterteSoknadsperioder(sykepengesoknad: Sykepengesoknad): List<Soknadsperiode> {
        val arbeidGjenopptattDato = getGyldigArbeidGjenopptattsvar(sykepengesoknad)
        return sykepengesoknad.skapOppdaterteSoknadsperioder(arbeidGjenopptattDato)
    }
}
