package no.nav.helse.flex.arbeidsgiverperiode

import no.nav.helse.flex.arbeidsgiverperiode.domain.Syketilfellebit
import no.nav.helse.flex.arbeidsgiverperiode.domain.Tag
import no.nav.helse.flex.sykepengesoknad.kafka.*
import java.time.LocalDateTime
import java.time.OffsetDateTime

private fun SykepengesoknadDTO.inntruffet(): OffsetDateTime =
    (
        when {
            sendtArbeidsgiver == null && sendtNav == null -> LocalDateTime.now()
            sendtArbeidsgiver == null -> sendtNav
            sendtNav == null -> sendtArbeidsgiver
            sendtNav!! > sendtArbeidsgiver -> sendtArbeidsgiver
            else -> sendtNav
        }
    )!!.tilOsloZone()

private fun SykepengesoknadDTO.statustag() =
    if (status == SoknadsstatusDTO.SENDT || status == SoknadsstatusDTO.NY || status == SoknadsstatusDTO.FREMTIDIG) {
        Tag.SENDT
    } else {
        Tag.KORRIGERT
    }

fun SykepengesoknadDTO.mapSoknadTilBiter(): List<Syketilfellebit> {
    val opprettet = OffsetDateTime.now().tilOsloZone()
    val tags =
        mutableListOf(Tag.SYKEPENGESOKNAD, statustag())
            .also {
                if (type == SoknadstypeDTO.BEHANDLINGSDAGER) {
                    it.add(Tag.BEHANDLINGSDAGER)
                }
            }.toSet()
    val list =
        mutableListOf(
            Syketilfellebit(
                orgnummer = arbeidsgiver?.orgnummer,
                opprettet = opprettet,
                inntruffet = inntruffet(),
                tags = tags,
                ressursId = id,
                fom = fom!!,
                tom = tom!!,
                fnr = fnr,
                korrigererSendtSoknad = korrigerer,
            ),
        )

    fravar(opprettet)?.let { list += it }
    arbeidGjenopptatt(opprettet)?.let { list += it }
    korrigertArbeidstid(opprettet)?.let { list += it }
    egenmelding(opprettet)?.let { list += it }
    fravarForSykmelding(opprettet)?.let { list += it }
    papirsykmelding(opprettet)?.let { list += it }
    behandingsdagBiter(opprettet)?.let { list += it }
    egenmeldingsdagerFraSykmelding(opprettet)?.let { list += it }

    return list
}

private fun SykepengesoknadDTO.fravar(opprettet: OffsetDateTime) =
    fravar
        ?.map {
            Syketilfellebit(
                orgnummer = arbeidsgiver?.orgnummer,
                opprettet = opprettet,
                inntruffet = inntruffet(),
                tags =
                    when (it.type) {
                        FravarstypeDTO.PERMISJON -> listOf(Tag.SYKEPENGESOKNAD, statustag(), Tag.PERMISJON)
                        FravarstypeDTO.UTLANDSOPPHOLD -> listOf(Tag.SYKEPENGESOKNAD, statustag(), Tag.OPPHOLD_UTENFOR_NORGE)
                        FravarstypeDTO.UTDANNING_DELTID ->
                            listOf(
                                Tag.SYKEPENGESOKNAD,
                                statustag(),
                                Tag.UTDANNING,
                                Tag.DELTID,
                            )

                        FravarstypeDTO.UTDANNING_FULLTID ->
                            listOf(
                                Tag.SYKEPENGESOKNAD,
                                statustag(),
                                Tag.UTDANNING,
                                Tag.FULLTID,
                            )

                        else -> listOf(Tag.SYKEPENGESOKNAD, statustag(), Tag.FERIE)
                    }.toSet(),
                ressursId = id,
                fom = it.fom!!,
                tom = (it.tom ?: tom)!!,
                fnr = fnr,
            )
        }

private fun SykepengesoknadDTO.arbeidGjenopptatt(opprettet: OffsetDateTime) =
    arbeidGjenopptatt
        ?.let {
            Syketilfellebit(
                orgnummer = arbeidsgiver?.orgnummer,
                opprettet = opprettet,
                inntruffet = inntruffet(),
                tags = setOf(Tag.SYKEPENGESOKNAD, statustag(), Tag.ARBEID_GJENNOPPTATT),
                ressursId = id,
                fom = it,
                tom = tom!!,
                fnr = fnr,
            )
        }

private fun SykepengesoknadDTO.egenmeldingsdagerFraSykmelding(opprettet: OffsetDateTime) =
    egenmeldingsdagerFraSykmelding
        ?.map {
            Syketilfellebit(
                orgnummer = arbeidsgiver?.orgnummer,
                opprettet = opprettet,
                inntruffet = inntruffet(),
                tags = setOf(Tag.SYKMELDING, statustag(), Tag.EGENMELDING),
                ressursId = id,
                fom = it,
                tom = it,
                fnr = fnr,
            )
        }

private fun SykepengesoknadDTO.korrigertArbeidstid(opprettet: OffsetDateTime) =
    soknadsperioder
        ?.filter { it.faktiskGrad != null }
        ?.map {
            Syketilfellebit(
                orgnummer = arbeidsgiver?.orgnummer,
                opprettet = opprettet,
                inntruffet = inntruffet(),
                tags = it.tagsForKorrigertArbeidstid(statustag()),
                ressursId = id,
                fom = it.fom!!,
                tom = it.tom!!,
                fnr = fnr,
            )
        }

private fun SykepengesoknadDTO.behandingsdagBiter(opprettet: OffsetDateTime) =
    behandlingsdager
        ?.map {
            Syketilfellebit(
                orgnummer = arbeidsgiver?.orgnummer,
                opprettet = opprettet,
                inntruffet = inntruffet(),
                tags = setOf(Tag.SYKEPENGESOKNAD, statustag(), Tag.BEHANDLINGSDAG),
                ressursId = id,
                fom = it,
                tom = it,
                fnr = fnr,
            )
        }

private fun SoknadsperiodeDTO.tagsForKorrigertArbeidstid(statustag: Tag): Set<Tag> =
    when {
        sykmeldingstype == SykmeldingstypeDTO.BEHANDLINGSDAGER ->
            listOf(
                Tag.SYKEPENGESOKNAD,
                statustag,
                Tag.KORRIGERT_ARBEIDSTID,
                Tag.BEHANDLINGSDAGER,
            )

        faktiskGrad!! <= 0 -> listOf(Tag.SYKEPENGESOKNAD, statustag, Tag.KORRIGERT_ARBEIDSTID, Tag.INGEN_AKTIVITET)
        faktiskGrad!! in 1..99 ->
            listOf(
                Tag.SYKEPENGESOKNAD,
                statustag,
                Tag.KORRIGERT_ARBEIDSTID,
                Tag.GRADERT_AKTIVITET,
            )
        // Faktisk grad er 100 %, som tilsvarer fullt arbeid.
        else -> listOf(Tag.SYKEPENGESOKNAD, statustag, Tag.KORRIGERT_ARBEIDSTID, Tag.FULL_AKTIVITET)
    }.toSet()

private fun SykepengesoknadDTO.egenmelding(opprettet: OffsetDateTime) =
    egenmeldinger
        ?.map {
            Syketilfellebit(
                orgnummer = arbeidsgiver?.orgnummer,
                opprettet = opprettet,
                inntruffet = inntruffet(),
                tags = setOf(Tag.SYKEPENGESOKNAD, statustag(), Tag.EGENMELDING),
                ressursId = id,
                fom = it.fom!!,
                tom = it.tom!!,
                fnr = fnr,
            )
        }

private fun SykepengesoknadDTO.fravarForSykmelding(opprettet: OffsetDateTime) =
    fravarForSykmeldingen
        ?.map {
            Syketilfellebit(
                orgnummer = arbeidsgiver?.orgnummer,
                opprettet = opprettet,
                inntruffet = inntruffet(),
                tags = setOf(Tag.SYKEPENGESOKNAD, statustag(), Tag.FRAVAR_FOR_SYKMELDING),
                ressursId = id,
                fom = it.fom!!,
                tom = it.tom!!,
                fnr = fnr,
            )
        }

private fun SykepengesoknadDTO.papirsykmelding(opprettet: OffsetDateTime) =
    papirsykmeldinger
        ?.map {
            Syketilfellebit(
                orgnummer = arbeidsgiver?.orgnummer,
                opprettet = opprettet,
                inntruffet = inntruffet(),
                tags = setOf(Tag.SYKEPENGESOKNAD, statustag(), Tag.PAPIRSYKMELDING),
                ressursId = id,
                fom = it.fom!!,
                tom = it.tom!!,
                fnr = fnr,
            )
        }
