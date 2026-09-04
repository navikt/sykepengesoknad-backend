package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadstype
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ErKunMeldingTilNavDagerForskjelligTest {
    private val forsteFom = LocalDate.of(2025, 1, 1)
    private val andreFom = LocalDate.of(2025, 1, 2)
    private val tom = LocalDate.of(2025, 1, 3)

    @Test
    fun `returnerer true når kun meldingTilNavDagerFraSykmelding er forskjellig`() {
        val eksisterende =
            hashSetOf(
                sammenlikner(
                    meldingTilNavDagerFraSykmelding = listOf(Periode(forsteFom, tom)),
                ),
            )
        val nye =
            hashSetOf(
                sammenlikner(
                    meldingTilNavDagerFraSykmelding = listOf(Periode(andreFom, tom)),
                ),
            )

        erKunMeldingTilNavDagerForskjellig(eksisterende, nye) `should be equal to` true
    }

    @Test
    fun `returnerer true når kun meldingTilNavDagerFraSykmelding er forskjellig med null`() {
        val eksisterende =
            hashSetOf(
                sammenlikner(
                    meldingTilNavDagerFraSykmelding = listOf(Periode(forsteFom, tom)),
                ),
            )
        val nye =
            hashSetOf(
                sammenlikner(
                    meldingTilNavDagerFraSykmelding = null,
                ),
            )

        erKunMeldingTilNavDagerForskjellig(eksisterende, nye) `should be equal to` true
    }

    @Test
    fun `returnerer false når alt er likt`() {
        val eksisterende =
            hashSetOf(
                sammenlikner(
                    meldingTilNavDagerFraSykmelding = listOf(Periode(forsteFom, tom)),
                ),
            )
        val nye =
            hashSetOf(
                sammenlikner(
                    meldingTilNavDagerFraSykmelding = listOf(Periode(forsteFom, tom)),
                ),
            )

        erKunMeldingTilNavDagerForskjellig(eksisterende, nye) `should be equal to` false
    }

    @Test
    fun `returnerer false når både arbeidssituasjon og meldingTilNavDagerFraSykmelding er forskjellig`() {
        val eksisterende =
            hashSetOf(
                sammenlikner(
                    arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                    meldingTilNavDagerFraSykmelding = listOf(Periode(forsteFom, tom)),
                ),
            )
        val nye =
            hashSetOf(
                sammenlikner(
                    arbeidssituasjon = Arbeidssituasjon.FRILANSER,
                    meldingTilNavDagerFraSykmelding = listOf(Periode(andreFom, tom)),
                ),
            )

        erKunMeldingTilNavDagerForskjellig(eksisterende, nye) `should be equal to` false
    }

    private fun sammenlikner(
        arbeidssituasjon: Arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
        meldingTilNavDagerFraSykmelding: List<Periode>? = null,
    ): SoknadSammenlikner =
        SoknadSammenlikner(
            fom = forsteFom,
            tom = tom,
            sykmeldingId = "sykmelding-id",
            arbeidssituasjon = arbeidssituasjon,
            soknadstype = Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
            soknadPerioder = emptyList(),
            arbeidsgiverOrgnummer = "999888777",
            meldingTilNavDagerFraSykmelding = meldingTilNavDagerFraSykmelding,
        )
}
