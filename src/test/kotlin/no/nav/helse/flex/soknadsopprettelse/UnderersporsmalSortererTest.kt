package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.sporsmal.andreInntektskilderArbeidsledig
import no.nav.helse.flex.soknadsopprettelse.sporsmal.andreInntektskilderArbeidstaker
import no.nav.helse.flex.soknadsopprettelse.sporsmal.andreInntektskilderSelvstendigOgFrilanser
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagMedlemskapArbeidUtenforNorgeSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagMedlemskapOppholdstillatelseSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagUndersporsmalTilArbeidUtenforNorgeSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.medIndex
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.*

class UnderersporsmalSortererTest {
    @Test
    fun `Test sortering av andre inntektskilder arbeidsledig`() {
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
    fun `Test sortering av andre inntektskilder frilanser`() {
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
    fun `Test sortering av andre inntektskilder arbeidstaker`() {
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
    fun `Test sortering av andre arbeidsgiver sporsmal`() {
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
    fun `Test sortering av reise med bil spm`() {
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
    fun `Test sortering av medlemskapspørsmål om oppholdstillatelse`() {
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

    @Test
    fun `Test sortering av medlemskapspørsmål om arbeid utenfor Norge`() {
        val sporsmalet = lagMedlemskapArbeidUtenforNorgeSporsmal(LocalDate.now())
        val soknad = sporsmalet.tilSoknad()

        val sporsmal = soknad.getSporsmalMedTag(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE).undersporsmal.first {
            it.tag == medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_GRUPPERING, 0)
        }

        val soknadShufflet = soknad.replaceSporsmal(sporsmal.copy(undersporsmal = sporsmal.undersporsmal.shuffled()))
        val soknadSortert = soknadShufflet.sorterUndersporsmal()

        val forventetSortering = listOf(
            medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_ARBEIDSGIVER, 0),
            medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_HVOR, 0),
            medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_NAAR, 0)
        )
        soknadSortert.getSporsmalMedTag(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE).undersporsmal.first {
            it.tag == medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_GRUPPERING, 0)
        }.undersporsmal.map { it.tag } `should be equal to` forventetSortering
    }

    @Test
    fun `Test sortering av medlemskapspørsmål om arbeid utenfor Norge med to underspørsmål`() {
        val sporsmalet = lagMedlemskapArbeidUtenforNorgeSporsmal(LocalDate.now())
        val soknad = sporsmalet.tilSoknad()

        val sporsmalMedToUndersporsmal = sporsmalet.copy(
            undersporsmal = sporsmalet.undersporsmal + listOf(
                lagUndersporsmalTilArbeidUtenforNorgeSporsmal(
                    1,
                    LocalDate.now(),
                    LocalDate.now()
                )
            )
        )

        val soknadShufflet = soknad.replaceSporsmal(
            sporsmalMedToUndersporsmal.copy(
                undersporsmal = shuffleRekursivt(sporsmalMedToUndersporsmal.undersporsmal)
            )
        )
        val soknadSortert = soknadShufflet.sorterUndersporsmal()

        val forventetSortering = listOf(
            medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_GRUPPERING, 0),
            medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_GRUPPERING, 1)
        )

        val forventetSorteringForstePeriode = listOf(
            medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_ARBEIDSGIVER, 0),
            medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_HVOR, 0),
            medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_NAAR, 0)
        )
        val forventetSorteringAndrePeriode = listOf(
            medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_ARBEIDSGIVER, 1),
            medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_HVOR, 1),
            medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_NAAR, 1)
        )

        soknadSortert.getSporsmalMedTag(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE).undersporsmal.map { it.tag } `should be equal to` forventetSortering

        soknadSortert.getSporsmalMedTag(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE).undersporsmal.first {
            it.tag == medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_GRUPPERING, 0)
        }.undersporsmal.map { it.tag } `should be equal to` forventetSorteringForstePeriode

        soknadSortert.getSporsmalMedTag(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE).undersporsmal.first {
            it.tag == medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_GRUPPERING, 1)
        }.undersporsmal.map { it.tag } `should be equal to` forventetSorteringAndrePeriode
    }

    private fun shuffleRekursivt(undersporsmal: List<Sporsmal>): List<Sporsmal> {
        return undersporsmal.shuffled().map {
            if (it.undersporsmal.isNotEmpty()) {
                it.copy(undersporsmal = shuffleRekursivt(it.undersporsmal))
            } else {
                it
            }
        }
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
