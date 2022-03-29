package no.nav.syfo.soknadsopprettelse.sporsmal

import no.nav.syfo.domain.Sporsmal
import no.nav.syfo.domain.Svartype
import no.nav.syfo.soknadsopprettelse.VAER_KLAR_OVER_AT

fun vaerKlarOverAtBehandlingsdager(): Sporsmal {
    return Sporsmal(
        tag = VAER_KLAR_OVER_AT,
        svartype = Svartype.IKKE_RELEVANT,
        sporsmalstekst = "Viktig å være klar over:",
        undertekst = "<ul>" +
            "<li>Denne søknaden gjelder hvis selve behandlingen har en slik virkning på deg at du ikke kan jobbe resten av dagen. Grunnen er altså behandlingens effekt, og ikke at du for eksempel måtte bruke arbeidstid.</li>" +
            "<li>NAV kan innhente opplysninger som er nødvendige for å behandle søknaden.</li>" +
            "<li>Fristen for å søke sykepenger er som hovedregel 3 måneder</li>" +
            "</ul>" +
            "<p>Du kan lese mer om rettigheter og plikter på <a href=\"https://www.nav.no/sykepenger\" target=\"_blank\">nav.no/sykepenger</a>.</p>"
    )
}
