package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.oppdatersporsmal.soknad.leggTilSporsmaal
import no.nav.helse.flex.soknadsopprettelse.NARINGSDRIVENDE_NY_I_ARBEIDSLIVET
import no.nav.helse.flex.soknadsopprettelse.NARINGSDRIVENDE_VARIG_ENDRING
import no.nav.helse.flex.soknadsopprettelse.NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET
import no.nav.helse.flex.soknadsopprettelse.sporsmal.lagSporsmalOmNaringsdrivendeNyIArbeidslivet
import no.nav.helse.flex.soknadsopprettelse.sporsmal.lagSporsmalOmNaringsdrivendeVarigEndring

fun Sykepengesoknad.naringsdrivendeMutering(): Sykepengesoknad {
    val virksomhetenAvvikletSvar = getSporsmalMedTagOrNull(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET)?.forsteSvar
    val nyIArbeidslivetSvar = getSporsmalMedTagOrNull(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET)?.forsteSvar

    return when {
        virksomhetenAvvikletSvar == "JA" -> {
            this.fjernSporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET).fjernSporsmal(NARINGSDRIVENDE_VARIG_ENDRING)
        }

        nyIArbeidslivetSvar == "JA" -> {
            this.fjernSporsmal(NARINGSDRIVENDE_VARIG_ENDRING)
        }

        virksomhetenAvvikletSvar == "NEI" -> {
            val oppdatertSoknad =
                if (harFunnetInntektFoerSykepengegrunnlaget()) {
                    this.leggTilSporsmaal(
                        lagSporsmalOmNaringsdrivendeNyIArbeidslivet(
                            fom = fom!!,
                            startSykeforlop = startSykeforlop,
                            sykepengegrunnlagNaeringsdrivende = selvstendigNaringsdrivende?.sykepengegrunnlagNaeringsdrivende,
                        ),
                    )
                } else {
                    this
                }

            oppdatertSoknad
                .leggTilSporsmaal(
                    lagSporsmalOmNaringsdrivendeVarigEndring(
                        fom = oppdatertSoknad.fom!!,
                        startSykeforlop = oppdatertSoknad.startSykeforlop,
                        sykepengegrunnlagNaeringsdrivende = oppdatertSoknad.selvstendigNaringsdrivende?.sykepengegrunnlagNaeringsdrivende,
                    ),
                )
        }

        nyIArbeidslivetSvar == "NEI" -> {
            this.leggTilSporsmaal(
                lagSporsmalOmNaringsdrivendeVarigEndring(
                    fom = fom!!,
                    startSykeforlop = startSykeforlop,
                    sykepengegrunnlagNaeringsdrivende = selvstendigNaringsdrivende?.sykepengegrunnlagNaeringsdrivende,
                ),
            )
        }

        else -> {
            this
        }
    }
}

private fun Sykepengesoknad.harFunnetInntektFoerSykepengegrunnlaget() =
    selvstendigNaringsdrivende?.sykepengegrunnlagNaeringsdrivende?.harFunnetInntektFoerSykepengegrunnlaget != true
