package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykmeldingstype
import no.nav.helse.flex.domain.sykmelding.Sykmeldingsperiode
import no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger.KlippMetrikk
import no.nav.helse.flex.soknadsopprettelse.splitt.Tidsenhet
import no.nav.helse.flex.soknadsopprettelse.splitt.splittMellomTyper
import no.nav.helse.flex.soknadsopprettelse.splitt.splittSykmeldingiSoknadsPerioder
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmeldingTilSoknadOpprettelse
import no.nav.helse.flex.testutil.lagSoknad
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.DayOfWeek
import java.time.LocalDate

class SplittSykmeldingperioderTest : FellesTestOppsett() {
    @Autowired
    lateinit var klippMetrikk: KlippMetrikk

    @Test
    fun splitter33dagersSMtil16Og17() {
        val sykmeldingDokument =
            skapArbeidsgiverSykmeldingTilSoknadOpprettelse(
                fom = LocalDate.of(2017, 1, 1),
                tom = LocalDate.of(2017, 2, 2),
            )

        val tidsenheter =
            sykmeldingDokument.splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.sykmeldingId,
                sykmeldingDokument.behandletTidspunkt,
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
            skapArbeidsgiverSykmeldingTilSoknadOpprettelse(
                fom = LocalDate.of(2017, 1, 1),
                tom = LocalDate.of(2017, 1, 16),
            )

        val tidsenheter =
            sykmeldingDokument.splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.sykmeldingId,
                sykmeldingDokument.behandletTidspunkt,
                "12345678",
                klippMetrikk,
            )

        assertThat(tidsenheter.size).isEqualTo(1)
        assertThat(tidsenheter[0].fom).isEqualTo(LocalDate.of(2017, 1, 1))
        assertThat(tidsenheter[0].tom).isEqualTo(LocalDate.of(2017, 1, 16))
    }

    @Test
    fun splitter92dagersiTreSykmeldinger() {
        val sykmeldingDokument =
            skapArbeidsgiverSykmeldingTilSoknadOpprettelse().copy(
                sykmeldingsperioder =
                    listOf(
                        Sykmeldingsperiode(
                            fom = LocalDate.of(2017, 1, 1),
                            tom = LocalDate.of(2017, 2, 15),
                            type = Sykmeldingstype.AKTIVITET_IKKE_MULIG,
                            gradert = null,
                            reisetilskudd = false,
                        ),
                        Sykmeldingsperiode(
                            fom = LocalDate.of(2017, 2, 16),
                            tom = LocalDate.of(2017, 4, 2),
                            type = Sykmeldingstype.AKTIVITET_IKKE_MULIG,
                            gradert = null,
                            reisetilskudd = false,
                        ),
                    ),
            )

        val tidsenheter =
            sykmeldingDokument.splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.sykmeldingId,
                sykmeldingDokument.behandletTidspunkt,
                "12345678",
                klippMetrikk,
            )

        assertThat(tidsenheter.size).isEqualTo(3)
        assertThat(tidsenheter[0].fom).isEqualTo(LocalDate.of(2017, 1, 1))
        assertThat(tidsenheter[0].tom).isEqualTo(LocalDate.of(2017, 1, 31))

        assertThat(tidsenheter[1].fom).isEqualTo(LocalDate.of(2017, 2, 1))
        assertThat(tidsenheter[1].tom).isEqualTo(LocalDate.of(2017, 3, 3))

        assertThat(tidsenheter[2].fom).isEqualTo(LocalDate.of(2017, 3, 4))
        assertThat(tidsenheter[2].tom).isEqualTo(LocalDate.of(2017, 4, 2))
    }

    @Test
    fun splitter100dagersSMiFireSykmeldinger() {
        val sykmeldingDokument =
            skapArbeidsgiverSykmeldingTilSoknadOpprettelse().copy(
                sykmeldingsperioder =
                    listOf(
                        Sykmeldingsperiode(
                            fom = LocalDate.of(2017, 1, 1),
                            tom = LocalDate.of(2017, 2, 15),
                            type = Sykmeldingstype.AKTIVITET_IKKE_MULIG,
                            gradert = null,
                            reisetilskudd = false,
                        ),
                        Sykmeldingsperiode(
                            fom = LocalDate.of(2017, 2, 16),
                            tom = LocalDate.of(2017, 4, 10),
                            type = Sykmeldingstype.AKTIVITET_IKKE_MULIG,
                            gradert = null,
                            reisetilskudd = false,
                        ),
                    ),
            )

        val tidsenheter =
            sykmeldingDokument.splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.sykmeldingId,
                sykmeldingDokument.behandletTidspunkt,
                "12345678",
                klippMetrikk,
            )

        assertThat(tidsenheter.size).isEqualTo(4)
        assertThat(tidsenheter[0].fom).isEqualTo(LocalDate.of(2017, 1, 1))
        assertThat(tidsenheter[0].tom).isEqualTo(LocalDate.of(2017, 1, 25))

        assertThat(tidsenheter[1].fom).isEqualTo(LocalDate.of(2017, 1, 26))
        assertThat(tidsenheter[1].tom).isEqualTo(LocalDate.of(2017, 2, 19))

        assertThat(tidsenheter[2].fom).isEqualTo(LocalDate.of(2017, 2, 20))
        assertThat(tidsenheter[2].tom).isEqualTo(LocalDate.of(2017, 3, 16))

        assertThat(tidsenheter[3].fom).isEqualTo(LocalDate.of(2017, 3, 17))
        assertThat(tidsenheter[3].tom).isEqualTo(LocalDate.of(2017, 4, 10))
    }

    @Test
    fun splitter32dagersSMiTo16dagers() {
        val sykmeldingDokument =
            skapArbeidsgiverSykmeldingTilSoknadOpprettelse(
                fom = LocalDate.of(2017, 1, 1),
                tom = LocalDate.of(2017, 2, 1),
            )

        val tidsenheter =
            sykmeldingDokument.splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.sykmeldingId,
                sykmeldingDokument.behandletTidspunkt,
                "12345678",
                klippMetrikk,
            )

        assertThat(tidsenheter.size).isEqualTo(2)
        assertThat(tidsenheter[0].fom).isEqualTo(LocalDate.of(2017, 1, 1))
        assertThat(tidsenheter[0].tom).isEqualTo(LocalDate.of(2017, 1, 16))

        assertThat(tidsenheter[1].fom).isEqualTo(LocalDate.of(2017, 1, 17))
        assertThat(tidsenheter[1].tom).isEqualTo(LocalDate.of(2017, 2, 1))
    }

    @Test
    fun splitter33dagersSMtil16Og17EnkeltstaendeBehandling() {
        val sykmeldingDokument =
            skapArbeidsgiverSykmeldingTilSoknadOpprettelse().copy(
                sykmeldingsperioder =
                    listOf(
                        Sykmeldingsperiode(
                            fom = LocalDate.of(2017, 1, 1),
                            tom = LocalDate.of(2017, 2, 2),
                            type = Sykmeldingstype.BEHANDLINGSDAGER,
                            gradert = null,
                            reisetilskudd = false,
                        ),
                    ),
            )

        val tidsenheter =
            sykmeldingDokument.splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.sykmeldingId,
                sykmeldingDokument.behandletTidspunkt,
                "12345678",
                klippMetrikk,
            )

        assertThat(tidsenheter.size).isEqualTo(2)
        assertThat(tidsenheter[0].fom).isEqualTo(LocalDate.of(2017, 1, 1))
        assertThat(tidsenheter[0].tom).isEqualTo(LocalDate.of(2017, 1, 22))

        assertThat(tidsenheter[1].fom).isEqualTo(LocalDate.of(2017, 1, 23))
        assertThat(tidsenheter[1].tom).isEqualTo(LocalDate.of(2017, 2, 2))
    }

    @Test
    fun splitterIkkesykmeldingDokumentunder32dagerEnkeltstaendeBehandling() {
        val sykmeldingDokument =
            skapArbeidsgiverSykmeldingTilSoknadOpprettelse().copy(
                sykmeldingsperioder =
                    listOf(
                        Sykmeldingsperiode(
                            fom = LocalDate.of(2017, 1, 1),
                            tom = LocalDate.of(2017, 1, 16),
                            type = Sykmeldingstype.BEHANDLINGSDAGER,
                            gradert = null,
                            reisetilskudd = false,
                        ),
                    ),
            )

        val tidsenheter =
            sykmeldingDokument.splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.sykmeldingId,
                sykmeldingDokument.behandletTidspunkt,
                "12345678",
                klippMetrikk,
            )

        assertThat(tidsenheter.size).isEqualTo(1)
        assertThat(tidsenheter[0].fom).isEqualTo(LocalDate.of(2017, 1, 1))
        assertThat(tidsenheter[0].tom).isEqualTo(LocalDate.of(2017, 1, 16))
    }

    @Test
    fun splitter61dagersiTreSykmeldingerEnkeltstaendeBehandling() {
        val sykmeldingDokument =
            skapArbeidsgiverSykmeldingTilSoknadOpprettelse().copy(
                sykmeldingsperioder =
                    listOf(
                        Sykmeldingsperiode(
                            fom = LocalDate.of(2017, 1, 1),
                            tom = LocalDate.of(2017, 2, 15),
                            type = Sykmeldingstype.BEHANDLINGSDAGER,
                            gradert = null,
                            reisetilskudd = false,
                        ),
                        Sykmeldingsperiode(
                            fom = LocalDate.of(2017, 2, 16),
                            tom = LocalDate.of(2017, 3, 2),
                            type = Sykmeldingstype.BEHANDLINGSDAGER,
                            gradert = null,
                            reisetilskudd = false,
                        ),
                    ),
            )
        val splittetPaType = sykmeldingDokument.splittMellomTyper()

        assertThat(splittetPaType.size).isEqualTo(2)

        val foerstePeriode =
            splittetPaType.first().splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.sykmeldingId,
                sykmeldingDokument.behandletTidspunkt,
                "12345678",
                klippMetrikk,
            )
        val andrePeriode =
            splittetPaType.last().splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.sykmeldingId,
                sykmeldingDokument.behandletTidspunkt,
                "12345678",
                klippMetrikk,
            )

        assertThat(foerstePeriode.size).isEqualTo(2)
        assertThat(foerstePeriode[0].fom).isEqualTo(LocalDate.of(2017, 1, 1))
        assertThat(foerstePeriode[0].tom).isEqualTo(LocalDate.of(2017, 1, 29))
        assertThat(foerstePeriode[0].tom.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY)

        assertThat(foerstePeriode[1].fom).isEqualTo(LocalDate.of(2017, 1, 30))
        assertThat(foerstePeriode[1].tom).isEqualTo(LocalDate.of(2017, 2, 15))
        assertThat(foerstePeriode[1].tom.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY)

        assertThatAlleUtenomForsteFomErMandag(foerstePeriode.take(2))
        assertThatAlleUtenomSisteTomErSondag(foerstePeriode.take(2))

        assertThat(andrePeriode[0].fom).isEqualTo(LocalDate.of(2017, 2, 16))
        assertThat(andrePeriode[0].tom).isEqualTo(LocalDate.of(2017, 3, 2))
        assertThat(andrePeriode[0].tom.getDayOfWeek()).isEqualTo(DayOfWeek.THURSDAY)
    }

    @Test
    fun splitter100dagerssykmeldingDokumentiFireSykmeldingerEnkeltstaendeBehandling() {
        val sykmeldingDokument =
            skapArbeidsgiverSykmeldingTilSoknadOpprettelse().copy(
                sykmeldingsperioder =
                    listOf(
                        Sykmeldingsperiode(
                            fom = LocalDate.of(2017, 1, 1),
                            tom = LocalDate.of(2017, 2, 15),
                            type = Sykmeldingstype.BEHANDLINGSDAGER,
                            gradert = null,
                            reisetilskudd = false,
                        ),
                        Sykmeldingsperiode(
                            fom = LocalDate.of(2017, 2, 16),
                            tom = LocalDate.of(2017, 4, 10),
                            type = Sykmeldingstype.BEHANDLINGSDAGER,
                            gradert = null,
                            reisetilskudd = false,
                        ),
                    ),
            )
        val splittetPaType = sykmeldingDokument.splittMellomTyper()

        assertThat(splittetPaType.size).isEqualTo(2)

        val foerstePeriode =
            splittetPaType.first().splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.sykmeldingId,
                sykmeldingDokument.behandletTidspunkt,
                "12345678",
                klippMetrikk,
            )
        val andrePeriode =
            splittetPaType.last().splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.sykmeldingId,
                sykmeldingDokument.behandletTidspunkt,
                "12345678",
                klippMetrikk,
            )

        assertThat(foerstePeriode.size).isEqualTo(2)
        assertThat(foerstePeriode[0].fom).isEqualTo(LocalDate.of(2017, 1, 1))
        assertThat(foerstePeriode[0].tom).isEqualTo(LocalDate.of(2017, 1, 29))
        assertThat(foerstePeriode[0].tom.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY)

        assertThat(foerstePeriode[1].fom).isEqualTo(LocalDate.of(2017, 1, 30))
        assertThat(foerstePeriode[1].tom).isEqualTo(LocalDate.of(2017, 2, 15))
        assertThat(foerstePeriode[1].tom.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY)

        assertThatAlleUtenomForsteFomErMandag(foerstePeriode.take(2))
        assertThatAlleUtenomSisteTomErSondag(foerstePeriode.take(2))

        assertThat(andrePeriode[0].fom).isEqualTo(LocalDate.of(2017, 2, 16))
        assertThat(andrePeriode[0].tom).isEqualTo(LocalDate.of(2017, 3, 12))
        assertThat(andrePeriode[0].tom.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY)

        assertThat(andrePeriode[1].fom).isEqualTo(LocalDate.of(2017, 3, 13))
        assertThat(andrePeriode[1].tom).isEqualTo(LocalDate.of(2017, 4, 10))
        assertThat(andrePeriode[1].tom.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY)

        assertThatAlleUtenomForsteFomErMandag(andrePeriode.reversed().take(2).reversed())
        assertThatAlleUtenomSisteTomErSondag(andrePeriode.reversed().take(2).reversed())
    }

    @Test
    fun splitter32dagerssykmeldingDokumentiTo16dagersEnkeltstaendeBehandling() {
        val sykmeldingDokument =
            skapArbeidsgiverSykmeldingTilSoknadOpprettelse().copy(
                sykmeldingsperioder =
                    listOf(
                        Sykmeldingsperiode(
                            fom = LocalDate.of(2017, 1, 1),
                            tom = LocalDate.of(2017, 2, 1),
                            type = Sykmeldingstype.BEHANDLINGSDAGER,
                            gradert = null,
                            reisetilskudd = false,
                        ),
                    ),
            )
        val tidsenheter =
            sykmeldingDokument.splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.sykmeldingId,
                sykmeldingDokument.behandletTidspunkt,
                "12345678",
                klippMetrikk,
            )

        assertThat(tidsenheter.size).isEqualTo(2)
        assertThat(tidsenheter[0].fom).isEqualTo(LocalDate.of(2017, 1, 1))
        assertThat(tidsenheter[0].tom).isEqualTo(LocalDate.of(2017, 1, 22))
        assertThat(tidsenheter[0].tom.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY)

        assertThatAlleUtenomForsteFomErMandag(tidsenheter)
        assertThatAlleUtenomSisteTomErSondag(tidsenheter)

        assertThat(tidsenheter[tidsenheter.size - 1].fom).isEqualTo(LocalDate.of(2017, 1, 23))
        assertThat(tidsenheter[tidsenheter.size - 1].tom).isEqualTo(LocalDate.of(2017, 2, 1))
        assertThat(tidsenheter[tidsenheter.size - 1].tom.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY)
    }

    @Test
    fun splitterIkkeSlikAtSisteSoknadBlirKortereEnnEnUkeEnkeltstaendeBehandling() {
        val sykmeldingDokument =
            skapArbeidsgiverSykmeldingTilSoknadOpprettelse().copy(
                sykmeldingsperioder =
                    listOf(
                        Sykmeldingsperiode(
                            fom = LocalDate.of(2019, 11, 1),
                            tom = LocalDate.of(2019, 12, 9),
                            type = Sykmeldingstype.BEHANDLINGSDAGER,
                            gradert = null,
                            reisetilskudd = false,
                        ),
                    ),
            )
        val tidsenheter =
            sykmeldingDokument.splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                emptyList(),
                sykmeldingDokument.sykmeldingId,
                sykmeldingDokument.behandletTidspunkt,
                "12345678",
                klippMetrikk,
            )

        assertThat(tidsenheter.size).isEqualTo(2)
        assertThat(tidsenheter[0].fom).isEqualTo(LocalDate.of(2019, 11, 1))
        assertThat(tidsenheter[0].tom).isEqualTo(LocalDate.of(2019, 11, 17))
        assertThat(tidsenheter[0].tom.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY)

        assertThatAlleUtenomForsteFomErMandag(tidsenheter)
        assertThatAlleUtenomSisteTomErSondag(tidsenheter)

        assertThat(tidsenheter[tidsenheter.size - 1].fom).isEqualTo(LocalDate.of(2019, 11, 18))
        assertThat(tidsenheter[tidsenheter.size - 1].tom).isEqualTo(LocalDate.of(2019, 12, 9))
        assertThat(tidsenheter[tidsenheter.size - 1].tom.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY)
    }

    @Test
    fun `splitter søknader med gammel søknad som mangler signaturdato`() {
        val gammelSoknadUtenSignaturdato =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2017, 2, 1),
                tom = LocalDate.of(2017, 2, 20),
                startSykeforlop = LocalDate.of(2017, 2, 1),
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        val sykmeldingDokument =
            skapArbeidsgiverSykmeldingTilSoknadOpprettelse(
                arbeidsgiverOrgnummer = "org-1",
            ).copy(
                sykmeldingsperioder =
                    listOf(
                        Sykmeldingsperiode(
                            fom = LocalDate.of(2017, 1, 1),
                            tom = LocalDate.of(2017, 2, 15),
                            type = Sykmeldingstype.AKTIVITET_IKKE_MULIG,
                            gradert = null,
                            reisetilskudd = false,
                        ),
                        Sykmeldingsperiode(
                            fom = LocalDate.of(2017, 2, 16),
                            tom = LocalDate.of(2017, 4, 2),
                            type = Sykmeldingstype.AKTIVITET_IKKE_MULIG,
                            gradert = null,
                            reisetilskudd = false,
                        ),
                    ),
            )

        val tidsenheter =
            sykmeldingDokument.splittSykmeldingiSoknadsPerioder(
                Arbeidssituasjon.ARBEIDSTAKER,
                listOf(gammelSoknadUtenSignaturdato),
                sykmeldingDokument.sykmeldingId,
                sykmeldingDokument.signaturDato!!,
                "org-1",
                klippMetrikk,
            )

        assertThat(tidsenheter.size).isEqualTo(4)
        assertThat(tidsenheter[0].fom).isEqualTo(LocalDate.of(2017, 1, 1))
        assertThat(tidsenheter[0].tom).isEqualTo(LocalDate.of(2017, 1, 31))

        assertThat(tidsenheter[1].fom).isEqualTo(LocalDate.of(2017, 2, 1))
        assertThat(tidsenheter[1].tom).isEqualTo(LocalDate.of(2017, 2, 20))

        assertThat(tidsenheter[2].fom).isEqualTo(LocalDate.of(2017, 2, 21))
        assertThat(tidsenheter[2].tom).isEqualTo(LocalDate.of(2017, 3, 13))

        assertThat(tidsenheter[3].fom).isEqualTo(LocalDate.of(2017, 3, 14))
        assertThat(tidsenheter[3].tom).isEqualTo(LocalDate.of(2017, 4, 2))
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
