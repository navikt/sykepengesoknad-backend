package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.domain.flatten
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknadGradert
import no.nav.helse.flex.oppdatersporsmal.soknad.leggTilSporsmaal
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.testutil.besvarsporsmal
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.junit.jupiter.api.Test

class NaringsdrivendeErstattGamleSporsmalMuteringTest {
    @Test
    fun `Soknad uten gammelt spørsmål skal ikke endres`() {
        val soknad = opprettNyNaeringsdrivendeSoknadGradert()

        val mutertSoknad = soknad.naringsdrivendeErstattGamleSporsmalMutering()

        mutertSoknad.sporsmal.size `should be equal to` soknad.sporsmal.size
        mutertSoknad.sporsmal `should be equal to` soknad.sporsmal
    }

    @Test
    fun `Soknad med gammelt spørsmål skal få gammelt spørsmål erstattet med nytt`() {
        val soknad = opprettSoknadMedGamleInntektsopplysningerSporsmalInkludertUndersporsmal()

        val antallSporsmalForMutering = soknad.sporsmal.size

        val mutertSoknad = soknad.naringsdrivendeErstattGamleSporsmalMutering()

        mutertSoknad.sporsmal.size `should be equal to` antallSporsmalForMutering
        mutertSoknad.getSporsmalMedTagOrNull(INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET) `should be equal to` null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET) `should not be` null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET_DATO) `should not be` null
    }

    @Test
    fun `Nytt spørsmål skal ha riktig struktur med underspørsmål`() {
        val soknad = opprettSoknadMedGamleInntektsopplysningerSporsmalInkludertUndersporsmal()

        val mutertSoknad = soknad.naringsdrivendeErstattGamleSporsmalMutering()

        val nyttSporsmal = mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET)
        nyttSporsmal `should not be` null
        nyttSporsmal!!.svartype `should be equal to` Svartype.JA_NEI
        nyttSporsmal.undersporsmal.size `should be equal to` 1
        nyttSporsmal.undersporsmal.first().tag `should be equal to` NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET_DATO
    }

    @Test
    fun `Mutering skal kjøres på nytt uten feil hvis gammelt spørsmål allerede er erstattet`() {
        val soknad = opprettSoknadMedGamleInntektsopplysningerSporsmalInkludertUndersporsmal()

        val mutertSoknad = soknad.naringsdrivendeErstattGamleSporsmalMutering()
        val dobbeltMutertSoknad = mutertSoknad.naringsdrivendeErstattGamleSporsmalMutering()

        dobbeltMutertSoknad.sporsmal.size `should be equal to` mutertSoknad.sporsmal.size
        dobbeltMutertSoknad.sporsmal `should be equal to` mutertSoknad.sporsmal
    }

    @Test
    fun `Alle INNTEKTSOPPLYSNINGER-tags skal forsvinne etter mutering med komplett gammel spørsmålstruktur`() {
        val soknad = opprettSoknadMedGamleInntektsopplysningerSporsmalInkludertUndersporsmal()

        val mutertSoknad = soknad.naringsdrivendeErstattGamleSporsmalMutering()

        val alleTagsEtterMutering = mutertSoknad.sporsmal.flatten().map { it.tag }
        alleTagsEtterMutering.none { it.startsWith("INNTEKTSOPPLYSNINGER_") } `should be equal to` true
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET) `should not be` null
    }

    @Test
    fun `Videre mutering skal fungere på nye spørsmål`() {
        val soknad = opprettSoknadMedGamleInntektsopplysningerSporsmalInkludertUndersporsmal()

        val mutertSoknad = soknad.naringsdrivendeErstattGamleSporsmalMutering()

        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET) `should not be` null

        val mutertEtterForsteSvar =
            mutertSoknad
                .besvarsporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET, "NEI")
                .naringsdrivendeErstattGamleSporsmalMutering()
                .naringsdrivendeMutering()

        mutertEtterForsteSvar.getSporsmalMedTagOrNull(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET) `should not be` null

        val mutertEtterAndreSvar =
            mutertEtterForsteSvar
                .besvarsporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET, "NEI")
                .naringsdrivendeErstattGamleSporsmalMutering()
                .naringsdrivendeMutering()

        mutertEtterAndreSvar.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VARIG_ENDRING) `should not be` null

        val mutertEtterTredjeSvar =
            mutertEtterAndreSvar
                .besvarsporsmal(NARINGSDRIVENDE_VARIG_ENDRING, "NEI")
                .naringsdrivendeErstattGamleSporsmalMutering()
                .naringsdrivendeMutering()

        mutertEtterTredjeSvar.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET)!!.forsteSvar `should be equal to` "NEI"
        mutertEtterTredjeSvar.getSporsmalMedTagOrNull(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET)!!.forsteSvar `should be equal to` "NEI"
        mutertEtterTredjeSvar.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VARIG_ENDRING)!!.forsteSvar `should be equal to` "NEI"

        val alleTagsEtterMutering = mutertEtterTredjeSvar.sporsmal.flatten().map { it.tag }
        alleTagsEtterMutering.none { it.startsWith("INNTEKTSOPPLYSNINGER_") } `should be equal to` true
    }
}

private fun opprettSoknadMedGamleInntektsopplysningerSporsmalInkludertUndersporsmal(): Sykepengesoknad {
    val varigEndringBegrunnelse =
        Sporsmal(
            tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE,
            sporsmalstekst = "Hvilken endring har skjedd?",
            undertekst = "Du kan velge ett eller flere alternativer",
            svartype = Svartype.CHECKBOX_GRUPPE,
            undersporsmal =
                listOf(
                    Sporsmal(
                        tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE_OPPRETTELSE_NEDLEGGELSE,
                        sporsmalstekst = "Opprettelse eller nedleggelse av næringsvirksomhet",
                        svartype = Svartype.CHECKBOX,
                        svar = listOf(Svar(id = null, verdi = "CHECKED")),
                    ),
                    Sporsmal(
                        tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE_ENDRET_INNSATS,
                        sporsmalstekst = "Økt eller redusert innsats",
                        svartype = Svartype.CHECKBOX,
                        svar = listOf(Svar(id = null, verdi = "")),
                    ),
                    Sporsmal(
                        tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE_OMLEGGING_AV_VIRKSOMHETEN,
                        sporsmalstekst = "Omlegging av virksomheten",
                        svartype = Svartype.CHECKBOX,
                        svar = listOf(Svar(id = null, verdi = "")),
                    ),
                    Sporsmal(
                        tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE_ENDRET_MARKEDSSITUASJON,
                        sporsmalstekst = "Endret markedssituasjon",
                        svartype = Svartype.CHECKBOX,
                        svar = listOf(Svar(id = null, verdi = "")),
                    ),
                    Sporsmal(
                        tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE_ANNET,
                        sporsmalstekst = "Annet",
                        svartype = Svartype.CHECKBOX,
                        svar = listOf(Svar(id = null, verdi = "")),
                    ),
                ),
        )

    val varigEndring25Prosent =
        Sporsmal(
            tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_25_PROSENT,
            sporsmalstekst = "Har du hatt mer enn 25 prosent endring i årsinntekten din som følge av den varige endringen?",
            svartype = Svartype.JA_NEI,
            kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
            svar = listOf(Svar(id = null, verdi = "JA")),
            undersporsmal =
                listOf(
                    Sporsmal(
                        tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_DATO,
                        sporsmalstekst = "Når skjedde den siste varige endringen?",
                        svartype = Svartype.DATO,
                        svar = listOf(Svar(id = null, verdi = "2026-06-10")),
                    ),
                ),
        )

    val varigEndring =
        Sporsmal(
            tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING,
            sporsmalstekst = "Har det skjedd en varig endring i arbeidssituasjonen eller virksomheten din?",
            svartype = Svartype.JA_NEI,
            kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
            svar = listOf(Svar(id = null, verdi = "JA")),
            undersporsmal = listOf(varigEndringBegrunnelse, varigEndring25Prosent),
        )

    val nyIArbeidslivet =
        Sporsmal(
            tag = INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET,
            sporsmalstekst = "Er du ny i arbeidslivet etter 1. januar 2026?",
            svartype = Svartype.RADIO_GRUPPE,
            undersporsmal =
                listOf(
                    Sporsmal(
                        tag = INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_JA,
                        sporsmalstekst = "Ja",
                        svartype = Svartype.RADIO,
                        kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                        undersporsmal =
                            listOf(
                                Sporsmal(
                                    tag = INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_DATO,
                                    sporsmalstekst = "Når begynte du i arbeidslivet?",
                                    svartype = Svartype.DATO,
                                    kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
                                ),
                            ),
                    ),
                    Sporsmal(
                        tag = INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_NEI,
                        sporsmalstekst = "Nei",
                        svartype = Svartype.RADIO,
                        kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                        svar = listOf(Svar(id = null, verdi = "CHECKED")),
                        undersporsmal = listOf(varigEndring),
                    ),
                ),
        )

    val avvikletNei =
        Sporsmal(
            tag = INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET_NEI,
            sporsmalstekst = "Nei",
            svartype = Svartype.RADIO,
            kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
            svar = listOf(Svar(id = null, verdi = "CHECKED")),
            undersporsmal = listOf(nyIArbeidslivet),
        )

    val avvikletJa =
        Sporsmal(
            tag = INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET_JA,
            sporsmalstekst = "Ja",
            svartype = Svartype.RADIO,
            kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
            undersporsmal =
                listOf(
                    Sporsmal(
                        tag = INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET_NAR,
                        sporsmalstekst = "Når ble virksomheten avviklet?",
                        svartype = Svartype.DATO,
                    ),
                ),
        )

    val gamleSporsmalMedUndersporsmal =
        Sporsmal(
            tag = INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET,
            sporsmalstekst = "Har du avviklet virksomheten din før du ble sykmeldt?",
            svartype = Svartype.RADIO_GRUPPE,
            undersporsmal = listOf(avvikletJa, avvikletNei),
        )

    return opprettNyNaeringsdrivendeSoknadGradert()
        .fjernSporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET)
        .fjernSporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET)
        .fjernSporsmal(NARINGSDRIVENDE_VARIG_ENDRING)
        .leggTilSporsmaal(gamleSporsmalMedUndersporsmal)
}
