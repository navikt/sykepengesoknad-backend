package no.nav.helse.flex.svarvalidering

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Svartype.*
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.sporsmalBuilder
import no.nav.helse.flex.mock.opprettNyArbeidstakerSoknad
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadOppholdUtland
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Arrays.asList
import java.util.Collections.emptyList

class ValideringTest {
    val soknad = opprettNyArbeidstakerSoknad()

    @Test
    fun jaNeiSporsmalHarRiktigAntallSvar() {
        getSporsmal(JA_NEI).`valider antall svar og forvent ValideringException`()
        getSporsmal(JA_NEI, getSvar(1)).validerAntallSvar()
        getSporsmal(JA_NEI, getSvar(2)).`valider antall svar og forvent ValideringException`()
    }

    @Test
    fun checkboxSporsmalHarRiktigAntallSvar() {
        getSporsmal(CHECKBOX).`valider antall svar og forvent ValideringException`()
        getSporsmal(CHECKBOX, getSvar(1)).validerAntallSvar()
        getSporsmal(CHECKBOX, getSvar(2)).`valider antall svar og forvent ValideringException`()
    }

    @Test
    fun checkboxGruppeSporsmalHarRiktigAntallSvar() {
        getSporsmal(CHECKBOX_GRUPPE).validerAntallSvar()
        getSporsmal(CHECKBOX_GRUPPE, getSvar(1)).`valider antall svar og forvent ValideringException`()
    }

    @Test
    fun checkboxPanelSporsmalHarRiktigAntallSvar() {
        getSporsmal(CHECKBOX_PANEL).`valider antall svar og forvent ValideringException`()
        getSporsmal(CHECKBOX_PANEL, getSvar(1)).validerAntallSvar()
        getSporsmal(CHECKBOX_PANEL, getSvar(2)).`valider antall svar og forvent ValideringException`()
    }

    @Test
    fun radioSporsmalHarRiktigAntallSvar() {
        getSporsmal(RADIO).`valider antall svar og forvent ValideringException`()
        getSporsmal(RADIO, getSvar(1)).validerAntallSvar()
        getSporsmal(RADIO, getSvar(2)).`valider antall svar og forvent ValideringException`()
    }

    @Test
    fun radioGruppeSporsmalHarRiktigAntallSvar() {
        getSporsmal(RADIO_GRUPPE).validerAntallSvar()
        getSporsmal(RADIO_GRUPPE, getSvar(1)).`valider antall svar og forvent ValideringException`()
    }

    @Test
    fun radioGruppeTimerProsentSporsmalHarRiktigAntallSvar() {
        getSporsmal(RADIO_GRUPPE_TIMER_PROSENT).validerAntallSvar()
        getSporsmal(RADIO_GRUPPE_TIMER_PROSENT, getSvar(1)).`valider antall svar og forvent ValideringException`()
    }

    @Test
    fun ikkeRelevantSporsmalHarRiktigAntallSvar() {
        getSporsmal(IKKE_RELEVANT).validerAntallSvar()
        getSporsmal(IKKE_RELEVANT, getSvar(1)).`valider antall svar og forvent ValideringException`()
    }

    @Test
    fun gruppeAvUndersporsmalHarRiktigAntallSvar() {
        getSporsmal(GRUPPE_AV_UNDERSPORSMAL).validerAntallSvar()
        getSporsmal(GRUPPE_AV_UNDERSPORSMAL, getSvar(1)).`valider antall svar og forvent ValideringException`()
    }

    @Test
    fun fritekstSporsmalHarRiktigAntallSvar() {
        getSporsmalMedGrenser(FRITEKST, "1", null).`valider antall svar og forvent ValideringException`()
        getSporsmal(FRITEKST, getSvar(1)).validerAntallSvar()
        getSporsmalMedGrenser(FRITEKST, "1", null).`valider antall svar og forvent ValideringException`()
    }

    @Test
    fun datoSporsmalHarRiktigAntallSvar() {
        getSporsmal(DATO).`valider antall svar og forvent ValideringException`()
        getSporsmal(DATO, getSvar(1)).validerAntallSvar()
        getSporsmal(DATO, getSvar(2)).`valider antall svar og forvent ValideringException`()
    }

    @Test
    fun timerSporsmalHarRiktigAntallSvar() {
        getSporsmal(TIMER).`valider antall svar og forvent ValideringException`()
        getSporsmal(TIMER, getSvar(1)).validerAntallSvar()
        getSporsmal(TIMER, getSvar(2)).`valider antall svar og forvent ValideringException`()
    }

    @Test
    fun prosentSporsmalHarRiktigAntallSvar() {
        getSporsmal(PROSENT).`valider antall svar og forvent ValideringException`()
        getSporsmal(PROSENT, getSvar(1)).validerAntallSvar()
        getSporsmal(PROSENT, getSvar(2)).`valider antall svar og forvent ValideringException`()
    }

    @Test
    fun tallSporsmalHarRiktigAntallSvar() {
        getSporsmal(TALL).`valider antall svar og forvent ValideringException`()
        getSporsmal(TALL, getSvar(1)).validerAntallSvar()
        getSporsmal(TALL, getSvar(2)).`valider antall svar og forvent ValideringException`()
    }

    @Test
    fun periodeSporsmalHarRiktigAntallSvar() {
        getSporsmal(PERIODE).`valider antall svar og forvent ValideringException`()
        getSporsmal(PERIODE, getSvar(1)).validerAntallSvar()
        getSporsmal(PERIODE, getSvar(2)).`valider antall svar og forvent ValideringException`()
    }

    @Test
    fun perioderSporsmalHarRiktigAntallSvar() {
        getSporsmal(PERIODER).`valider antall svar og forvent ValideringException`()
        getSporsmal(PERIODER, getSvar(1)).validerAntallSvar()
        getSporsmal(PERIODER, getSvar(2)).validerAntallSvar()
        getSporsmal(PERIODER, getSvar(3)).validerAntallSvar()
        getSporsmal(PERIODER, getSvar(4)).validerAntallSvar()
    }

    @Test
    fun jaNeiSporsmalHarRiktigVerdi() {
        assertThat(validerSvarverdi(getSporsmal(JA_NEI), "JA")).isTrue()
        assertThat(validerSvarverdi(getSporsmal(JA_NEI), "NEI")).isTrue()
        assertThat(validerSvarverdi(getSporsmal(JA_NEI), "BOGUS")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(JA_NEI), "")).isFalse()
    }

    @Test
    fun checkboxSporsmalHarRiktigVerdi() {
        assertThat(validerSvarverdi(getSporsmal(CHECKBOX), "CHECKED")).isTrue()
        assertThat(validerSvarverdi(getSporsmal(CHECKBOX), "BOGUS")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(CHECKBOX), "")).isFalse()
    }

    @Test
    fun checkboxGruppeSporsmalHarRiktigVerdi() {
        assertThat(validerSvarverdi(getSporsmal(CHECKBOX_GRUPPE), "BOGUS")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(CHECKBOX_GRUPPE), "")).isFalse()
    }

    @Test
    fun checkboxPanelSporsmalHarRiktigVerdi() {
        assertThat(validerSvarverdi(getSporsmal(CHECKBOX_PANEL), "CHECKED")).isTrue()
        assertThat(validerSvarverdi(getSporsmal(CHECKBOX_PANEL), "BOGUS")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(CHECKBOX_PANEL), "")).isFalse()
    }

    @Test
    fun radioSporsmalHarRiktigVerdi() {
        assertThat(validerSvarverdi(getSporsmal(RADIO), "CHECKED")).isTrue()
        assertThat(validerSvarverdi(getSporsmal(RADIO), "BOGUS")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(RADIO), "")).isFalse()
    }

    @Test
    fun radioGruppeSporsmalHarRiktigVerdi() {
        assertThat(validerSvarverdi(getSporsmal(RADIO_GRUPPE), "BOGUS")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(RADIO_GRUPPE), "")).isFalse()
    }

    @Test
    fun radioGruppeTimerProsentSporsmalHarRiktigVerdi() {
        assertThat(validerSvarverdi(getSporsmal(RADIO_GRUPPE_TIMER_PROSENT), "BOGUS")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(RADIO_GRUPPE_TIMER_PROSENT), "")).isFalse()
    }

    @Test
    fun ikkeRelevantHarRiktigVerdi() {
        assertThat(validerSvarverdi(getSporsmal(CHECKBOX), "BOGUS")).isFalse()
    }

    @Test
    fun fritekstSporsmalHarRiktigVerdi() {
        assertThat(validerSvarverdi(getSporsmal(FRITEKST), "BOGUS")).isTrue()
        assertThat(validerSvarverdi(getSporsmalMedGrenser(FRITEKST, null, null), "")).isTrue()
        assertThat(validerSvarverdi(getSporsmalMedGrenser(FRITEKST, "1", null), "")).isFalse()
        assertThat(validerSvarverdi(getSporsmalMedGrenser(FRITEKST, "1", null), "h")).isTrue()
        assertThat(validerSvarverdi(getSporsmalMedGrenser(FRITEKST, "1", null), " ")).isFalse()
    }

    @Test
    fun datoSporsmalHarRiktigVerdi() {
        assertThat(validerSvarverdi(getSporsmal(DATO), "2018-01-01")).isTrue()
        assertThat(validerSvarverdi(getSporsmal(DATO), "2018-15-15")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(DATO), "2018.01.01")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(DATO), "15-15-2018")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(DATO), "BOGUS")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(DATO), "")).isFalse()
    }

    @Test
    fun periodeSporsmalHarRiktigVerdi() {
        assertThat(validerSvarverdi(getSporsmal(PERIODE), "{\"fom\":\"2018-01-01\",\"tom\":\"2018-02-01\"}")).isTrue()
        assertThat(validerSvarverdi(getSporsmal(PERIODE), "{\"fom\":\"2018-01-01\",\"fom\":\"2018-02-01\"}")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(PERIODE), "2018-01-01")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(PERIODE), "2018-15-15")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(PERIODE), "2018.01.01")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(PERIODE), "15-15-2018")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(PERIODE), "BOGUS")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(PERIODE), "")).isFalse()
    }

    @Test
    fun perioderSporsmalHarRiktigVerdi() {
        assertThat(validerSvarverdi(getSporsmal(PERIODE), "{\"fom\":\"2018-01-01\",\"tom\":\"2018-02-01\"}")).isTrue()
        assertThat(validerSvarverdi(getSporsmal(PERIODE), "{\"fom\":\"2018-01-01\",\"fom\":\"2018-02-01\"}")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(PERIODER), "2018-01-01")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(PERIODER), "2018-15-15")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(PERIODER), "2018.01.01")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(PERIODER), "15-15-2018")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(PERIODER), "BOGUS")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(PERIODER), "")).isFalse()
    }

    @Test
    fun prosentSporsmalHarRiktigVerdi() {
        assertThat(validerSvarverdi(getSporsmal(PROSENT), "17")).isTrue()
        assertThat(validerSvarverdi(getSporsmal(PROSENT), "50.5")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(PROSENT), "BOGUS")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(PROSENT), "")).isFalse()
    }

    @Test
    fun timerSporsmalHarRiktigVerdi() {
        assertThat(validerSvarverdi(getSporsmal(TIMER), "17")).isTrue()
        assertThat(validerSvarverdi(getSporsmal(TIMER), "50.5")).isTrue()
        assertThat(validerSvarverdi(getSporsmal(TIMER), "37,5")).isTrue()
        assertThat(validerSvarverdi(getSporsmal(TIMER), "BOGUS")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(TIMER), "")).isFalse()
    }

    @Test
    fun tallSporsmalHarRiktigVerdi() {
        assertThat(validerSvarverdi(getSporsmal(TALL), "17")).isTrue()
        assertThat(validerSvarverdi(getSporsmal(TALL), "50.5")).isTrue()
        assertThat(validerSvarverdi(getSporsmal(TALL), "37,5")).isTrue()
        assertThat(validerSvarverdi(getSporsmal(TALL), "BOGUS")).isFalse()
        assertThat(validerSvarverdi(getSporsmal(TALL), "")).isFalse()
    }

    @Test
    fun landSporsmalHarRiktigVerdi() {
        assertThat(validerSvarverdi(getSporsmal(LAND), "Sverige")).isTrue()
        assertThat(validerSvarverdi(getSporsmal(LAND), "")).isFalse()
    }

    @Test
    fun valideringAvGrenserErOKHvisIngenGrenserErSatt() {
        assertThat(validerGrenser(getSporsmal(JA_NEI), "BOGUS")).isTrue()
        assertThat(validerGrenser(getSporsmal(FRITEKST), "BOGUS")).isTrue()
        assertThat(validerGrenser(getSporsmal(IKKE_RELEVANT), "BOGUS")).isTrue()
        assertThat(validerGrenser(getSporsmal(GRUPPE_AV_UNDERSPORSMAL), "BOGUS")).isTrue()
        assertThat(validerGrenser(getSporsmal(CHECKBOX), "BOGUS")).isTrue()
        assertThat(validerGrenser(getSporsmal(CHECKBOX_PANEL), "BOGUS")).isTrue()
        assertThat(validerGrenser(getSporsmal(CHECKBOX_GRUPPE), "BOGUS")).isTrue()
        assertThat(validerGrenser(getSporsmal(RADIO), "BOGUS")).isTrue()
        assertThat(validerGrenser(getSporsmal(RADIO_GRUPPE), "BOGUS")).isTrue()
        assertThat(validerGrenser(getSporsmal(RADIO_GRUPPE_TIMER_PROSENT), "BOGUS")).isTrue()
        assertThat(validerGrenser(getSporsmal(DATO), "2020-01-01")).isTrue()
        assertThat(validerGrenser(getSporsmal(PERIODE), "{\"fom\":\"2018-05-21\",\"tom\":\"2018-05-23\"}")).isTrue()
        assertThat(validerGrenser(getSporsmal(PERIODER), "{\"fom\":\"2018-05-21\",\"tom\":\"2018-05-23\"}")).isTrue()
        assertThat(validerGrenser(getSporsmal(TIMER), "1")).isTrue()
        assertThat(validerGrenser(getSporsmal(PROSENT), "1")).isTrue()
        assertThat(validerGrenser(getSporsmal(TALL), "1")).isTrue()
    }

    @Test
    fun datoSvarErInnenforGrenseneSomErSatt() {
        assertThat(validerGrenser(getSporsmalMedGrenser(DATO, "2018-05-20", "2018-05-25"), "2018-05-20")).isTrue()
        assertThat(validerGrenser(getSporsmalMedGrenser(DATO, "2018-05-20", "2018-05-25"), "2018-05-23")).isTrue()
        assertThat(validerGrenser(getSporsmalMedGrenser(DATO, "2018-05-20", "2018-05-25"), "2018-05-25")).isTrue()
        assertThat(validerGrenser(getSporsmalMedGrenser(DATO, "2018-05-20", "2018-05-25"), "2018-05-19")).isFalse()
        assertThat(validerGrenser(getSporsmalMedGrenser(DATO, "2018-05-20", "2018-05-25"), "2018-05-26")).isFalse()
    }

    @Test
    fun periodeSvarErInnenforGrenseneSomErSatt() {
        assertThat(
            validerGrenser(
                getSporsmalMedGrenser(PERIODE, "2018-05-20", "2018-05-25"),
                "{\"fom\":\"2018-05-21\",\"tom\":\"2018-05-23\"}",
            ),
        ).isTrue()
        assertThat(
            validerGrenser(
                getSporsmalMedGrenser(PERIODE, "2018-05-20", "2018-05-25"),
                "{\"fom\":\"2018-05-20\",\"tom\":\"2018-05-23\"}",
            ),
        ).isTrue()
        assertThat(
            validerGrenser(
                getSporsmalMedGrenser(PERIODE, "2018-05-20", "2018-05-25"),
                "{\"fom\":\"2018-05-20\",\"tom\":\"2018-05-25\"}",
            ),
        ).isTrue()
        assertThat(
            validerGrenser(
                getSporsmalMedGrenser(PERIODE, "2018-05-20", "2018-05-25"),
                "{\"fom\":\"2018-05-19\",\"tom\":\"2018-05-25\"}",
            ),
        ).isFalse()
        assertThat(
            validerGrenser(
                getSporsmalMedGrenser(PERIODE, "2018-05-20", "2018-05-25"),
                "{\"fom\":\"2018-05-20\",\"tom\":\"2018-05-26\"}",
            ),
        ).isFalse()
    }

    @Test
    fun perioderSvarErInnenforGrenseneSomErSatt() {
        assertThat(
            validerGrenser(
                getSporsmalMedGrenser(PERIODER, "2018-05-20", "2018-05-25"),
                "{\"fom\":\"2018-05-21\",\"tom\":\"2018-05-23\"}",
            ),
        ).isTrue()
        assertThat(
            validerGrenser(
                getSporsmalMedGrenser(PERIODER, "2018-05-20", "2018-05-25"),
                "{\"fom\":\"2018-05-20\",\"tom\":\"2018-05-23\"}",
            ),
        ).isTrue()
        assertThat(
            validerGrenser(
                getSporsmalMedGrenser(PERIODER, "2018-05-20", "2018-05-25"),
                "{\"fom\":\"2018-05-20\",\"tom\":\"2018-05-25\"}",
            ),
        ).isTrue()
        assertThat(
            validerGrenser(
                getSporsmalMedGrenser(PERIODER, "2018-05-20", "2018-05-25"),
                "{\"fom\":\"2018-05-19\",\"tom\":\"2018-05-25\"}",
            ),
        ).isFalse()
        assertThat(
            validerGrenser(
                getSporsmalMedGrenser(PERIODER, "2018-05-20", "2018-05-25"),
                "{\"fom\":\"2018-05-20\",\"tom\":\"2018-05-26\"}",
            ),
        ).isFalse()
    }

    @Test
    fun prosentSvarErInnenforGrenseneSomErSatt() {
        assertThat(validerGrenser(getSporsmalMedGrenser(PROSENT, "20", "40"), "20")).isTrue()
        assertThat(validerGrenser(getSporsmalMedGrenser(PROSENT, "20", "40"), "30")).isTrue()
        assertThat(validerGrenser(getSporsmalMedGrenser(PROSENT, "20", "40"), "40")).isTrue()
        assertThat(validerGrenser(getSporsmalMedGrenser(PROSENT, "20", "40"), "19")).isFalse()
        assertThat(validerGrenser(getSporsmalMedGrenser(PROSENT, "20", "40"), "41")).isFalse()
    }

    @Test
    fun timerSvarErInnenforGrenseneSomErSatt() {
        assertThat(validerGrenser(getSporsmalMedGrenser(TIMER, "20", "40"), "20")).isTrue()
        assertThat(validerGrenser(getSporsmalMedGrenser(TIMER, "20", "40"), "37.5")).isTrue()
        assertThat(validerGrenser(getSporsmalMedGrenser(TIMER, "20", "40"), "40")).isTrue()
        assertThat(validerGrenser(getSporsmalMedGrenser(TIMER, "20", "40"), "19")).isFalse()
        assertThat(validerGrenser(getSporsmalMedGrenser(TIMER, "20", "40"), "41")).isFalse()
    }

    @Test
    fun tallSvarErInnenforGrenseneSomErSatt() {
        assertThat(validerGrenser(getSporsmalMedGrenser(TALL, "20", "40"), "20")).isTrue()
        assertThat(validerGrenser(getSporsmalMedGrenser(TALL, "20", "40"), "37.5")).isTrue()
        assertThat(validerGrenser(getSporsmalMedGrenser(TALL, "20", "40"), "40")).isTrue()
        assertThat(validerGrenser(getSporsmalMedGrenser(TALL, "20", "40"), "19")).isFalse()
        assertThat(validerGrenser(getSporsmalMedGrenser(TALL, "20", "40"), "41")).isFalse()
    }

    @Test
    fun fritekstSvarErInnenforGrenseneSomErSatt() {
        assertThat(validerGrenser(getSporsmalMedGrenser(FRITEKST, "0", "20"), "En laaaaaaaaaang tekst")).isFalse()
        assertThat(validerGrenser(getSporsmalMedGrenser(FRITEKST, "0", "20"), "En kort tekst")).isTrue()
        assertThat(validerGrenser(getSporsmalMedGrenser(FRITEKST, "20", "40"), "En kort tekst")).isFalse()
    }

    @Test
    fun valideringOKVedSvarPaAlleUndersporsmal() {
        val sporsmal =
            sporsmalBuilder()
                .tag("ANY")
                .svartype(JA_NEI)
                .undersporsmal(getBesvarteCheckedSporsmal(CHECKBOX, 3, 3))
                .build()

        assertThat(validerUndersporsmal(sporsmal)).isTrue()
    }

    @Test
    fun valideringAvUndersporsmalFeilerVedManglendeSvar() {
        val sporsmal =
            sporsmalBuilder()
                .tag("ANY")
                .svartype(JA_NEI)
                .undersporsmal(getBesvarteCheckedSporsmal(CHECKBOX, 3, 2))
                .build()

        assertThat(validerUndersporsmal(sporsmal)).isFalse()
    }

    @Test
    fun valideringAvCheckboxGruppeKreverMinstEttSvar() {
        val sporsmal =
            sporsmalBuilder()
                .tag("ANY")
                .svartype(CHECKBOX_GRUPPE)
                .undersporsmal(getBesvarteCheckedSporsmal(CHECKBOX, 3, 0))
                .build()

        assertThat(validerUndersporsmal(sporsmal)).isFalse()
    }

    @Test
    fun valideringAvCheckboxGruppeKreverBareEttSvar() {
        val sporsmal =
            sporsmalBuilder()
                .tag("ANY")
                .svartype(CHECKBOX_GRUPPE)
                .undersporsmal(getBesvarteCheckedSporsmal(CHECKBOX, 3, 1))
                .build()

        assertThat(validerUndersporsmal(sporsmal)).isTrue()
    }

    @Test
    fun checkboxGruppeValidererOKForFlereBesvarte() {
        val sporsmal =
            sporsmalBuilder()
                .tag("ANY")
                .svartype(CHECKBOX_GRUPPE)
                .undersporsmal(getBesvarteCheckedSporsmal(CHECKBOX, 3, 2))
                .build()

        assertThat(validerUndersporsmal(sporsmal)).isTrue()
    }

    @Test
    fun valideringAvRadioGruppeFeilerVedManglendeSvar() {
        val sporsmal =
            sporsmalBuilder()
                .tag("ANY")
                .svartype(RADIO_GRUPPE)
                .undersporsmal(getBesvarteCheckedSporsmal(RADIO, 3, 0))
                .build()

        assertThat(validerUndersporsmal(sporsmal)).isFalse()
    }

    @Test
    fun valideringAvRadioGruppeKreverAkkuratEttSvar() {
        val sporsmal =
            sporsmalBuilder()
                .tag("ANY")
                .svartype(RADIO_GRUPPE)
                .undersporsmal(getBesvarteCheckedSporsmal(RADIO, 3, 1))
                .build()

        assertThat(validerUndersporsmal(sporsmal)).isTrue()
    }

    @Test
    fun valideringAvRadioGruppeTimerProsentFeilerVedMerEnnEttSvar() {
        val sporsmal =
            sporsmalBuilder()
                .tag("ANY")
                .svartype(RADIO_GRUPPE)
                .undersporsmal(getBesvarteCheckedSporsmal(RADIO, 3, 2))
                .build()

        assertThat(validerUndersporsmal(sporsmal)).isFalse()
    }

    @Test
    fun valideringAvRadioGruppeTimerProsentFeilerVedManglendeSvar() {
        val sporsmal =
            sporsmalBuilder()
                .tag("ANY")
                .svartype(RADIO_GRUPPE)
                .undersporsmal(getBesvarteCheckedSporsmal(RADIO, 3, 0))
                .build()

        assertThat(validerUndersporsmal(sporsmal)).isFalse()
    }

    @Test
    fun valideringAvRadioGruppeTimerProsentKreverAkkuratEttSvar() {
        val sporsmal =
            sporsmalBuilder()
                .tag("ANY")
                .svartype(RADIO_GRUPPE)
                .undersporsmal(getBesvarteCheckedSporsmal(RADIO, 3, 1))
                .build()

        assertThat(validerUndersporsmal(sporsmal)).isTrue()
    }

    @Test
    fun valideringAvRadioGruppeFeilerVedMerEnnEttSvar() {
        val sporsmal =
            sporsmalBuilder()
                .tag("ANY")
                .svartype(RADIO_GRUPPE)
                .undersporsmal(getBesvarteCheckedSporsmal(RADIO, 3, 2))
                .build()

        assertThat(validerUndersporsmal(sporsmal)).isFalse()
    }

    @Test
    fun ikkeRelevantValidererOKForBesvarteCheckboxPanel() {
        val sporsmal =
            sporsmalBuilder()
                .tag("ANY")
                .svartype(IKKE_RELEVANT)
                .undersporsmal(getBesvarteCheckedSporsmal(CHECKBOX_PANEL, 1, 1))
                .build()

        assertThat(validerUndersporsmal(sporsmal)).isTrue()
    }

    @Test
    fun ikkeRelevantValidererFeilForUbesvarteCheckboxPanel() {
        val sporsmal =
            sporsmalBuilder()
                .tag("ANY")
                .svartype(IKKE_RELEVANT)
                .undersporsmal(getBesvarteCheckedSporsmal(CHECKBOX_PANEL, 1, 0))
                .build()

        assertThat(validerUndersporsmal(sporsmal)).isFalse()
    }

    @Test
    fun gruppeAvUndersporsmalValidererOKForBesvarteCheckboxPanel() {
        val sporsmal =
            sporsmalBuilder()
                .tag("ANY")
                .svartype(GRUPPE_AV_UNDERSPORSMAL)
                .undersporsmal(getBesvarteCheckedSporsmal(CHECKBOX_PANEL, 1, 1))
                .build()

        assertThat(validerUndersporsmal(sporsmal)).isTrue()
    }

    @Test
    fun gruppeAvUndersporsmalValidererFeilForUbesvarteCheckboxPanel() {
        val sporsmal =
            sporsmalBuilder()
                .tag("ANY")
                .svartype(GRUPPE_AV_UNDERSPORSMAL)
                .undersporsmal(getBesvarteCheckedSporsmal(CHECKBOX_PANEL, 1, 0))
                .build()

        assertThat(validerUndersporsmal(sporsmal)).isFalse()
    }

    @Test
    fun ferieSporsmalMedSvarverdiJaOppholdUtland() {
        val soknadGodkjentFerieSporsmal =
            settOppSoknadOppholdUtland("fnr").copy(
                sporsmal =
                    listOf(
                        getSporsmalMedTomtUndersporsmal(JA_NEI, listOf(Svar(null, "JA"))),
                        getSporsmalMedTomtUndersporsmal(JA_NEI, listOf(Svar(null, "JA"))),
                        getUndersporsmalMedTagOgSvar("FERIE", "NEI"),
                    ),
            )
        assertThat(validerSvarPaSoknad(soknadGodkjentFerieSporsmal)).isTrue()
    }

    @Test
    fun ferieSporsmalMedSvarverdiNeiOppholdUtland() {
        val soknadIkkeGodkjentFerieSporsmal =
            settOppSoknadOppholdUtland("fnr").copy(
                sporsmal =
                    listOf(
                        getSporsmalMedTomtUndersporsmal(JA_NEI, listOf(Svar(null, "JA"))),
                        getSporsmalMedTomtUndersporsmal(JA_NEI, listOf(Svar(null, "JA"))),
                        getUndersporsmalMedTagOgSvar("FERIE", "JA"),
                    ),
            )
        assertThat(validerSvarPaSoknad(soknadIkkeGodkjentFerieSporsmal)).isFalse()
    }

    private fun getBesvarteCheckedSporsmal(
        svartype: Svartype,
        antall: Int,
        besvarte: Int,
    ): MutableList<Sporsmal> {
        val sporsmal = ArrayList<Sporsmal>()
        for (i in 0 until antall) {
            sporsmal.add(
                sporsmalBuilder()
                    .tag("ANY")
                    .svartype(svartype)
                    .undersporsmal(emptyList())
                    .svar(
                        if (i >= besvarte) {
                            emptyList()
                        } else {
                            listOf(Svar(null, "CHECKED"))
                        },
                    )
                    .build(),
            )
        }
        return sporsmal
    }

    private fun getSporsmal(svartype: Svartype): Sporsmal {
        return getSporsmalMedGrenser(svartype, null, null)
    }

    private fun getSporsmal(
        svartype: Svartype,
        svar: List<Svar>,
    ): Sporsmal {
        return getSporsmalMedGrenser(svartype, null, null, svar)
    }

    private fun getSporsmalMedGrenser(
        svartype: Svartype,
        min: String?,
        max: String?,
        svar: List<Svar> = emptyList(),
    ): Sporsmal {
        return sporsmalBuilder()
            .tag("ANY")
            .svartype(svartype)
            .min(min)
            .max(max)
            .svar(svar)
            .build()
    }

    private fun getSporsmalMedTomtUndersporsmal(
        svartype: Svartype,
        svar: List<Svar>,
    ): Sporsmal {
        return sporsmalBuilder()
            .tag("ANY")
            .svartype(svartype)
            .min(null)
            .max(null)
            .svar(svar)
            .undersporsmal(emptyList())
            .build()
    }

    @Test
    fun landSvarErInnenforGrenseneSomErSatt() {
        assertThat(validerGrenser(getSporsmalMedGrenser(LAND, "0", "20"), "En laaaaaaaaaang tekst")).isFalse()
        assertThat(validerGrenser(getSporsmalMedGrenser(LAND, "0", "20"), "En kort tekst")).isTrue()
        assertThat(validerGrenser(getSporsmalMedGrenser(LAND, "20", "40"), "En kort tekst")).isFalse()
    }

    private fun getUndersporsmalMedTagOgSvar(
        tag: String,
        verdipasvar: String,
    ): Sporsmal {
        val svar = ArrayList<Svar>()
        svar.add(Svar(null, verdipasvar))
        return sporsmalBuilder()
            .tag("ANY")
            .svartype(JA_NEI)
            .svar(listOf(Svar(null, "JA")))
            .undersporsmal(
                asList(
                    getSporsmalMedTomtUndersporsmal(JA_NEI, listOf(Svar(null, "JA"))),
                    sporsmalBuilder()
                        .tag(tag)
                        .svartype(JA_NEI)
                        .svar(listOf(Svar(null, verdipasvar)))
                        .undersporsmal(emptyList())
                        .build(),
                ),
            )
            .build()
    }

    private fun getSvar(antall: Int): List<Svar> {
        val svar = ArrayList<Svar>()
        for (i in 0 until antall) {
            svar.add(Svar(null, "SVAR"))
        }
        return svar
    }

    private fun Sporsmal.`valider antall svar og forvent ValideringException`() {
        assertThrows<ValideringException> {
            validerAntallSvar()
        }
    }

    private fun validerSvarverdi(
        sporsmal: Sporsmal,
        verdi: String,
    ): Boolean {
        return try {
            sporsmal.copy(svar = listOf(Svar(null, verdi = verdi))).validerSvarverdier()
            true
        } catch (e: ValideringException) {
            false
        } catch (e: IllegalStateException) {
            false
        }
    }

    private fun validerGrenser(
        sporsmal: Sporsmal,
        verdi: String,
    ): Boolean {
        return try {
            sporsmal.copy(svar = listOf(Svar(null, verdi = verdi))).validerGrenserPaSvar()
            true
        } catch (e: ValideringException) {
            false
        } catch (e: IllegalStateException) {
            false
        }
    }

    private fun validerUndersporsmal(sporsmal: Sporsmal): Boolean {
        return try {
            sporsmal.validerUndersporsmal()
            true
        } catch (e: ValideringException) {
            false
        } catch (e: IllegalStateException) {
            false
        }
    }

    private fun validerSvarPaSoknad(soknad: Sykepengesoknad): Boolean {
        return try {
            soknad.validerSvarPaSoknad()
            true
        } catch (e: ValideringException) {
            false
        }
    }
}
