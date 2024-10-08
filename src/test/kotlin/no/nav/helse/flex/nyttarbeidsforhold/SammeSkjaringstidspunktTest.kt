package no.nav.helse.flex.nyttarbeidsforhold

import no.nav.helse.flex.client.aareg.ArbeidsforholdOversikt
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.mock.opprettNySoknad
import no.nav.helse.flex.mockdispatcher.skapArbeidsforholdOversikt
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_I_ARBEID
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_NAR
import no.nav.helse.flex.soknadsopprettelse.ingenArbeidsdagerMellomStartdatoOgEtterStartsyketilfelle
import no.nav.helse.flex.soknadsopprettelse.sporsmal.tilbakeIFulltArbeidSporsmal
import no.nav.helse.flex.testutil.byttSvar
import org.amshove.kluent.`should be false`
import org.amshove.kluent.`should be true`
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SammeSkjaringstidspunktTest {
    // 9. september 2022 er en fredag

    @Test
    fun `Ingen tidligere søknader har samme skjæringstidspunkt`() {
        ingenArbeidsdagerMellomStartdatoOgEtterStartsyketilfelle(
            arbeidsforholdOversikt = arbeidsforholdoversikt(startdato = LocalDate.of(2022, 9, 12)),
            eksisterendeSoknader = emptyList(),
            soknad(
                startSykeforlop = LocalDate.of(2022, 9, 10),
                fom = LocalDate.of(2022, 9, 10),
                tom = LocalDate.of(2022, 9, 15),
            ),
        ).`should be true`()
    }

    @Test
    fun `En tidligere søknad med gap har ikke samme skjæringstidspunkt`() {
        ingenArbeidsdagerMellomStartdatoOgEtterStartsyketilfelle(
            arbeidsforholdOversikt = arbeidsforholdoversikt(startdato = LocalDate.of(2022, 9, 12)),
            eksisterendeSoknader =
                listOf(
                    soknad(
                        fom = LocalDate.of(2022, 9, 5),
                        tom = LocalDate.of(2022, 9, 8),
                    ),
                ),
            soknad(
                startSykeforlop = LocalDate.of(2022, 9, 5),
                fom = LocalDate.of(2022, 9, 10),
                tom = LocalDate.of(2022, 9, 15),
            ),
        ).`should be false`()
    }

    @Test
    fun `En tidligere søknad med gap kun i helg har samme skjæringstidspunkt`() {
        ingenArbeidsdagerMellomStartdatoOgEtterStartsyketilfelle(
            arbeidsforholdOversikt = arbeidsforholdoversikt(startdato = LocalDate.of(2022, 9, 14)),
            eksisterendeSoknader =
                listOf(
                    soknad(
                        fom = LocalDate.of(2022, 9, 5),
                        tom = LocalDate.of(2022, 9, 9),
                    ),
                ),
            soknad(
                startSykeforlop = LocalDate.of(2022, 9, 5),
                fom = LocalDate.of(2022, 9, 13),
                tom = LocalDate.of(2022, 9, 15),
            ),
        ).`should be false`()
    }

    @Test
    fun `En tidligere søknad helt inntil har samme skjæringstidspunkt`() {
        ingenArbeidsdagerMellomStartdatoOgEtterStartsyketilfelle(
            arbeidsforholdOversikt = arbeidsforholdoversikt(startdato = LocalDate.of(2022, 9, 12)),
            eksisterendeSoknader =
                listOf(
                    soknad(
                        fom = LocalDate.of(2022, 9, 5),
                        tom = LocalDate.of(2022, 9, 9),
                    ),
                ),
            soknad(
                startSykeforlop = LocalDate.of(2022, 9, 5),
                fom = LocalDate.of(2022, 9, 10),
                tom = LocalDate.of(2022, 9, 15),
            ),
        ).`should be true`()
    }

    @Test
    fun `En tidligere søknad helt inntil med arbeid gjenolpptatt har ikke samme skjæringstidspunkt`() {
        ingenArbeidsdagerMellomStartdatoOgEtterStartsyketilfelle(
            arbeidsforholdOversikt = arbeidsforholdoversikt(startdato = LocalDate.of(2022, 9, 12)),
            eksisterendeSoknader =
                listOf(
                    soknad(
                        fom = LocalDate.of(2022, 9, 5),
                        tom = LocalDate.of(2022, 9, 9),
                        arbeidGjenopptatt = LocalDate.of(2022, 9, 9),
                    ),
                ),
            soknad(
                startSykeforlop = LocalDate.of(2022, 9, 5),
                fom = LocalDate.of(2022, 9, 10),
                tom = LocalDate.of(2022, 9, 15),
            ),
        ).`should be false`()
    }
}

fun arbeidsforholdoversikt(startdato: LocalDate): ArbeidsforholdOversikt {
    return skapArbeidsforholdOversikt(
        fnr = "1234",
        startdato = startdato,
        arbeidssted = "999333667",
    )
}

fun soknad(
    startSykeforlop: LocalDate? = null,
    arbeidsgiverOrgnummer: String = "333333333",
    fom: LocalDate = LocalDate.of(2022, 9, 15),
    tom: LocalDate = LocalDate.of(2022, 9, 15),
    arbeidGjenopptatt: LocalDate? = null,
): Sykepengesoknad {
    fun sporsmal(): List<Sporsmal> {
        if (arbeidGjenopptatt != null) {
            val sporsmalListe = listOf(tilbakeIFulltArbeidSporsmal(opprettNySoknad()))
            return sporsmalListe
                .byttSvar(TILBAKE_I_ARBEID, listOf(Svar(null, "JA")))
                .byttSvar(
                    TILBAKE_NAR,
                    listOf(
                        Svar(
                            null,
                            arbeidGjenopptatt.toString(),
                        ),
                    ),
                )
        }
        return emptyList()
    }

    return opprettNySoknad().copy(
        startSykeforlop = startSykeforlop,
        arbeidsgiverOrgnummer = arbeidsgiverOrgnummer,
        fom = fom,
        tom = tom,
        sporsmal = sporsmal(),
        status = Soknadstatus.SENDT,
    )
}
