package no.nav.helse.flex.sending

import no.nav.helse.flex.domain.*
import no.nav.helse.flex.domain.Soknadstatus.NY
import no.nav.helse.flex.domain.Soknadstatus.UTKAST_TIL_KORRIGERING
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.oppholdUtenforEOS.OppholdUtenforEOSService
import no.nav.helse.flex.repository.SvarDAO
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.repository.normaliser
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.MottakerAvSoknadService
import no.nav.helse.flex.soknadsopprettelse.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional
class SoknadSender(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val svarDAO: SvarDAO,
    private val mottakerAvSoknadService: MottakerAvSoknadService,
    private val soknadProducer: SoknadProducer,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val opprettSoknadService: OpprettSoknadService,
    private val oppholdUtenforEOSService: OppholdUtenforEOSService,
) {
    fun sendSoknad(
        sykepengesoknad: Sykepengesoknad,
        avsendertype: Avsendertype,
        dodsdato: LocalDate?,
        identer: FolkeregisterIdenter,
    ): Sykepengesoknad {
        validerSoknad(sykepengesoknad)

        if (oppholdUtenforEOSService.skalOppretteSoknadForOppholdUtenforEOS(sykepengesoknad)) {
            opprettSoknadService.opprettSoknadUtland(identer)
        }

        svarDAO.overskrivSvar(sykepengesoknad)

        sykepengesoknad.korrigerer?.let {
            sykepengesoknadDAO.oppdaterKorrigertAv(sykepengesoknad)
        }

        val soknadSomSendes =
            sykepengesoknadDAO
                .finnSykepengesoknad(sykepengesoknad.id)
                .copy(status = Soknadstatus.SENDT)

        val mottaker = mottakerAvSoknadService.finnMottakerAvSoknad(soknadSomSendes, identer)
        val sendtSoknad = sykepengesoknadDAO.sendSoknad(sykepengesoknad, mottaker, avsendertype)
        val opprinneligSendt =
            sykepengesoknadRepository
                .findByFnrIn(identer.alle())
                .finnOpprinneligSendt(sendtSoknad.normaliser().soknad)

        soknadProducer.soknadEvent(
            sykepengesoknad = sendtSoknad,
            mottaker = mottaker,
            erEttersending = false,
            dodsdato = dodsdato,
            opprinneligSendt = opprinneligSendt,
        )

        return sendtSoknad
    }

    private fun validerSoknad(sykepengesoknad: Sykepengesoknad) {
        when {
            sykepengesoknad.status !in listOf(NY, UTKAST_TIL_KORRIGERING) ->
                throw RuntimeException("Søknad ${sykepengesoknad.id} kan ikke gå i fra status ${sykepengesoknad.status} til SENDT.")
            sykepengesoknad.sporsmal.isEmpty() ->
                throw RuntimeException("Kan ikke sende soknad ${sykepengesoknad.id} som ikke har spørsmål.")
        }
    }
}
