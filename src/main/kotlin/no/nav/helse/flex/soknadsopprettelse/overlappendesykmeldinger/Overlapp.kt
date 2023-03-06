package no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger

import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.KlippVariant
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.soknadsopprettelse.tilSoknadsperioder
import no.nav.helse.flex.util.isAfterOrEqual
import no.nav.helse.flex.util.isBeforeOrEqual
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class Overlapp(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val klippMetrikk: KlippMetrikk,
    private val soknadsklipper: Soknadsklipper,
    private val sykmeldingsklipper: Sykmeldingsklipper
) {
    private val log = logger()

    fun klipp(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        arbeidsgiverStatusDTO: ArbeidsgiverStatusDTO?,
        identer: FolkeregisterIdenter
    ): SykmeldingKafkaMessage {
        klippEksisterendeSoknader(
            sykmeldingKafkaMessage = sykmeldingKafkaMessage,
            orgnummer = arbeidsgiverStatusDTO?.orgnummer,
            identer = identer
        )

        return klippSykmelding(
            sykmeldingKafkaMessage = sykmeldingKafkaMessage,
            orgnummer = arbeidsgiverStatusDTO?.orgnummer,
            identer = identer
        )
    }

    private fun klippEksisterendeSoknader(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        orgnummer: String?,
        identer: FolkeregisterIdenter
    ) {
        val soknadKandidater = sykepengesoknadDAO.soknadKandidater(
            orgnummer = orgnummer,
            identer = identer,
            sykmeldingKafkaMessage = sykmeldingKafkaMessage
        )

        soknadKandidater.finnSoknaderSomOverlapperEtter(sykmeldingKafkaMessage)

        soknadKandidater.finnSoknaderSomOverlapperFullstendig(sykmeldingKafkaMessage)

        soknadKandidater.finnSoknaderSomOverlapperFor(sykmeldingKafkaMessage)

        soknadKandidater.finnSoknaderSomOverlapperInni(sykmeldingKafkaMessage)
    }

    private fun klippSykmelding(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        orgnummer: String?,
        identer: FolkeregisterIdenter
    ): SykmeldingKafkaMessage {
        var kafkaMessage = sykmeldingKafkaMessage

        val soknadKandidater = sykepengesoknadDAO.soknadKandidater(
            orgnummer = orgnummer,
            identer = identer,
            sykmeldingKafkaMessage = sykmeldingKafkaMessage
        )

        kafkaMessage = soknadKandidater.finnSykmeldingSomOverlapperEtter(kafkaMessage)

        kafkaMessage = soknadKandidater.finnSykmeldingSomOverlapperFor(kafkaMessage)

        kafkaMessage = soknadKandidater.finnSykmeldingSomOverlapperFullstendig(kafkaMessage)

        kafkaMessage = soknadKandidater.finnSykmeldingSomOverlapperInni(kafkaMessage)

        return kafkaMessage
    }

    private fun List<Sykepengesoknad>.finnSoknaderSomOverlapperEtter(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage
    ) {
        val sykmeldingId = sykmeldingKafkaMessage.sykmelding.id
        val sykmeldingPeriode = sykmeldingKafkaMessage.periode()
        this.filter { it.fom!!.isBefore(sykmeldingPeriode.start) }
            .filter { it.tom!!.isBeforeOrEqual(sykmeldingPeriode.endInclusive) }
            .forEach { sok ->
                val klipper = sok.status in listOf(
                    Soknadstatus.FREMTIDIG,
                    Soknadstatus.NY,
                    Soknadstatus.AVBRUTT
                )
                if (klipper) {
                    soknadsklipper.klippSoknaderSomOverlapperEtter(
                        sykmeldingId,
                        sok,
                        sykmeldingPeriode
                    )
                }

                if (sok.status != Soknadstatus.SENDT) {
                    klippMetrikk.klippMetrikk(
                        klippMetrikkVariant = KlippVariant.SOKNAD_STARTER_INNI_SLUTTER_ETTER,
                        soknadstatus = sok.status.toString(),
                        sykmeldingId = sykmeldingId,
                        klippet = klipper,
                        endringIUforegrad = finnEndringIUforegrad(
                            tidligerePerioder = sok.soknadPerioder!!.filter {
                                sykmeldingPeriode.overlap(it.fom..it.tom)
                            },
                            nyePerioder = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.filter {
                                (sok.fom!!..sok.tom!!).overlap(it.fom..it.tom)
                            }.tilSoknadsperioder()
                        ),
                        eksisterendeSykepengesoknadId = sok.id
                    )
                }
            }
    }

    private fun List<Sykepengesoknad>.finnSoknaderSomOverlapperFullstendig(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage
    ) {
        val sykmeldingId = sykmeldingKafkaMessage.sykmelding.id
        val sykmeldingPeriode = sykmeldingKafkaMessage.periode()
        this.filter { it.fom!!.isAfterOrEqual(sykmeldingPeriode.start) }
            .filter { it.tom!!.isBeforeOrEqual(sykmeldingPeriode.endInclusive) }
            .forEach { sok ->
                val klipper = sok.status in listOf(
                    Soknadstatus.FREMTIDIG,
                    Soknadstatus.NY,
                    Soknadstatus.AVBRUTT
                )
                if (klipper) {
                    soknadsklipper.klippSoknaderSomOverlapperFullstendig(
                        sykmeldingId,
                        sok
                    )
                }

                if (sok.status != Soknadstatus.SENDT) {
                    klippMetrikk.klippMetrikk(
                        klippMetrikkVariant = KlippVariant.SOKNAD_STARTER_FOR_SLUTTER_ETTER,
                        soknadstatus = sok.status.toString(),
                        sykmeldingId = sykmeldingId,
                        klippet = klipper,
                        endringIUforegrad = finnEndringIUforegrad(
                            tidligerePerioder = sok.soknadPerioder!!.filter {
                                sykmeldingPeriode.overlap(it.fom..it.tom)
                            },
                            nyePerioder = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.filter {
                                (sok.fom!!..sok.tom!!).overlap(it.fom..it.tom)
                            }.tilSoknadsperioder()
                        ),
                        eksisterendeSykepengesoknadId = sok.id
                    )
                }
            }
    }

    private fun List<Sykepengesoknad>.finnSoknaderSomOverlapperFor(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage
    ) {
        val sykmeldingId = sykmeldingKafkaMessage.sykmelding.id
        val sykmeldingPeriode = sykmeldingKafkaMessage.periode()
        this.filter { it.fom!!.isAfterOrEqual(sykmeldingPeriode.start) }
            .filter { it.tom!!.isAfter(sykmeldingPeriode.endInclusive) }
            .forEach { sok ->
                val klipper = sok.status in listOf(
                    Soknadstatus.FREMTIDIG,
                    Soknadstatus.NY,
                    Soknadstatus.AVBRUTT
                )
                if (klipper) {
                    soknadsklipper.klippSoknaderSomOverlapperFor(
                        sykmeldingId,
                        sok,
                        sykmeldingPeriode
                    )
                }

                if (sok.status != Soknadstatus.SENDT) {
                    klippMetrikk.klippMetrikk(
                        klippMetrikkVariant = KlippVariant.SOKNAD_STARTER_FOR_SLUTTER_INNI,
                        soknadstatus = sok.status.toString(),
                        sykmeldingId = sykmeldingId,
                        klippet = klipper,
                        eksisterendeSykepengesoknadId = sok.id,
                        endringIUforegrad = finnEndringIUforegrad(
                            tidligerePerioder = sok.soknadPerioder!!.filter {
                                sykmeldingPeriode.overlap(it.fom..it.tom)
                            },
                            nyePerioder = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.filter {
                                (sok.fom!!..sok.tom!!).overlap(it.fom..it.tom)
                            }.tilSoknadsperioder()
                        )
                    )
                }
            }
    }

    private fun List<Sykepengesoknad>.finnSoknaderSomOverlapperInni(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage
    ) {
        val sykmeldingId = sykmeldingKafkaMessage.sykmelding.id
        val sykmeldingPeriode = sykmeldingKafkaMessage.periode()
        this.filter { it.fom!!.isBefore(sykmeldingPeriode.start) }
            .filter { it.tom!!.isAfter(sykmeldingPeriode.endInclusive) }
            .forEach { sok ->
                val klipper = sok.status in listOf(
                    Soknadstatus.FREMTIDIG,
                    Soknadstatus.NY,
                    Soknadstatus.AVBRUTT
                )
                if (klipper) {
                    soknadsklipper.klippSoknaderSomOverlapperInni(
                        sykmeldingId,
                        sok,
                        sykmeldingPeriode
                    )
                }

                if (sok.status != Soknadstatus.SENDT) {
                    klippMetrikk.klippMetrikk(
                        klippMetrikkVariant = KlippVariant.SOKNAD_STARTER_INNI_SLUTTER_INNI,
                        soknadstatus = sok.status.toString(),
                        sykmeldingId = sykmeldingKafkaMessage.sykmelding.id,
                        klippet = klipper,
                        eksisterendeSykepengesoknadId = sok.id,
                        endringIUforegrad = finnEndringIUforegrad(
                            tidligerePerioder = sok.soknadPerioder!!.filter {
                                sykmeldingPeriode.overlap(it.fom..it.tom)
                            },
                            nyePerioder = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.filter {
                                (sok.fom!!..sok.tom!!).overlap(it.fom..it.tom)
                            }.tilSoknadsperioder()
                        )
                    )
                }
            }
    }

    private fun List<Sykepengesoknad>.finnSykmeldingSomOverlapperEtter(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage
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
                        }.tilSoknadsperioder()
                    )

                    val klipper = endringIUforegrad == EndringIUforegrad.SAMME_UFØREGRAD
                    if (klipper) {
                        sykmeldingPerioder = sykmeldingsklipper.klippSykmeldingSomOverlapperEtter(
                            sykmeldingId,
                            sok,
                            sykPeriode,
                            sokPeriode,
                            sykmeldingPerioder
                        )
                    }

                    klippMetrikk.klippMetrikk(
                        klippMetrikkVariant = KlippVariant.SYKMELDING_STARTER_FOR_SLUTTER_INNI,
                        soknadstatus = sok.status.toString(),
                        sykmeldingId = sykmeldingId,
                        eksisterendeSykepengesoknadId = sok.id,
                        klippet = klipper,
                        endringIUforegrad = endringIUforegrad
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

    private fun List<Sykepengesoknad>.finnSykmeldingSomOverlapperFor(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage
    ): SykmeldingKafkaMessage {
        val sykmeldingId = sykmeldingKafkaMessage.sykmelding.id
        var sykmeldingPerioder = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder

        this.sortedBy { it.fom }
            .forEach { sok ->
                val sykPeriode = sykmeldingPerioder.periode()
                val sokPeriode = sok.fom!!..sok.tom!!

                if (sokPeriode.overlap(sykPeriode) &&
                    sok.fom.isAfter(sykPeriode.start) &&
                    sok.tom.isAfterOrEqual(sykPeriode.endInclusive) &&
                    sok.status == Soknadstatus.SENDT
                ) {
                    val endringIUforegrad = finnEndringIUforegrad(
                        tidligerePerioder = sok.soknadPerioder!!.filter {
                            sykPeriode.overlap(it.fom..it.tom)
                        },
                        nyePerioder = sykmeldingPerioder.filter {
                            sokPeriode.overlap(it.fom..it.tom)
                        }.tilSoknadsperioder()
                    )

                    val klipper = endringIUforegrad == EndringIUforegrad.SAMME_UFØREGRAD
                    if (klipper) {
                        sykmeldingPerioder = sykmeldingsklipper.klippSykmeldingSomOverlapperFor(
                            sykmeldingId,
                            sok,
                            sykPeriode,
                            sokPeriode,
                            sykmeldingPerioder
                        )
                    }
                    klippMetrikk.klippMetrikk(
                        klippMetrikkVariant = KlippVariant.SYKMELDING_STARTER_INNI_SLUTTER_ETTER,
                        soknadstatus = sok.status.toString(),
                        sykmeldingId = sykmeldingId,
                        eksisterendeSykepengesoknadId = sok.id,
                        klippet = klipper,
                        endringIUforegrad = endringIUforegrad
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

    private fun List<Sykepengesoknad>.finnSykmeldingSomOverlapperFullstendig(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage
    ): SykmeldingKafkaMessage {
        val sykmeldingId = sykmeldingKafkaMessage.sykmelding.id
        val sykmeldingPerioder = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder

        this.sortedBy { it.fom }
            .forEach { sok ->
                val sykPeriode = sykmeldingPerioder.periode()
                val sokPeriode = sok.fom!!..sok.tom!!

                if (sokPeriode.overlap(sykPeriode) &&
                    sok.fom.isAfter(sykPeriode.start) &&
                    sok.tom.isBefore(sykPeriode.endInclusive) &&
                    sok.status == Soknadstatus.SENDT
                ) {
                    klippMetrikk.klippMetrikk(
                        klippMetrikkVariant = KlippVariant.SYKMELDING_STARTER_INNI_SLUTTER_INNI,
                        soknadstatus = sok.status.toString(),
                        sykmeldingId = sykmeldingId,
                        eksisterendeSykepengesoknadId = sok.id,
                        klippet = false,
                        endringIUforegrad = finnEndringIUforegrad(
                            tidligerePerioder = sok.soknadPerioder!!.filter {
                                sykPeriode.overlap(it.fom..it.tom)
                            },
                            nyePerioder = sykmeldingPerioder.filter {
                                sokPeriode.overlap(it.fom..it.tom)
                            }.tilSoknadsperioder()
                        )
                    )
                }
            }

        return sykmeldingKafkaMessage
    }

    private fun List<Sykepengesoknad>.finnSykmeldingSomOverlapperInni(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage
    ): SykmeldingKafkaMessage {
        val sykmeldingId = sykmeldingKafkaMessage.sykmelding.id
        val sykmeldingPerioder = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder

        this.sortedBy { it.fom }
            .forEach { sok ->
                val sykPeriode = sykmeldingPerioder.periode()
                val sokPeriode = sok.fom!!..sok.tom!!

                if (sokPeriode.overlap(sykPeriode) &&
                    sok.fom.isBeforeOrEqual(sykPeriode.start) &&
                    sok.tom.isAfterOrEqual(sykPeriode.endInclusive) &&
                    sok.status == Soknadstatus.SENDT
                ) {
                    klippMetrikk.klippMetrikk(
                        klippMetrikkVariant = KlippVariant.SYKMELDING_STARTER_FOR_SLUTTER_ETTER,
                        soknadstatus = sok.status.toString(),
                        sykmeldingId = sykmeldingId,
                        eksisterendeSykepengesoknadId = sok.id,
                        klippet = false,
                        endringIUforegrad = finnEndringIUforegrad(
                            tidligerePerioder = sok.soknadPerioder!!.filter {
                                sykPeriode.overlap(it.fom..it.tom)
                            },
                            nyePerioder = sykmeldingPerioder.filter {
                                sokPeriode.overlap(it.fom..it.tom)
                            }.tilSoknadsperioder()
                        )
                    )
                }
            }

        return sykmeldingKafkaMessage
    }
}
