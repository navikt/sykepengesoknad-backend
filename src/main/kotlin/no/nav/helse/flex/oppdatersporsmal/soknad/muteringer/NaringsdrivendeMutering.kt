package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.oppdatersporsmal.soknad.erIkkeAvType
import no.nav.helse.flex.oppdatersporsmal.soknad.leggTilSporsmaal
import no.nav.helse.flex.soknadsopprettelse.NARINGSDRIVENDE_NY_I_ARBEIDSLIVET
import no.nav.helse.flex.soknadsopprettelse.NARINGSDRIVENDE_VARIG_ENDRING
import no.nav.helse.flex.soknadsopprettelse.NARINGSDRIVENDE_VIRKSOMHETEN_DIN_AVVIKLET
import no.nav.helse.flex.soknadsopprettelse.sporsmal.lagSporsmalOmNaringsdrivendeNyIArbeidslivet
import no.nav.helse.flex.soknadsopprettelse.sporsmal.lagSporsmalOmNaringsdrivendeVarigEndring

fun Sykepengesoknad.naringsdrivendeMutering(): Sykepengesoknad {
    if (erIkkeAvType(Soknadstype.SELVSTENDIGE_OG_FRILANSERE)) {
        return this
    }

    val virksomhetenDinAvvikletSvar = getSporsmalMedTagOrNull(NARINGSDRIVENDE_VIRKSOMHETEN_DIN_AVVIKLET)?.forsteSvar
    val nyIArbeidslivetSvar = getSporsmalMedTagOrNull(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET)?.forsteSvar

    return when {
        virksomhetenDinAvvikletSvar == "JA" -> {
            this.fjernSporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET).fjernSporsmal(NARINGSDRIVENDE_VARIG_ENDRING)
        }
        nyIArbeidslivetSvar == "JA" -> {
            this.fjernSporsmal(NARINGSDRIVENDE_VARIG_ENDRING)
        }
        virksomhetenDinAvvikletSvar == "NEI" -> {
            this
                .leggTilSporsmaal(
                    lagSporsmalOmNaringsdrivendeNyIArbeidslivet(
                        soknad = this,
                        sykepengegrunnlagNaeringsdrivende = selvstendigNaringsdrivende?.sykepengegrunnlagNaeringsdrivende,
                    ),
                ).leggTilSporsmaal(
                    lagSporsmalOmNaringsdrivendeVarigEndring(
                        soknad = this,
                        sykepengegrunnlagNaeringsdrivende = selvstendigNaringsdrivende?.sykepengegrunnlagNaeringsdrivende,
                    ),
                )
        }
        nyIArbeidslivetSvar == "NEI" -> {
            this.leggTilSporsmaal(
                lagSporsmalOmNaringsdrivendeVarigEndring(
                    soknad = this,
                    sykepengegrunnlagNaeringsdrivende = selvstendigNaringsdrivende?.sykepengegrunnlagNaeringsdrivende,
                ),
            )
        }
        else -> {
            this
        }
    }
}
