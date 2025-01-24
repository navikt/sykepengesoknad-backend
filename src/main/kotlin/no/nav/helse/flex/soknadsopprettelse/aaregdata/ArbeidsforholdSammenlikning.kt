package no.nav.helse.flex.soknadsopprettelse.aaregdata

import no.nav.helse.flex.client.aareg.*
import no.nav.helse.flex.soknadsopprettelse.yearMonth
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

fun Arbeidsforhold.erIkkeVidereforingAvAnnetArbeidsforhold(alleArbeidsforhold: List<Arbeidsforhold>): Boolean {
    return !alleArbeidsforhold
        .filter { it !== this }
        .any { this.erMestSannsynligEllerKanskjeVidereføringAv(it) }
}

fun Arbeidsforhold.ansattFom(): LocalDate = this.ansettelsesperiode.startdato

fun Arbeidsforhold.ansattTomEllerTidensEnde(): LocalDate = this.ansettelsesperiode.sluttdato ?: LocalDate.MAX

internal fun Pair<Arbeidsforhold, Arbeidsforhold>.erGanskeRettEtterHverandre(): Boolean =
    first.ansattTomEllerTidensEnde().until(second.ansattFom(), ChronoUnit.DAYS).absoluteValue <= 4

internal fun Arbeidsforhold.erMestSannsynligEllerKanskjeVidereføringAv(forrige: Arbeidsforhold): Boolean =
    erMestSannsynligVidereføringAv(forrige) || kanKanskjeVæreVidereføringAv(forrige)

internal fun Arbeidsforhold.erMestSannsynligVidereføringAv(forrige: Arbeidsforhold): Boolean {
    if (this.opplysningspliktig.sammenliknbar() != forrige.opplysningspliktig.sammenliknbar()) return false
    if ((forrige to this).erGanskeRettEtterHverandre() && forrige.ansettelsesperiode.sluttaarsak != null && (
            listOf(
                "endringIOrganisasjonsstrukturEllerByttetJobbInternt",
                "kontraktEngasjementEllerVikariatErUtloept",
            ).contains(forrige.ansettelsesperiode.sluttaarsak.kode)
        ) ||
        (
            (this.arbeidssted.sammenliknbar() == forrige.arbeidssted.sammenliknbar()) && forrige.ansettelsesperiode.sluttaarsak != null &&
                listOf(
                    "byttetLoenssystemEllerRegnskapsfoerer",
                ).contains(forrige.ansettelsesperiode.sluttaarsak.kode)
        )
    ) {
        return true
    }
    return false
}

data class Sammenlikbar(
    val type: String,
    val identer: Set<Ident>,
)

private fun Opplysningspliktig.sammenliknbar(): Sammenlikbar {
    return Sammenlikbar(this.type, this.identer.toSet())
}

private fun Arbeidssted.sammenliknbar(): Sammenlikbar {
    return Sammenlikbar(this.type, this.identer.toSet())
}

fun List<Ansettelsesdetaljer>.sortertKronologiskPåGyldighet(): List<Ansettelsesdetaljer> =
    this.sortedWith { a, b ->
        a.rapporteringsmaaneder.fra.compareTo(b.rapporteringsmaaneder.fra).let { gyldigFom ->
            if (gyldigFom == 0) {
                (a.rapporteringsmaaneder.til ?: LocalDate.MAX.yearMonth()).compareTo(
                    b.rapporteringsmaaneder.til ?: LocalDate.MAX.yearMonth(),
                )
            } else {
                gyldigFom
            }
        }
    }

fun Ansettelsesdetaljer.erGanskeLik(annen: Ansettelsesdetaljer): Boolean =
    (this.yrke == annen.yrke) && (this.type == annen.type) && (this.avtaltStillingsprosent?.equals(annen.avtaltStillingsprosent) == true)

internal fun Arbeidsforhold.kanKanskjeVæreVidereføringAv(forrige: Arbeidsforhold): Boolean {
    if (this.ansettelsesdetaljer.isEmpty() || forrige.ansettelsesdetaljer.isEmpty()) return false
    if ((forrige to this).erGanskeRettEtterHverandre() && forrige.ansettelsesperiode.sluttaarsak != null &&
        (forrige.ansettelsesperiode.sluttaarsak.kode == "endringIOrganisasjonsstrukturEllerByttetJobbInternt") &&
        (
            forrige.ansettelsesdetaljer.sortertKronologiskPåGyldighet().last()
                .erGanskeLik(this.ansettelsesdetaljer.sortertKronologiskPåGyldighet().first())
        )
    ) {
        return true
    }
    return false
}
