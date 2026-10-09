package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import no.nav.helse.flex.domain.*
import no.nav.helse.flex.mock.opprettNyArbeidstakerSoknad
import no.nav.helse.flex.mock.opprettNyFiskerHyreSoknad
import no.nav.helse.flex.soknadsopprettelse.FERIE_NAR_V2
import no.nav.helse.flex.soknadsopprettelse.FERIE_V2
import no.nav.helse.flex.soknadsopprettelse.JOBBET_DU_GRADERT
import no.nav.helse.flex.soknadsopprettelse.OPPHOLD_UTENFOR_EOS_NAR
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_I_ARBEID
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_NAR
import no.nav.helse.flex.soknadsopprettelse.UTDANNING
import no.nav.helse.flex.soknadsopprettelse.UTLANDSOPPHOLD_SOKT_SYKEPENGER
import no.nav.helse.flex.soknadsopprettelse.fellesPlasseringSporsmal
import no.nav.helse.flex.soknadsopprettelse.oppdateringhelpers.finnGyldigDatoSvar
import no.nav.helse.flex.soknadsopprettelse.oppdateringhelpers.skapOppdaterteSoknadsperioder
import no.nav.helse.flex.util.PeriodeMapper
import no.nav.helse.flex.util.periodeTilJson
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

class ArbeidstakereOgFiskereHyreMuteringTest {
    val testDato: LocalDate = LocalDate.of(2024, 6, 1)

    private fun forHverSoknadstype(test: (soknad: Sykepengesoknad) -> Unit): List<DynamicTest> =
        listOf(
            "arbeidstaker" to opprettNyArbeidstakerSoknad(),
            "fisker hyre" to opprettNyFiskerHyreSoknad(),
        ).map { (navn, soknad) -> dynamicTest(navn) { test(soknad) } }

    @TestFactory
    fun utenFriskmeldingErOppdaterteSoknadsperioderUendret() =
        forHverSoknadstype { soknad ->
            val sykepengesoknad =
                soknad.replaceSporsmal(soknad.getSporsmalMedTag(TILBAKE_NAR).copy(svar = emptyList()))
            val oppdaterteSoknadsperioder = getOppdaterteSoknadsperioder(sykepengesoknad)

            assertThat(oppdaterteSoknadsperioder).isEqualTo(sykepengesoknad.soknadPerioder)
        }

    @TestFactory
    fun periodeKuttesVedFriskmelding() =
        forHverSoknadstype { soknad ->
            val periode1Fom = testDato.plusDays(5)
            val periode1Tom = testDato.plusDays(15)
            val periode2Fom = testDato.plusDays(16)
            val periode2Tom = testDato.plusDays(25)
            val friskmeldtDato = testDato.plusDays(19)
            val periode2TomOppdatert = testDato.plusDays(18)

            val sykepengesoknad =
                soknad
                    .copy(
                        soknadPerioder =
                            listOf(
                                Soknadsperiode(fom = periode1Fom, tom = periode1Tom, grad = 100, sykmeldingstype = null),
                                Soknadsperiode(fom = periode2Fom, tom = periode2Tom, grad = 100, sykmeldingstype = null),
                            ),
                    ).medTilbakeNarSvar(friskmeldtDato.format(DateTimeFormatter.ISO_LOCAL_DATE))
            val oppdaterteSoknadsperioder = getOppdaterteSoknadsperioder(sykepengesoknad)

            assertThat(oppdaterteSoknadsperioder).hasSize(2)
            assertThat(oppdaterteSoknadsperioder[0].fom).isEqualTo(periode1Fom)
            assertThat(oppdaterteSoknadsperioder[0].tom).isEqualTo(periode1Tom)
            assertThat(oppdaterteSoknadsperioder[1].fom).isEqualTo(periode2Fom)
            assertThat(oppdaterteSoknadsperioder[1].tom).isEqualTo(periode2TomOppdatert)
        }

    @TestFactory
    fun periodeFjernesVedFriskmelding() =
        forHverSoknadstype { soknad ->
            val periode1Fom = testDato.plusDays(5)
            val periode1Tom = testDato.plusDays(15)
            val periode2Fom = testDato.plusDays(16)
            val periode2Tom = testDato.plusDays(25)
            val friskmeldtDato = testDato.plusDays(10)
            val periode1TomOppdatert = testDato.plusDays(9)

            val sykepengesoknad =
                soknad
                    .copy(
                        soknadPerioder =
                            listOf(
                                Soknadsperiode(periode1Fom, periode1Tom, grad = 100, sykmeldingstype = null),
                                Soknadsperiode(periode2Fom, periode2Tom, grad = 100, sykmeldingstype = null),
                            ),
                    ).medTilbakeNarSvar(friskmeldtDato.format(DateTimeFormatter.ISO_LOCAL_DATE))
            val oppdaterteSoknadsperioder = getOppdaterteSoknadsperioder(sykepengesoknad)

            assertThat(oppdaterteSoknadsperioder).hasSize(1)
            assertThat(oppdaterteSoknadsperioder[0].fom).isEqualTo(periode1Fom)
            assertThat(oppdaterteSoknadsperioder[0].tom).isEqualTo(periode1TomOppdatert)
        }

    @TestFactory
    fun ferieFjernesVedFriskmelding() =
        forHverSoknadstype { soknad ->
            val fom = testDato.plusDays(5)
            val tom = testDato.plusDays(15)

            val sykepengesoknad =
                soknad
                    .copy(fom = fom, tom = tom, soknadPerioder = listOf(Soknadsperiode(fom, tom, 100, null)))
                    .medTilbakeNarSvar(fom.format(DateTimeFormatter.ISO_LOCAL_DATE), fom = fom, tom = tom)
            val oppdatertSoknad = sykepengesoknad.arbeidGjenopptattMutering()

            assertThat(oppdatertSoknad.getSporsmalMedTagOrNull(FERIE_V2)).isNull()
        }

    @TestFactory
    fun ferieKuttesTilEnDag() =
        forHverSoknadstype { soknad ->
            val fom = testDato.minusDays(25)
            val tom = testDato.minusDays(17)
            val arbeidGjenopptattDato = testDato.minusDays(24)

            val sykepengesoknad =
                soknad
                    .copy(fom = fom, tom = tom)
                    .medTilbakeNarSvar(arbeidGjenopptattDato.format(DateTimeFormatter.ISO_LOCAL_DATE))
            val ferieNar = sykepengesoknad.arbeidGjenopptattMutering().getSporsmalMedTag(FERIE_NAR_V2)

            assertThat(ferieNar.min).isEqualTo(ferieNar.max)
            assertThat(ferieNar.max).isEqualTo(fom.format(DateTimeFormatter.ISO_LOCAL_DATE))
        }

    @TestFactory
    fun utdanningFjernesVedFriskmelding() =
        forHverSoknadstype { soknad ->
            val fom = testDato.plusDays(5)
            val tom = testDato.plusDays(15)

            val sykepengesoknad =
                soknad
                    .copy(fom = fom, tom = tom, soknadPerioder = listOf(Soknadsperiode(fom, tom, 100, null)))
                    .medTilbakeNarSvar(fom.format(DateTimeFormatter.ISO_LOCAL_DATE), fom = fom, tom = tom)
            val oppdatertSoknad = sykepengesoknad.arbeidGjenopptattMutering()

            assertThat(oppdatertSoknad.getSporsmalMedTagOrNull(UTDANNING)).isNull()
        }

    @TestFactory
    fun utenFriskmeldingErFeriesporsmalUendret() =
        forHverSoknadstype { soknad ->
            val sykepengesoknad =
                soknad.replaceSporsmal(
                    soknad.getSporsmalMedTag(TILBAKE_NAR).copy(min = null, max = null, svar = emptyList()),
                )
            val max = sykepengesoknad.getSporsmalMedTag(FERIE_NAR_V2).max
            val oppdatertMax = sykepengesoknad.arbeidGjenopptattMutering().getSporsmalMedTag(FERIE_NAR_V2).max

            assertThat(max).isEqualTo(oppdatertMax)
        }

    @TestFactory
    fun ferieperiodeKuttesVedFriskmelding() =
        forHverSoknadstype { soknad ->
            val periodeFom = testDato.minusDays(25)
            val periodeTom = testDato.minusDays(17)
            val arbeidGjenopptattDato = testDato.minusDays(23)

            val sykepengesoknad =
                soknad
                    .copy(fom = periodeFom, tom = periodeTom)
                    .medTilbakeNarSvar(arbeidGjenopptattDato.format(DateTimeFormatter.ISO_LOCAL_DATE))
            val oppdatertMax = sykepengesoknad.arbeidGjenopptattMutering().getSporsmalMedTag(FERIE_NAR_V2).max

            assertThat(oppdatertMax).isEqualTo(arbeidGjenopptattDato.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE))
        }

    @TestFactory
    fun leggerIkkeTilSporsmalOmABeholdeSykepenger() =
        forHverSoknadstype { soknad ->
            assertThat(soknad.getSporsmalMedTagOrNull(UTLANDSOPPHOLD_SOKT_SYKEPENGER)).isNull()
        }

    @TestFactory
    fun leggerIkkeTilSporsmalHvisUtlandsoppholdErIHelg() =
        forHverSoknadstype { soknad ->
            val nesteLordag = testDato.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
            val nesteSondag = nesteLordag.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))

            val sykepengesoknad = soknad.medPeriodesvar(OPPHOLD_UTENFOR_EOS_NAR, nesteLordag, nesteSondag)
            val oppdatertSoknad = sykepengesoknad.oppdaterMedSvarPaUtlandsopphold()

            assertThat(oppdatertSoknad.getSporsmalMedTagOrNull(UTLANDSOPPHOLD_SOKT_SYKEPENGER)).isNull()
        }

    @TestFactory
    fun leggerIkkeTilSporsmalHvisUtlandsoppholdErIFerie() =
        forHverSoknadstype { soknad ->
            val nesteMandag = testDato.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            val nesteFredag = nesteMandag.with(TemporalAdjusters.next(DayOfWeek.FRIDAY))

            val sykepengesoknad =
                soknad
                    .medPeriodesvar(FERIE_NAR_V2, nesteMandag, nesteFredag)
                    .medPeriodesvar(OPPHOLD_UTENFOR_EOS_NAR, nesteMandag, nesteFredag)
            val oppdatertSoknad = sykepengesoknad.oppdaterMedSvarPaUtlandsopphold()

            assertThat(oppdatertSoknad.getSporsmalMedTagOrNull(UTLANDSOPPHOLD_SOKT_SYKEPENGER)).isNull()
        }

    @TestFactory
    fun sortererSporsmalISykepengesoknad() =
        forHverSoknadstype { soknad ->
            val ekstraGradertsporsmal = Sporsmal(tag = JOBBET_DU_GRADERT + 4, svartype = Svartype.JA_NEI)

            val nyttSporsmalForan = (listOf(ekstraGradertsporsmal) + soknad.sporsmal).sortedBy { it.fellesPlasseringSporsmal() }
            val nyttSporsmalBak = (soknad.sporsmal + ekstraGradertsporsmal).sortedBy { it.fellesPlasseringSporsmal() }

            assertThat(nyttSporsmalForan.map { it.tag }).isEqualTo(nyttSporsmalBak.map { it.tag })
        }

    @TestFactory
    fun parserISODato() =
        forHverSoknadstype { soknad ->
            val dato = testDato.minusDays(19)

            val sykepengesoknad = soknad.medTilbakeNarSvar(dato.format(DateTimeFormatter.ISO_LOCAL_DATE))

            assertThat(getGyldigArbeidGjenopptattsvar(sykepengesoknad)).isEqualTo(dato)
        }

    @TestFactory
    fun parserDatoPaSporsmalsformat() =
        forHverSoknadstype { soknad ->
            val dato = testDato.minusDays(19)

            val sykepengesoknad = soknad.medTilbakeNarSvar(dato.format(PeriodeMapper.sporsmalstekstFormat))

            assertThat(getGyldigArbeidGjenopptattsvar(sykepengesoknad)).isEqualTo(dato)
        }

    @TestFactory
    fun feilFormatGirNull() =
        forHverSoknadstype { soknad ->
            val sykepengesoknad = soknad.medTilbakeNarSvar("02.02.20__")

            assertThat(getGyldigArbeidGjenopptattsvar(sykepengesoknad)).isNull()
        }

    private fun Sykepengesoknad.medTilbakeNarSvar(
        svar: String,
        fom: LocalDate? = null,
        tom: LocalDate? = null,
    ): Sykepengesoknad =
        replaceSporsmal(
            getSporsmalMedTag(TILBAKE_NAR).copy(
                min = fom?.format(DateTimeFormatter.ISO_LOCAL_DATE),
                max = tom?.format(DateTimeFormatter.ISO_LOCAL_DATE),
                svar = listOf(Svar(null, svar)),
            ),
        )

    private fun Sykepengesoknad.medPeriodesvar(
        tag: String,
        fom: LocalDate,
        tom: LocalDate,
    ): Sykepengesoknad =
        replaceSporsmal(
            getSporsmalMedTag(tag).copy(
                min = null,
                max = null,
                svar = listOf(Svar(null, periodeTilJson(fom, tom))),
            ),
        )

    private fun getGyldigArbeidGjenopptattsvar(sykepengesoknad: Sykepengesoknad): LocalDate? =
        sykepengesoknad.finnGyldigDatoSvar(TILBAKE_I_ARBEID, TILBAKE_NAR)

    private fun getOppdaterteSoknadsperioder(sykepengesoknad: Sykepengesoknad): List<Soknadsperiode> =
        sykepengesoknad.skapOppdaterteSoknadsperioder(getGyldigArbeidGjenopptattsvar(sykepengesoknad))
}
