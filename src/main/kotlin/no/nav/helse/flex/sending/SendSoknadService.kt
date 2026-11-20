package no.nav.helse.flex.sending

import no.nav.helse.flex.domain.Avsendertype.BRUKER
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.frisktilarbeid.FjernFremtidigeFtaSoknaderService
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.OppholdUtenforEOSService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SendSoknadService(
    private val soknadSender: SoknadSender,
    private val oppholdUtenforEOSService: OppholdUtenforEOSService,
    private val fjernFremtidigeFtaSoknaderService: FjernFremtidigeFtaSoknaderService,
) {
    @Transactional(rollbackFor = [Throwable::class])
    fun sendSoknadFraBruker(
        sykepengesoknad: Sykepengesoknad,
        identer: FolkeregisterIdenter,
    ): Sykepengesoknad {
        val sendtSoknad = soknadSender.sendSoknad(sykepengesoknad, BRUKER, null, identer)

        oppholdUtenforEOSService.skalOppretteSoknadForOppholdUtenforEOS(sendtSoknad, identer)
        fjernFremtidigeFtaSoknaderService.fjernFremtidigeFriskmeldtSoknaderHvisFerdig(sendtSoknad, identer)

        return sendtSoknad
    }
}
