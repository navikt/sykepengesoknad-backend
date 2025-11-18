package no.nav.helse.flex.arbeidsgiverperiode

import no.nav.helse.flex.arbeidsgiverperiode.ListContainsPredicate.Companion.tagsSize
import no.nav.helse.flex.arbeidsgiverperiode.domain.*
import no.nav.helse.flex.arbeidsgiverperiode.domain.Tag.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

fun genererOppfolgingstilfelle(
    biter: List<Syketilfellebit>,
    andreKorrigerteRessurser: List<String> = emptyList(),
    tilleggsbiter: List<Syketilfellebit> = emptyList(),
    grense: LocalDateTime? = null,
    startSyketilfelle: LocalDate? = null,
): List<Oppfolgingstilfelle>? {
    startSyketilfelle?.let {
        val registertSykemeldingFomForSyketille =
            biter
                .asSequence()
                .filter { it.tags.containsAny(Tag.SYKMELDING, PAPIRSYKMELDING) }
                .map { it.fom }
                .filter { it.isEqualOrAfter(startSyketilfelle) }
                .sorted()
                .firstOrNull()
        registertSykemeldingFomForSyketille?.let {
        }
    }
    return genererOppfolgingstilfelle(
        biter,
        andreKorrigerteRessurser,
        tilleggsbiter,
        grense,
    )
}

fun genererOppfolgingstilfelle(
    biter: List<Syketilfellebit>,
    andreKorrigerteRessurser: List<String> = emptyList(),
    tilleggsbiter: List<Syketilfellebit> = emptyList(),
    grense: LocalDateTime? = null,
): List<Oppfolgingstilfelle>? {
    val korrigerteBiter = biter.finnBiterSomTilleggsbiterVilKorrigere(andreKorrigerteRessurser, tilleggsbiter)

    val merge =
        ArrayList(biter)
            .apply {
                addAll(tilleggsbiter)
                addAll(korrigerteBiter)
            }.filtrerBortKorrigerteBiter()

    if (merge.isEmpty()) {
        return null
    }

    val tidslinje =
        Tidslinje(
            Syketilfellebiter(
                prioriteringsliste =
                    listOf(
                        SYKEPENGESOKNAD and SENDT and ARBEID_GJENNOPPTATT,
                        SYKEPENGESOKNAD and SENDT and KORRIGERT_ARBEIDSTID and BEHANDLINGSDAGER,
                        SYKEPENGESOKNAD and SENDT and KORRIGERT_ARBEIDSTID and FULL_AKTIVITET,
                        SYKEPENGESOKNAD and SENDT and KORRIGERT_ARBEIDSTID and (GRADERT_AKTIVITET or INGEN_AKTIVITET),
                        SYKEPENGESOKNAD and SENDT and (PERMISJON or FERIE),
                        SYKEPENGESOKNAD and SENDT and (EGENMELDING or PAPIRSYKMELDING or FRAVAR_FOR_SYKMELDING),
                        SYKEPENGESOKNAD and SENDT and tagsSize(2),
                        SYKEPENGESOKNAD and SENDT and BEHANDLINGSDAG,
                        SYKEPENGESOKNAD and SENDT and BEHANDLINGSDAGER,
                        SYKMELDING and (SENDT or BEKREFTET) and PERIODE and BEHANDLINGSDAGER,
                        SYKMELDING and (SENDT or BEKREFTET) and PERIODE and FULL_AKTIVITET,
                        SYKMELDING and (SENDT or BEKREFTET) and PERIODE and (GRADERT_AKTIVITET or INGEN_AKTIVITET),
                        SYKMELDING and (SENDT or BEKREFTET) and EGENMELDING,
                        SYKMELDING and BEKREFTET and ANNET_FRAVAR,
                        SYKMELDING and SENDT and PERIODE and REISETILSKUDD and UKJENT_AKTIVITET,
                        SYKMELDING and NY and PERIODE and BEHANDLINGSDAGER,
                        SYKMELDING and NY and PERIODE and FULL_AKTIVITET,
                        SYKMELDING and NY and PERIODE and (GRADERT_AKTIVITET or INGEN_AKTIVITET),
                        SYKMELDING and NY and PERIODE and REISETILSKUDD and UKJENT_AKTIVITET,
                    ),
                biter = merge,
            ),
        )

    return grupperIOppfolgingstilfeller(
        tidslinje
            .tidslinjeSomListe()
            .filterBortBiterEtter(grense),
    )
}

private fun List<Syketilfelledag>.filterBortBiterEtter(grense: LocalDateTime?) =
    if (grense != null) filter { !it.dag.isAfter(grense.toLocalDate()) } else this

fun Set<Tag>.containsAny(vararg tags: Tag): Boolean {
    for (tag in tags) {
        if (this.contains(tag)) {
            return true
        }
    }
    return false
}

fun Set<Tag>.erstattTag(
    fra: Tag,
    til: Tag,
): Set<Tag> =
    ArrayList(this)
        .apply {
            add(this.indexOf(fra), til)
            remove(fra)
        }.toSet()

private fun List<Syketilfellebit>.filtrerBortKorrigerteBiter(): List<Syketilfellebit> =
    filterNot { muligSendtBit ->
        muligSendtBit.tags.toList() in (SYKEPENGESOKNAD and SENDT) &&
            any { muligKorrigertBit ->
                muligKorrigertBit.tags.toList() in (SYKEPENGESOKNAD and KORRIGERT) &&
                    muligKorrigertBit.ressursId == muligSendtBit.ressursId
            }
    }

private fun List<Syketilfellebit>.finnBiterSomTilleggsbiterVilKorrigere(
    andreKorrigerteRessurser: List<String>,
    tilleggsbiter: List<Syketilfellebit>,
): List<Syketilfellebit> =
    filter { it.tags.toList() in (SYKEPENGESOKNAD and SENDT) }
        .filter { it.ressursId in andreKorrigerteRessurser }
        .map { bitSomSkalKorrigeres ->
            val forsteTilleggsbitInntruffet =
                tilleggsbiter
                    .filter { it.tags.toList() in (SYKEPENGESOKNAD and SENDT) }
                    .sortedBy { it.inntruffet }
                    .firstOrNull()
                    ?.inntruffet
                    ?: OffsetDateTime.now()
            Syketilfellebit(
                id = bitSomSkalKorrigeres.id,
                orgnummer = bitSomSkalKorrigeres.orgnummer,
                opprettet = OffsetDateTime.now(),
                inntruffet = forsteTilleggsbitInntruffet,
                tags = bitSomSkalKorrigeres.tags.erstattTag(SENDT, KORRIGERT),
                ressursId = bitSomSkalKorrigeres.ressursId,
                fom = bitSomSkalKorrigeres.fom,
                tom = bitSomSkalKorrigeres.tom,
                fnr = bitSomSkalKorrigeres.fnr,
            )
        }
