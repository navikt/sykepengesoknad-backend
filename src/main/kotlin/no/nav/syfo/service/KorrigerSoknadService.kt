package no.nav.syfo.service

import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.soknadsopprettelse.ANSVARSERKLARING
import no.nav.syfo.soknadsopprettelse.BEKREFT_OPPLYSNINGER
import no.nav.syfo.svarvalidering.ValideringException
import no.nav.syfo.util.Metrikk
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
@Transactional
class KorrigerSoknadService(
    val sykepengesoknadDAO: SykepengesoknadDAO,
    val metrikk: Metrikk,
    val identService: IdentService,
) {

    fun finnEllerOpprettUtkast(soknadSomKorrigeres: Sykepengesoknad, identer: FolkeregisterIdenter): Sykepengesoknad {
        if (soknadSomKorrigeres.status != Soknadstatus.SENDT) {
            throw ValideringException("Kan ikke korrigere søknad: ${soknadSomKorrigeres.id} som ikke har status sendt")
        }
        if (soknadSomKorrigeres.soknadstype == Soknadstype.OPPHOLD_UTLAND) {
            throw ValideringException("Kan ikke korrigere søknad av typen Opphold utland")
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
                if (spm.tag == ANSVARSERKLARING || spm.tag == BEKREFT_OPPLYSNINGER) {
                    spm.copy(svar = emptyList())
                } else {
                    spm
                }
            }
        )

        sykepengesoknadDAO.lagreSykepengesoknad(korrigering)
        metrikk.tellUtkastTilKorrigeringOpprettet(korrigering.soknadstype)
        return sykepengesoknadDAO.finnSykepengesoknad(korrigering.id)
    }
}
