package no.nav.helse.flex.arbeidstaker

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class KombinasjonSykmeldingerTest : BaseTestClass() {
    final val fnr = "123456789"

    val basisDato = LocalDate.of(2020, 3, 13)

    @BeforeEach
    fun beforeEach() {
        databaseReset.resetDatabase()
    }

    @Test
    fun `Splitter behandlingsdager og vanlig sykmelding`() {
        val sykmeldingId = UUID.randomUUID().toString()
        sendSykmelding(
            sykmeldingKafkaMessage(
                sykmeldingId = sykmeldingId,
                fnr = fnr,
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = basisDato,
                            tom = basisDato.plusDays(2),
                            type = PeriodetypeDTO.BEHANDLINGSDAGER,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = 1,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                        SykmeldingsperiodeAGDTO(
                            fom = basisDato.plusDays(3),
                            tom = basisDato.plusDays(4),
                            type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                    ).shuffled(),
            ),
            forventaSoknader = 2,
        )

        val hentetViaRest = hentSoknaderMetadata(fnr).filter { it.sykmeldingId == sykmeldingId }
        assertThat(hentetViaRest).hasSize(2)
        assertThat(hentetViaRest[0].soknadstype).isEqualTo(RSSoknadstype.BEHANDLINGSDAGER)
        assertThat(hentetViaRest[0].fom).isEqualTo(basisDato)
        assertThat(hentetViaRest[0].tom).isEqualTo(basisDato.plusDays(2))
        assertThat(hentetViaRest[1].soknadstype).isEqualTo(RSSoknadstype.ARBEIDSTAKERE)
        assertThat(hentetViaRest[1].fom).isEqualTo(basisDato.plusDays(3))
        assertThat(hentetViaRest[1].tom).isEqualTo(basisDato.plusDays(4))
    }

    @Test
    fun `Splitter behandlingsdager og reisetilskudd`() {
        val sykmeldingId = UUID.randomUUID().toString()
        sendSykmelding(
            sykmeldingKafkaMessage(
                sykmeldingId = sykmeldingId,
                fnr = fnr,
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = basisDato,
                            tom = basisDato.plusDays(2),
                            type = PeriodetypeDTO.BEHANDLINGSDAGER,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = 1,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                        SykmeldingsperiodeAGDTO(
                            fom = basisDato.plusDays(3),
                            tom = basisDato.plusDays(4),
                            type = PeriodetypeDTO.REISETILSKUDD,
                            reisetilskudd = true,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                    ).shuffled(),
            ),
            forventaSoknader = 2,
        )

        val hentetViaRest = hentSoknaderMetadata(fnr).filter { it.sykmeldingId == sykmeldingId }
        assertThat(hentetViaRest).hasSize(2)
        assertThat(hentetViaRest[0].soknadstype).isEqualTo(RSSoknadstype.BEHANDLINGSDAGER)
        assertThat(hentetViaRest[0].fom).isEqualTo(basisDato)
        assertThat(hentetViaRest[0].tom).isEqualTo(basisDato.plusDays(2))
        assertThat(hentetViaRest[1].soknadstype).isEqualTo(RSSoknadstype.REISETILSKUDD)
        assertThat(hentetViaRest[1].fom).isEqualTo(basisDato.plusDays(3))
        assertThat(hentetViaRest[1].tom).isEqualTo(basisDato.plusDays(4))
    }

    @Test
    fun `Splitter ikke gradert og 100 prosent`() {
        val sykmeldingId = UUID.randomUUID().toString()
        sendSykmelding(
            sykmeldingKafkaMessage(
                sykmeldingId = sykmeldingId,
                fnr = fnr,
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = basisDato,
                            tom = basisDato.plusDays(2),
                            type = PeriodetypeDTO.GRADERT,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = GradertDTO(grad = 1, reisetilskudd = false),
                            innspillTilArbeidsgiver = null,
                        ),
                        SykmeldingsperiodeAGDTO(
                            fom = basisDato.plusDays(3),
                            tom = basisDato.plusDays(4),
                            type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                    ).shuffled(),
            ),
        )

        val hentetViaRest = hentSoknaderMetadata(fnr).filter { it.sykmeldingId == sykmeldingId }
        assertThat(hentetViaRest).hasSize(1)
        assertThat(hentetViaRest[0].soknadstype).isEqualTo(RSSoknadstype.ARBEIDSTAKERE)
        assertThat(hentetViaRest[0].fom).isEqualTo(basisDato)
        assertThat(hentetViaRest[0].tom).isEqualTo(basisDato.plusDays(4))
    }

    @Test
    fun `Splitter gradert reisetilskudd og 100 prosent`() {
        val sykmeldingId = UUID.randomUUID().toString()
        sendSykmelding(
            sykmeldingKafkaMessage(
                sykmeldingId = sykmeldingId,
                fnr = fnr,
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = basisDato,
                            tom = basisDato.plusDays(2),
                            type = PeriodetypeDTO.GRADERT,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = GradertDTO(grad = 1, reisetilskudd = true),
                            innspillTilArbeidsgiver = null,
                        ),
                        SykmeldingsperiodeAGDTO(
                            fom = basisDato.plusDays(3),
                            tom = basisDato.plusDays(4),
                            type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                    ).shuffled(),
            ),
            forventaSoknader = 2,
        )

        val hentetViaRest = hentSoknaderMetadata(fnr).filter { it.sykmeldingId == sykmeldingId }
        assertThat(hentetViaRest).hasSize(2)
        assertThat(hentetViaRest[0].soknadstype).isEqualTo(RSSoknadstype.GRADERT_REISETILSKUDD)
        assertThat(hentetViaRest[0].fom).isEqualTo(basisDato)
        assertThat(hentetViaRest[0].tom).isEqualTo(basisDato.plusDays(2))
        assertThat(hentetViaRest[1].soknadstype).isEqualTo(RSSoknadstype.ARBEIDSTAKERE)
        assertThat(hentetViaRest[1].fom).isEqualTo(basisDato.plusDays(3))
        assertThat(hentetViaRest[1].tom).isEqualTo(basisDato.plusDays(4))
    }

    @Test
    fun `Splitter gradert reisetilskudd før 100 prosent og gradert`() {
        val sykmeldingId = UUID.randomUUID().toString()
        sendSykmelding(
            sykmeldingKafkaMessage(
                sykmeldingId = sykmeldingId,
                fnr = fnr,
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = basisDato,
                            tom = basisDato.plusDays(2),
                            type = PeriodetypeDTO.GRADERT,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = GradertDTO(grad = 1, reisetilskudd = true),
                            innspillTilArbeidsgiver = null,
                        ),
                        SykmeldingsperiodeAGDTO(
                            fom = basisDato.plusDays(3),
                            tom = basisDato.plusDays(4),
                            type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                        SykmeldingsperiodeAGDTO(
                            fom = basisDato.plusDays(5),
                            tom = basisDato.plusDays(6),
                            type = PeriodetypeDTO.GRADERT,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = GradertDTO(20, false),
                            innspillTilArbeidsgiver = null,
                        ),
                    ).shuffled(),
            ),
            forventaSoknader = 2,
        )

        val hentetViaRest = hentSoknaderMetadata(fnr).filter { it.sykmeldingId == sykmeldingId }
        assertThat(hentetViaRest).hasSize(2)
        assertThat(hentetViaRest[0].soknadstype).isEqualTo(RSSoknadstype.GRADERT_REISETILSKUDD)
        assertThat(hentetViaRest[0].fom).isEqualTo(basisDato)
        assertThat(hentetViaRest[0].tom).isEqualTo(basisDato.plusDays(2))
        assertThat(hentetViaRest[1].soknadstype).isEqualTo(RSSoknadstype.ARBEIDSTAKERE)
        assertThat(hentetViaRest[1].fom).isEqualTo(basisDato.plusDays(3))
        assertThat(hentetViaRest[1].tom).isEqualTo(basisDato.plusDays(6))
    }
}
