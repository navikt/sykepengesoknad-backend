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
import no.nav.helse.flex.domain.sykmelding.finnSoknadstype
import no.nav.helse.flex.julesoknad.LagreJulesoknadKandidater
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.SlettSoknaderTilKorrigertSykmeldingService
import no.nav.helse.flex.util.Metrikk
import no.nav.helse.flex.util.max
import no.nav.helse.flex.util.min
import no.nav.helse.flex.util.osloZone
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.ceil
import kotlin.math.floor
import no.nav.syfo.model.Merknad as SmMerknad

@Service
@Transactional
class OpprettSoknadService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val metrikk: Metrikk,
    private val soknadProducer: SoknadProducer,
    private val lagreJulesoknadKandidater: LagreJulesoknadKandidater,
    private val slettSoknaderTilKorrigertSykmeldingService: SlettSoknaderTilKorrigertSykmeldingService,
) {
    private val log = logger()

    fun opprettSykepengesoknaderForSykmelding(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        arbeidssituasjon: Arbeidssituasjon,
        identer: FolkeregisterIdenter,
        arbeidsgiverStatusDTO: ArbeidsgiverStatusDTO?,
        flexSyketilfelleSykeforloep: List<Sykeforloep>,
    ): List<AktiveringBestilling> {
        val sykmelding = sykmeldingKafkaMessage.sykmelding

        val eksisterendeSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer)
        val eksisterendeSoknaderOgOprettedeSoknader = eksisterendeSoknader.toMutableList()

        val sykeforloep = flexSyketilfelleSykeforloep
            .firstOrNull { it.sykmeldinger.any { sm -> sm.id == sykmelding.id } }
            ?: throw SykeforloepManglerSykemeldingException("Sykeforloep mangler sykmelding ${sykmelding.id}")
        val startSykeforlop = sykeforloep.oppfolgingsdato

        val sykmeldingSplittetMellomTyper = sykmelding.splittMellomTyper()
        val soknaderTilOppretting = ArrayList<Sykepengesoknad>()

        sykmeldingSplittetMellomTyper.forEach { sm ->
            val soknadsPerioder = sm.splittLangeSykmeldingperioder()
            soknadsPerioder.map {
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
                    soknadstype = finnSoknadstype(arbeidssituasjon, perioderFraSykmeldingen),
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
                val arbeidsgiverOrgnummer: String?,
            )

            fun Sykepengesoknad.tilSoknadSammenlikner() =
                SoknadSammenlikner(
                    fom = this.fom,
                    tom = this.tom,
                    sykmeldingId = this.sykmeldingId,
                    arbeidssituasjon = this.arbeidssituasjon,
                    arbeidsgiverOrgnummer = this.arbeidsgiverOrgnummer,
                    soknadstype = this.soknadstype,
                    soknadPerioder = this.soknadPerioder,
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

    private fun Tidsenhet.delOppISoknadsperioder(sykmeldingDokument: ArbeidsgiverSykmelding): List<SykmeldingsperiodeAGDTO> {
        return sykmeldingDokument
            .sykmeldingsperioder
            .filter { periode -> periodeTrefferInnenforTidsenhet(periode, this) }
            .map {
                it.copy(
                    fom = max(it.fom, this.fom),
                    tom = min(it.tom, this.tom)
                )
            }
            .sortedBy { it.fom }
    }

    private fun periodeTrefferInnenforTidsenhet(periode: SykmeldingsperiodeAGDTO, tidsenhet: Tidsenhet): Boolean {
        return !periode.fom.isAfter(tidsenhet.tom) && !periode.tom.isBefore(tidsenhet.fom)
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

internal class Tidsenhet(
    val fom: LocalDate,
    val tom: LocalDate
)

private fun ArbeidsgiverSykmelding.harBehandlingsdager(): Boolean {
    return this.sykmeldingsperioder.any { it.type == BEHANDLINGSDAGER }
}

private fun Pair<SykmeldingsperiodeAGDTO, SykmeldingsperiodeAGDTO>.erKompatible(): Boolean {
    fun SykmeldingsperiodeAGDTO.erAktivitetIkkeMulig(): Boolean {
        return this.type == AKTIVITET_IKKE_MULIG
    }

    fun SykmeldingsperiodeAGDTO.erGradertUtenReisetilskudd(): Boolean {
        val gradertRt = this.gradert?.reisetilskudd ?: false
        return this.type == GRADERT && !this.reisetilskudd && !gradertRt
    }

    fun SykmeldingsperiodeAGDTO.erGradertEller100Prosent(): Boolean =
        this.erAktivitetIkkeMulig() || erGradertUtenReisetilskudd()

    return first.erGradertEller100Prosent() && second.erGradertEller100Prosent()
}

private fun ArbeidsgiverSykmelding.splittMellomTyper(): List<ArbeidsgiverSykmelding> {
    val ret = ArrayList<ArbeidsgiverSykmelding>()
    var behandles: ArbeidsgiverSykmelding? = null

    this.sykmeldingsperioder.sortedBy { it.fom }.forEach {
        if (behandles == null) {
            behandles = this.copy(sykmeldingsperioder = listOf(it))
            return@forEach
        }
        fun ArbeidsgiverSykmelding.erKompatibel(sykmeldingsperiodeDTO: SykmeldingsperiodeAGDTO): Boolean {
            return Pair(sykmeldingsperiodeDTO, this.sykmeldingsperioder.last()).erKompatible()
        }
        behandles = if (behandles!!.erKompatibel(it)) {
            behandles!!.copy(sykmeldingsperioder = listOf(*behandles!!.sykmeldingsperioder.toTypedArray(), it))
        } else {
            ret.add(behandles!!)
            this.copy(sykmeldingsperioder = listOf(it))
        }
    }
    if (behandles != null) {
        ret.add(behandles!!)
    }

    return ret
}

private fun ArbeidsgiverSykmelding.splittLangeSykmeldingperioderMedBehandlingsdager(): List<Tidsenhet> {
    val liste = ArrayList<Tidsenhet>()

    for (periode in this.sykmeldingsperioder.sortedBy { it.fom }) {
        liste.addAll(splittPeriodeBasertPaaUke(periode))
    }

    return liste
}

fun antallDager(fom: LocalDate, tom: LocalDate): Long {
    return ChronoUnit.DAYS.between(fom, tom) + 1
}

private fun splittPeriodeBasertPaaUke(periode: SykmeldingsperiodeAGDTO): List<Tidsenhet> {
    val liste = ArrayList<Tidsenhet>()

    val senesteTom = periode.tom
    var fom = periode.fom
    val lengdePaaSykmelding = antallDager(fom, senesteTom)
    val antallDeler = ceil(lengdePaaSykmelding / 28.0)

    if (antallDeler == 1.0) {
        liste.add(Tidsenhet(fom = fom, tom = senesteTom))
        return liste
    }

    val grunnlengde = floor(lengdePaaSykmelding / antallDeler)

    var lengde = 28
    if (grunnlengde <= 21) {
        lengde = 21
    }

    var tom: LocalDate
    do {
        tom = min(sammeEllerSistSondag(fom.plusDays(lengde.toLong())), senesteTom)
        if (ChronoUnit.DAYS.between(tom, senesteTom) <= 4L) {
            tom = senesteTom
        }
        liste.add(Tidsenhet(fom = fom, tom = tom))
        fom = tom.plusDays(1)
    } while (tom.isBefore(senesteTom))

    return liste
}

private fun sammeEllerSistSondag(localDate: LocalDate): LocalDate {
    var day = localDate
    while (true) {
        if (day.dayOfWeek == DayOfWeek.SUNDAY) {
            return day
        }
        day = day.minusDays(1)
    }
}

internal fun ArbeidsgiverSykmelding.splittLangeSykmeldingperioder(): List<Tidsenhet> {
    if (this.harBehandlingsdager()) {
        return this.splittLangeSykmeldingperioderMedBehandlingsdager()
    }

    val perioder = this.sykmeldingsperioder

    fun eldstePeriodeFOM(perioder: List<SykmeldingsperiodeAGDTO>): LocalDate {
        return perioder.sortedBy { it.fom }.firstOrNull()?.fom ?: throw SykeforloepManglerSykemeldingException()
    }

    fun nyestePeriodeFoerst(perioder: List<SykmeldingsperiodeAGDTO>): List<SykmeldingsperiodeAGDTO> {
        return perioder.sortedByDescending { it.fom }
    }

    fun hentNyestePeriode(perioder: List<SykmeldingsperiodeAGDTO>): SykmeldingsperiodeAGDTO {
        return nyestePeriodeFoerst(perioder)
            .stream()
            .findFirst().get()
    }

    fun hentSenesteTOMFraPerioder(perioder: List<SykmeldingsperiodeAGDTO>): LocalDate {
        val nyestePeriodeFoerst = nyestePeriodeFoerst(perioder)
        return hentNyestePeriode(nyestePeriodeFoerst).tom
    }

    val liste = ArrayList<Tidsenhet>()

    val lengdePaaSykmelding =
        ChronoUnit.DAYS.between(eldstePeriodeFOM(perioder), hentSenesteTOMFraPerioder(perioder)) + 1

    val antallDeler = ceil(lengdePaaSykmelding / 31.0)
    val grunnlengde = floor(lengdePaaSykmelding / antallDeler)
    var rest = lengdePaaSykmelding % grunnlengde

    var soknadFOM = eldstePeriodeFOM(perioder)

    var i = 0
    while (i < antallDeler) {
        val lengde = grunnlengde.toInt() + if (rest-- > 0) 1 else 0
        val tidsenhet = Tidsenhet(fom = soknadFOM, tom = soknadFOM.plusDays((lengde - 1).toLong()))
        liste.add(tidsenhet)
        soknadFOM = tidsenhet.tom.plusDays(1)
        i++
    }
    return liste
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
