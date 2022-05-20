package no.nav.syfo.kafka.producer

import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.domain.Mottaker
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Soknadstype.*
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.mapper.SykepengesoknadTilSykepengesoknadDTOMapper
import no.nav.syfo.logger
import no.nav.syfo.soknadsopprettelse.ANDRE_INNTEKTSKILDER
import no.nav.syfo.soknadsopprettelse.FRISKMELDT
import no.nav.syfo.soknadsopprettelse.FRISKMELDT_START
import no.nav.syfo.util.Metrikk
import org.springframework.stereotype.Component
import java.text.NumberFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Component
class SoknadProducer(
    private val kafkaProducer: AivenKafkaProducer,
    private val metrikk: Metrikk,
    private val sykepengesoknadTilSykepengesoknadDTOMapper: SykepengesoknadTilSykepengesoknadDTOMapper
) {

    private val log = logger()

    fun soknadEvent(
        sykepengesoknad: Sykepengesoknad,
        mottaker: Mottaker? = null,
        erEttersending: Boolean = false,
        dodsdato: LocalDate? = null
    ) {

        val sykepengesoknadDTO = sykepengesoknadTilSykepengesoknadDTOMapper.mapTilSykepengesoknadDTO(
            sykepengesoknad,
            mottaker,
            erEttersending
        ).copy(dodsdato = dodsdato)

        kafkaProducer.produserMelding(sykepengesoknadDTO)

        // Metrikker
        try {
            if (sykepengesoknad.status == Soknadstatus.SENDT) {
                metrikk.tellSoknadSendt(sykepengesoknad.soknadstype)
            }
            return when (sykepengesoknad.soknadstype) {
                SELVSTENDIGE_OG_FRILANSERE -> {
                    metrikk.prosesserSelvstendigSoknad(sykepengesoknad, sykepengesoknadDTO)
                    metrikkSelvstendigFrilanser(sykepengesoknad)
                }
                ARBEIDSLEDIG -> metrikkArbeidsledig(sykepengesoknad)
                BEHANDLINGSDAGER -> metrikkBehandlingsdager(sykepengesoknad)
                ANNET_ARBEIDSFORHOLD -> metrikkAnnetArbeidsforhold(sykepengesoknad)
                ARBEIDSTAKERE -> metrikkArbeidstaker(sykepengesoknadDTO)
                OPPHOLD_UTLAND, REISETILSKUDD, GRADERT_REISETILSKUDD -> {
                    // Ingen custom metrikker for disse
                }
            }
        } catch (e: Exception) {
            log.warn("Uventet feil ved opptelling av metrikk", e)
        }
    }

    private fun metrikkAnnetArbeidsforhold(soknad: Sykepengesoknad) {
        if (soknad.status == Soknadstatus.SENDT) {
            metrikk.tellDagerFraAktiveringTilInnsending(
                ANNET_ARBEIDSFORHOLD.name,
                beregnDagerBruktPaInnsending(soknad.tom!!)
            )
            if (soknad.startSykeforlop?.isBefore(soknad.fom) == true) {
                metrikk.tellForlengelseSoknadISyketilfelle(ANNET_ARBEIDSFORHOLD.name)
            } else {
                metrikk.tellForsteSoknadISyketilfelle(ANNET_ARBEIDSFORHOLD.name)
            }
        }
    }

    private fun metrikkArbeidsledig(soknad: Sykepengesoknad) {
        if (Soknadstatus.SENDT == soknad.status) {
            metrikk.tellDagerFraAktiveringTilInnsending(
                ARBEIDSLEDIG.name,
                beregnDagerBruktPaInnsending(soknad.tom!!)
            )
            if (soknad.startSykeforlop?.isBefore(soknad.fom) == true) {
                metrikk.tellForlengelseSoknadISyketilfelle(ARBEIDSLEDIG.name)
            } else {
                metrikk.tellForsteSoknadISyketilfelle(ARBEIDSLEDIG.name)
            }
            if (soknad.getSporsmalMedTagOrNull(FRISKMELDT)?.forsteSvar == "NEI") {
                val friskmeldtDato = LocalDate.parse(
                    soknad.getSporsmalMedTagOrNull(FRISKMELDT_START)?.forsteSvar,
                    DateTimeFormatter.ISO_DATE
                )
                val antallFriskmeldteDager = friskmeldtDato.until(soknad.tom, ChronoUnit.DAYS) + 1 // Fordi det er tom
                metrikk.tellAntallFriskmeldteDagerForArbeidsledige(antallFriskmeldteDager)
            }
        }
    }

    private fun metrikkSelvstendigFrilanser(soknad: Sykepengesoknad) {
        if (Soknadstatus.SENDT == soknad.status) {
            metrikk.tellDagerFraAktiveringTilInnsending(
                soknad.soknadstype.name,
                beregnDagerBruktPaInnsending(soknad.tom!!)
            )
            if (soknad.startSykeforlop?.isBefore(soknad.fom) == true) {
                metrikk.tellForlengelseSoknadISyketilfelle(soknad.soknadstype.name)
            } else {
                metrikk.tellForsteSoknadISyketilfelle(soknad.soknadstype.name)
            }
        }
    }

    private fun metrikkBehandlingsdager(soknad: Sykepengesoknad) {
        if (Soknadstatus.SENDT == soknad.status) {
            metrikk.tellDagerFraAktiveringTilInnsending(
                BEHANDLINGSDAGER.name,
                beregnDagerBruktPaInnsending(soknad.tom!!)
            )
            if (soknad.startSykeforlop?.isBefore(soknad.fom) == true) {
                metrikk.tellForlengelseSoknadISyketilfelle(BEHANDLINGSDAGER.name)
            } else {
                metrikk.tellForsteSoknadISyketilfelle(BEHANDLINGSDAGER.name)
            }
        }
    }

    private fun beregnDagerBruktPaInnsending(tom: LocalDate): Long {
        val aktivering = tom.atTime(1, 0)
        val innsending = LocalDateTime.now()
        return Duration.between(aktivering, innsending).toDays()
    }

    private fun metrikkArbeidstaker(soknad: SykepengesoknadDTO) {
        val dagerSpartVedArbeidGjenopptattTidligere =
            finnDagerSpartVedArbeidGjenopptattTidligere(soknad.arbeidGjenopptatt, soknad.soknadsperioder)
        val dagerSpartFordiJobbetMerEnnSoknadTilsier =
            finnDagerSpartFordiJobbetMerEnnSoknadTilsier(soknad.arbeidGjenopptatt, soknad.soknadsperioder!!)

        val nf = NumberFormat.getNumberInstance()
        nf.maximumFractionDigits = 2
        if (dagerSpartVedArbeidGjenopptattTidligere > 0 || dagerSpartFordiJobbetMerEnnSoknadTilsier > 0) {
            metrikk.tellSoknadMedSparteDagsverk()
            if (dagerSpartVedArbeidGjenopptattTidligere > 0) {
                metrikk.antallDagsverkSpart("tilbake_for_tiden", dagerSpartVedArbeidGjenopptattTidligere)
            }
            if (dagerSpartFordiJobbetMerEnnSoknadTilsier > 0) {
                metrikk.antallDagsverkSpart("jobbet_mer", dagerSpartFordiJobbetMerEnnSoknadTilsier)
            }
        }

        if (soknad.sendtArbeidsgiver != null && soknad.sendtNav == null) {
            metrikk.tellSoknaderKunSendtTilArbeidsgiver()
        }

        if (SoknadsstatusDTO.SENDT == soknad.status) {
            metrikk.tellDagerFraAktiveringTilInnsending(
                ARBEIDSTAKERE.name,
                beregnDagerBruktPaInnsending(soknad.tom!!)
            )
            if (soknad.startSyketilfelle != null && soknad.startSyketilfelle!!.isBefore(soknad.fom!!)) {
                metrikk.tellForlengelseSoknadISyketilfelle(ARBEIDSTAKERE.name)
            } else {
                metrikk.tellForsteSoknadISyketilfelle(ARBEIDSTAKERE.name)
            }
            if (soknad.harFlereInntektsKilder()) {
                metrikk.tellSoknadMedFlereInntektsKilder()
            }
        }
    }
}

fun SykepengesoknadDTO.harFlereInntektsKilder(): Boolean {
    return sporsmal?.firstOrNull { spm ->
        spm.tag == ANDRE_INNTEKTSKILDER && spm.svar?.firstOrNull()?.verdi == "JA"
    }?.undersporsmal?.firstOrNull()?.undersporsmal?.any { inntektSpm ->
        inntektSpm.svar?.firstOrNull()?.verdi == "CHECKED"
    } ?: false
}

fun finnDagerSpartVedArbeidGjenopptattTidligere(
    arbeidGjenopptatt: LocalDate?,
    soknadsperioder: List<SoknadsperiodeDTO>?
): Double {
    var dagerSpart = 0.0
    if (arbeidGjenopptatt != null) {
        for ((_, tom, sykmeldingsgrad) in soknadsperioder!!) {
            if (!arbeidGjenopptatt.isAfter(tom!!)) {
                dagerSpart = dagerSpart + (Period.between(arbeidGjenopptatt, tom).days + 1) * sykmeldingsgrad!! / 100.0
            }
        }
    }
    return dagerSpart
}

fun finnDagerSpartFordiJobbetMerEnnSoknadTilsier(
    arbeidGjenopptatt: LocalDate?,
    soknadsperioder: List<SoknadsperiodeDTO>
): Double {
    var dagerSpart = 0.0
    for ((fom, tom, sykmeldingsgrad, faktiskGrad) in soknadsperioder) {
        if (faktiskGrad != null) {
            val sisteDagIPeriode: LocalDate?
            if (arbeidGjenopptatt == null) {
                sisteDagIPeriode = tom
            } else if (arbeidGjenopptatt.isBefore(tom!!)) {
                sisteDagIPeriode = arbeidGjenopptatt
            } else {
                sisteDagIPeriode = tom
            }
            dagerSpart = dagerSpart + (sykmeldingsgrad!! - (100 - faktiskGrad)) * (
                Period.between(
                    fom!!,
                    sisteDagIPeriode!!
                ).days + 1
                ) / 100.0
        }
    }
    return dagerSpart
}
