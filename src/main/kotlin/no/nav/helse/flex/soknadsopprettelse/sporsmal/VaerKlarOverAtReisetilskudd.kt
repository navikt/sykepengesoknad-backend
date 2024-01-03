@file:Suppress("ktlint:standard:max-line-length")

package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.soknadsopprettelse.VAER_KLAR_OVER_AT

fun vaerKlarOverAtReisetilskudd(): Sporsmal {
    return Sporsmal(
        tag = VAER_KLAR_OVER_AT,
        svartype = Svartype.IKKE_RELEVANT,
        sporsmalstekst = "Viktig å være klar over:",
        undertekst =
            """
            <ul>
            <li>Retten til reisetilskudd gjelder bare hvis du trenger midlertidig transport til og fra arbeidsstedet på grunn av helseplager.</li>
            <li>Du kan få reisetilskudd hvis du i utgangspunktet har rett til sykepenger.</li>
            <li>NAV kan innhente flere opplysninger som er nødvendige for å behandle søknaden.</li>
            <li>NAV kan holde igjen eller kreve tilbake penger hvis du gir uriktige eller ufullstendige opplysninger.</li>
            <li>Det å gi feil opplysninger kan være straffbart.</li>
            <li>Fristen for å søke reisetilskudd er som hovedregel 3 måneder.</li>
            <li>Du kan endre svarene i denne søknaden opp til 12 måneder etter du sendte den inn første gangen.</li>
            <li>Du kan lese mer om rettigheter og plikter på <a href=\\"https://www.nav.no/reisetilskudd\\" target=\\"_blank\\">nav.no/reisetilskudd</a>.</li>
            </ul>
            """,
    )
}
