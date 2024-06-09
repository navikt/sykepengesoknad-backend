package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.sporsmal.andreInntektskilderArbeidsledig
import no.nav.helse.flex.soknadsopprettelse.sporsmal.andreInntektskilderArbeidstaker
import no.nav.helse.flex.soknadsopprettelse.sporsmal.andreInntektskilderSelvstendigOgFrilanser
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagGruppertUndersporsmalTilSporsmalOmArbeidUtenforNorge
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmArbeidUtenforNorge
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmOppholdUtenforEos
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmOppholdUtenforNorge
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagSporsmalOmOppholdstillatelse
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.medIndex
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import java.util.*

class UnderersporsmalSortererTest {
    @Test
    fun `Test sortering av spørsmål om andre inntektskilder i arbeidsledigsøknad`() {
        val sporsmalet = andreInntektskilderArbeidsledig(fom = LocalDate.now(), tom = LocalDate.now())
        val soknad = sporsmalet.tilSoknad()

        val soknadShufflet = soknad.shuffleSporsmalRekursivt()
        val soknadSortert = soknadShufflet.sorterUndersporsmal()

        val forventetSortering =
            listOf(
                INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD,
                INNTEKTSKILDE_SELVSTENDIG,
                INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA,
                INNTEKTSKILDE_JORDBRUKER,
                INNTEKTSKILDE_FRILANSER,
                INNTEKTSKILDE_OMSORGSLONN,
                INNTEKTSKILDE_FOSTERHJEM,
                INNTEKTSKILDE_ANNET,
            )
        soknadSortert.getSporsmalMedTag(HVILKE_ANDRE_INNTEKTSKILDER).undersporsmal.map { it.tag } `should be equal to` forventetSortering
    }

    @Test
    fun `Test sortering av spørsmål om andre inntektskilder frilansersøknad`() {
        val sporsmalet = andreInntektskilderSelvstendigOgFrilanser(Arbeidssituasjon.FRILANSER)
        val soknad = sporsmalet.tilSoknad()

        val soknadShufflet = soknad.shuffleSporsmalRekursivt()
        val soknadSortert = soknadShufflet.sorterUndersporsmal()

        val forventetSortering =
            listOf(
                INNTEKTSKILDE_ARBEIDSFORHOLD,
                INNTEKTSKILDE_JORDBRUKER,
                INNTEKTSKILDE_FRILANSER_SELVSTENDIG,
                INNTEKTSKILDE_ANNET,
            )
        soknadSortert.getSporsmalMedTag(HVILKE_ANDRE_INNTEKTSKILDER)
            .undersporsmal.map { it.tag } `should be equal to` forventetSortering
    }

    @Test
    fun `Test sortering av spørsmål om andre inntektskilder arbeidstakersøknad`() {
        val sporsmalet = andreInntektskilderArbeidstaker("Staten")
        val soknad = sporsmalet.tilSoknad()

        val soknadShufflet = soknad.shuffleSporsmalRekursivt()
        val soknadSortert = soknadShufflet.sorterUndersporsmal()

        val forventetSortering =
            listOf(
                INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD,
                INNTEKTSKILDE_SELVSTENDIG,
                INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA,
                INNTEKTSKILDE_JORDBRUKER,
                INNTEKTSKILDE_FRILANSER,
                INNTEKTSKILDE_ANNET,
            )
        soknadSortert.getSporsmalMedTag(HVILKE_ANDRE_INNTEKTSKILDER)
            .undersporsmal.map { it.tag } `should be equal to` forventetSortering
    }

    @Test
    fun `Test sortering av spørsmål om andre arbeidsgivere`() {
        val soknad = settOppSoknadOppholdUtland("12345")
        val soknadShufflet = soknad.shuffleSporsmalRekursivt()
        val soknadSortert = soknadShufflet.sorterUndersporsmal()

        val forventetSortering =
            listOf(
                SYKMELDINGSGRAD,
                FERIE,
            )
        soknadSortert.getSporsmalMedTag(ARBEIDSGIVER).undersporsmal.map { it.tag } `should be equal to` forventetSortering
    }

    @Test
    fun `Test sortering av spørsmål om reise med bil`() {
        val sporsmalet = reiseMedBilSpørsmål("1 til 2. juli", LocalDate.now(), LocalDate.now())
        val soknad = sporsmalet.tilSoknad()

        val soknadShufflet = soknad.shuffleSporsmalRekursivt()
        val soknadSortert = soknadShufflet.sorterUndersporsmal()

        val forventetSortering =
            listOf(
                BIL_DATOER,
                BIL_BOMPENGER,
                KM_HJEM_JOBB,
            )
        soknadSortert.getSporsmalMedTag(REISE_MED_BIL)
            .undersporsmal.map { it.tag } `should be equal to` forventetSortering
    }

    @Test
    fun `Test sortering av medlemskapspørsmål om oppholdstillatelse`() {
        val sporsmalet = lagSporsmalOmOppholdstillatelse(LocalDate.now())
        val soknad = sporsmalet.tilSoknad()

        val sporsmal = soknad.getSporsmalMedTag(MEDLEMSKAP_OPPHOLDSTILLATELSE_V2)
        val soknadShufflet = soknad.replaceSporsmal(sporsmal.copy(undersporsmal = sporsmal.undersporsmal.shuffled()))
        val soknadSortert = soknadShufflet.sorterUndersporsmal()

        val forventetSortering =
            listOf(
                MEDLEMSKAP_OPPHOLDSTILLATELSE_VEDTAKSDATO,
                MEDLEMSKAP_OPPHOLDSTILLATELSE_PERIODE,
            )

        soknadSortert.getSporsmalMedTag(MEDLEMSKAP_OPPHOLDSTILLATELSE_V2)
            .undersporsmal.map { it.tag } `should be equal to` forventetSortering
    }

    @Test
    fun `Test sortering av opprinnelig medlemskapspørsmål om oppholdstillatelse`() {
        val sporsmalet = lagOpprinneligSporsmalOmOppholdstillatelse(LocalDate.now())
        val soknad = sporsmalet.tilSoknad()

        val sporsmal = soknad.getSporsmalMedTag(MEDLEMSKAP_OPPHOLDSTILLATELSE)
        val soknadShufflet = soknad.replaceSporsmal(sporsmal.copy(undersporsmal = sporsmal.undersporsmal.shuffled()))
        val soknadSortert = soknadShufflet.sorterUndersporsmal()

        val forventetSortering =
            listOf(
                MEDLEMSKAP_OPPHOLDSTILLATELSE_VEDTAKSDATO,
                MEDLEMSKAP_OPPHOLDSTILLATELSE_GRUPPE,
            )

        val forventetSorteringGruppe =
            listOf(
                MEDLEMSKAP_OPPHOLDSTILLATELSE_MIDLERTIDIG,
                MEDLEMSKAP_OPPHOLDSTILLATELSE_PERMANENT,
            )

        soknadSortert.getSporsmalMedTag(MEDLEMSKAP_OPPHOLDSTILLATELSE)
            .undersporsmal.map { it.tag } `should be equal to` forventetSortering

        soknadSortert.getSporsmalMedTag(MEDLEMSKAP_OPPHOLDSTILLATELSE_GRUPPE)
            .undersporsmal.map { it.tag } `should be equal to` forventetSorteringGruppe
    }

    @Test
    fun `Test sortering av medlemskapsørsmål om arbeid utenfor Norge`() {
        val sporsmalet = lagSporsmalOmArbeidUtenforNorge(LocalDate.now())
        val soknad = sporsmalet.tilSoknad()

        val soknadShufflet = soknad.shuffleSporsmalRekursivt()
        val soknadSortert = soknadShufflet.sorterUndersporsmal()

        val forventetSortering =
            listOf(
                medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_HVOR, 0),
                medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_ARBEIDSGIVER, 0),
                medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_NAAR, 0),
            )

        soknadSortert.getSporsmalMedTag(medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_GRUPPERING, 0))
            .undersporsmal.map { it.tag } `should be equal to` forventetSortering
    }

    @Test
    fun `Test sortering av medlemskapspørsmål om arbeid utenfor Norge med to underspørsmål`() {
        val sporsmalet =
            lagSporsmalOmArbeidUtenforNorge(LocalDate.now()).let {
                it.copy(
                    undersporsmal =
                        it.undersporsmal +
                            listOf(
                                lagGruppertUndersporsmalTilSporsmalOmArbeidUtenforNorge(1, LocalDate.now()),
                            ),
                )
            }
        val soknad = sporsmalet.tilSoknad()

        val soknadShufflet = soknad.shuffleSporsmalRekursivt()
        val soknadSortert = soknadShufflet.sorterUndersporsmal()

        val forventetSortering =
            listOf(
                medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_GRUPPERING, 0),
                medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_GRUPPERING, 1),
            )

        val forventetSorteringForstePeriode =
            listOf(
                medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_HVOR, 0),
                medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_ARBEIDSGIVER, 0),
                medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_NAAR, 0),
            )
        val forventetSorteringAndrePeriode =
            listOf(
                medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_HVOR, 1),
                medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_ARBEIDSGIVER, 1),
                medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_NAAR, 1),
            )

        soknadSortert.getSporsmalMedTag(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE)
            .undersporsmal.map { it.tag } `should be equal to` forventetSortering

        soknadSortert.getSporsmalMedTag(medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_GRUPPERING, 0))
            .undersporsmal.map { it.tag } `should be equal to` forventetSorteringForstePeriode

        soknadSortert.getSporsmalMedTag(medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_GRUPPERING, 1))
            .undersporsmal.map { it.tag } `should be equal to` forventetSorteringAndrePeriode
    }

    @Test
    fun `Test sortering av medlemskapspørsmål om opphold utenfor Norge`() {
        val sporsmalet = lagSporsmalOmOppholdUtenforNorge(LocalDate.now())
        val soknad = sporsmalet.tilSoknad()

        val soknadShufflet = soknad.shuffleSporsmalRekursivt()
        val soknadSortert = soknadShufflet.sorterUndersporsmal()

        val forventetSortering =
            listOf(
                medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_HVOR, 0),
                medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE, 0),
                medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_NAAR, 0),
            )

        soknadSortert.getSporsmalMedTag(
            medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_GRUPPERING, 0),
        ).undersporsmal.map { it.tag } `should be equal to` forventetSortering

        val forventetSorteringBegrunnelse =
            listOf(
                medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE_STUDIE, 0),
                medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE_FERIE, 0),
                medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE_BO, 0),
                medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE_EKTEFELLE, 0),
                medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE_ANNET, 0),
            )

        soknadSortert.getSporsmalMedTag(
            medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE, 0),
        ).undersporsmal.map { it.tag } `should be equal to` forventetSorteringBegrunnelse
    }

    @Test
    fun `Test sortering av medlemskapspørsmål om opphold utenfor EØS`() {
        val sporsmalet = lagSporsmalOmOppholdUtenforEos(LocalDate.now())
        val soknad = sporsmalet.tilSoknad()

        val soknadShufflet = soknad.shuffleSporsmalRekursivt()
        val soknadSortert = soknadShufflet.sorterUndersporsmal()

        val forventetSortering =
            listOf(
                medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_HVOR, 0),
                medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE, 0),
                medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_NAAR, 0),
            )

        soknadSortert.getSporsmalMedTag(
            medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_GRUPPERING, 0),
        ).undersporsmal.map { it.tag } `should be equal to` forventetSortering

        val forventetSorteringBegrunnelse =
            listOf(
                medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE_STUDIE, 0),
                medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE_FERIE, 0),
                medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE_BO, 0),
                medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE_EKTEFELLE, 0),
                medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE_ANNET, 0),
            )

        soknadSortert.getSporsmalMedTag(
            medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE, 0),
        ).undersporsmal.map { it.tag } `should be equal to` forventetSorteringBegrunnelse
    }

    @Test
    fun `Tag blir returnert uten index uansett hvor stort tall index er`() {
        fjernIndexFraTag("SPORSMAL_TAG_1") `should be equal to` "SPORSMAL_TAG_"
        fjernIndexFraTag("SPORSMAL_TAG_11") `should be equal to` "SPORSMAL_TAG_"
        fjernIndexFraTag("SPORSMAL_TAG_111") `should be equal to` "SPORSMAL_TAG_"
        fjernIndexFraTag("SPORSMAL_TAG_1111") `should be equal to` "SPORSMAL_TAG_"
    }

    @Test
    fun `Tag blir ikke fjernet fra hvis det ikke finnes en index`() {
        fjernIndexFraTag("SPORSMAL_TAG") `should be equal to` "SPORSMAL_TAG"
        fjernIndexFraTag("SPORSMAL_TAG_") `should be equal to` "SPORSMAL_TAG_"
    }

    @Test
    fun `Finn høyeste index til underspørsmål`() {
        finnHoyesteIndex(
            listOf(
                lagGruppertUndersporsmalTilSporsmalOmArbeidUtenforNorge(0, LocalDate.now()),
            ),
        ) `should be equal to` 0

        finnHoyesteIndex(
            listOf(
                lagGruppertUndersporsmalTilSporsmalOmArbeidUtenforNorge(1, LocalDate.now()),
                lagGruppertUndersporsmalTilSporsmalOmArbeidUtenforNorge(111, LocalDate.now()),
                lagGruppertUndersporsmalTilSporsmalOmArbeidUtenforNorge(9, LocalDate.now()),
            ),
        ) `should be equal to` 111
    }

    private fun Sykepengesoknad.shuffleSporsmalRekursivt(): Sykepengesoknad {
        return this.copy(sporsmal = shuffleRekursivt(sporsmal))
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

    private fun lagOpprinneligSporsmalOmOppholdstillatelse(tom: LocalDate): Sporsmal {
        return Sporsmal(
            tag = MEDLEMSKAP_OPPHOLDSTILLATELSE,
            sporsmalstekst = "Har du oppholdstillatelse fra Utlendingsdirektoratet?",
            svartype = Svartype.JA_NEI,
            kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
            undersporsmal =
                listOf(
                    Sporsmal(
                        tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_VEDTAKSDATO,
                        sporsmalstekst = "Hvilken dato fikk du denne oppholdstillatelsen?",
                        svartype = Svartype.DATO,
                        // Vi vet ikke hvor lang tid tilbake en oppholdstillatelse kan ha bli gitt så vi setter 10 år i
                        // samarbeid med LovMe.
                        min = tom.minusYears(10).format(ISO_LOCAL_DATE),
                        // Vi vet at en vedtaksdato ikke kan være i fremtiden så vi setter dagens dato som maks.
                        max = tom.format(ISO_LOCAL_DATE),
                    ),
                    Sporsmal(
                        tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_GRUPPE,
                        sporsmalstekst = "Er oppholdstillatelsen midlertidig eller permanent?",
                        svartype = Svartype.RADIO_GRUPPE,
                        undersporsmal =
                            listOf(
                                Sporsmal(
                                    tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_MIDLERTIDIG,
                                    sporsmalstekst = "Midlertidig",
                                    svartype = Svartype.RADIO,
                                    kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                                    undersporsmal =
                                        listOf(
                                            Sporsmal(
                                                tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_MIDLERTIDIG_PERIODE,
                                                sporsmalstekst = "Periode for oppholdstillatelse",
                                                svartype = Svartype.PERIODE,
                                                min = tom.minusYears(10).format(ISO_LOCAL_DATE),
                                                max = tom.plusYears(10).format(ISO_LOCAL_DATE),
                                            ),
                                        ),
                                ),
                                Sporsmal(
                                    tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_PERMANENT,
                                    sporsmalstekst = "Permanent",
                                    svartype = Svartype.RADIO,
                                    kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                                    undersporsmal =
                                        listOf(
                                            Sporsmal(
                                                tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_PERMANENT_DATO,
                                                sporsmalstekst = "Fra og med",
                                                svartype = Svartype.DATO,
                                                min = tom.minusYears(10).format(ISO_LOCAL_DATE),
                                                max = tom.format(ISO_LOCAL_DATE),
                                            ),
                                        ),
                                ),
                            ),
                    ),
                ),
        )
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
        egenmeldingsdagerFraSykmelding = null,
        forstegangssoknad = false,
    )
}
