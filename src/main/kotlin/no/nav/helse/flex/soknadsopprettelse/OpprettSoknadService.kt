package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.aktivering.AktiveringBestilling
import no.nav.helse.flex.domain.*
import no.nav.helse.flex.domain.Merknad
import no.nav.helse.flex.domain.exception.SykeforloepManglerSykemeldingException
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.domain.sykmelding.finnSoknadsType
import no.nav.helse.flex.julesoknad.LagreJulesoknadKandidater
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.SlettSoknaderTilKorrigertSykmeldingService
import no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger.KlippMetrikk
import no.nav.helse.flex.soknadsopprettelse.splitt.delOppISoknadsperioder
import no.nav.helse.flex.soknadsopprettelse.splitt.splittMellomTyper
import no.nav.helse.flex.soknadsopprettelse.splitt.splittSykmeldingiSoknadsPerioder
import no.nav.helse.flex.util.EnumUtil
import no.nav.helse.flex.util.Metrikk
import no.nav.helse.flex.util.osloZone
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO.*
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.ShortNameKafkaDTO
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*
import no.nav.syfo.model.Merknad as SmMerknad

@Service
@Transactional(rollbackFor = [Throwable::class])
class OpprettSoknadService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val metrikk: Metrikk,
    private val klippMetrikk: KlippMetrikk,
    private val soknadProducer: SoknadProducer,
    private val lagreJulesoknadKandidater: LagreJulesoknadKandidater,
    private val slettSoknaderTilKorrigertSykmeldingService: SlettSoknaderTilKorrigertSykmeldingService,
) {
    private val log = logger()

    fun opprettSykepengesoknaderForSykmelding(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        arbeidssituasjon: Arbeidssituasjon,
        identer: FolkeregisterIdenter,
        arbeidsgiverStatusDTO: ArbeidsgiverStatusKafkaDTO?,
        flexSyketilfelleSykeforloep: List<Sykeforloep>,
    ): List<AktiveringBestilling> {
        val sykmelding = sykmeldingKafkaMessage.sykmelding

        val eksisterendeSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer)

        val sykeforloep =
            flexSyketilfelleSykeforloep.firstOrNull {
                it.sykmeldinger.any { sm -> sm.id == sykmelding.id }
            } ?: throw SykeforloepManglerSykemeldingException("Sykeforloep mangler sykmelding ${sykmelding.id}")
        val startSykeforlop = sykeforloep.oppfolgingsdato

        val sykmeldingSplittetMellomTyper = sykmelding.splittMellomTyper()
        val soknaderTilOppretting =
            sykmeldingSplittetMellomTyper.map { sm ->
                sm.splittSykmeldingiSoknadsPerioder(
                    arbeidssituasjon,
                    eksisterendeSoknader,
                    sm.id,
                    sm.behandletTidspunkt.toInstant(),
                    arbeidsgiverStatusDTO?.orgnummer,
                    klippMetrikk,
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
                        sykmeldingSignaturDato = sm.signaturDato?.toInstant(),
                        soknadPerioder = perioderFraSykmeldingen.tilSoknadsperioder(),
                        egenmeldtSykmelding = sm.egenmeldt,
                        merknaderFraSykmelding = sm.merknader.tilMerknader(),
                        soknadstype = finnSoknadsType(arbeidssituasjon, perioderFraSykmeldingen),
                        status = Soknadstatus.FREMTIDIG,
                        opprettet = Instant.now(),
                        sporsmal = emptyList(),
                        utenlandskSykmelding = sykmeldingKafkaMessage.sykmelding.utenlandskSykmelding != null,
                        egenmeldingsdagerFraSykmelding =
                            sykmeldingKafkaMessage.event.sporsmals?.firstOrNull {
                                    spm ->
                                spm.shortName == ShortNameKafkaDTO.EGENMELDINGSDAGER
                            }?.svar,
                        forstegangssoknad = null,
                        tidligereArbeidsgiverOrgnummer = sykmeldingKafkaMessage.event.tidligereArbeidsgiver?.orgnummer,
                        aktivertDato = null,
                        fiskerBlad =
                            EnumUtil.konverter(
                                FiskerBlad::class.java,
                                sykmeldingKafkaMessage.event.brukerSvar?.fisker?.blad?.svar?.name,
                            ),
                        antattArbeidsgiverperiode = null,
                    )
                }
                    .filter { it.soknadPerioder?.isNotEmpty() ?: true }
                    .also { it.lagreJulesoknadKandidater() }
            }.flatten()

        for (sykepengesoknad in soknaderTilOppretting) {
            if (flexSyketilfelleClient != null) {
                val arbeidsgiverperiode =
                    flexSyketilfelleClient.beregnArbeidsgiverperiode(
                        soknad = sykepengesoknad,
                        sykmelding = null,
                        forelopig = sykepengesoknad.status != Soknadstatus.SENDT,
                        identer = identer,
                    )

                if (arbeidsgiverperiode != null && arbeidsgiverperiode.oppbruktArbeidsgiverperiode) {
                    // sykepengesoknad.lagreSykepengesoknad(sykepengesoknad.copy(antattArbeidsgiverperiode = true))
                    log.info("found soknad med oppbrukt arbeidsgiverperiode")
                }
            }
        }

        val eksisterendeSoknaderForSm = eksisterendeSoknader.filter { it.sykmeldingId == sykmelding.id }

        if (eksisterendeSoknaderForSm.isNotEmpty()) {
            val sammenliknbartSettAvNyeSoknader = soknaderTilOppretting.map { it.tilSoknadSammenlikner() }.toHashSet()
            val sammenliknbartSettAvEksisterendeSoknaderForSm =
                eksisterendeSoknaderForSm
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
            .map { it.markerForsteganssoknad(eksisterendeSoknader, soknaderTilOppretting) }
            .map { it.lagreSøknad() }
            .mapNotNull { it.publiserEllerReturnerAktiveringBestilling() }
    }

    private fun List<Sykepengesoknad>.lagreJulesoknadKandidater() {
        return lagreJulesoknadKandidater.lagreJulesoknadKandidater(this)
    }

    fun Sykepengesoknad.lagreSøknad(): Sykepengesoknad {
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
        val sykepengesoknad = sykepengesoknadDAO.finnSykepengesoknad(oppholdUtlandSoknad.id)
        soknadProducer.soknadEvent(sykepengesoknad)
        return sykepengesoknad
    }
}

private fun Sykepengesoknad.markerForsteganssoknad(
    eksisterendeSoknader: List<Sykepengesoknad>,
    soknaderTilOppretting: List<Sykepengesoknad>,
): Sykepengesoknad {
    val alleSoknader =
        mutableListOf(
            *eksisterendeSoknader.toTypedArray(),
            *soknaderTilOppretting.toTypedArray(),
        )
    return this.copy(
        forstegangssoknad =
            erForsteSoknadTilArbeidsgiverIForlop(
                alleSoknader.filterNot { eksisterendeSoknad -> eksisterendeSoknad.id == this.id },
                this,
            ),
    )
}

fun SykmeldingKafkaMessage.skapSoknadsId(
    fom: LocalDate,
    tom: LocalDate,
): String {
    return UUID.nameUUIDFromBytes(
        "${sykmelding.id}$fom$tom${event.timestamp}".toByteArray(),
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
        sykmeldingstype = type.tilSykmeldingstype(),
    )
}

fun List<SmMerknad>?.tilMerknader(): List<Merknad>? = this?.map { Merknad(type = it.type, beskrivelse = it.beskrivelse) }

fun antallDager(
    fom: LocalDate,
    tom: LocalDate,
): Long {
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
