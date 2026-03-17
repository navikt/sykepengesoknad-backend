package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.aktivering.AktiveringBestilling
import no.nav.helse.flex.domain.*
import no.nav.helse.flex.domain.Arbeidssituasjon.*
import no.nav.helse.flex.domain.exception.SykeforloepManglerSykemeldingException
import no.nav.helse.flex.domain.sykmelding.SykmeldingTilSoknadOpprettelse
import no.nav.helse.flex.domain.sykmelding.Sykmeldingsperiode
import no.nav.helse.flex.domain.sykmelding.bestemSoknadsTypeNy
import no.nav.helse.flex.julesoknad.LagreJulesoknadKandidater
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.SelvstendigNaringsdrivendeInfoService
import no.nav.helse.flex.service.SlettSoknaderTilKorrigertSykmeldingService
import no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger.KlippMetrikk
import no.nav.helse.flex.soknadsopprettelse.splitt.delOppISoknadsperioder
import no.nav.helse.flex.soknadsopprettelse.splitt.splittMellomTyper
import no.nav.helse.flex.soknadsopprettelse.splitt.splittSykmeldingiSoknadsPerioder
import no.nav.helse.flex.util.osloZone
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO.*
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import no.nav.syfo.model.Merknad as SmMerknad

@Service
@Transactional(rollbackFor = [Throwable::class])
class OpprettSoknadService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val klippMetrikk: KlippMetrikk,
    private val soknadProducer: SoknadProducer,
    private val lagreJulesoknadKandidater: LagreJulesoknadKandidater,
    private val slettSoknaderTilKorrigertSykmeldingService: SlettSoknaderTilKorrigertSykmeldingService,
    private val selvstendigNaringsdrivendeInfoService: SelvstendigNaringsdrivendeInfoService,
) {
    private val log = logger()

    fun opprettSykepengesoknaderForSykmelding(
        sykmeldingTilSoknadOpprettelse: SykmeldingTilSoknadOpprettelse,
        arbeidssituasjon: Arbeidssituasjon,
        identer: FolkeregisterIdenter,
        arbeidsgiverStatusDTO: ArbeidsgiverStatusKafkaDTO?,
        flexSyketilfelleSykeforloep: List<Sykeforloep>,
    ): List<AktiveringBestilling> {
        val eksisterendeSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer)

        val sykeforloep =
            flexSyketilfelleSykeforloep.firstOrNull {
                it.sykmeldinger.any { sm -> sm.id == sykmeldingTilSoknadOpprettelse.sykmeldingId }
            }
                ?: throw SykeforloepManglerSykemeldingException(
                    "Sykeforloep mangler sykmelding ${sykmeldingTilSoknadOpprettelse.sykmeldingId}",
                )
        val startSykeforlop = sykeforloep.oppfolgingsdato
        val sykmeldingSplittetMellomTyper = sykmeldingTilSoknadOpprettelse.splittMellomTyper()
        val soknaderTilOppretting =
            sykmeldingSplittetMellomTyper
                .map { sykmelding ->
                    sykmelding
                        .splittSykmeldingiSoknadsPerioder(
                            arbeidssituasjon,
                            eksisterendeSoknader,
                            sykmelding.sykmeldingId,
                            sykmelding.behandletTidspunkt,
                            arbeidsgiverStatusDTO?.orgnummer,
                            klippMetrikk,
                        ).map {
                            val selvstendigNaringsdrivendeInfo =
                                hentSelvstendigNaringsdrivendeInfo(
                                    arbeidssituasjon,
                                    identer,
                                    sykmeldingTilSoknadOpprettelse,
                                )
                            val perioderFraSykmeldingen = it.delOppISoknadsperioder(sykmelding)
                            val soknadsId =
                                skapSoknadsId(
                                    it.fom,
                                    it.tom,
                                    sykmeldingTilSoknadOpprettelse.sykmeldingId,
                                    sykmeldingTilSoknadOpprettelse.eventTimestamp,
                                )
                            val beregnetArbeidssituasjon =
                                if (selvstendigNaringsdrivendeInfo == null) {
                                    arbeidssituasjon
                                } else {
                                    if (selvstendigNaringsdrivendeInfo.erBarnepasser) {
                                        log.info(
                                            "Endrer arbeidssituasjon fra $arbeidssituasjon til BARNEPASSER for sykmelding: ${sykmelding.sykmeldingId}",
                                        )
                                        BARNEPASSER
                                    } else {
                                        arbeidssituasjon
                                    }
                                }

                            Sykepengesoknad(
                                id = soknadsId,
                                fnr = identer.originalIdent,
                                startSykeforlop = startSykeforlop,
                                fom = it.fom,
                                tom = it.tom,
                                arbeidssituasjon = beregnetArbeidssituasjon,
                                arbeidsgiverOrgnummer = arbeidsgiverStatusDTO?.orgnummer,
                                arbeidsgiverNavn = arbeidsgiverStatusDTO?.orgNavn?.prettyOrgnavn(),
                                sykmeldingId = sykmelding.sykmeldingId,
                                sykmeldingSkrevet = sykmelding.behandletTidspunkt,
                                sykmeldingSignaturDato = sykmelding.signaturDato,
                                soknadPerioder = perioderFraSykmeldingen.tilSoknadsperioderNy(),
                                egenmeldtSykmelding = sykmelding.egenmeldt,
                                merknaderFraSykmelding = sykmelding.merknader,
                                soknadstype = bestemSoknadsTypeNy(arbeidssituasjon, perioderFraSykmeldingen),
                                status = Soknadstatus.FREMTIDIG,
                                opprettet = Instant.now(),
                                sporsmal = emptyList(),
                                utenlandskSykmelding = sykmeldingTilSoknadOpprettelse.erUtlandskSykmelding,
                                egenmeldingsdagerFraSykmelding = sykmeldingTilSoknadOpprettelse.egenmeldingsdagerFraSykmelding,
                                forstegangssoknad = null,
                                tidligereArbeidsgiverOrgnummer = sykmeldingTilSoknadOpprettelse.tidligereArbeidsgiverOrgnummer,
                                aktivertDato = null,
                                fiskerBlad = sykmeldingTilSoknadOpprettelse.fiskerBlad,
                                selvstendigNaringsdrivende = selvstendigNaringsdrivendeInfo,
                            )
                        }.filter { it.soknadPerioder?.isNotEmpty() ?: true }
                        .also { it.lagreJulesoknadKandidater() }
                }.flatten()

        val eksisterendeSoknaderForSm = eksisterendeSoknader.filter { it.sykmeldingId == sykmeldingTilSoknadOpprettelse.sykmeldingId }

        if (eksisterendeSoknaderForSm.isNotEmpty()) {
            val sammenliknbartSettAvNyeSoknader = soknaderTilOppretting.map { it.tilSoknadSammenlikner() }.toHashSet()
            val sammenliknbartSettAvEksisterendeSoknaderForSm =
                eksisterendeSoknaderForSm
                    .filter { it.status !in listOf(Soknadstatus.KORRIGERT, Soknadstatus.UTKAST_TIL_KORRIGERING) }
                    .map { it.tilSoknadSammenlikner() }
                    .toHashSet()
            if (sammenliknbartSettAvEksisterendeSoknaderForSm == sammenliknbartSettAvNyeSoknader) {
                log.info(
                    "Oppretter ikke søknader for sykmelding: ${sykmeldingTilSoknadOpprettelse.sykmeldingId} siden eksisterende identiske søknader finnes.",
                )
                return emptyList()
            } else {
                slettSoknaderTilKorrigertSykmeldingService.slettSoknader(eksisterendeSoknaderForSm)
            }
        }

        return soknaderTilOppretting
            .map { it.markerForsteganssoknad(eksisterendeSoknader, soknaderTilOppretting) }
            .map { it.lagreSykepengesoknad() }
            .mapNotNull { it.publiserEllerReturnerAktiveringBestilling() }
    }

    internal fun hentSelvstendigNaringsdrivendeInfo(
        arbeidssituasjon: Arbeidssituasjon,
        identer: FolkeregisterIdenter,
        sykmeldingTilSoknadOpprettelse: SykmeldingTilSoknadOpprettelse,
    ): SelvstendigNaringsdrivendeInfo? =
        if (listOf(FISKER, JORDBRUKER, NAERINGSDRIVENDE).contains(arbeidssituasjon)) {
            selvstendigNaringsdrivendeInfoService.lagSelvstendigNaringsdrivendeInfo(
                identer = identer,
                sykmeldingId = sykmeldingTilSoknadOpprettelse.sykmeldingId,
                brukerHarOppgittForsikring = sykmeldingTilSoknadOpprettelse.brukerHarOppgittForsikring,
                arbeidssituasjon = arbeidssituasjon,
            )
        } else {
            null
        }

    private fun List<Sykepengesoknad>.lagreJulesoknadKandidater() = lagreJulesoknadKandidater.lagreJulesoknadKandidater(this)

    fun Sykepengesoknad.lagreSykepengesoknad(): Sykepengesoknad {
        log.info("Oppretter ${this.soknadstype} søknad: ${this.id} for sykmelding: ${this.sykmeldingId} med status ${this.status}")
        val lagretSoknad = sykepengesoknadDAO.lagreSykepengesoknad(this)
        return lagretSoknad
    }

    fun Sykepengesoknad.publiserEllerReturnerAktiveringBestilling(): AktiveringBestilling? {
        val skalAktiveres = this.tom!!.isBefore(LocalDate.now(osloZone))
        if (skalAktiveres) {
            return AktiveringBestilling(this.fnr, this.id)
        }
        // Publiserer søknad med status FREMTIDIG som aktiveres av cron-jobb senere.
        soknadProducer.soknadEvent(this)

        return null
    }

    fun opprettSoknadUtland(identer: FolkeregisterIdenter): Sykepengesoknad =
        sykepengesoknadDAO.finnAlleredeOpprettetSoknad(identer)
            ?: opprettNySoknadUtland(identer.originalIdent)

    private fun opprettNySoknadUtland(fnr: String): Sykepengesoknad {
        val oppholdUtlandSoknad = settOppSoknadOppholdUtland(fnr = fnr)
        sykepengesoknadDAO.lagreSykepengesoknad(oppholdUtlandSoknad)

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

fun skapSoknadsId(
    fom: LocalDate,
    tom: LocalDate,
    sykmeldingId: String,
    eventTimestamp: OffsetDateTime,
): String =
    UUID
        .nameUUIDFromBytes(
            "${sykmeldingId}$fom$tom$eventTimestamp".toByteArray(),
        ).toString()

fun List<SykmeldingsperiodeAGDTO>.tilSoknadsperioder(): List<Soknadsperiode> = this.map { it.tilSoknadsperioder() }

fun List<Sykmeldingsperiode>.tilSoknadsperioderNy(): List<Soknadsperiode> = this.map { it.tilSoknadsperioderNy() }

fun SykmeldingsperiodeAGDTO.tilSoknadsperioder(): Soknadsperiode {
    val gradForAktivitetIkkeMulig = if (type == AKTIVITET_IKKE_MULIG) 100 else null
    return Soknadsperiode(
        fom = fom,
        tom = tom,
        grad = gradert?.grad ?: gradForAktivitetIkkeMulig ?: 0,
        sykmeldingstype = type.tilSykmeldingstype(),
    )
}

fun Sykmeldingsperiode.tilSoknadsperioderNy(): Soknadsperiode {
    val gradForAktivitetIkkeMulig = if (type == Sykmeldingstype.AKTIVITET_IKKE_MULIG) 100 else null
    return Soknadsperiode(
        fom = fom,
        tom = tom,
        grad = gradert?.grad ?: gradForAktivitetIkkeMulig ?: 0,
        sykmeldingstype = type,
    )
}

fun List<SmMerknad>?.tilMerknader(): List<Merknad>? = this?.map { Merknad(type = it.type, beskrivelse = it.beskrivelse) }

fun antallDager(
    fom: LocalDate,
    tom: LocalDate,
): Long = ChronoUnit.DAYS.between(fom, tom) + 1

fun eldstePeriodeFom(perioder: List<Sykmeldingsperiode>): LocalDate =
    perioder
        .minByOrNull {
            it.fom
        }?.fom ?: throw SykeforloepManglerSykemeldingException()

fun sistePeriodeTom(perioder: List<Sykmeldingsperiode>): LocalDate = perioder.maxByOrNull { it.fom }!!.tom

private fun PeriodetypeDTO.tilSykmeldingstype(): Sykmeldingstype =
    when (this) {
        AKTIVITET_IKKE_MULIG -> Sykmeldingstype.AKTIVITET_IKKE_MULIG
        AVVENTENDE -> Sykmeldingstype.AVVENTENDE
        BEHANDLINGSDAGER -> Sykmeldingstype.BEHANDLINGSDAGER
        GRADERT -> Sykmeldingstype.GRADERT
        REISETILSKUDD -> Sykmeldingstype.REISETILSKUDD
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
