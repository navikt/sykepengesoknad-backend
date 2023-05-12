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
        val soknadKandidaterSomKanKlippes = sykepengesoknadDAO.soknadKandidaterSomKanKlippes(
            orgnummer = orgnummer,
            identer = identer,
            sykmeldingKafkaMessage = sykmeldingKafkaMessage
        )

        soknadKandidaterSomKanKlippes.soknaderSomOverlapperEtter(sykmeldingKafkaMessage)

        soknadKandidaterSomKanKlippes.soknaderSomOverlapperFullstendig(sykmeldingKafkaMessage)

        soknadKandidaterSomKanKlippes.soknaderSomOverlapperFor(sykmeldingKafkaMessage)

        soknadKandidaterSomKanKlippes.soknaderSomOverlapperInni(sykmeldingKafkaMessage)
    }

    private fun klippSykmelding(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        orgnummer: String?,
        identer: FolkeregisterIdenter
    ): SykmeldingKafkaMessage {
        var kafkaMessage = sykmeldingKafkaMessage

        val soknadKandidaterSomKanKlippes = sykepengesoknadDAO.soknadKandidaterSomKanKlippes(
            orgnummer = orgnummer,
            identer = identer,
            sykmeldingKafkaMessage = sykmeldingKafkaMessage
        )

        kafkaMessage = soknadKandidaterSomKanKlippes.sykmeldingSomOverlapperSendteSoknaderEtter(kafkaMessage)

        kafkaMessage = soknadKandidaterSomKanKlippes.sykmeldingSomOverlapperSendteSoknaderFor(kafkaMessage)

        val soknadKandidaterSomKanKlippeSykmeldingen = sykepengesoknadDAO.soknadKandidaterSomKanKlippeSykmeldingen(
            orgnummer = orgnummer,
            identer = identer,
            sykmeldingKafkaMessage = kafkaMessage
        )

        kafkaMessage = soknadKandidaterSomKanKlippeSykmeldingen.sykmeldingSomOverlapperMedNyereSoknader(kafkaMessage)

        return kafkaMessage
    }

    private fun List<Sykepengesoknad>.soknaderSomOverlapperEtter(
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

    private fun List<Sykepengesoknad>.soknaderSomOverlapperFullstendig(
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

    private fun List<Sykepengesoknad>.soknaderSomOverlapperFor(
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

    private fun List<Sykepengesoknad>.soknaderSomOverlapperInni(
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

    private fun List<Sykepengesoknad>.sykmeldingSomOverlapperSendteSoknaderEtter(
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
            }

        if (sykmeldingPerioder == sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder) {
            return sykmeldingKafkaMessage
        }

        val originalSykmeldingFom = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.minOf { it.fom }
        val nySykmeldingFom = sykmeldingPerioder.minOf { it.fom }
        log.info("Klipper overlappende sykmelding $sykmeldingId, original fom $originalSykmeldingFom ny fom $nySykmeldingFom")

        return sykmeldingKafkaMessage.erstattPerioder(sykmeldingPerioder)
    }

    private fun List<Sykepengesoknad>.sykmeldingSomOverlapperSendteSoknaderFor(
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
            }

        if (sykmeldingPerioder == sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder) {
            return sykmeldingKafkaMessage
        }

        val originalSykmeldingTom = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.maxOf { it.tom }
        val nySykmeldingTom = sykmeldingPerioder.maxOf { it.tom }
        log.info("Klipper overlappende sykmelding $sykmeldingId, original tom $originalSykmeldingTom ny tom $nySykmeldingTom")

        return sykmeldingKafkaMessage.erstattPerioder(sykmeldingPerioder)
    }

    private fun List<Sykepengesoknad>.sykmeldingSomOverlapperMedNyereSoknader(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage
    ): SykmeldingKafkaMessage {
        val sykmeldingId = sykmeldingKafkaMessage.sykmelding.id
        var sykmeldingPerioder = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder

        this.sortedBy { it.fom }
            .filter {
                it.status in listOf(
                    Soknadstatus.FREMTIDIG,
                    Soknadstatus.NY,
                    Soknadstatus.AVBRUTT,
                    Soknadstatus.SENDT
                )
            }
            .forEach { sok ->
                val sykPeriode = sykmeldingPerioder.periode()
                val sokPeriode = sok.fom!!..sok.tom!!

                if (sokPeriode.overlap(sykPeriode) &&
                    sok.fom.isAfter(sykPeriode.start) &&
                    sok.tom.isAfterOrEqual(sykPeriode.endInclusive)
                ) {
                    log.info("Det er innsendt en eldre sykmelding $sykmeldingId som overlapper med ${sok.status} soknad ${sok.id}, den overlappende delen starter inni og slutter samtidig eller etter")

                    klippMetrikk.klippMetrikk(
                        klippMetrikkVariant = KlippVariant.SYKMELDING_STARTER_INNI_SLUTTER_ETTER,
                        soknadstatus = sok.status.toString(),
                        sykmeldingId = sykmeldingId,
                        eksisterendeSykepengesoknadId = sok.id,
                        klippet = true,
                        endringIUforegrad = finnEndringIUforegrad(
                            tidligerePerioder = sok.soknadPerioder!!.filter {
                                sykPeriode.overlap(it.fom..it.tom)
                            },
                            nyePerioder = sykmeldingPerioder.filter {
                                sokPeriode.overlap(it.fom..it.tom)
                            }.tilSoknadsperioder()
                        )
                    )

                    sykmeldingPerioder = sykmeldingsklipper.klippSykmeldingSomOverlapperFor(
                        sykmeldingId,
                        sok,
                        sykPeriode,
                        sokPeriode,
                        sykmeldingPerioder
                    )
                }

                if (sokPeriode.overlap(sykPeriode) &&
                    sok.fom.isBeforeOrEqual(sykPeriode.start) &&
                    sok.tom.isBefore(sykPeriode.endInclusive)
                ) {
                    log.info("Det er innsendt en eldre sykmelding $sykmeldingId som overlapper med ${sok.status} soknad ${sok.id}, den overlappende delen starter før eller samtidig og slutter inni")

                    klippMetrikk.klippMetrikk(
                        klippMetrikkVariant = KlippVariant.SYKMELDING_STARTER_FOR_SLUTTER_INNI,
                        soknadstatus = sok.status.toString(),
                        sykmeldingId = sykmeldingId,
                        eksisterendeSykepengesoknadId = sok.id,
                        klippet = true,
                        endringIUforegrad = finnEndringIUforegrad(
                            tidligerePerioder = sok.soknadPerioder!!.filter {
                                sykPeriode.overlap(it.fom..it.tom)
                            },
                            nyePerioder = sykmeldingPerioder.filter {
                                sokPeriode.overlap(it.fom..it.tom)
                            }.tilSoknadsperioder()
                        )
                    )

                    sykmeldingPerioder = sykmeldingsklipper.klippSykmeldingSomOverlapperEtter(
                        sykmeldingId,
                        sok,
                        sykPeriode,
                        sokPeriode,
                        sykmeldingPerioder
                    )
                }

                if (sokPeriode.overlap(sykPeriode) &&
                    sok.fom.isBeforeOrEqual(sykPeriode.start) &&
                    sok.tom.isAfterOrEqual(sykPeriode.endInclusive)
                ) {
                    log.info("Det er innsendt en eldre sykmelding $sykmeldingId som overlapper med ${sok.status} soknad ${sok.id}, den overlappende delen starter før eller samtidig og slutter samtidig eller etter")

                    klippMetrikk.klippMetrikk(
                        klippMetrikkVariant = KlippVariant.SYKMELDING_STARTER_FOR_SLUTTER_ETTER,
                        soknadstatus = sok.status.toString(),
                        sykmeldingId = sykmeldingId,
                        eksisterendeSykepengesoknadId = sok.id,
                        klippet = true,
                        endringIUforegrad = finnEndringIUforegrad(
                            tidligerePerioder = sok.soknadPerioder!!.filter {
                                sykPeriode.overlap(it.fom..it.tom)
                            },
                            nyePerioder = sykmeldingPerioder.filter {
                                sokPeriode.overlap(it.fom..it.tom)
                            }.tilSoknadsperioder()
                        )
                    )
                    sykmeldingPerioder = sykmeldingsklipper.klippSykmeldingSomOverlapperInni(
                        sykmeldingId,
                        sok,
                        sykmeldingPerioder
                    )
                }

                if (sokPeriode.overlap(sykPeriode) &&
                    sok.fom.isAfter(sykPeriode.start) &&
                    sok.tom.isBefore(sykPeriode.endInclusive)
                ) {
                    log.info("Det er innsendt en eldre sykmelding $sykmeldingId som overlapper med ${sok.status} soknad ${sok.id}, den overlappende delen starter og slutter inni")

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

        if (sykmeldingPerioder == sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder) {
            return sykmeldingKafkaMessage
        }

        return sykmeldingKafkaMessage.erstattPerioder(sykmeldingPerioder)
    }
}
