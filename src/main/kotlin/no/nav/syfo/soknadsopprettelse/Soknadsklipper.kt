package no.nav.syfo.soknadsopprettelse

import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Soknadsperiode
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.syfo.logger
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.service.AktiverEnkeltSoknadService
import no.nav.syfo.service.FolkeregisterIdenter
import no.nav.syfo.soknadsopprettelse.Soknadsklipper.EndringIUforegrad.FLERE_PERIODER
import no.nav.syfo.soknadsopprettelse.Soknadsklipper.EndringIUforegrad.LAVERE_UFØREGRAD
import no.nav.syfo.soknadsopprettelse.Soknadsklipper.EndringIUforegrad.SAMME_UFØREGRAD
import no.nav.syfo.soknadsopprettelse.Soknadsklipper.EndringIUforegrad.VET_IKKE
import no.nav.syfo.soknadsopprettelse.Soknadsklipper.EndringIUforegrad.ØKT_UFØREGRAD
import no.nav.syfo.util.Metrikk
import no.nav.syfo.util.erHelg
import no.nav.syfo.util.isAfterOrEqual
import no.nav.syfo.util.isBeforeOrEqual
import no.nav.syfo.util.overlap
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Component
@Transactional
class Soknadsklipper(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val metrikk: Metrikk,
    private val aktiverEnkeltSoknadService: AktiverEnkeltSoknadService,
) {

    val log = logger()

    private enum class EndringIUforegrad {
        FLERE_PERIODER,
        ØKT_UFØREGRAD,
        SAMME_UFØREGRAD,
        LAVERE_UFØREGRAD,
        VET_IKKE,
    }

    fun klippEksisterendeSoknader(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        arbeidssituasjon: Arbeidssituasjon,
        arbeidsgiverStatusDTO: ArbeidsgiverStatusDTO?,
        identer: FolkeregisterIdenter,
    ) {
        if (arbeidssituasjon != Arbeidssituasjon.ARBEIDSTAKER) {
            return
        }

        val eksisterendeSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer)
        val sykmeldingFom = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.minOf { it.fom }
        val sykmeldingTom = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.maxOf { it.tom }
        val sykmeldingPeriode = sykmeldingFom..sykmeldingTom

        val soknadKandidater = eksisterendeSoknader.soknadKandidater(
            behandletTidspunkt = sykmeldingKafkaMessage.sykmelding.behandletTidspunkt.toLocalDateTime(),
            orgnummer = arbeidsgiverStatusDTO?.orgnummer,
            sykmeldingPeriode = sykmeldingPeriode,
        )

        soknadKandidater.klippSoknaderSomOverlapperEtter(
            sykmeldingPeriode = sykmeldingPeriode,
            sykmeldingId = sykmeldingKafkaMessage.sykmelding.id,
            nyeSoknadPerioder = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.tilSoknadsperioder()
        )

        soknadKandidater.klippSoknaderSomOverlapperFullstendig(
            sykmeldingPeriode = sykmeldingPeriode,
            sykmeldingId = sykmeldingKafkaMessage.sykmelding.id,
        )

        soknadKandidater.klippSoknaderSomOverlapperFor(
            sykmeldingPeriode = sykmeldingPeriode,
            sykmeldingId = sykmeldingKafkaMessage.sykmelding.id,
        )

        soknadKandidater.klippSoknaderSomOverlapperInni(
            sykmeldingPeriode = sykmeldingPeriode,
            sykmeldingId = sykmeldingKafkaMessage.sykmelding.id,
        )
    }

    private fun List<Sykepengesoknad>.soknadKandidater(
        behandletTidspunkt: LocalDateTime,
        orgnummer: String?,
        sykmeldingPeriode: ClosedRange<LocalDate>,
    ): List<Sykepengesoknad> {
        return this.asSequence()
            .filter { it.soknadstype == Soknadstype.ARBEIDSTAKERE }
            .filter { it.sykmeldingSkrevet!!.isBefore(behandletTidspunkt) }
            .filter { it.arbeidsgiverOrgnummer == orgnummer }
            .filter { sok ->
                val soknadPeriode = sok.fom!!..sok.tom!!
                sykmeldingPeriode.overlap(soknadPeriode)
            }
            .toList()
    }

    private fun List<Sykepengesoknad>.klippSoknaderSomOverlapperEtter(
        sykmeldingPeriode: ClosedRange<LocalDate>,
        sykmeldingId: String,
        nyeSoknadPerioder: List<Soknadsperiode>,
    ) {
        this.filter { it.fom!!.isBefore(sykmeldingPeriode.start) }
            .filter { it.tom!!.isBeforeOrEqual(sykmeldingPeriode.endInclusive) }
            .forEach { sok ->
                if (sok.status == Soknadstatus.FREMTIDIG) {
                    log.info("Sykmelding $sykmeldingId klipper søknad ${sok.id} på ${sykmeldingPeriode.start}")
                    sykepengesoknadDAO.klippSoknad(
                        sykepengesoknadUuid = sok.id,
                        klippFom = sykmeldingPeriode.start
                    )
                    if (sykmeldingPeriode.start.minusDays(1) < LocalDate.now()) {
                        aktiverEnkeltSoknadService.aktiverSoknad(sok.id)
                    }
                }

                if (sok.status == Soknadstatus.NY || sok.status == Soknadstatus.SENDT) {
                    val soknadPeriode = sok.fom!!..sok.tom!!

                    val nyePerioderSomOverlapper = nyeSoknadPerioder
                        .filter { soknadPeriode.overlap(it.fom..it.tom) }
                    val tidligerePerioderSomOverlapper = sok.soknadPerioder!!
                        .filter { sykmeldingPeriode.overlap(it.fom..it.tom) }

                    val endringIUforegrad = finnEndringIUforegrad(
                        tidligerePerioder = tidligerePerioderSomOverlapper,
                        nyePerioder = nyePerioderSomOverlapper,
                    )

                    if (endringIUforegrad == SAMME_UFØREGRAD) {
                        metrikk.klippKandidatScenarioEnMotsatt(soknadstatus = sok.status.toString())
                    }
                }

                metrikk.klippKandidatScenarioEn(
                    soknadstatus = sok.status.toString(),
                    uforegrad = finnEndringIUforegrad(
                        tidligerePerioder = sok.soknadPerioder,
                        nyePerioder = nyeSoknadPerioder,
                    ).toString()
                )
            }
    }

    private fun List<Sykepengesoknad>.klippSoknaderSomOverlapperFullstendig(
        sykmeldingPeriode: ClosedRange<LocalDate>,
        sykmeldingId: String,
    ) {
        this.filter { it.fom!!.isAfterOrEqual(sykmeldingPeriode.start) }
            .filter { it.tom!!.isBeforeOrEqual(sykmeldingPeriode.endInclusive) }
            .forEach { sok ->
                if (sok.status == Soknadstatus.FREMTIDIG) {
                    log.info("Sykmelding $sykmeldingId overlapper søknad ${sok.id} fullstendig")
                }
                metrikk.klippSoknaderSomOverlapper(
                    overlapp = "FULLSTENDIG",
                    soknadstatus = sok.status.toString(),
                )
            }
    }

    private fun List<Sykepengesoknad>.klippSoknaderSomOverlapperFor(
        sykmeldingPeriode: ClosedRange<LocalDate>,
        sykmeldingId: String,
    ) {
        this.filter { it.fom!!.isAfterOrEqual(sykmeldingPeriode.start) }
            .filter { it.tom!!.isAfter(sykmeldingPeriode.endInclusive) }
            .forEach { sok ->
                if (sok.status == Soknadstatus.FREMTIDIG) {
                    log.info("Sykmelding $sykmeldingId overlapper søknad ${sok.id} før ${sykmeldingPeriode.endInclusive}")
                }
                metrikk.klippSoknaderSomOverlapper(
                    overlapp = "FOR",
                    soknadstatus = sok.status.toString(),
                )
            }
    }

    private fun List<Sykepengesoknad>.klippSoknaderSomOverlapperInni(
        sykmeldingPeriode: ClosedRange<LocalDate>,
        sykmeldingId: String,
    ) {
        this.filter { it.fom!!.isBefore(sykmeldingPeriode.start) }
            .filter { it.tom!!.isAfter(sykmeldingPeriode.endInclusive) }
            .forEach { sok ->
                if (sok.status == Soknadstatus.FREMTIDIG) {
                    log.info("Sykmelding $sykmeldingId overlapper søknad ${sok.id} inni fra ${sykmeldingPeriode.start} til ${sykmeldingPeriode.endInclusive}")
                    finnLengdePaPerioder(
                        orginalPeriode = sok.fom!!..sok.tom!!,
                        overlappendePeriode = sykmeldingPeriode,
                    )
                }
                metrikk.klippSoknaderSomOverlapper(
                    overlapp = "INNI",
                    soknadstatus = sok.status.toString(),
                )
            }
    }

    private fun finnEndringIUforegrad(
        tidligerePerioder: List<Soknadsperiode>?,
        nyePerioder: List<Soknadsperiode>,
    ): EndringIUforegrad {
        if (tidligerePerioder == null || tidligerePerioder.size > 1 || nyePerioder.size > 1) {
            return FLERE_PERIODER
        }
        if (nyePerioder.first().grad > tidligerePerioder.first().grad) {
            return ØKT_UFØREGRAD
        }
        if (nyePerioder.first().grad == tidligerePerioder.first().grad) {
            return SAMME_UFØREGRAD
        }
        if (nyePerioder.first().grad < tidligerePerioder.first().grad) {
            return LAVERE_UFØREGRAD
        }
        return VET_IKKE
    }

    private fun finnLengdePaPerioder(
        orginalPeriode: ClosedRange<LocalDate>,
        overlappendePeriode: ClosedRange<LocalDate>,
    ) {
        val periode1 = orginalPeriode.start.datesUntil(overlappendePeriode.start).toList()
        val periode2 = overlappendePeriode.start.datesUntil(overlappendePeriode.endInclusive.plusDays(1)).toList()
        val periode3 = overlappendePeriode.endInclusive.plusDays(1).datesUntil(orginalPeriode.endInclusive.plusDays(1)).toList()

        val perioderEtterKlipp = "${periode1.size}-${periode2.size}-${periode3.size}"
        metrikk.overlapperInniPerioder(perioderEtterKlipp)

        fun List<LocalDate>.minusHelg() = filter { !it.erHelg() }
        val perioderEtterKlippMinusHelg = "${periode1.minusHelg().size}-${periode2.minusHelg().size}-${periode3.minusHelg().size}"
        metrikk.overlapperInniPerioderUtenHelg(perioderEtterKlippMinusHelg)
    }
}
