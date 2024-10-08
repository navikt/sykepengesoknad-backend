package no.nav.helse.flex.nyttarbeidsforhold

import no.nav.helse.flex.client.aareg.ArbeidsforholdOversikt
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.mock.opprettNySoknad
import no.nav.helse.flex.mockdispatcher.skapArbeidsforholdOversikt
import no.nav.helse.flex.soknadsopprettelse.ingenArbeidsdagerMellomStartdatoOgEtterStartsyketilfelle
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
                tom = LocalDate.of(2022, 9, 15)
            ),
        ).`should be true`()
    }

    @Test
    fun `En tidligere søknad med gap har ikke samme skjæringstidspunkt`() {
        ingenArbeidsdagerMellomStartdatoOgEtterStartsyketilfelle(
            arbeidsforholdOversikt = arbeidsforholdoversikt(startdato = LocalDate.of(2022, 9, 12)),
            eksisterendeSoknader = listOf(
                soknad(
                    fom = LocalDate.of(2022, 9, 5),
                    tom = LocalDate.of(2022, 9, 8)
                )
            ),
            soknad(
                startSykeforlop = LocalDate.of(2022, 9, 5),
                fom = LocalDate.of(2022, 9, 10),
                tom = LocalDate.of(2022, 9, 15)
            ),
        ).`should be false`()
    }

    @Test
    fun `en tidligere søknad med gap til startdato har samme skjæringstidspunkt`() {
        ingenArbeidsdagerMellomStartdatoOgEtterStartsyketilfelle(
            arbeidsforholdOversikt = arbeidsforholdoversikt(startdato = LocalDate.of(2022, 9, 15)),
            eksisterendeSoknader = emptyList(),
            soknad(startSykeforlop = LocalDate.of(2022, 9, 15)),
        ).`should be true`()
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

    ): Sykepengesoknad {
    return opprettNySoknad().copy(
        startSykeforlop = startSykeforlop,
        arbeidsgiverOrgnummer = arbeidsgiverOrgnummer,
        fom = fom,
        tom = tom,
        status = Soknadstatus.SENDT,
    )
}
