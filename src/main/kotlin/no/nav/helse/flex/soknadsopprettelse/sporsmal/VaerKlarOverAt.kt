package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.soknadsopprettelse.VAER_KLAR_OVER_AT

fun vaerKlarOverAt(gradertReisetilskudd: Boolean): Sporsmal {

    val sporsmalstekst = if (gradertReisetilskudd) {
        "<ul>" +
            "<li>Du kan bare få sykepenger og reisetilskudd hvis det er din egen sykdom eller skade som hindrer deg i å jobbe. Sosiale eller økonomiske problemer gir ikke rett til sykepenger eller reisetilskudd.</li>" +
            "<li>Du kan miste retten til sykepenger og reisetilskudd hvis du nekter å opplyse om din egen arbeidsevne, eller hvis du ikke tar imot behandling eller tilrettelegging.</li>" +
            "<li>Retten til sykepenger og reisetilskudd gjelder bare inntekt du har mottatt som lønn og betalt skatt av på sykmeldingstidspunktet.</li>" +
            "<li>NAV kan innhente opplysninger som er nødvendige for å behandle søknaden.</li>" +
            "<li>Du må melde fra til NAV hvis du satt i varetekt, sonet straff eller var under forvaring i sykmeldingsperioden.</li>" +
            "<li>Fristen for å søke sykepenger er som hovedregel 3 måneder</li>" +
            "</ul>" +
            "<p>Du kan lese mer om rettigheter og plikter på <a href=\"https://www.nav.no/sykepenger\" target=\"_blank\">nav.no/sykepenger</a> og <a href=\"https://www.nav.no/reisetilskudd\" target=\"_blank\">nav.no/reisetilskudd</a>.</p>"
    } else {
        "<ul>" +
            "<li>Du kan bare få sykepenger hvis det er din egen sykdom eller skade som hindrer deg i å jobbe. Sosiale eller økonomiske problemer gir ikke rett til sykepenger.</li>" +
            "<li>Du kan miste retten til sykepenger hvis du nekter å opplyse om din egen arbeidsevne, eller hvis du ikke tar imot behandling eller tilrettelegging.</li>" +
            "<li>Retten til sykepenger gjelder bare inntekt du har mottatt som lønn og betalt skatt av på sykmeldingstidspunktet.</li>" +
            "<li>NAV kan innhente opplysninger som er nødvendige for å behandle søknaden.</li>" +
            "<li>Du må melde fra til NAV hvis du satt i varetekt, sonet straff eller var under forvaring i sykmeldingsperioden.</li>" +
            "<li>Fristen for å søke sykepenger er som hovedregel 3 måneder</li>" +
            "</ul>" +
            "<p>Du kan lese mer om rettigheter og plikter på <a href=\"https://www.nav.no/sykepenger\" target=\"_blank\">nav.no/sykepenger</a>.</p>"
    }

    return Sporsmal(
        tag = VAER_KLAR_OVER_AT,
        svartype = Svartype.IKKE_RELEVANT,
        sporsmalstekst = "Viktig å være klar over:",
        undertekst = sporsmalstekst
    )
}
