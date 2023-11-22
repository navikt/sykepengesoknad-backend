package no.nav.helse.flex.service

import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.exception.AbstractApiError
import no.nav.helse.flex.exception.LogLevel
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadDbRecord
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.repository.normaliser
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER
import no.nav.helse.flex.soknadsopprettelse.TIL_SLUTT
import no.nav.helse.flex.svarvalidering.ValideringException
import no.nav.helse.flex.util.Metrikk
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

@Service
@Transactional
class KorrigerSoknadService(
    val sykepengesoknadDAO: SykepengesoknadDAO,
    val metrikk: Metrikk,
    val identService: IdentService,
    val sykepengesoknadRepository: SykepengesoknadRepository
) {

    fun finnEllerOpprettUtkast(soknadSomKorrigeres: Sykepengesoknad, identer: FolkeregisterIdenter): Sykepengesoknad {
        if (soknadSomKorrigeres.status != Soknadstatus.SENDT) {
            throw ValideringException("Kan ikke korrigere søknad: ${soknadSomKorrigeres.id} som ikke har status sendt")
        }
        if (soknadSomKorrigeres.soknadstype == Soknadstype.OPPHOLD_UTLAND) {
            throw ValideringException("Kan ikke korrigere søknad av typen Opphold utland")
        }
        val utvidetSoknad = utvidSoknadMedKorrigeringsfristUtlopt(soknadSomKorrigeres, identer)
        if (utvidetSoknad.korrigeringsfristUtlopt == true) {
            throw KorrigeringsfristUtloptException(soknadSomKorrigeres)
        }

        return sykepengesoknadDAO.finnSykepengesoknader(identer)
            .firstOrNull { soknad -> soknadSomKorrigeres.id == soknad.korrigerer }
            ?: opprettUtkast(soknadSomKorrigeres)
    }

    private fun opprettUtkast(soknadSomKorrigeres: Sykepengesoknad): Sykepengesoknad {
        val korrigering = soknadSomKorrigeres.copy(
            id = UUID.randomUUID().toString(),
            status = Soknadstatus.UTKAST_TIL_KORRIGERING,
            opprettet = Instant.now(),
            sendtNav = null,
            sendtArbeidsgiver = null,
            korrigerer = soknadSomKorrigeres.id,
            sporsmal = soknadSomKorrigeres.sporsmal.map { spm ->
                when (spm.tag) {
                    ANSVARSERKLARING, BEKREFT_OPPLYSNINGER -> {
                        spm.copy(svar = emptyList())
                    }
                    TIL_SLUTT -> {
                        val endretUndersporsmal = spm.undersporsmal.mapIndexed { index, underspm ->
                            if (index == 0) {
                                underspm.copy(svar = emptyList())
                            } else {
                                underspm
                            }
                        }
                        spm.copy(svar = emptyList(), undersporsmal = endretUndersporsmal)
                    }
                    else -> {
                        spm
                    }
                }
            }

        )

        sykepengesoknadDAO.lagreSykepengesoknad(korrigering)
        metrikk.tellUtkastTilKorrigeringOpprettet(korrigering.soknadstype)
        return sykepengesoknadDAO.finnSykepengesoknad(korrigering.id)
    }

    fun utvidSoknadMedKorrigeringsfristUtlopt(
        sykepengesoknad: Sykepengesoknad,
        identer: FolkeregisterIdenter
    ): Sykepengesoknad {
        val soknader = sykepengesoknadRepository.findByFnrIn(identer.alle())
        val sykepengesoknadDbRecord = sykepengesoknad.normaliser().soknad
        soknader.finnTidligsteSendt(sykepengesoknadDbRecord)?.let {
            return sykepengesoknad.copy(
                korrigeringsfristUtlopt = OffsetDateTime.now().minusMonths(12).isAfter(
                    it.atOffset(
                        ZoneOffset.UTC
                    )
                )
            )
        }

        return sykepengesoknad
    }
}

fun List<SykepengesoknadDbRecord>.finnTidligsteSendt(soknad: SykepengesoknadDbRecord): Instant? {
    if (soknad.korrigerer != null) {
        return this.finnOpprinneligSendt(soknad.korrigerer)
    }
    return soknad.sendt
}

fun List<SykepengesoknadDbRecord>.finnOpprinneligSendt(korrigerer: String): Instant? {
    val opprinnelig = this.firstOrNull { it.sykepengesoknadUuid == korrigerer }
        ?: throw RuntimeException("Forventa å finne søknad med id $korrigerer")

    if (opprinnelig.korrigerer != null) {
        return finnOpprinneligSendt(opprinnelig.korrigerer)
    }

    return opprinnelig.sendt
}

class KorrigeringsfristUtloptException(soknad: Sykepengesoknad) : AbstractApiError(
    message = "Kan ikke korrigere søknad: ${soknad.id} som har korrigeringsfrist utløpt",
    httpStatus = HttpStatus.BAD_REQUEST,
    reason = "KORRIGERINGSFRIST_UTLOPT",
    loglevel = LogLevel.ERROR
)
