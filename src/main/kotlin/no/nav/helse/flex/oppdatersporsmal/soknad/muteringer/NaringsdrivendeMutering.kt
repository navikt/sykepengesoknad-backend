package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.oppdatersporsmal.soknad.leggTilSporsmaal
import no.nav.helse.flex.soknadsopprettelse.NARINGSDRIVENDE_NY_I_ARBEIDSLIVET
import no.nav.helse.flex.soknadsopprettelse.NARINGSDRIVENDE_VARIG_ENDRING
import no.nav.helse.flex.soknadsopprettelse.NARINGSDRIVENDE_VIRKSOMHETEN_DIN_AVVIKLET
import no.nav.helse.flex.soknadsopprettelse.sporsmal.lagSporsmalOmNaringsdrivendeNyIArbeidslivet
import no.nav.helse.flex.soknadsopprettelse.sporsmal.lagSporsmalOmNaringsdrivendeVarigEndring

fun Sykepengesoknad.naringsdrivendeMutering(): Sykepengesoknad {
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
            val oppdatertSoknad =
                if (selvstendigNaringsdrivende?.sykepengegrunnlagNaeringsdrivende?.harFunnetInntektFoerSykepengegrunnlaget != true) {
                    this.leggTilSporsmaal(
                        lagSporsmalOmNaringsdrivendeNyIArbeidslivet(
                            soknad = this,
                            sykepengegrunnlagNaeringsdrivende = selvstendigNaringsdrivende?.sykepengegrunnlagNaeringsdrivende,
                        ),
                    )
                } else {
                    this
                }

            oppdatertSoknad
                .leggTilSporsmaal(
                    lagSporsmalOmNaringsdrivendeVarigEndring(
                        soknad = oppdatertSoknad,
                        sykepengegrunnlagNaeringsdrivende = oppdatertSoknad.selvstendigNaringsdrivende?.sykepengegrunnlagNaeringsdrivende,
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
