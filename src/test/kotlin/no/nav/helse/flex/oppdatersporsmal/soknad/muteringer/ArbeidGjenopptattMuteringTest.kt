package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.Sykmeldingstype
import no.nav.helse.flex.mock.opprettNyArbeidstakerSoknad
import no.nav.helse.flex.mock.opprettNyFiskerHyreSoknad
import no.nav.helse.flex.mock.opprettNyFiskerLottOgHyreSoknad
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknad100Prosent
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknadGradert
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.fiskerLottOpprettholdtInntekt100Prosent
import no.nav.helse.flex.testutil.besvarsporsmal
import no.nav.helse.flex.util.formatterPeriode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

class ArbeidGjenopptattMuteringTest {
    private val testDato: LocalDate = LocalDate.of(2024, 6, 1)

    @Nested
    inner class ArbeidstakerOgFiskerHyre {
        private fun forHverSoknadstype(test: (soknad: Sykepengesoknad) -> Unit): List<DynamicTest> =
            listOf(
                "arbeidstaker" to opprettNyArbeidstakerSoknad(svarPaSoknad = false, basisDato = testDato),
                "fisker hyre" to opprettNyFiskerHyreSoknad(svarPaSoknad = false, basisDato = testDato),
            ).map { (navn, soknad) -> dynamicTest(navn) { test(soknad) } }

        @TestFactory
        fun `spørsmål i søknaden gjenoppstår hvis de av en eller annen grunn mangla`() =
            forHverSoknadstype { soknad ->
                val soknadUtenOppholdUtenforEOS =
                    soknad
                        .besvarsporsmal(TILBAKE_I_ARBEID, svar = "NEI")
                        .fjernSporsmal("OPPHOLD_UTENFOR_EOS")

                soknadUtenOppholdUtenforEOS.getSporsmalMedTagOrNull(OPPHOLD_UTENFOR_EOS).shouldBeNull()
                soknadUtenOppholdUtenforEOS.sporsmal.shouldHaveSize(8)

                val mutertSoknad = soknadUtenOppholdUtenforEOS.arbeidGjenopptattMutering()

                mutertSoknad.getSporsmalMedTagOrNull(OPPHOLD_UTENFOR_EOS).shouldNotBeNull()
                mutertSoknad.sporsmal.shouldHaveSize(9)
            }

        @TestFactory
        fun `mutering av spørsmål om perioder som kommer etter svar på tilbake i arbeid`() =
            forHverSoknadstype { soknad ->
                soknad.sporsmal.shouldHaveSize(9)
                soknad.getSporsmalMedTagOrNull("ARBEID_UNDERVEIS_100_PROSENT_0").shouldNotBeNull()

                val mutertSoknadUtenSpm =
                    soknad
                        .besvarsporsmal(TILBAKE_I_ARBEID, svar = "JA")
                        .besvarsporsmal(TILBAKE_NAR, svar = soknad.fom!!.plusDays(4).format(ISO_LOCAL_DATE))
                        .arbeidGjenopptattMutering()

                mutertSoknadUtenSpm.sporsmal.shouldHaveSize(8)
                mutertSoknadUtenSpm.getSporsmalMedTagOrNull("JOBBET_DU_GRADERT_1").shouldBeNull()

                val mutertSoknadMedSpm =
                    mutertSoknadUtenSpm
                        .besvarsporsmal(TILBAKE_I_ARBEID, svar = "NEI")
                        .arbeidGjenopptattMutering()

                mutertSoknadMedSpm.sporsmal.shouldHaveSize(9)
                mutertSoknadMedSpm.getSporsmalMedTagOrNull("JOBBET_DU_GRADERT_1").shouldNotBeNull()
            }

        @TestFactory
        fun `muter vekk og gjenopprett spørsmål arbeid gjenopptatt`() =
            forHverSoknadstype { soknad ->
                soknad.sporsmal.shouldHaveSize(9)

                val mutertSoknad =
                    soknad
                        .besvarsporsmal(TILBAKE_I_ARBEID, "JA")
                        .besvarsporsmal(TILBAKE_NAR, soknad.fom!!.format(ISO_LOCAL_DATE))
                        .arbeidGjenopptattMutering()

                mutertSoknad.sporsmal.shouldHaveSize(3)

                val mutertGjenopprettSoknad =
                    soknad
                        .besvarsporsmal(TILBAKE_I_ARBEID, "NEI")
                        .arbeidGjenopptattMutering()

                mutertGjenopprettSoknad.sporsmal.shouldHaveSize(9)
            }

        @TestFactory
        fun `en liten tekstlig endring i et spørsmål gjør ikke at det byttes ut`() =
            forHverSoknadstype { soknad ->
                val soknadMedEgenPermisjonSpmTekst =
                    soknad.replaceSporsmal(
                        soknad.getSporsmalMedTag(PERMISJON_V2).copy(sporsmalstekst = "Var De i permisjon?"),
                    )

                val arbeidGjenopptattMutering = soknadMedEgenPermisjonSpmTekst.arbeidGjenopptattMutering()
                soknadMedEgenPermisjonSpmTekst shouldBe arbeidGjenopptattMutering
            }
    }

    @Nested
    inner class Naringsdrivende {
        @TestFactory
        fun `opphold utenfor EOS gjenoppstr for selvstendige nr tilbake i arbeid endres fra fom`() =
            listOf(
                Arbeidssituasjon.NAERINGSDRIVENDE,
                Arbeidssituasjon.FISKER,
            ).map { arbeidssituasjon ->
                dynamicTest(arbeidssituasjon.name) {
                    val soknad = opprettNyNaeringsdrivendeSoknad100Prosent().copy(arbeidssituasjon = arbeidssituasjon)
                    soknad.getSporsmalMedTagOrNull(OPPHOLD_UTENFOR_EOS).shouldNotBeNull()

                    val tilbakeIArbeidFraFom =
                        soknad
                            .besvarsporsmal(TILBAKE_I_ARBEID, "JA")
                            .besvarsporsmal(TILBAKE_NAR, soknad.fom!!.format(ISO_LOCAL_DATE))
                            .arbeidGjenopptattMutering()
                    tilbakeIArbeidFraFom.getSporsmalMedTagOrNull(OPPHOLD_UTENFOR_EOS).shouldBeNull()

                    val ikkeTilbakeIArbeid =
                        tilbakeIArbeidFraFom
                            .besvarsporsmal(TILBAKE_I_ARBEID, "NEI")
                            .arbeidGjenopptattMutering()
                    ikkeTilbakeIArbeid.getSporsmalMedTagOrNull(OPPHOLD_UTENFOR_EOS).shouldNotBeNull()
                }
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
                it shouldNotBe null
                it.sporsmalstekst shouldBe "Hadde du inntekt i virksomheten din mens du var sykmeldt " +
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

            soknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT_GRADERT) shouldBe null
            mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT_GRADERT).let {
                it shouldNotBe null
                it.sporsmalstekst shouldBe "Hadde du inntekt i virksomheten din mens du var sykmeldt " +
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

            soknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT_GRADERT) shouldNotBe null
            mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT_GRADERT) shouldBe null
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

            soknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT) shouldBe null
            mutertSoknad.getSporsmalMedTag(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT).let {
                it shouldNotBe null
                it.sporsmalstekst shouldBe
                    "Hadde du inntekt i virksomheten din selv om du var 100 % sykmeldt og ikke jobbet selv?"
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

            soknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT) shouldNotBe null
            mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT) shouldBe null
        }

        @Test
        fun `Arbeid underveis 100 prosent nei skal ikke mutere opprettholdt inntekt til gradert sporsmål`() {
            val soknad = opprettNyNaeringsdrivendeSoknad100Prosent()

            val mutertSoknad =
                soknad
                    .besvarsporsmal(ARBEID_UNDERVEIS_100_PROSENT + "0", "NEI")
                    .arbeidGjenopptattMutering()

            soknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT) shouldNotBe null
            mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT) shouldNotBe null

            soknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT_GRADERT) shouldBe null
            mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT_GRADERT) shouldBe null
        }

        @Test
        fun `Arbeid underveis 100 prosent skal mutere opprettholdt inntekt til gradert sporsmål`() {
            val soknad = opprettNyNaeringsdrivendeSoknad100Prosent()

            val mutertSoknad =
                soknad
                    .besvarsporsmal(ARBEID_UNDERVEIS_100_PROSENT + "0", "JA")
                    .arbeidGjenopptattMutering()

            soknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT) shouldNotBe null
            mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT) shouldBe null

            soknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT_GRADERT) shouldBe null
            mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT_GRADERT) shouldNotBe null
        }
    }

    @Nested
    inner class FiskerLottOgHyre {
        @Test
        fun `Arbeid underveis 100 prosent nei skal ikke mutere lottspørsmålet til gradert for fisker med lott og hyre`() {
            val soknad = opprettFiskerLottOgHyreSoknad100Prosent()

            val mutertSoknad =
                soknad
                    .besvarsporsmal(ARBEID_UNDERVEIS_100_PROSENT + "0", "NEI")
                    .arbeidGjenopptattMutering()

            mutertSoknad.getSporsmalMedTagOrNull(FISKER_LOTT_OPPRETTHOLDT_INNTEKT).shouldNotBeNull()
            mutertSoknad.getSporsmalMedTagOrNull(FISKER_LOTT_OPPRETTHOLDT_INNTEKT_GRADERT).shouldBeNull()
        }

        @Test
        fun `Arbeid underveis 100 prosent ja skal mutere lottspørsmålet til gradert for fisker med lott og hyre`() {
            val soknad = opprettFiskerLottOgHyreSoknad100Prosent()

            val mutertSoknad =
                soknad
                    .besvarsporsmal(ARBEID_UNDERVEIS_100_PROSENT + "0", "JA")
                    .arbeidGjenopptattMutering()

            mutertSoknad.getSporsmalMedTagOrNull(FISKER_LOTT_OPPRETTHOLDT_INNTEKT).shouldBeNull()
            mutertSoknad.getSporsmalMedTagOrNull(FISKER_LOTT_OPPRETTHOLDT_INNTEKT_GRADERT).shouldNotBeNull()
            mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT).shouldBeNull()
            mutertSoknad.getSporsmalMedTagOrNull(NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT_GRADERT).shouldBeNull()
        }

        private fun opprettFiskerLottOgHyreSoknad100Prosent(): Sykepengesoknad {
            val gradertSoknad = opprettNyFiskerLottOgHyreSoknad(svarPaSoknad = false)
            val soknad =
                gradertSoknad
                    .copy(
                        soknadPerioder =
                            listOf(
                                Soknadsperiode(
                                    fom = gradertSoknad.fom!!,
                                    tom = gradertSoknad.tom!!,
                                    grad = 100,
                                    sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG,
                                ),
                            ),
                    ).fjernSporsmal("JOBBET_DU_GRADERT_1")
                    .fjernSporsmal(FISKER_LOTT_OPPRETTHOLDT_INNTEKT_GRADERT)

            return soknad
                .copy(
                    sporsmal = soknad.sporsmal + fiskerLottOpprettholdtInntekt100Prosent(soknad.fom!!, soknad.tom!!),
                ).also {
                    it.getSporsmalMedTagOrNull(FISKER_LOTT_OPPRETTHOLDT_INNTEKT).shouldNotBeNull()
                    it.getSporsmalMedTagOrNull(FISKER_LOTT_OPPRETTHOLDT_INNTEKT_GRADERT).shouldBeNull()
                }
        }

        @Test
        fun `Muter vekk og gjenopprett spørsmål for fisker med lott og hyre`() {
            val soknad = opprettNyFiskerLottOgHyreSoknad(svarPaSoknad = false)

            sammenlignFiskerLottOgHyreSporsmal(soknad)

            val mutertSoknad =
                soknad
                    .besvarsporsmal(TILBAKE_I_ARBEID, "JA")
                    .besvarsporsmal(TILBAKE_NAR, soknad.fom!!.format(ISO_LOCAL_DATE))
                    .arbeidGjenopptattMutering()

            mutertSoknad.sporsmal.shouldHaveSize(8)

            val mutertGjenopprettSoknad =
                soknad
                    .besvarsporsmal(TILBAKE_I_ARBEID, "NEI")
                    .arbeidGjenopptattMutering()

            sammenlignFiskerLottOgHyreSporsmal(mutertGjenopprettSoknad)
        }

        private fun sammenlignFiskerLottOgHyreSporsmal(soknad: Sykepengesoknad) {
            soknad.sporsmal.map { it.tag } shouldBe
                listOf(
                    ANSVARSERKLARING,
                    TILBAKE_I_ARBEID,
                    FERIE_V2,
                    PERMISJON_V2,
                    "ARBEID_UNDERVEIS_100_PROSENT_0",
                    "JOBBET_DU_GRADERT_1",
                    ARBEID_UTENFOR_NORGE,
                    FISKER_LOTT_OPPRETTHOLDT_INNTEKT_GRADERT,
                    ANDRE_INNTEKTSKILDER_V2,
                    OPPHOLD_UTENFOR_EOS,
                    NARINGSDRIVENDE_OPPHOLD_I_UTLANDET,
                    NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET,
                    NARINGSDRIVENDE_NY_I_ARBEIDSLIVET,
                    NARINGSDRIVENDE_VARIG_ENDRING,
                    TIL_SLUTT,
                )
        }
    }
}
