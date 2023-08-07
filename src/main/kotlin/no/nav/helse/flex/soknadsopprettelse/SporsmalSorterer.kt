package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad

fun Sykepengesoknad.sorterSporsmal(): Sykepengesoknad {
    val soknad = this

    fun Sporsmal.plasseringSporsmal(): Int {
        return when (soknad.soknadstype) {
            Soknadstype.SELVSTENDIGE_OG_FRILANSERE -> fellesPlasseringSporsmal()
            Soknadstype.ARBEIDSTAKERE -> fellesPlasseringSporsmal()
            Soknadstype.ARBEIDSLEDIG -> fellesPlasseringSporsmal()
            Soknadstype.ANNET_ARBEIDSFORHOLD -> fellesPlasseringSporsmal()
            Soknadstype.BEHANDLINGSDAGER -> plasseringSporsmalBehandlingsdager()
            Soknadstype.OPPHOLD_UTLAND -> plasseringSporsmalUtland()
            Soknadstype.REISETILSKUDD -> fellesPlasseringSporsmal()
            Soknadstype.GRADERT_REISETILSKUDD -> fellesPlasseringSporsmal()
        }
    }

    return this.copy(sporsmal = this.sporsmal.sortedBy { it.plasseringSporsmal() }).sorterUndersporsmal()
}

fun Sporsmal.fellesPlasseringSporsmal(): Int {
    return when (tag) {
        ANSVARSERKLARING -> -1000
        UTENLANDSK_SYKMELDING_BOSTED -> -900
        UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE -> -899
        UTENLANDSK_SYKMELDING_TRYGD_UTENFOR_NORGE -> -898
        FRISKMELDT -> -10

        EGENMELDINGER -> -9
        FRAVAR_FOR_SYKMELDINGEN -> -9
        TILBAKE_I_ARBEID -> -8
        FERIE_V2 -> -7
        PERMISJON_V2 -> -6
        UTLAND_V2 -> -5
        UTLANDSOPPHOLD_SOKT_SYKEPENGER -> -4

        ARBEID_UTENFOR_NORGE -> 99

        FERIE_PERMISJON_UTLAND -> 100
        ANDRE_INNTEKTSKILDER -> 104
        ANDRE_INNTEKTSKILDER_V2 -> 104
        UTLAND -> 105
        UTDANNING -> 106

        ARBEIDSLEDIG_UTLAND -> 107

        PERMITTERT_NAA -> 797
        PERMITTERT_PERIODE -> 798
        YRKESSKADE -> 799

        BRUKTE_REISETILSKUDDET -> 800
        TRANSPORT_TIL_DAGLIG -> 801
        REISE_MED_BIL -> 802
        KVITTERINGER -> 803
        UTBETALING -> 804

        MEDLEMSKAP_OPPHOLDSTILLATELSE -> 850
        MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE -> 851
        // Det vil ikke bli spurt om disse to i samme søknad.
        MEDLEMSKAP_OPPHOLD_UTENFOR_EOS -> 852
        MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE -> 852

        VAER_KLAR_OVER_AT -> 999
        BEKREFT_OPPLYSNINGER -> 1000
        else -> plasseringAvSporsmalSomKanRepeteresFlereGanger()
    }
}

private fun Sporsmal.plasseringAvSporsmalSomKanRepeteresFlereGanger(): Int {
    return if (tag.startsWith(JOBBET_DU_GRADERT)) {
        Integer.parseInt(tag.replace(JOBBET_DU_GRADERT, ""))
    } else if (tag.startsWith(JOBBET_DU_100_PROSENT)) {
        Integer.parseInt(tag.replace(JOBBET_DU_100_PROSENT, ""))
    } else if (tag.startsWith(ARBEID_UNDERVEIS_100_PROSENT)) {
        Integer.parseInt(tag.replace(ARBEID_UNDERVEIS_100_PROSENT, ""))
    } else {
        0
    }
}
