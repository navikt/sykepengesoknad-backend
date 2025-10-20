package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import no.nav.helse.flex.domain.SelvstendigNaringsdrivendeInfo
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknad
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.testutil.besvarsporsmal
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.junit.jupiter.api.Test

class NaringsdrivendeMuteringTest {
    @Test
    fun `Mutering av soknad uten svar skal ikke føre til noen endringer`() {
        val soknad = opprettNyNaeringsdrivendeSoknad(true)

        val mutertSoknad = soknad.naringsdrivendeMutering()

        soknad.sporsmal.size `should be equal to` 12
        mutertSoknad.sporsmal.size `should be equal to` soknad.sporsmal.size
        mutertSoknad.sporsmal `should be equal to` soknad.sporsmal
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VIRKSOMHETEN_DIN_AVVIKLET) `should not be` null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VIRKSOMHETEN_DIN_AVVIKLET_DATO) `should not be` null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET) `should not be` null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO) `should not be` null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VARIG_ENDRING) `should not be` null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VARIG_ENDRING_DATO) `should not be` null
    }

    @Test
    fun `Mutering av soknad uten relevante naringsdrivende sporsmal skal ikke føre til noen endringer`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad(true)
                .fjernSporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_DIN_AVVIKLET)
                .fjernSporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET)
                .fjernSporsmal(NARINGSDRIVENDE_VARIG_ENDRING)

        val mutertSoknad = soknad.naringsdrivendeMutering()

        soknad.sporsmal.size `should be equal to` 9
        mutertSoknad.sporsmal.size `should be equal to` soknad.sporsmal.size
        mutertSoknad.sporsmal `should be equal to` soknad.sporsmal
    }

    @Test
    fun `Avviklet ja svar skal fjerne ny i arbeidslivet og varig endring spørsmål`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad(true)
                .besvarsporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_DIN_AVVIKLET, "JA")

        val mutertSoknad = soknad.naringsdrivendeMutering()

        soknad.sporsmal.size `should be equal to` 12
        mutertSoknad.sporsmal.size `should be equal to` 10
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VIRKSOMHETEN_DIN_AVVIKLET).forsteSvar `should be equal to` "JA"
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET) `should be equal to` null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO) `should be equal to` null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VARIG_ENDRING) `should be equal to` null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VARIG_ENDRING_DATO) `should be equal to` null
    }

    @Test
    fun `Avviklet ja svar skal fjerne ny i arbeidslivet og varig endring spørsmål selv om de har svar`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad(true)
                .besvarsporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_DIN_AVVIKLET, "JA")
                .besvarsporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_DIN_AVVIKLET_DATO, "2019-01-01")
                .besvarsporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET, "JA")
                .besvarsporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO, "2020-01-01")
                .besvarsporsmal(NARINGSDRIVENDE_VARIG_ENDRING, "JA")
                .besvarsporsmal(NARINGSDRIVENDE_VARIG_ENDRING_DATO, "2021-01-01")

        val mutertSoknad = soknad.naringsdrivendeMutering()

        soknad.sporsmal.size `should be equal to` 12
        mutertSoknad.sporsmal.size `should be equal to` 10
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VIRKSOMHETEN_DIN_AVVIKLET).forsteSvar `should be equal to` "JA"
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VIRKSOMHETEN_DIN_AVVIKLET_DATO).forsteSvar `should be equal to` "2019-01-01"
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET) `should be equal to` null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO) `should be equal to` null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VARIG_ENDRING) `should be equal to` null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VARIG_ENDRING_DATO) `should be equal to` null
    }

    @Test
    fun `Avviklet nei svar skal ikke endre svar på ny i arbeidslivet og varig endring spørsmål`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad(true)
                .besvarsporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_DIN_AVVIKLET, "NEI")
                .besvarsporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET, "NEI")
                .besvarsporsmal(NARINGSDRIVENDE_VARIG_ENDRING, "JA")
                .besvarsporsmal(NARINGSDRIVENDE_VARIG_ENDRING_DATO, "2021-01-01")

        val mutertSoknad = soknad.naringsdrivendeMutering()

        soknad.sporsmal.size `should be equal to` 12
        mutertSoknad.sporsmal.size `should be equal to` soknad.sporsmal.size
        mutertSoknad.sporsmal `should be equal to` soknad.sporsmal
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VIRKSOMHETEN_DIN_AVVIKLET).forsteSvar `should be equal to` "NEI"
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET).forsteSvar `should be equal to` "NEI"
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VARIG_ENDRING).forsteSvar `should be equal to` "JA"
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VARIG_ENDRING_DATO).forsteSvar `should be equal to` "2021-01-01"
    }

    @Test
    fun `Avviklet nei svar skal legge til manglende ny i arbeidslivet og varig endring spørsmål`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad(true)
                .besvarsporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_DIN_AVVIKLET, "NEI")
                .fjernSporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET)
                .fjernSporsmal(NARINGSDRIVENDE_VARIG_ENDRING)

        val mutertSoknad = soknad.naringsdrivendeMutering()

        soknad.sporsmal.size `should be equal to` 10
        mutertSoknad.sporsmal.size `should be equal to` 12
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VIRKSOMHETEN_DIN_AVVIKLET).forsteSvar `should be equal to` "NEI"
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET) `should not be` null
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO) `should not be` null
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VARIG_ENDRING) `should not be` null
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VARIG_ENDRING_DATO) `should not be` null
    }

    @Test
    fun `Avviklet nei svar skal ikke legge til manglende ny i arbeidslivet spørsmål hvis bruker har inntekt før sykepengegrunnlaget`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad(true)
                .besvarsporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_DIN_AVVIKLET, "NEI")
                .fjernSporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET)
                .fjernSporsmal(NARINGSDRIVENDE_VARIG_ENDRING)

        val sokandMedInntektFoerSykepengegrunnlaget =
            soknad.copy(
                selvstendigNaringsdrivende =
                    SelvstendigNaringsdrivendeInfo(
                        roller = emptyList(),
                        sykepengegrunnlagNaeringsdrivende =
                            lagSykepengegrunnlagNaeringsdrivende().copy(
                                harFunnetInntektFoerSykepengegrunnlaget = true,
                            ),
                        ventetid = null,
                        erBarnepasser = false,
                        brukerHarOppgittForsikring = false,
                    ),
            )

        val mutertSoknad = sokandMedInntektFoerSykepengegrunnlaget.naringsdrivendeMutering()

        sokandMedInntektFoerSykepengegrunnlaget.sporsmal.size `should be equal to` 10
        mutertSoknad.sporsmal.size `should be equal to` 11
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VIRKSOMHETEN_DIN_AVVIKLET).forsteSvar `should be equal to` "NEI"
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET) `should be` null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO) `should be` null
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VARIG_ENDRING) `should not be` null
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VARIG_ENDRING_DATO) `should not be` null
    }

    @Test
    fun `Ny i arbeidslivet ja svar skal fjerne varig endring spørsmål`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad(true)
                .besvarsporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET, "JA")
                .besvarsporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO, "2020-01-01")

        val mutertSoknad = soknad.naringsdrivendeMutering()

        soknad.sporsmal.size `should be equal to` 12
        mutertSoknad.sporsmal.size `should be equal to` 11
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET).forsteSvar `should be equal to` "JA"
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO).forsteSvar `should be equal to` "2020-01-01"
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VARIG_ENDRING) `should be equal to` null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VARIG_ENDRING_DATO) `should be equal to` null
    }

    @Test
    fun `Ny i arbeidslivet ja svar skal fjerne varig endring spørsmål selv om det har svar`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad(true)
                .besvarsporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET, "JA")
                .besvarsporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO, "2020-01-01")
                .besvarsporsmal(NARINGSDRIVENDE_VARIG_ENDRING, "JA")
                .besvarsporsmal(NARINGSDRIVENDE_VARIG_ENDRING_DATO, "2021-01-01")

        val mutertSoknad = soknad.naringsdrivendeMutering()

        soknad.sporsmal.size `should be equal to` 12
        mutertSoknad.sporsmal.size `should be equal to` 11
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET).forsteSvar `should be equal to` "JA"
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO).forsteSvar `should be equal to` "2020-01-01"
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VARIG_ENDRING) `should be equal to` null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VARIG_ENDRING_DATO) `should be equal to` null
    }

    @Test
    fun `Ny i arbeidslivet nei svar skal ikke endre svar på varig endring spørsmål`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad(true)
                .besvarsporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET, "NEI")
                .besvarsporsmal(NARINGSDRIVENDE_VARIG_ENDRING, "JA")
                .besvarsporsmal(NARINGSDRIVENDE_VARIG_ENDRING_DATO, "2021-01-01")

        val mutertSoknad = soknad.naringsdrivendeMutering()

        soknad.sporsmal.size `should be equal to` 12
        mutertSoknad.sporsmal.size `should be equal to` soknad.sporsmal.size
        mutertSoknad.sporsmal `should be equal to` soknad.sporsmal
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET).forsteSvar `should be equal to` "NEI"
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO).forsteSvar `should be equal to` null
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VARIG_ENDRING).forsteSvar `should be equal to` "JA"
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VARIG_ENDRING_DATO).forsteSvar `should be equal to` "2021-01-01"
    }

    @Test
    fun `Ny i arbeidslivet nei svar skal legge til manglende varig endring spørsmål`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad(true)
                .besvarsporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET, "NEI")
                .fjernSporsmal(NARINGSDRIVENDE_VARIG_ENDRING)

        val mutertSoknad = soknad.naringsdrivendeMutering()

        soknad.sporsmal.size `should be equal to` 11
        mutertSoknad.sporsmal.size `should be equal to` 12
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET).forsteSvar `should be equal to` "NEI"
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO).forsteSvar `should be equal to` null
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VARIG_ENDRING) `should not be` null
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VARIG_ENDRING_DATO) `should not be` null
    }
}
