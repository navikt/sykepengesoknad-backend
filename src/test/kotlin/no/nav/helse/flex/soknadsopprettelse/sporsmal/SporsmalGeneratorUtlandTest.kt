package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.FiskerLottOgHyre
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.mock.opprettNySoknad
import no.nav.helse.flex.mockdispatcher.AaregMockDispatcher
import no.nav.helse.flex.mockdispatcher.InntektskomponentenMockDispatcher
import no.nav.helse.flex.mockdispatcher.skapArbeidsforholdOversikt
import no.nav.helse.flex.mockdispatcher.skapHentInntekterResponse
import no.nav.helse.flex.repository.SoknadLagrer
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.FLERE_INNTEKTSKILDER_GRUPPE
import no.nav.helse.flex.soknadsopprettelse.NYTT_ARBEIDSFORHOLD_UNDERVEIS
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadOppholdUtland
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be null`
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

class SporsmalGeneratorUtlandTest : FakesTestOppsett() {
    @Autowired
    private lateinit var soknadLagrer: SoknadLagrer

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var sporsmalGenerator: SporsmalGenerator

    @ParameterizedTest(name = "{0}")
    @EnumSource(Arbeidssituasjon::class, names = ["FISKER", "ANNET"], mode = EnumSource.Mode.EXCLUDE)
    fun `utenlandssøknader uten datoer påvirker ikke nye arbeidsforhold`(arbeidssituasjon: Arbeidssituasjon) {
        val soknad = opprettSoknadMedUtenlandssoknaderIHistorikken(arbeidssituasjon)

        InntektskomponentenMockDispatcher.enqueue(skapHentInntekterResponse(fnr = soknad.fnr))
        sporsmalGenerator.lagSporsmalPaSoknad(soknad.id)

        sjekkArbeidsforholdFraAareg(soknad.id, forventedeOrgnumre = emptyList())
        sjekkGhostInntekter(soknad.id, forventedeOrgnumre = emptyList())

        leggTilTidligereSykefravar(soknad)
        if (soknad.erTilsvarendeArbeidstaker()) {
            klargjorArbeidsforholdFraAareg(soknad)
        }
        InntektskomponentenMockDispatcher.enqueue(skapHentInntekterResponse(fnr = soknad.fnr))
        sporsmalGenerator.lagSporsmalPaSoknad(soknad.id)

        sjekkArbeidsforholdFraAareg(
            soknad.id,
            forventedeOrgnumre = if (soknad.erTilsvarendeArbeidstaker()) listOf("999333666") else emptyList(),
        )
        sjekkGhostInntekter(soknad.id, forventedeOrgnumre = emptyList())
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Arbeidssituasjon::class, names = ["ARBEIDSTAKER", "FISKER", "ANNET"], mode = EnumSource.Mode.EXCLUDE)
    fun `andre arbeidssituasjoner får ghost fra inntektskomponenten uten å hente nye arbeidsforhold fra Aareg`(
        arbeidssituasjon: Arbeidssituasjon,
    ) {
        val soknad = opprettSoknadMedUtenlandssoknaderIHistorikken(arbeidssituasjon)
        leggTilTidligereSykefravar(soknad)
        InntektskomponentenMockDispatcher.enqueue(
            skapHentInntekterResponse(fnr = soknad.fnr, loennsinntektOrgnumre = listOf("999333666")),
        )

        sporsmalGenerator.lagSporsmalPaSoknad(soknad.id)

        sjekkArbeidsforholdFraAareg(soknad.id, forventedeOrgnumre = emptyList())
        sjekkGhostInntekter(soknad.id, forventedeOrgnumre = listOf("999333666"))
        val lagretSoknad = sykepengesoknadDAO.finnSykepengesoknad(soknad.id)
        lagretSoknad.ghostInntekter!!.single().kilde `should be equal to` Kilde.INNTEKTSKOMPONENTEN
        lagretSoknad.sporsmal.count { it.tag == FLERE_INNTEKTSKILDER_GRUPPE } `should be equal to` 1
        lagretSoknad.sporsmal.none { it.tag.startsWith(NYTT_ARBEIDSFORHOLD_UNDERVEIS) } `should be equal to` true
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(FiskerLottOgHyre::class)
    fun `fiskere henter ikke nye arbeidsforhold fra Aareg heller ikke utenfor arbeidsgiverperioden`(fiskerLottOgHyre: FiskerLottOgHyre) {
        val soknad =
            opprettSoknadMedUtenlandssoknaderIHistorikken(Arbeidssituasjon.FISKER).copy(
                fiskerLottOgHyre = fiskerLottOgHyre,
                arbeidsgiverOrgnummer = "999333777",
                arbeidsgiverNavn = "Arbeidsgiveren AS",
            )
        soknadLagrer.lagreSoknad(soknad)
        leggTilTidligereSykefravar(soknad)
        InntektskomponentenMockDispatcher.enqueue(skapHentInntekterResponse(fnr = soknad.fnr))

        sporsmalGenerator.lagSporsmalPaSoknad(soknad.id)

        sjekkArbeidsforholdFraAareg(soknad.id, forventedeOrgnumre = emptyList())
        sjekkGhostInntekter(soknad.id, forventedeOrgnumre = emptyList())
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Arbeidssituasjon::class, names = ["FISKER", "ANNET"])
    fun `utenlandssøknader uten datoer hindrer ikke spørsmål for arbeidssituasjoner uten nye arbeidsforhold`(
        arbeidssituasjon: Arbeidssituasjon,
    ) {
        val soknad = opprettSoknadMedUtenlandssoknaderIHistorikken(arbeidssituasjon)

        sporsmalGenerator.lagSporsmalPaSoknad(soknad.id)

        val lagretSoknad = sykepengesoknadDAO.finnSykepengesoknad(soknad.id)
        lagretSoknad.sporsmal.isNotEmpty() `should be equal to` true
        lagretSoknad.arbeidsforholdFraAareg `should be equal to`
            if (arbeidssituasjon == Arbeidssituasjon.FISKER) emptyList() else null
        lagretSoknad.ghostInntekter `should be equal to`
            if (arbeidssituasjon == Arbeidssituasjon.FISKER) emptyList() else null
    }

    private fun opprettSoknadMedUtenlandssoknaderIHistorikken(arbeidssituasjon: Arbeidssituasjon): Sykepengesoknad {
        val soknad =
            opprettNySoknad()
                .copy(
                    fnr = "1111123457${arbeidssituasjon.ordinal}",
                    arbeidssituasjon = arbeidssituasjon,
                    soknadstype =
                        when (arbeidssituasjon) {
                            Arbeidssituasjon.ARBEIDSTAKER -> Soknadstype.ARBEIDSTAKERE
                            Arbeidssituasjon.ARBEIDSLEDIG -> Soknadstype.ARBEIDSLEDIG
                            Arbeidssituasjon.FISKER -> Soknadstype.FISKERE
                            Arbeidssituasjon.ANNET -> Soknadstype.ANNET_ARBEIDSFORHOLD

                            Arbeidssituasjon.NAERINGSDRIVENDE,
                            Arbeidssituasjon.FRILANSER,
                            Arbeidssituasjon.BARNEPASSER,
                            Arbeidssituasjon.JORDBRUKER,
                            -> Soknadstype.SELVSTENDIGE_OG_FRILANSERE
                        },
                    arbeidsgiverOrgnummer = if (arbeidssituasjon == Arbeidssituasjon.ARBEIDSTAKER) "999333777" else null,
                    arbeidsgiverNavn = if (arbeidssituasjon == Arbeidssituasjon.ARBEIDSTAKER) "Arbeidsgiveren AS" else null,
                    fiskerLottOgHyre = if (arbeidssituasjon == Arbeidssituasjon.FISKER) FiskerLottOgHyre.LOTT else null,
                    sporsmal = emptyList(),
                ).let { soknad ->
                    val tom = soknad.fom!!.plusDays(18)
                    soknad.copy(
                        tom = tom,
                        soknadPerioder =
                            listOf(
                                soknad.soknadPerioder!!.first().copy(fom = soknad.fom, tom = tom, grad = 100),
                            ),
                    )
                }
        soknadLagrer.lagreSoknad(soknad)
        soknadLagrer.lagreSoknad(settOppSoknadOppholdUtland(soknad.fnr).copy(status = Soknadstatus.NY))
        soknadLagrer.lagreSoknad(settOppSoknadOppholdUtland(soknad.fnr).copy(status = Soknadstatus.SENDT))

        return soknad
    }

    private fun leggTilTidligereSykefravar(soknad: Sykepengesoknad) {
        soknadLagrer.lagreSoknad(
            soknad.copy(
                id = UUID.randomUUID().toString(),
                fom = soknad.fom!!.minusDays(3),
                tom = soknad.fom.minusDays(1),
                status = Soknadstatus.SENDT,
                soknadPerioder = emptyList(),
            ),
        )
    }

    private fun klargjorArbeidsforholdFraAareg(soknad: Sykepengesoknad) {
        AaregMockDispatcher.enqueue(
            listOf(
                skapArbeidsforholdOversikt(
                    fnr = soknad.fnr,
                    startdato = soknad.fom!!.minusDays(3),
                    arbeidssted = "999333666",
                    opplysningspliktigOrganisasjonsnummer = "123456789",
                ),
            ),
        )
    }

    private fun sjekkArbeidsforholdFraAareg(
        soknadId: String,
        forventedeOrgnumre: List<String>,
    ) {
        val lagretSoknad = sykepengesoknadDAO.finnSykepengesoknad(soknadId)
        lagretSoknad.sporsmal.isNotEmpty() `should be equal to` true
        lagretSoknad.arbeidsforholdFraAareg.`should not be null`().map { it.arbeidsstedOrgnummer } `should be equal to`
            forventedeOrgnumre
    }

    private fun sjekkGhostInntekter(
        soknadId: String,
        forventedeOrgnumre: List<String>,
    ) {
        val lagretSoknad = sykepengesoknadDAO.finnSykepengesoknad(soknadId)
        lagretSoknad.ghostInntekter.`should not be null`().map { it.orgnummer } `should be equal to` forventedeOrgnumre
    }
}
