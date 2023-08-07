package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.sporsmal.andreInntektskilderArbeidsledig
import no.nav.helse.flex.soknadsopprettelse.sporsmal.andreInntektskilderArbeidstaker
import no.nav.helse.flex.soknadsopprettelse.sporsmal.andreInntektskilderSelvstendigOgFrilanser
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagMedlemskapOppholdstillatelseSporsmal
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.*

class UnderersporsmalSortererTest {
    @Test
    fun `test sortering av andre inntektskilder arbeidsledig`() {
        val sporsmalet = andreInntektskilderArbeidsledig(fom = LocalDate.now(), tom = LocalDate.now())
        val soknad = sporsmalet.tilSoknad()
        val sporsmal = soknad.getSporsmalMedTag(HVILKE_ANDRE_INNTEKTSKILDER)
        val soknadShufflet = soknad.replaceSporsmal(sporsmal.copy(undersporsmal = sporsmal.undersporsmal.shuffled()))

        val soknadSortert = soknadShufflet.sorterUndersporsmal()

        val forventetSortering = listOf(
            INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD,
            INNTEKTSKILDE_SELVSTENDIG,
            INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA,
            INNTEKTSKILDE_JORDBRUKER,
            INNTEKTSKILDE_FRILANSER,
            INNTEKTSKILDE_OMSORGSLONN,
            INNTEKTSKILDE_FOSTERHJEM,
            INNTEKTSKILDE_ANNET
        )
        soknadSortert.getSporsmalMedTag(HVILKE_ANDRE_INNTEKTSKILDER).undersporsmal.map { it.tag } `should be equal to` forventetSortering
    }

    @Test
    fun `test sortering av andre inntektskilder frilanser`() {
        val sporsmalet = andreInntektskilderSelvstendigOgFrilanser(Arbeidssituasjon.FRILANSER)
        val soknad = sporsmalet.tilSoknad()
        val sporsmal = soknad.getSporsmalMedTag(HVILKE_ANDRE_INNTEKTSKILDER)
        val soknadShufflet = soknad.replaceSporsmal(sporsmal.copy(undersporsmal = sporsmal.undersporsmal.shuffled()))

        val soknadSortert = soknadShufflet.sorterUndersporsmal()

        val forventetSortering = listOf(
            INNTEKTSKILDE_ARBEIDSFORHOLD,
            INNTEKTSKILDE_JORDBRUKER,
            INNTEKTSKILDE_FRILANSER_SELVSTENDIG,
            INNTEKTSKILDE_ANNET
        )
        soknadSortert.getSporsmalMedTag(HVILKE_ANDRE_INNTEKTSKILDER).undersporsmal.map { it.tag } `should be equal to` forventetSortering
    }

    @Test
    fun `test sortering av andre inntektskilder arbeidstaker`() {
        val sporsmalet = andreInntektskilderArbeidstaker("Staten")
        val soknad = sporsmalet.tilSoknad()
        val sporsmal = soknad.getSporsmalMedTag(HVILKE_ANDRE_INNTEKTSKILDER)
        val soknadShufflet = soknad.replaceSporsmal(sporsmal.copy(undersporsmal = sporsmal.undersporsmal.shuffled()))

        val soknadSortert = soknadShufflet.sorterUndersporsmal()

        val forventetSortering = listOf(
            INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD,
            INNTEKTSKILDE_SELVSTENDIG,
            INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA,
            INNTEKTSKILDE_JORDBRUKER,
            INNTEKTSKILDE_FRILANSER,
            INNTEKTSKILDE_ANNET
        )
        soknadSortert.getSporsmalMedTag(HVILKE_ANDRE_INNTEKTSKILDER).undersporsmal.map { it.tag } `should be equal to` forventetSortering
    }

    @Test
    fun `test sortering av andre arbeidsgiver sporsmal`() {
        val soknad = settOppSoknadOppholdUtland("12345")
        val sporsmal = soknad.getSporsmalMedTag(ARBEIDSGIVER)
        val soknadShufflet = soknad.replaceSporsmal(sporsmal.copy(undersporsmal = sporsmal.undersporsmal.shuffled()))

        val soknadSortert = soknadShufflet.sorterUndersporsmal()

        val forventetSortering = listOf(
            SYKMELDINGSGRAD,
            FERIE
        )
        soknadSortert.getSporsmalMedTag(ARBEIDSGIVER).undersporsmal.map { it.tag } `should be equal to` forventetSortering
    }

    @Test
    fun `test sortering av reise med bil spm`() {
        val sporsmalet = reiseMedBilSpørsmål("1 til 2. juli", LocalDate.now(), LocalDate.now())
        val soknad = sporsmalet.tilSoknad()
        val sporsmal = soknad.getSporsmalMedTag(REISE_MED_BIL)
        val soknadShufflet = soknad.replaceSporsmal(sporsmal.copy(undersporsmal = sporsmal.undersporsmal.shuffled()))

        val soknadSortert = soknadShufflet.sorterUndersporsmal()

        val forventetSortering = listOf(
            BIL_DATOER,
            BIL_BOMPENGER,
            KM_HJEM_JOBB
        )
        soknadSortert.getSporsmalMedTag(REISE_MED_BIL).undersporsmal.map { it.tag } `should be equal to` forventetSortering
    }

    @Test
    fun `test sortering av medlemskapspørsmål om oppholdstillatelse`() {
        val sporsmalet = lagMedlemskapOppholdstillatelseSporsmal(LocalDate.now())
        val soknad = sporsmalet.tilSoknad()

        val sporsmal = soknad.getSporsmalMedTag(MEDLEMSKAP_OPPHOLDSTILLATELSE)
        val soknadShufflet = soknad.replaceSporsmal(sporsmal.copy(undersporsmal = sporsmal.undersporsmal.shuffled()))
        val soknadSortert = soknadShufflet.sorterUndersporsmal()

        val forventetSortering = listOf(
            MEDLEMSKAP_OPPHOLDSTILLATELSE_VEDTAKSDATO,
            MEDLEMSKAP_OPPHOLDSTILLATELSE_PERMANENT
        )
        soknadSortert.getSporsmalMedTag(MEDLEMSKAP_OPPHOLDSTILLATELSE).undersporsmal.map { it.tag } `should be equal to` forventetSortering
    }
}

fun Sporsmal.tilSoknad(): Sykepengesoknad {
    return Sykepengesoknad(
        id = UUID.randomUUID().toString(),
        fnr = "123",
        status = Soknadstatus.NY,
        opprettet = Instant.now(),
        sporsmal = listOf(this),
        soknadstype = Soknadstype.OPPHOLD_UTLAND,
        arbeidssituasjon = null,
        fom = null,
        tom = null,
        soknadPerioder = emptyList(),
        startSykeforlop = null,
        sykmeldingSkrevet = null,
        sykmeldingId = null,
        egenmeldtSykmelding = null,
        merknaderFraSykmelding = null,
        utenlandskSykmelding = false,
        egenmeldingsdagerFraSykmelding = null
    )
}
