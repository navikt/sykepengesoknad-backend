package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.helse.flex.domain.SelvstendigNaringsdrivendeInfo
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknadGradert
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.naringsdrivende.lagSykepengegrunnlagNaeringsdrivende
import no.nav.helse.flex.testutil.besvarsporsmal
import org.junit.jupiter.api.Test

private const val MAX_SPORSMAL = 13

class NaringsdrivendeMuteringTest {
    @Test
    fun `Mutering av soknad uten svar skal ikke føre til noen endringer`() {
        val soknad = opprettNyNaeringsdrivendeSoknadGradert()

        val mutertSoknad = soknad.naringsdrivendeMutering()

        soknad.sporsmal.size shouldBe MAX_SPORSMAL
        mutertSoknad.sporsmal.size shouldBe soknad.sporsmal.size
        mutertSoknad.sporsmal shouldBe soknad.sporsmal
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET) shouldNotBe null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET_DATO) shouldNotBe null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET) shouldNotBe null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO) shouldNotBe null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VARIG_ENDRING) shouldNotBe null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VARIG_ENDRING_DATO) shouldNotBe null
    }

    @Test
    fun `Mutering av soknad uten relevante naringsdrivende sporsmal skal ikke føre til noen endringer`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknadGradert()
                .fjernSporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET)
                .fjernSporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET)
                .fjernSporsmal(NARINGSDRIVENDE_VARIG_ENDRING)

        val mutertSoknad = soknad.naringsdrivendeMutering()

        soknad.sporsmal.size shouldBe MAX_SPORSMAL - 3
        mutertSoknad.sporsmal.size shouldBe soknad.sporsmal.size
        mutertSoknad.sporsmal shouldBe soknad.sporsmal
    }

    @Test
    fun `Avviklet ja svar skal fjerne ny i arbeidslivet og varig endring spørsmål`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknadGradert()
                .besvarsporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET, "JA")

        val mutertSoknad = soknad.naringsdrivendeMutering()

        soknad.sporsmal.size shouldBe MAX_SPORSMAL
        mutertSoknad.sporsmal.size shouldBe MAX_SPORSMAL - 2
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET).forsteSvar shouldBe "JA"
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET) shouldBe null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO) shouldBe null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VARIG_ENDRING) shouldBe null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VARIG_ENDRING_DATO) shouldBe null
    }

    @Test
    fun `Avviklet ja svar skal fjerne ny i arbeidslivet og varig endring spørsmål selv om de har svar`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknadGradert()
                .besvarsporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET, "JA")
                .besvarsporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET_DATO, "2019-01-01")
                .besvarsporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET, "JA")
                .besvarsporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO, "2020-01-01")
                .besvarsporsmal(NARINGSDRIVENDE_VARIG_ENDRING, "JA")
                .besvarsporsmal(NARINGSDRIVENDE_VARIG_ENDRING_DATO, "2021-01-01")

        val mutertSoknad = soknad.naringsdrivendeMutering()

        soknad.sporsmal.size shouldBe MAX_SPORSMAL
        mutertSoknad.sporsmal.size shouldBe MAX_SPORSMAL - 2
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET).forsteSvar shouldBe "JA"
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET_DATO).forsteSvar shouldBe "2019-01-01"
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET) shouldBe null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO) shouldBe null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VARIG_ENDRING) shouldBe null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VARIG_ENDRING_DATO) shouldBe null
    }

    @Test
    fun `Avviklet nei svar skal ikke endre svar på ny i arbeidslivet og varig endring spørsmål`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknadGradert()
                .besvarsporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET, "NEI")
                .besvarsporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET, "NEI")
                .besvarsporsmal(NARINGSDRIVENDE_VARIG_ENDRING, "JA")
                .besvarsporsmal(NARINGSDRIVENDE_VARIG_ENDRING_DATO, "2021-01-01")

        val mutertSoknad = soknad.naringsdrivendeMutering()

        soknad.sporsmal.size shouldBe MAX_SPORSMAL
        mutertSoknad.sporsmal.size shouldBe soknad.sporsmal.size
        mutertSoknad.sporsmal shouldBe soknad.sporsmal
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET).forsteSvar shouldBe "NEI"
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET).forsteSvar shouldBe "NEI"
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VARIG_ENDRING).forsteSvar shouldBe "JA"
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VARIG_ENDRING_DATO).forsteSvar shouldBe "2021-01-01"
    }

    @Test
    fun `Avviklet nei svar skal legge til manglende ny i arbeidslivet og varig endring spørsmål`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknadGradert()
                .besvarsporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET, "NEI")
                .fjernSporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET)
                .fjernSporsmal(NARINGSDRIVENDE_VARIG_ENDRING)

        val mutertSoknad = soknad.naringsdrivendeMutering()

        soknad.sporsmal.size shouldBe MAX_SPORSMAL - 2
        mutertSoknad.sporsmal.size shouldBe MAX_SPORSMAL
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET).forsteSvar shouldBe "NEI"
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET) shouldNotBe null
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO) shouldNotBe null
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VARIG_ENDRING) shouldNotBe null
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VARIG_ENDRING_DATO) shouldNotBe null
    }

    @Test
    fun `Avviklet nei svar skal ikke legge til manglende ny i arbeidslivet spørsmål hvis bruker har inntekt før sykepengegrunnlaget`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknadGradert()
                .besvarsporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET, "NEI")
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
                        erBarnepasser = false,
                        brukerHarOppgittForsikring = false,
                    ),
            )

        val mutertSoknad = sokandMedInntektFoerSykepengegrunnlaget.naringsdrivendeMutering()

        sokandMedInntektFoerSykepengegrunnlaget.sporsmal.size shouldBe MAX_SPORSMAL - 2
        mutertSoknad.sporsmal.size shouldBe MAX_SPORSMAL - 1
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET).forsteSvar shouldBe "NEI"
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET) shouldBe null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO) shouldBe null
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VARIG_ENDRING) shouldNotBe null
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VARIG_ENDRING_DATO) shouldNotBe null
    }

    @Test
    fun `Ny i arbeidslivet ja svar skal fjerne varig endring spørsmål`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknadGradert()
                .besvarsporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET, "JA")
                .besvarsporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO, "2020-01-01")

        val mutertSoknad = soknad.naringsdrivendeMutering()

        soknad.sporsmal.size shouldBe MAX_SPORSMAL
        mutertSoknad.sporsmal.size shouldBe MAX_SPORSMAL - 1
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET).forsteSvar shouldBe "JA"
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO).forsteSvar shouldBe "2020-01-01"
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VARIG_ENDRING) shouldBe null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VARIG_ENDRING_DATO) shouldBe null
    }

    @Test
    fun `Ny i arbeidslivet ja svar skal fjerne varig endring spørsmål selv om det har svar`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknadGradert()
                .besvarsporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET, "JA")
                .besvarsporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO, "2020-01-01")
                .besvarsporsmal(NARINGSDRIVENDE_VARIG_ENDRING, "JA")
                .besvarsporsmal(NARINGSDRIVENDE_VARIG_ENDRING_DATO, "2021-01-01")

        val mutertSoknad = soknad.naringsdrivendeMutering()

        soknad.sporsmal.size shouldBe MAX_SPORSMAL
        mutertSoknad.sporsmal.size shouldBe MAX_SPORSMAL - 1
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET).forsteSvar shouldBe "JA"
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO).forsteSvar shouldBe "2020-01-01"
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VARIG_ENDRING) shouldBe null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_VARIG_ENDRING_DATO) shouldBe null
    }

    @Test
    fun `Ny i arbeidslivet nei svar skal ikke endre svar på varig endring spørsmål`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknadGradert()
                .besvarsporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET, "NEI")
                .besvarsporsmal(NARINGSDRIVENDE_VARIG_ENDRING, "JA")
                .besvarsporsmal(NARINGSDRIVENDE_VARIG_ENDRING_DATO, "2021-01-01")

        val mutertSoknad = soknad.naringsdrivendeMutering()

        soknad.sporsmal.size shouldBe MAX_SPORSMAL
        mutertSoknad.sporsmal.size shouldBe soknad.sporsmal.size
        mutertSoknad.sporsmal shouldBe soknad.sporsmal
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET).forsteSvar shouldBe "NEI"
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO).forsteSvar shouldBe null
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VARIG_ENDRING).forsteSvar shouldBe "JA"
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VARIG_ENDRING_DATO).forsteSvar shouldBe "2021-01-01"
    }

    @Test
    fun `Ny i arbeidslivet nei svar skal legge til manglende varig endring spørsmål`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknadGradert()
                .besvarsporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET, "NEI")
                .fjernSporsmal(NARINGSDRIVENDE_VARIG_ENDRING)

        val mutertSoknad = soknad.naringsdrivendeMutering()

        soknad.sporsmal.size shouldBe MAX_SPORSMAL - 1
        mutertSoknad.sporsmal.size shouldBe MAX_SPORSMAL
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET).forsteSvar shouldBe "NEI"
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO).forsteSvar shouldBe null
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VARIG_ENDRING) shouldNotBe null
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_VARIG_ENDRING_DATO) shouldNotBe null
    }
}
