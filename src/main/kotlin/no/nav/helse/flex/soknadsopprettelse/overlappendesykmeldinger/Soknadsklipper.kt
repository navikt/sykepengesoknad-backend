package no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger

import no.nav.helse.flex.aktivering.kafka.AktiveringBestilling
import no.nav.helse.flex.aktivering.kafka.AktiveringProducer
import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.KlippVariant
import no.nav.helse.flex.repository.KlippetSykepengesoknadDbRecord
import no.nav.helse.flex.repository.KlippetSykepengesoknadRepository
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger.Soknadsklipper.EndringIUforegrad.FLERE_PERIODER
import no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger.Soknadsklipper.EndringIUforegrad.LAVERE_UFØREGRAD
import no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger.Soknadsklipper.EndringIUforegrad.SAMME_UFØREGRAD
import no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger.Soknadsklipper.EndringIUforegrad.VET_IKKE
import no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger.Soknadsklipper.EndringIUforegrad.ØKT_UFØREGRAD
import no.nav.helse.flex.soknadsopprettelse.sporsmal.SporsmalGenerator
import no.nav.helse.flex.soknadsopprettelse.tilSoknadsperioder
import no.nav.helse.flex.util.isAfterOrEqual
import no.nav.helse.flex.util.isBeforeOrEqual
import no.nav.helse.flex.util.overlap
import no.nav.helse.flex.util.serialisertTilString
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

@Component
@Transactional
class Soknadsklipper(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val klippMetrikk: KlippMetrikk,
    private val aktiveringProducer: AktiveringProducer,
    private val soknadProducer: SoknadProducer,
    private val klippetSykepengesoknadRepository: KlippetSykepengesoknadRepository,
    private val sporsmalGenerator: SporsmalGenerator,
) {

    val log = logger()

    enum class EndringIUforegrad {
        FLERE_PERIODER,
        ØKT_UFØREGRAD,
        SAMME_UFØREGRAD,
        LAVERE_UFØREGRAD,
        VET_IKKE,
    }

    fun klipp(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        arbeidsgiverStatusDTO: ArbeidsgiverStatusDTO?,
        identer: FolkeregisterIdenter,
    ): SykmeldingKafkaMessage {
        klippEksisterendeSoknader(
            sykmeldingKafkaMessage = sykmeldingKafkaMessage,
            orgnummer = arbeidsgiverStatusDTO?.orgnummer,
            identer = identer,
        )

        return klippSykmelding(
            sykmeldingKafkaMessage = sykmeldingKafkaMessage,
            orgnummer = arbeidsgiverStatusDTO?.orgnummer,
            identer = identer,
        )
    }

    private fun klippEksisterendeSoknader(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        orgnummer: String?,
        identer: FolkeregisterIdenter,
    ) {

        val soknadKandidater = soknadKandidater(
            orgnummer = orgnummer,
            identer = identer,
            sykmeldingKafkaMessage = sykmeldingKafkaMessage,
        )

        soknadKandidater.klippSoknaderSomOverlapperEtter(
            sykmeldingKafkaMessage = sykmeldingKafkaMessage,
        )

        soknadKandidater.klippSoknaderSomOverlapperFullstendig(
            sykmeldingKafkaMessage = sykmeldingKafkaMessage,

        )

        soknadKandidater.klippSoknaderSomOverlapperFor(
            sykmeldingKafkaMessage = sykmeldingKafkaMessage,
        )

        soknadKandidater.klippSoknaderSomOverlapperInni(
            sykmeldingKafkaMessage = sykmeldingKafkaMessage,
        )
    }

    private fun klippSykmelding(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        orgnummer: String?,
        identer: FolkeregisterIdenter,
    ): SykmeldingKafkaMessage {
        var kafkaMessage = sykmeldingKafkaMessage

        val soknadKandidater = soknadKandidater(
            orgnummer = orgnummer,
            identer = identer,
            sykmeldingKafkaMessage = sykmeldingKafkaMessage,
        )

        kafkaMessage = soknadKandidater.klippSykmeldingSomOverlapperEtter(
            sykmeldingKafkaMessage = kafkaMessage,
        )

        kafkaMessage = soknadKandidater.klippSykmeldingSomOverlapperFor(
            sykmeldingKafkaMessage = kafkaMessage
        )

        return kafkaMessage
    }

    private fun soknadKandidater(
        orgnummer: String?,
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        identer: FolkeregisterIdenter,
    ): List<Sykepengesoknad> {
        val sykmeldingId = sykmeldingKafkaMessage.sykmelding.id
        val behandletTidspunkt = sykmeldingKafkaMessage.sykmelding.behandletTidspunkt.toInstant()
        val sykmeldingPeriode = sykmeldingKafkaMessage.periode()
        return sykepengesoknadDAO.finnSykepengesoknader(identer)
            .asSequence()
            .filterNot { it.sykmeldingId == sykmeldingId } // Korrigerte sykmeldinger håndteres her SlettSoknaderTilKorrigertSykmeldingService
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
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,

    ) {
        val sykmeldingId = sykmeldingKafkaMessage.sykmelding.id
        val sykmeldingPeriode = sykmeldingKafkaMessage.periode()
        this.filter { it.fom!!.isBefore(sykmeldingPeriode.start) }
            .filter { it.tom!!.isBeforeOrEqual(sykmeldingPeriode.endInclusive) }
            .forEach { sok ->
                val klipper = sok.status in listOf(
                    Soknadstatus.FREMTIDIG, Soknadstatus.NY, Soknadstatus.AVBRUTT
                )
                if (klipper) {
                    log.info(
                        "Sykmelding $sykmeldingId klipper søknad ${sok.id} tom fra: ${sok.tom} til: ${
                        sykmeldingPeriode.start.minusDays(
                            1
                        )
                        }"
                    )

                    val nyePerioder = sykepengesoknadDAO.klippSoknadTom(
                        sykepengesoknadUuid = sok.id,
                        klipp = sykmeldingPeriode.start
                    )

                    klippetSykepengesoknadRepository.save(
                        KlippetSykepengesoknadDbRecord(
                            sykepengesoknadUuid = sok.id,
                            sykmeldingUuid = sykmeldingId,
                            klippVariant = KlippVariant.SOKNAD_STARTER_INNI_SLUTTER_ETTER,
                            periodeFor = sok.soknadPerioder!!.serialisertTilString(),
                            periodeEtter = nyePerioder.serialisertTilString(),
                            timestamp = Instant.now(),
                        )
                    )

                    if (sok.status != Soknadstatus.FREMTIDIG) {
                        sporsmalGenerator.lagSporsmalPaSoknad(sok.id)
                        val oppdatertSoknad = sykepengesoknadDAO.finnSykepengesoknad(sok.id)
                        soknadProducer.soknadEvent(oppdatertSoknad, null, false)
                    }

                    if (sok.status == Soknadstatus.FREMTIDIG && nyePerioder.maxOf { it.tom } < LocalDate.now()) {
                        aktiveringProducer.leggPaAktiveringTopic(AktiveringBestilling(sok.fnr, sok.id))
                    }
                }

                val endringIUforegrad = finnEndringIUforegrad(
                    tidligerePerioder = sok.soknadPerioder!!.filter {
                        sykmeldingPeriode.overlap(it.fom..it.tom)
                    },
                    nyePerioder = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.filter {
                        (sok.fom!!..sok.tom!!).overlap(it.fom..it.tom)
                    }.tilSoknadsperioder(),
                )
                klippMetrikk.klippMetrikk(
                    klippMetrikkVariant = KlippVariant.SOKNAD_STARTER_INNI_SLUTTER_ETTER,
                    soknadstatus = sok.status.toString(),
                    sykmeldingId = sykmeldingId,
                    klippet = klipper,
                    endringIUforegrad = endringIUforegrad,
                    eksisterendeSykepengesoknadId = sok.id,
                )
            }
    }

    private fun List<Sykepengesoknad>.klippSoknaderSomOverlapperFullstendig(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,

    ) {
        val sykmeldingId = sykmeldingKafkaMessage.sykmelding.id
        val sykmeldingPeriode = sykmeldingKafkaMessage.periode()
        this.filter { it.fom!!.isAfterOrEqual(sykmeldingPeriode.start) }
            .filter { it.tom!!.isBeforeOrEqual(sykmeldingPeriode.endInclusive) }
            .forEach { sok ->
                val klipper = sok.status in listOf(
                    Soknadstatus.FREMTIDIG, Soknadstatus.NY, Soknadstatus.AVBRUTT
                )
                if (klipper) {
                    log.info("Sykmelding $sykmeldingId overlapper søknad ${sok.id} fullstendig")

                    klippetSykepengesoknadRepository.save(
                        KlippetSykepengesoknadDbRecord(
                            sykepengesoknadUuid = sok.id,
                            sykmeldingUuid = sykmeldingId,
                            klippVariant = KlippVariant.SOKNAD_STARTER_FOR_SLUTTER_ETTER,
                            periodeFor = sok.soknadPerioder!!.serialisertTilString(),
                            periodeEtter = null,
                            timestamp = Instant.now(),
                        )
                    )

                    val fullstendigOverlappetSoknad = sok.copy(status = Soknadstatus.SLETTET)
                    sykepengesoknadDAO.slettSoknad(fullstendigOverlappetSoknad)

                    soknadProducer.soknadEvent(fullstendigOverlappetSoknad, null, false)
                }
                val endringIUforegrad = finnEndringIUforegrad(
                    tidligerePerioder = sok.soknadPerioder!!.filter {
                        sykmeldingPeriode.overlap(it.fom..it.tom)
                    },
                    nyePerioder = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.filter {
                        (sok.fom!!..sok.tom!!).overlap(it.fom..it.tom)
                    }.tilSoknadsperioder(),
                )
                klippMetrikk.klippMetrikk(
                    klippMetrikkVariant = KlippVariant.SOKNAD_STARTER_FOR_SLUTTER_ETTER,
                    soknadstatus = sok.status.toString(),
                    sykmeldingId = sykmeldingId,
                    klippet = klipper,
                    endringIUforegrad = endringIUforegrad,
                    eksisterendeSykepengesoknadId = sok.id,
                )
            }
    }

    private fun List<Sykepengesoknad>.klippSoknaderSomOverlapperFor(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,

    ) {
        val sykmeldingId = sykmeldingKafkaMessage.sykmelding.id
        val sykmeldingPeriode = sykmeldingKafkaMessage.periode()
        this.filter { it.fom!!.isAfterOrEqual(sykmeldingPeriode.start) }
            .filter { it.tom!!.isAfter(sykmeldingPeriode.endInclusive) }
            .forEach { sok ->
                val klipper = sok.status in listOf(
                    Soknadstatus.FREMTIDIG, Soknadstatus.NY, Soknadstatus.AVBRUTT
                )
                if (klipper) {
                    log.info(
                        "Sykmelding $sykmeldingId klipper søknad ${sok.id} fom fra: ${sok.fom} til: ${
                        sykmeldingPeriode.endInclusive.plusDays(
                            1
                        )
                        }"
                    )

                    val nyePerioder = sykepengesoknadDAO.klippSoknadFom(
                        sykepengesoknadUuid = sok.id,
                        klipp = sykmeldingPeriode.endInclusive
                    )

                    klippetSykepengesoknadRepository.save(
                        KlippetSykepengesoknadDbRecord(
                            sykepengesoknadUuid = sok.id,
                            sykmeldingUuid = sykmeldingId,
                            klippVariant = KlippVariant.SOKNAD_STARTER_FOR_SLUTTER_INNI,
                            periodeFor = sok.soknadPerioder!!.serialisertTilString(),
                            periodeEtter = nyePerioder.serialisertTilString(),
                            timestamp = Instant.now(),
                        )
                    )

                    if (sok.status != Soknadstatus.FREMTIDIG) {
                        sporsmalGenerator.lagSporsmalPaSoknad(sok.id)
                        val oppdatertSoknad = sykepengesoknadDAO.finnSykepengesoknad(sok.id)
                        soknadProducer.soknadEvent(oppdatertSoknad, null, false)
                    }
                }
                val endringIUforegrad = finnEndringIUforegrad(
                    tidligerePerioder = sok.soknadPerioder!!.filter {
                        sykmeldingPeriode.overlap(it.fom..it.tom)
                    },
                    nyePerioder = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.filter {
                        (sok.fom!!..sok.tom!!).overlap(it.fom..it.tom)
                    }.tilSoknadsperioder(),
                )
                klippMetrikk.klippMetrikk(
                    klippMetrikkVariant = KlippVariant.SOKNAD_STARTER_FOR_SLUTTER_INNI,
                    soknadstatus = sok.status.toString(),
                    sykmeldingId = sykmeldingId,
                    klippet = klipper,
                    eksisterendeSykepengesoknadId = sok.id,
                    endringIUforegrad = endringIUforegrad,
                )
            }
    }

    private fun List<Sykepengesoknad>.klippSoknaderSomOverlapperInni(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
    ) {
        val sykmeldingPeriode = sykmeldingKafkaMessage.periode()
        this.filter { it.fom!!.isBefore(sykmeldingPeriode.start) }
            .filter { it.tom!!.isAfter(sykmeldingPeriode.endInclusive) }
            .forEach { sok ->
                val endringIUforegrad = finnEndringIUforegrad(
                    tidligerePerioder = sok.soknadPerioder!!.filter {
                        sykmeldingPeriode.overlap(it.fom..it.tom)
                    },
                    nyePerioder = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.filter {
                        (sok.fom!!..sok.tom!!).overlap(it.fom..it.tom)
                    }.tilSoknadsperioder(),
                )
                klippMetrikk.klippMetrikk(
                    klippMetrikkVariant = KlippVariant.SOKNAD_STARTER_INNI_SLUTTER_INNI,
                    soknadstatus = sok.status.toString(),
                    sykmeldingId = sykmeldingKafkaMessage.sykmelding.id,
                    klippet = false,
                    eksisterendeSykepengesoknadId = sok.id,
                    endringIUforegrad = endringIUforegrad,
                )
            }
    }

    private fun List<Sykepengesoknad>.klippSykmeldingSomOverlapperEtter(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
    ): SykmeldingKafkaMessage {
        val sykmeldingId = sykmeldingKafkaMessage.sykmelding.id
        var sykmeldingPerioder = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder

        this.sortedBy { it.tom }
            .forEach { sok ->
                val sykPeriode = sykmeldingPerioder.periode()
                val sokPeriode = sok.fom!!..sok.tom!!

                if (sokPeriode.overlap(sykPeriode) &&
                    sok.fom.isBeforeOrEqual(sykPeriode.start) &&
                    sok.tom.isBefore(sykPeriode.endInclusive) &&
                    sok.status == Soknadstatus.SENDT
                ) {
                    val endringIUforegrad = finnEndringIUforegrad(
                        tidligerePerioder = sok.soknadPerioder!!.filter {
                            sykPeriode.overlap(it.fom..it.tom)
                        },
                        nyePerioder = sykmeldingPerioder.filter {
                            sokPeriode.overlap(it.fom..it.tom)
                        }.tilSoknadsperioder(),
                    )

                    val klipper = endringIUforegrad == SAMME_UFØREGRAD
                    if (klipper) {
                        log.info("Sykmelding $sykmeldingId overlapper ${sok.status} søknad ${sok.id} etter ${sykPeriode.start}")

                        val nyeSykmeldingPerioder = sykmeldingPerioder
                            .filterNot { it.fom in sokPeriode && it.tom in sokPeriode }
                            .map {
                                if (it.fom in sokPeriode) {
                                    return@map it.copy(
                                        fom = sokPeriode.endInclusive.plusDays(1)
                                    )
                                }

                                return@map it
                            }

                        klippetSykepengesoknadRepository.save(
                            KlippetSykepengesoknadDbRecord(
                                sykepengesoknadUuid = sok.id,
                                sykmeldingUuid = sykmeldingId,
                                klippVariant = KlippVariant.SYKMELDING_STARTER_FOR_SLUTTER_INNI,
                                periodeFor = sykmeldingPerioder.tilSoknadsperioder().serialisertTilString(),
                                periodeEtter = nyeSykmeldingPerioder.tilSoknadsperioder().serialisertTilString(),
                                timestamp = Instant.now(),
                            )
                        )

                        sykmeldingPerioder = nyeSykmeldingPerioder
                    }
                    klippMetrikk.klippMetrikk(
                        klippMetrikkVariant = KlippVariant.SYKMELDING_STARTER_FOR_SLUTTER_INNI,
                        soknadstatus = sok.status.toString(),
                        sykmeldingId = sykmeldingId,
                        eksisterendeSykepengesoknadId = sok.id,
                        klippet = klipper,
                        endringIUforegrad = endringIUforegrad,
                    )
                }
            }

        if (sykmeldingPerioder.isEmpty()) {
            throw RuntimeException("Kan ikke klippe sykmelding $sykmeldingId med fullstendig overlappende perioder")
        }

        if (sykmeldingPerioder == sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder) {
            return sykmeldingKafkaMessage
        }

        val originalSykmeldingFom = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.minOf { it.fom }
        val nySykmeldingFom = sykmeldingPerioder.minOf { it.fom }
        log.info("Klipper overlappende sykmelding $sykmeldingId, original fom $originalSykmeldingFom ny fom $nySykmeldingFom")

        return sykmeldingKafkaMessage.erstattPerioder(sykmeldingPerioder)
    }

    private fun List<Sykepengesoknad>.klippSykmeldingSomOverlapperFor(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
    ): SykmeldingKafkaMessage {
        val sykmeldingId = sykmeldingKafkaMessage.sykmelding.id
        var sykmeldingPerioder = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder

        this.sortedBy { it.fom }
            .forEach { sok ->
                val sykPeriode = sykmeldingPerioder.periode()
                val sokPeriode = sok.fom!!..sok.tom!!

                if (sokPeriode.overlap(sykPeriode) &&
                    sok.fom.isAfter(sykPeriode.start) &&
                    sok.tom.isAfterOrEqual(sykPeriode.endInclusive)

                ) {
                    var klipper = false
                    val endringIUforegrad = finnEndringIUforegrad(
                        tidligerePerioder = sok.soknadPerioder!!.filter {
                            sykPeriode.overlap(it.fom..it.tom)
                        },
                        nyePerioder = sykmeldingPerioder.filter {
                            sokPeriode.overlap(it.fom..it.tom)
                        }.tilSoknadsperioder(),
                    )

                    if (endringIUforegrad == SAMME_UFØREGRAD && sok.status == Soknadstatus.SENDT) {
                        log.info("Sykmelding $sykmeldingId overlapper ${sok.status} søknad ${sok.id} før ${sykPeriode.endInclusive}")

                        val nyeSykmeldingPerioder = sykmeldingPerioder
                            .filterNot { it.fom in sokPeriode && it.tom in sokPeriode }
                            .map {
                                if (it.tom in sokPeriode) {
                                    return@map it.copy(
                                        tom = sokPeriode.start.minusDays(1)
                                    )
                                }

                                return@map it
                            }
                        klipper = true
                        klippetSykepengesoknadRepository.save(
                            KlippetSykepengesoknadDbRecord(
                                sykepengesoknadUuid = sok.id,
                                sykmeldingUuid = sykmeldingId,
                                klippVariant = KlippVariant.SYKMELDING_STARTER_INNI_SLUTTER_ETTER,
                                periodeFor = sykmeldingPerioder.tilSoknadsperioder().serialisertTilString(),
                                periodeEtter = nyeSykmeldingPerioder.tilSoknadsperioder().serialisertTilString(),
                                timestamp = Instant.now(),
                            )
                        )

                        sykmeldingPerioder = nyeSykmeldingPerioder
                    }
                    klippMetrikk.klippMetrikk(
                        klippMetrikkVariant = KlippVariant.SYKMELDING_STARTER_INNI_SLUTTER_ETTER,
                        soknadstatus = sok.status.toString(),
                        sykmeldingId = sykmeldingId,
                        eksisterendeSykepengesoknadId = sok.id,
                        klippet = klipper,
                        endringIUforegrad = endringIUforegrad,
                    )
                }
            }

        if (sykmeldingPerioder.isEmpty()) {
            throw RuntimeException("Kan ikke klippe sykmelding $sykmeldingId med fullstendig overlappende perioder")
        }

        if (sykmeldingPerioder == sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder) {
            return sykmeldingKafkaMessage
        }

        val originalSykmeldingTom = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.maxOf { it.tom }
        val nySykmeldingTom = sykmeldingPerioder.maxOf { it.tom }
        log.info("Klipper overlappende sykmelding $sykmeldingId, original tom $originalSykmeldingTom ny tom $nySykmeldingTom")

        return sykmeldingKafkaMessage.erstattPerioder(sykmeldingPerioder)
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

    private fun SykmeldingKafkaMessage.periode() = sykmelding.sykmeldingsperioder.periode()

    private fun List<SykmeldingsperiodeAGDTO>.periode(): ClosedRange<LocalDate> {
        val sykmeldingFom = minOf { it.fom }
        val sykmeldingTom = maxOf { it.tom }
        return sykmeldingFom..sykmeldingTom
    }

    private fun SykmeldingKafkaMessage.erstattPerioder(nyePerioder: List<SykmeldingsperiodeAGDTO>) = copy(
        sykmelding = sykmelding.copy(
            sykmeldingsperioder = nyePerioder
        )
    )
}
