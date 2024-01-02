package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger.KlippMetrikk
import no.nav.helse.flex.soknadsopprettelse.splitt.Tidsenhet
import no.nav.helse.flex.soknadsopprettelse.splitt.splittMellomTyper
import no.nav.helse.flex.soknadsopprettelse.splitt.splittSykmeldingiSoknadsPerioder
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.DayOfWeek
import java.time.LocalDate

class SplittSykmeldingperioderTest : BaseTestClass() {
    @Autowired
    lateinit var klippMetrikk: KlippMetrikk

    @Test
    fun splitter33dagersSMtil16Og17() {
        val sykmeldingDokument =
            skapArbeidsgiverSykmelding(
                fom = LocalDate.of(2017, 1, 1),
                tom = LocalDate.of(2017, 2, 2),
            )

        val tidsenheter =
            sykmeldingDokument.splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.id,
                sykmeldingDokument.behandletTidspunkt.toInstant(),
                "12345678",
                klippMetrikk,
            )
        assertThat(tidsenheter.size).isEqualTo(2)
        assertThat(tidsenheter[0].fom).isEqualTo(LocalDate.of(2017, 1, 1))
        assertThat(tidsenheter[0].tom).isEqualTo(LocalDate.of(2017, 1, 17))

        assertThat(tidsenheter[1].fom).isEqualTo(LocalDate.of(2017, 1, 18))
        assertThat(tidsenheter[1].tom).isEqualTo(LocalDate.of(2017, 2, 2))
    }

    @Test
    fun splitterIkkeSMunder32dager() {
        val sykmeldingDokument =
            skapArbeidsgiverSykmelding(
                fom = LocalDate.of(2017, 1, 1),
                tom = LocalDate.of(2017, 1, 16),
            )

        val tidsenheter =
            sykmeldingDokument.splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.id,
                sykmeldingDokument.behandletTidspunkt.toInstant(),
                "12345678",
                klippMetrikk,
            )

        assertThat(tidsenheter.size).isEqualTo(1)
        assertThat(tidsenheter.get(0).fom).isEqualTo(LocalDate.of(2017, 1, 1))
        assertThat(tidsenheter.get(0).tom).isEqualTo(LocalDate.of(2017, 1, 16))
    }

    @Test
    fun splitter92dagersiTreSykmeldinger() {
        val sykmeldingDokument =
            skapArbeidsgiverSykmelding().copy(
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2017, 1, 1),
                            tom = LocalDate.of(2017, 2, 15),
                            type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2017, 2, 16),
                            tom = LocalDate.of(2017, 4, 2),
                            type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                    ),
            )

        val tidsenheter =
            sykmeldingDokument.splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.id,
                sykmeldingDokument.behandletTidspunkt.toInstant(),
                "12345678",
                klippMetrikk,
            )

        assertThat(tidsenheter.size).isEqualTo(3)
        assertThat(tidsenheter.get(0).fom).isEqualTo(LocalDate.of(2017, 1, 1))
        assertThat(tidsenheter.get(0).tom).isEqualTo(LocalDate.of(2017, 1, 31))

        assertThat(tidsenheter.get(1).fom).isEqualTo(LocalDate.of(2017, 2, 1))
        assertThat(tidsenheter.get(1).tom).isEqualTo(LocalDate.of(2017, 3, 3))

        assertThat(tidsenheter.get(2).fom).isEqualTo(LocalDate.of(2017, 3, 4))
        assertThat(tidsenheter.get(2).tom).isEqualTo(LocalDate.of(2017, 4, 2))
    }

    @Test
    fun splitter100dagersSMiFireSykmeldinger() {
        val sykmeldingDokument =
            skapArbeidsgiverSykmelding().copy(
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2017, 1, 1),
                            tom = LocalDate.of(2017, 2, 15),
                            type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2017, 2, 16),
                            tom = LocalDate.of(2017, 4, 10),
                            type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                    ),
            )

        val tidsenheter =
            sykmeldingDokument.splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.id,
                sykmeldingDokument.behandletTidspunkt.toInstant(),
                "12345678",
                klippMetrikk,
            )

        assertThat(tidsenheter.size).isEqualTo(4)
        assertThat(tidsenheter.get(0).fom).isEqualTo(LocalDate.of(2017, 1, 1))
        assertThat(tidsenheter.get(0).tom).isEqualTo(LocalDate.of(2017, 1, 25))

        assertThat(tidsenheter.get(1).fom).isEqualTo(LocalDate.of(2017, 1, 26))
        assertThat(tidsenheter.get(1).tom).isEqualTo(LocalDate.of(2017, 2, 19))

        assertThat(tidsenheter.get(2).fom).isEqualTo(LocalDate.of(2017, 2, 20))
        assertThat(tidsenheter.get(2).tom).isEqualTo(LocalDate.of(2017, 3, 16))

        assertThat(tidsenheter.get(3).fom).isEqualTo(LocalDate.of(2017, 3, 17))
        assertThat(tidsenheter.get(3).tom).isEqualTo(LocalDate.of(2017, 4, 10))
    }

    @Test
    fun splitter32dagersSMiTo16dagers() {
        val sykmeldingDokument =
            skapArbeidsgiverSykmelding(
                fom = LocalDate.of(2017, 1, 1),
                tom = LocalDate.of(2017, 2, 1),
            )

        val tidsenheter =
            sykmeldingDokument.splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.id,
                sykmeldingDokument.behandletTidspunkt.toInstant(),
                "12345678",
                klippMetrikk,
            )

        assertThat(tidsenheter.size).isEqualTo(2)
        assertThat(tidsenheter.get(0).fom).isEqualTo(LocalDate.of(2017, 1, 1))
        assertThat(tidsenheter.get(0).tom).isEqualTo(LocalDate.of(2017, 1, 16))

        assertThat(tidsenheter.get(1).fom).isEqualTo(LocalDate.of(2017, 1, 17))
        assertThat(tidsenheter.get(1).tom).isEqualTo(LocalDate.of(2017, 2, 1))
    }

    @Test
    fun splitter33dagersSMtil16Og17EnkeltstaendeBehandling() {
        val sykmeldingDokument =
            skapArbeidsgiverSykmelding().copy(
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2017, 1, 1),
                            tom = LocalDate.of(2017, 2, 2),
                            type = PeriodetypeDTO.BEHANDLINGSDAGER,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                    ),
            )

        val tidsenheter =
            sykmeldingDokument.splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.id,
                sykmeldingDokument.behandletTidspunkt.toInstant(),
                "12345678",
                klippMetrikk,
            )

        assertThat(tidsenheter.size).isEqualTo(2)
        assertThat(tidsenheter.get(0).fom).isEqualTo(LocalDate.of(2017, 1, 1))
        assertThat(tidsenheter.get(0).tom).isEqualTo(LocalDate.of(2017, 1, 22))

        assertThat(tidsenheter.get(1).fom).isEqualTo(LocalDate.of(2017, 1, 23))
        assertThat(tidsenheter.get(1).tom).isEqualTo(LocalDate.of(2017, 2, 2))
    }

    @Test
    fun splitterIkkesykmeldingDokumentunder32dagerEnkeltstaendeBehandling() {
        val sykmeldingDokument =
            skapArbeidsgiverSykmelding().copy(
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2017, 1, 1),
                            tom = LocalDate.of(2017, 1, 16),
                            type = PeriodetypeDTO.BEHANDLINGSDAGER,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                    ),
            )

        val tidsenheter =
            sykmeldingDokument.splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.id,
                sykmeldingDokument.behandletTidspunkt.toInstant(),
                "12345678",
                klippMetrikk,
            )

        assertThat(tidsenheter.size).isEqualTo(1)
        assertThat(tidsenheter.get(0).fom).isEqualTo(LocalDate.of(2017, 1, 1))
        assertThat(tidsenheter.get(0).tom).isEqualTo(LocalDate.of(2017, 1, 16))
    }

    @Test
    fun splitter61dagersiTreSykmeldingerEnkeltstaendeBehandling() {
        val sykmeldingDokument =
            skapArbeidsgiverSykmelding().copy(
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2017, 1, 1),
                            tom = LocalDate.of(2017, 2, 15),
                            type = PeriodetypeDTO.BEHANDLINGSDAGER,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2017, 2, 16),
                            tom = LocalDate.of(2017, 3, 2),
                            type = PeriodetypeDTO.BEHANDLINGSDAGER,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                    ),
            )
        val splittetPaType = sykmeldingDokument.splittMellomTyper()

        assertThat(splittetPaType.size).isEqualTo(2)

        val foerstePeriode =
            splittetPaType.first().splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.id,
                sykmeldingDokument.behandletTidspunkt.toInstant(),
                "12345678",
                klippMetrikk,
            )
        val andrePeriode =
            splittetPaType.last().splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.id,
                sykmeldingDokument.behandletTidspunkt.toInstant(),
                "12345678",
                klippMetrikk,
            )

        assertThat(foerstePeriode.size).isEqualTo(2)
        assertThat(foerstePeriode.get(0).fom).isEqualTo(LocalDate.of(2017, 1, 1))
        assertThat(foerstePeriode.get(0).tom).isEqualTo(LocalDate.of(2017, 1, 29))
        assertThat(foerstePeriode.get(0).tom.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY)

        assertThat(foerstePeriode.get(1).fom).isEqualTo(LocalDate.of(2017, 1, 30))
        assertThat(foerstePeriode.get(1).tom).isEqualTo(LocalDate.of(2017, 2, 15))
        assertThat(foerstePeriode.get(1).tom.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY)

        assertThatAlleUtenomForsteFomErMandag(foerstePeriode.take(2))
        assertThatAlleUtenomSisteTomErSondag(foerstePeriode.take(2))

        assertThat(andrePeriode.get(0).fom).isEqualTo(LocalDate.of(2017, 2, 16))
        assertThat(andrePeriode.get(0).tom).isEqualTo(LocalDate.of(2017, 3, 2))
        assertThat(andrePeriode.get(0).tom.getDayOfWeek()).isEqualTo(DayOfWeek.THURSDAY)
    }

    @Test
    fun splitter100dagerssykmeldingDokumentiFireSykmeldingerEnkeltstaendeBehandling() {
        val sykmeldingDokument =
            skapArbeidsgiverSykmelding().copy(
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2017, 1, 1),
                            tom = LocalDate.of(2017, 2, 15),
                            type = PeriodetypeDTO.BEHANDLINGSDAGER,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2017, 2, 16),
                            tom = LocalDate.of(2017, 4, 10),
                            type = PeriodetypeDTO.BEHANDLINGSDAGER,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                    ),
            )
        val splittetPaType = sykmeldingDokument.splittMellomTyper()

        assertThat(splittetPaType.size).isEqualTo(2)

        val foerstePeriode =
            splittetPaType.first().splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.id,
                sykmeldingDokument.behandletTidspunkt.toInstant(),
                "12345678",
                klippMetrikk,
            )
        val andrePeriode =
            splittetPaType.last().splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.id,
                sykmeldingDokument.behandletTidspunkt.toInstant(),
                "12345678",
                klippMetrikk,
            )

        assertThat(foerstePeriode.size).isEqualTo(2)
        assertThat(foerstePeriode.get(0).fom).isEqualTo(LocalDate.of(2017, 1, 1))
        assertThat(foerstePeriode.get(0).tom).isEqualTo(LocalDate.of(2017, 1, 29))
        assertThat(foerstePeriode.get(0).tom.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY)

        assertThat(foerstePeriode.get(1).fom).isEqualTo(LocalDate.of(2017, 1, 30))
        assertThat(foerstePeriode.get(1).tom).isEqualTo(LocalDate.of(2017, 2, 15))
        assertThat(foerstePeriode.get(1).tom.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY)

        assertThatAlleUtenomForsteFomErMandag(foerstePeriode.take(2))
        assertThatAlleUtenomSisteTomErSondag(foerstePeriode.take(2))

        assertThat(andrePeriode.get(0).fom).isEqualTo(LocalDate.of(2017, 2, 16))
        assertThat(andrePeriode.get(0).tom).isEqualTo(LocalDate.of(2017, 3, 12))
        assertThat(andrePeriode.get(0).tom.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY)

        assertThat(andrePeriode.get(1).fom).isEqualTo(LocalDate.of(2017, 3, 13))
        assertThat(andrePeriode.get(1).tom).isEqualTo(LocalDate.of(2017, 4, 10))
        assertThat(andrePeriode.get(1).tom.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY)

        assertThatAlleUtenomForsteFomErMandag(andrePeriode.reversed().take(2).reversed())
        assertThatAlleUtenomSisteTomErSondag(andrePeriode.reversed().take(2).reversed())
    }

    @Test
    fun splitter32dagerssykmeldingDokumentiTo16dagersEnkeltstaendeBehandling() {
        val sykmeldingDokument =
            skapArbeidsgiverSykmelding().copy(
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2017, 1, 1),
                            tom = LocalDate.of(2017, 2, 1),
                            type = PeriodetypeDTO.BEHANDLINGSDAGER,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                    ),
            )
        val tidsenheter =
            sykmeldingDokument.splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.id,
                sykmeldingDokument.behandletTidspunkt.toInstant(),
                "12345678",
                klippMetrikk,
            )

        assertThat(tidsenheter.size).isEqualTo(2)
        assertThat(tidsenheter.get(0).fom).isEqualTo(LocalDate.of(2017, 1, 1))
        assertThat(tidsenheter.get(0).tom).isEqualTo(LocalDate.of(2017, 1, 22))
        assertThat(tidsenheter.get(0).tom.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY)

        assertThatAlleUtenomForsteFomErMandag(tidsenheter)
        assertThatAlleUtenomSisteTomErSondag(tidsenheter)

        assertThat(tidsenheter.get(tidsenheter.size - 1).fom).isEqualTo(LocalDate.of(2017, 1, 23))
        assertThat(tidsenheter.get(tidsenheter.size - 1).tom).isEqualTo(LocalDate.of(2017, 2, 1))
        assertThat(tidsenheter.get(tidsenheter.size - 1).tom.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY)
    }

    @Test
    fun splitterIkkeSlikAtSisteSoknadBlirKortereEnnEnUkeEnkeltstaendeBehandling() {
        val sykmeldingDokument =
            skapArbeidsgiverSykmelding().copy(
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2019, 11, 1),
                            tom = LocalDate.of(2019, 12, 9),
                            type = PeriodetypeDTO.BEHANDLINGSDAGER,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                    ),
            )
        val tidsenheter =
            sykmeldingDokument.splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.id,
                sykmeldingDokument.behandletTidspunkt.toInstant(),
                "12345678",
                klippMetrikk,
            )

        assertThat(tidsenheter.size).isEqualTo(2)
        assertThat(tidsenheter.get(0).fom).isEqualTo(LocalDate.of(2019, 11, 1))
        assertThat(tidsenheter.get(0).tom).isEqualTo(LocalDate.of(2019, 11, 17))
        assertThat(tidsenheter.get(0).tom.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY)

        assertThatAlleUtenomForsteFomErMandag(tidsenheter)
        assertThatAlleUtenomSisteTomErSondag(tidsenheter)

        assertThat(tidsenheter.get(tidsenheter.size - 1).fom).isEqualTo(LocalDate.of(2019, 11, 18))
        assertThat(tidsenheter.get(tidsenheter.size - 1).tom).isEqualTo(LocalDate.of(2019, 12, 9))
        assertThat(tidsenheter.get(tidsenheter.size - 1).tom.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY)
    }

    private fun assertThatAlleUtenomSisteTomErSondag(tidsenheter: List<Tidsenhet>) {
        for (i in 0 until tidsenheter.size - 1) {
            assertThat(tidsenheter[i].tom.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY)
        }
    }

    private fun assertThatAlleUtenomForsteFomErMandag(tidsenheter: List<Tidsenhet>) {
        for (i in 1 until tidsenheter.size) {
            assertThat(tidsenheter[i].fom.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY)
        }
    }
}
