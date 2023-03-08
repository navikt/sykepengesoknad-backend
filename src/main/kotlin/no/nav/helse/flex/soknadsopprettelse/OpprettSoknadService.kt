package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.aktivering.kafka.AktiveringBestilling
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Merknad
import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykeforloep
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.Sykmeldingstype
import no.nav.helse.flex.domain.exception.SykeforloepManglerSykemeldingException
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.domain.sykmelding.finnSoknadsType
import no.nav.helse.flex.julesoknad.LagreJulesoknadKandidater
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.SlettSoknaderTilKorrigertSykmeldingService
import no.nav.helse.flex.soknadsopprettelse.splitt.delOppISoknadsperioder
import no.nav.helse.flex.soknadsopprettelse.splitt.splittMellomTyper
import no.nav.helse.flex.soknadsopprettelse.splitt.splittSykmeldingiSoknadsPerioder
import no.nav.helse.flex.util.*
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO.AKTIVITET_IKKE_MULIG
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO.AVVENTENDE
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO.BEHANDLINGSDAGER
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO.GRADERT
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO.REISETILSKUDD
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*
import no.nav.syfo.model.Merknad as SmMerknad

@Service
@Transactional
class OpprettSoknadService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val metrikk: Metrikk,
    private val soknadProducer: SoknadProducer,
    private val lagreJulesoknadKandidater: LagreJulesoknadKandidater,
    private val slettSoknaderTilKorrigertSykmeldingService: SlettSoknaderTilKorrigertSykmeldingService
) {
    private val log = logger()

    fun opprettSykepengesoknaderForSykmelding(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        arbeidssituasjon: Arbeidssituasjon,
        identer: FolkeregisterIdenter,
        arbeidsgiverStatusDTO: ArbeidsgiverStatusDTO?,
        flexSyketilfelleSykeforloep: List<Sykeforloep>
    ): List<AktiveringBestilling> {
        val sykmelding = sykmeldingKafkaMessage.sykmelding

        val eksisterendeSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer)
        val eksisterendeSoknaderOgOprettedeSoknader = eksisterendeSoknader.toMutableList()

        val sykeforloep = flexSyketilfelleSykeforloep.firstOrNull {
            it.sykmeldinger.any { sm -> sm.id == sykmelding.id }
        } ?: throw SykeforloepManglerSykemeldingException("Sykeforloep mangler sykmelding ${sykmelding.id}")
        val startSykeforlop = sykeforloep.oppfolgingsdato

        val sykmeldingSplittetMellomTyper = sykmelding.splittMellomTyper()
        val soknaderTilOppretting = ArrayList<Sykepengesoknad>()

        sykmeldingSplittetMellomTyper.forEach { sm ->
            sm.splittSykmeldingiSoknadsPerioder(
                arbeidssituasjon,
                eksisterendeSoknader,
                sm.id,
                sm.behandletTidspunkt.toInstant(),
                arbeidsgiverStatusDTO?.orgnummer
            ).map {
                val perioderFraSykmeldingen = it.delOppISoknadsperioder(sm)
                Sykepengesoknad(
                    id = sykmeldingKafkaMessage.skapSoknadsId(it.fom, it.tom),
                    fnr = identer.originalIdent,
                    startSykeforlop = startSykeforlop,
                    fom = it.fom,
                    tom = it.tom,
                    arbeidssituasjon = arbeidssituasjon,
                    arbeidsgiverOrgnummer = arbeidsgiverStatusDTO?.orgnummer,
                    arbeidsgiverNavn = arbeidsgiverStatusDTO?.orgNavn?.prettyOrgnavn(),
                    sykmeldingId = sm.id,
                    sykmeldingSkrevet = sm.behandletTidspunkt.toInstant(),
                    soknadPerioder = perioderFraSykmeldingen.tilSoknadsperioder(),
                    egenmeldtSykmelding = sm.egenmeldt,
                    merknaderFraSykmelding = sm.merknader.tilMerknader(),
                    soknadstype = finnSoknadsType(arbeidssituasjon, perioderFraSykmeldingen),
                    status = Soknadstatus.FREMTIDIG,
                    opprettet = Instant.now(),
                    sporsmal = emptyList(),
                    utenlandskSykmelding = sykmeldingKafkaMessage.sykmelding.utenlandskSykmelding != null
                )
            }
                .filter { it.soknadPerioder?.isNotEmpty() ?: true }
                .also { it.lagreJulesoknadKandidater() }
                .forEach {
                    eksisterendeSoknaderOgOprettedeSoknader.add(it)
                    soknaderTilOppretting.add(it)
                }
        }

        val eksisterendeSoknaderForSm = eksisterendeSoknader.filter { it.sykmeldingId == sykmelding.id }

        if (eksisterendeSoknaderForSm.isNotEmpty()) {
            data class SoknadSammenlikner(
                val fom: LocalDate?,
                val tom: LocalDate?,
                val sykmeldingId: String?,
                val arbeidssituasjon: Arbeidssituasjon?,
                val soknadstype: Soknadstype,
                val soknadPerioder: List<Soknadsperiode>?,
                val arbeidsgiverOrgnummer: String?
            )

            fun Sykepengesoknad.tilSoknadSammenlikner() =
                SoknadSammenlikner(
                    fom = this.fom,
                    tom = this.tom,
                    sykmeldingId = this.sykmeldingId,
                    arbeidssituasjon = this.arbeidssituasjon,
                    arbeidsgiverOrgnummer = this.arbeidsgiverOrgnummer,
                    soknadstype = this.soknadstype,
                    soknadPerioder = this.soknadPerioder
                )

            val sammenliknbartSettAvNyeSoknader = soknaderTilOppretting.map { it.tilSoknadSammenlikner() }.toHashSet()
            val sammenliknbartSettAvEksisterendeSoknaderForSm = eksisterendeSoknaderForSm
                .filter { it.status != Soknadstatus.KORRIGERT && it.status != Soknadstatus.UTKAST_TIL_KORRIGERING }
                .map { it.tilSoknadSammenlikner() }
                .toHashSet()
            if (sammenliknbartSettAvEksisterendeSoknaderForSm == sammenliknbartSettAvNyeSoknader) {
                log.info("Oppretter ikke søknader for sykmelding ${sykmelding.id} siden eksisterende identiske søknader finnes")
                return emptyList()
            } else {
                slettSoknaderTilKorrigertSykmeldingService.slettSoknader(eksisterendeSoknaderForSm)
            }
        }

        return soknaderTilOppretting
            .map { it.lagreSøknad(eksisterendeSoknader) }
            .mapNotNull { it.publiserEllerReturnerAktiveringBestilling() }
    }

    private fun List<Sykepengesoknad>.lagreJulesoknadKandidater() {
        return lagreJulesoknadKandidater.lagreJulesoknadKandidater(this)
    }

    fun Sykepengesoknad.lagreSøknad(eksisterendeSoknader: List<Sykepengesoknad>): Sykepengesoknad {
        log.info("Oppretter ${this.soknadstype} søknad ${this.id} for sykmelding: ${this.sykmeldingId} med status ${this.status}")

        val lagretSoknad = sykepengesoknadDAO.lagreSykepengesoknad(this)

        metrikk.tellSoknadOpprettet(lagretSoknad.soknadstype)

        return lagretSoknad
    }

    fun Sykepengesoknad.publiserEllerReturnerAktiveringBestilling(): AktiveringBestilling? {
        val skalAktiveres = this.tom!!.isBefore(LocalDate.now(osloZone))
        if (skalAktiveres) {
            return AktiveringBestilling(this.fnr, this.id)
        }
        // publiser fremtidig søknad. denne aktiveres av kronjobb senere
        soknadProducer.soknadEvent(this)

        return null
    }

    fun opprettSoknadUtland(identer: FolkeregisterIdenter): Sykepengesoknad {
        return sykepengesoknadDAO.finnAlleredeOpprettetSoknad(identer)
            ?: opprettNySoknadUtland(identer.originalIdent)
    }

    private fun opprettNySoknadUtland(fnr: String): Sykepengesoknad {
        val oppholdUtlandSoknad = settOppSoknadOppholdUtland(fnr)
        sykepengesoknadDAO.lagreSykepengesoknad(oppholdUtlandSoknad)
        metrikk.tellSoknadOpprettet(Soknadstype.OPPHOLD_UTLAND)

        log.info("Oppretter søknad for utenlandsopphold: {}", oppholdUtlandSoknad.id)
        return sykepengesoknadDAO.finnSykepengesoknad(oppholdUtlandSoknad.id)
    }
}

fun SykmeldingKafkaMessage.skapSoknadsId(fom: LocalDate, tom: LocalDate): String {
    return UUID.nameUUIDFromBytes(
        "${sykmelding.id}$fom$tom${event.timestamp}".toByteArray()
    ).toString()
}

fun List<SykmeldingsperiodeAGDTO>.tilSoknadsperioder(): List<Soknadsperiode> {
    return this.map { it.tilSoknadsperioder() }
}

fun SykmeldingsperiodeAGDTO.tilSoknadsperioder(): Soknadsperiode {
    val gradForAktivitetIkkeMulig = if (type == AKTIVITET_IKKE_MULIG) 100 else null
    return Soknadsperiode(
        fom = fom,
        tom = tom,
        grad = gradert?.grad ?: gradForAktivitetIkkeMulig ?: 0,
        sykmeldingstype = type.tilSykmeldingstype()
    )
}

private fun List<SmMerknad>?.tilMerknader(): List<Merknad>? =
    this?.map { Merknad(type = it.type, beskrivelse = it.beskrivelse) }

fun antallDager(fom: LocalDate, tom: LocalDate): Long {
    return ChronoUnit.DAYS.between(fom, tom) + 1
}

fun eldstePeriodeFOM(perioder: List<SykmeldingsperiodeAGDTO>): LocalDate {
    return perioder.sortedBy { it.fom }.firstOrNull()?.fom ?: throw SykeforloepManglerSykemeldingException()
}

fun hentSenesteTOMFraPerioder(perioder: List<SykmeldingsperiodeAGDTO>): LocalDate {
    return perioder.sortedByDescending { it.fom }.first().tom
}

private fun PeriodetypeDTO.tilSykmeldingstype(): Sykmeldingstype {
    return when (this) {
        AKTIVITET_IKKE_MULIG -> Sykmeldingstype.AKTIVITET_IKKE_MULIG
        AVVENTENDE -> Sykmeldingstype.AVVENTENDE
        BEHANDLINGSDAGER -> Sykmeldingstype.BEHANDLINGSDAGER
        GRADERT -> Sykmeldingstype.GRADERT
        REISETILSKUDD -> Sykmeldingstype.REISETILSKUDD
    }
}
