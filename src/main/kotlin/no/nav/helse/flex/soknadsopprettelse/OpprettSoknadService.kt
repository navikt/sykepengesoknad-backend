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
import no.nav.helse.flex.service.SelvstendigNaringsdrivendeInfoService
import no.nav.helse.flex.service.SlettSoknaderTilKorrigertSykmeldingService
import no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger.KlippMetrikk
import no.nav.helse.flex.soknadsopprettelse.splitt.delOppISoknadsperioder
import no.nav.helse.flex.soknadsopprettelse.splitt.splittMellomTyper
import no.nav.helse.flex.soknadsopprettelse.splitt.splittSykmeldingiSoknadsPerioder
import no.nav.helse.flex.unleash.UnleashToggles
import no.nav.helse.flex.util.EnumUtil
import no.nav.helse.flex.util.osloZone
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO.*
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.ShortNameKafkaDTO
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.time.Instant
import java.time.LocalDate
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
    private val unleashToggles: UnleashToggles,
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
            sykmeldingSplittetMellomTyper
                .map { sm ->
                    sm
                        .splittSykmeldingiSoknadsPerioder(
                            arbeidssituasjon,
                            eksisterendeSoknader,
                            sm.id,
                            sm.behandletTidspunkt.toInstant(),
                            arbeidsgiverStatusDTO?.orgnummer,
                            klippMetrikk,
                        ).map {
                            val perioderFraSykmeldingen = it.delOppISoknadsperioder(sm)
                            val soknadsId = sykmeldingKafkaMessage.skapSoknadsId(it.fom, it.tom)
                            Sykepengesoknad(
                                id = soknadsId,
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
                                    sykmeldingKafkaMessage.event.sporsmals
                                        ?.firstOrNull { spm ->
                                            spm.shortName == ShortNameKafkaDTO.EGENMELDINGSDAGER
                                        }?.svar,
                                forstegangssoknad = null,
                                tidligereArbeidsgiverOrgnummer = sykmeldingKafkaMessage.event.tidligereArbeidsgiver?.orgnummer,
                                aktivertDato = null,
                                fiskerBlad =
                                    EnumUtil.konverter(
                                        FiskerBlad::class.java,
                                        sykmeldingKafkaMessage.event.brukerSvar
                                            ?.fisker
                                            ?.blad
                                            ?.svar
                                            ?.name,
                                    ),
                                selvstendigNaringsdrivende = hentSelvstendigNaringsdrivendeInfo(arbeidssituasjon, identer, soknadsId),
                            )
                        }.filter { it.soknadPerioder?.isNotEmpty() ?: true }
                        .also { it.lagreJulesoknadKandidater() }
                }.flatten()

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

    internal fun hentSelvstendigNaringsdrivendeInfo(
        arbeidssituasjon: Arbeidssituasjon,
        identer: FolkeregisterIdenter,
        soknadsId: String? = null,
    ): SelvstendigNaringsdrivendeInfo? =
        when (arbeidssituasjon) {
            Arbeidssituasjon.NAERINGSDRIVENDE ->
                if (unleashToggles.brregEnabled(identer.originalIdent)) {
                    try {
                        selvstendigNaringsdrivendeInfoService.hentSelvstendigNaringsdrivendeInfo(
                            identer = identer,
                        )
                    } catch (_: HttpClientErrorException.NotFound) {
                        log.warn("Fant ikke roller for person i brreg for søknad med id $soknadsId")
                        SelvstendigNaringsdrivendeInfo(roller = emptyList())
                    }
                } else {
                    SelvstendigNaringsdrivendeInfo(roller = emptyList())
                }
            else -> null
        }

    private fun List<Sykepengesoknad>.lagreJulesoknadKandidater() = lagreJulesoknadKandidater.lagreJulesoknadKandidater(this)

    fun Sykepengesoknad.lagreSøknad(): Sykepengesoknad {
        log.info("Oppretter ${this.soknadstype} søknad ${this.id} for sykmelding: ${this.sykmeldingId} med status ${this.status}")
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

fun SykmeldingKafkaMessage.skapSoknadsId(
    fom: LocalDate,
    tom: LocalDate,
): String =
    UUID
        .nameUUIDFromBytes(
            "${sykmelding.id}$fom$tom${event.timestamp}".toByteArray(),
        ).toString()

fun List<SykmeldingsperiodeAGDTO>.tilSoknadsperioder(): List<Soknadsperiode> = this.map { it.tilSoknadsperioder() }

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
): Long = ChronoUnit.DAYS.between(fom, tom) + 1

fun eldstePeriodeFOM(perioder: List<SykmeldingsperiodeAGDTO>): LocalDate =
    perioder
        .sortedBy {
            it.fom
        }.firstOrNull()
        ?.fom ?: throw SykeforloepManglerSykemeldingException()

fun hentSenesteTOMFraPerioder(perioder: List<SykmeldingsperiodeAGDTO>): LocalDate = perioder.sortedByDescending { it.fom }.first().tom

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
