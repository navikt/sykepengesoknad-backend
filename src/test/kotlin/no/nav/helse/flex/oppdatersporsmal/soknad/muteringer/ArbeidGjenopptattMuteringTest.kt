package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import no.nav.helse.flex.mock.opprettNyArbeidstakerSoknad
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknad100Prosent
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknadGradert
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.testutil.besvarsporsmal
import no.nav.helse.flex.util.formatterPeriode
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be null`
import org.amshove.kluent.`should not be equal to`
import org.amshove.kluent.`should not be null`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

class ArbeidGjenopptattMuteringTest {
    @Test
    fun `spørsmål i søknaden gjenoppstår hvis de av en eller annen grunn mangla`() {
        val soknad = opprettNyArbeidstakerSoknad(svarPaSoknad = false)

        val soknadUtenOppholdUtenforEOS =
            soknad
                .besvarsporsmal(TILBAKE_I_ARBEID, svar = "NEI")
                .fjernSporsmal("OPPHOLD_UTENFOR_EOS")

        soknadUtenOppholdUtenforEOS.getSporsmalMedTagOrNull(OPPHOLD_UTENFOR_EOS).`should be null`()
        soknadUtenOppholdUtenforEOS.sporsmal.shouldHaveSize(8)

        val mutertSoknad = soknadUtenOppholdUtenforEOS.arbeidGjenopptattMutering()

        mutertSoknad.getSporsmalMedTagOrNull(OPPHOLD_UTENFOR_EOS).`should not be null`()
        mutertSoknad.sporsmal.shouldHaveSize(9)
    }

    @Test
    fun `mutering av spørsmål om perioder som kommer etter svar på tilbake i arbeid`() {
        val basisdato = LocalDate.now()
        val soknad = opprettNyArbeidstakerSoknad(svarPaSoknad = false, basisDato = basisdato)

        soknad.sporsmal.shouldHaveSize(9)
        soknad.getSporsmalMedTagOrNull("ARBEID_UNDERVEIS_100_PROSENT_0").`should not be null`()

        val mutertSoknadUtenSpm =
            soknad
                .besvarsporsmal(TILBAKE_I_ARBEID, svar = "JA")
                .besvarsporsmal(TILBAKE_NAR, svar = soknad.fom!!.plusDays(4).format(ISO_LOCAL_DATE))
                .arbeidGjenopptattMutering()

        mutertSoknadUtenSpm.sporsmal.shouldHaveSize(8)
        mutertSoknadUtenSpm.getSporsmalMedTagOrNull("JOBBET_DU_GRADERT_1").`should be null`()

        val mutertSoknadMedSpm =
            mutertSoknadUtenSpm
                .besvarsporsmal(TILBAKE_I_ARBEID, svar = "NEI")
                .arbeidGjenopptattMutering()

        mutertSoknadMedSpm.sporsmal.shouldHaveSize(9)
        mutertSoknadMedSpm.getSporsmalMedTagOrNull("JOBBET_DU_GRADERT_1").`should not be null`()
    }

    @Test
    fun `en liten tekstlig endring i et spørsmål gjør ikke at det byttes ut`() {
        val soknad = opprettNyArbeidstakerSoknad(svarPaSoknad = false)

        val soknadMedEgenPermisjonSpmTekst =
            soknad.replaceSporsmal(
                soknad.getSporsmalMedTag(PERMISJON_V2).copy(sporsmalstekst = "Var De i permisjon?"),
            )

        val arbeidGjenopptattMutering = soknadMedEgenPermisjonSpmTekst.arbeidGjenopptattMutering()
        soknadMedEgenPermisjonSpmTekst `should be equal to` arbeidGjenopptattMutering
    }

    @Test
    fun `Tilbake i fullt arbeid skal oppdatere spørsmålet næringsdrivende opprettholdt inntekt gradert med ny tom dato`() {
        val soknad = opprettNyNaeringsdrivendeSoknadGradert()
        val tilbakeIArbeid = soknad.fom!!.plusDays(4)
        val nyTom = tilbakeIArbeid.minusDays(1)

        val mutertSoknad =
            soknad
                .besvarsporsmal(TILBAKE_I_ARBEID, "JA")
                .besvarsporsmal(TILBAKE_NAR, tilbakeIArbeid.format(ISO_LOCAL_DATE))
                .arbeidGjenopptattMutering()

        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT_GRADERT).let {
            it `should not be equal to` null
            it.sporsmalstekst `should be equal to` "Hadde du inntekt i virksomheten din mens du var sykmeldt " +
                "${formatterPeriode(soknad.fom, nyTom)}, som ikke var et resultat av at du selv jobbet?"
        }
    }

    @Test
    fun `Tilbake i fullt arbeid skal legge til spørsmålet næringsdrivende opprettholdt inntekt gradert`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknadGradert()
                .fjernSporsmal(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT_GRADERT)
        val tilbakeIArbeid = soknad.fom!!.plusDays(4)
        val nyTom = tilbakeIArbeid.minusDays(1)

        val mutertSoknad =
            soknad
                .besvarsporsmal(TILBAKE_I_ARBEID, "JA")
                .besvarsporsmal(TILBAKE_NAR, tilbakeIArbeid.format(ISO_LOCAL_DATE))
                .arbeidGjenopptattMutering()

        soknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT_GRADERT) `should be equal to` null
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT_GRADERT).let {
            it `should not be equal to` null
            it.sporsmalstekst `should be equal to` "Hadde du inntekt i virksomheten din mens du var sykmeldt " +
                "${formatterPeriode(soknad.fom, nyTom)}, som ikke var et resultat av at du selv jobbet?"
        }
    }

    @Test
    fun `Tilbake i fullt arbeid skal fjerne spørsmålet næringsdrivende opprettholdt inntekt gradert`() {
        val soknad = opprettNyNaeringsdrivendeSoknadGradert()

        val mutertSoknad =
            soknad
                .besvarsporsmal(TILBAKE_I_ARBEID, "JA")
                .besvarsporsmal(TILBAKE_NAR, soknad.fom!!.format(ISO_LOCAL_DATE))
                .arbeidGjenopptattMutering()

        soknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT_GRADERT) `should not be equal to` null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT_GRADERT) `should be equal to` null
    }

    @Test
    fun `Tilbake i fullt arbeid skal legge til spørsmålet næringsdrivende opprettholdt inntekt`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad100Prosent()
                .fjernSporsmal(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT)
        val tilbakeIArbeid = soknad.fom!!.plusDays(4)

        val mutertSoknad =
            soknad
                .besvarsporsmal(TILBAKE_I_ARBEID, "JA")
                .besvarsporsmal(TILBAKE_NAR, tilbakeIArbeid.format(ISO_LOCAL_DATE))
                .arbeidGjenopptattMutering()

        soknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT) `should be equal to` null
        mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT).let {
            it `should not be equal to` null
            it.sporsmalstekst `should be equal to` "Hadde du inntekt i virksomheten din selv om du var 100 % sykmeldt og ikke jobbet selv?"
        }
    }

    @Test
    fun `Tilbake i fullt arbeid skal fjerne spørsmålet næringsdrivende opprettholdt inntekt`() {
        val soknad = opprettNyNaeringsdrivendeSoknad100Prosent()

        val mutertSoknad =
            soknad
                .besvarsporsmal(TILBAKE_I_ARBEID, "JA")
                .besvarsporsmal(TILBAKE_NAR, soknad.fom!!.format(ISO_LOCAL_DATE))
                .arbeidGjenopptattMutering()

        soknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT) `should not be equal to` null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT) `should be equal to` null
    }

    @Test
    fun `Arbeid underveis 100 prosent nei skal ikke mutere opprettholdt inntekt til gradert sporsmål`() {
        val soknad = opprettNyNaeringsdrivendeSoknad100Prosent()

        val mutertSoknad =
            soknad
                .besvarsporsmal(ARBEID_UNDERVEIS_100_PROSENT + "0", "NEI")
                .arbeidGjenopptattMutering()

        soknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT) `should not be equal to` null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT) `should not be equal to` null

        soknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT_GRADERT) `should be equal to` null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT_GRADERT) `should be equal to` null
    }

    @Test
    fun `Arbeid underveis 100 prosent skal mutere opprettholdt inntekt til gradert sporsmål`() {
        val soknad = opprettNyNaeringsdrivendeSoknad100Prosent()

        val mutertSoknad =
            soknad
                .besvarsporsmal(ARBEID_UNDERVEIS_100_PROSENT + "0", "JA")
                .arbeidGjenopptattMutering()

        soknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT) `should not be equal to` null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT) `should be equal to` null

        soknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT_GRADERT) `should be equal to` null
        mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT_GRADERT) `should not be equal to` null
    }
}
