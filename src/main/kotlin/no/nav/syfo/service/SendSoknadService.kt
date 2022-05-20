package no.nav.syfo.service

import no.nav.syfo.domain.Avsendertype
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Soknadstatus.NY
import no.nav.syfo.domain.Soknadstatus.UTKAST_TIL_KORRIGERING
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.kafka.producer.SoknadProducer
import no.nav.syfo.repository.SvarDAO
import no.nav.syfo.repository.SykepengesoknadDAO
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional
class SendSoknadService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val svarDAO: SvarDAO,
    private val mottakerAvSoknadService: MottakerAvSoknadService,
    private val soknadProducer: SoknadProducer
) {
    fun sendSoknad(sykepengesoknad: Sykepengesoknad, avsendertype: Avsendertype, dodsdato: LocalDate?, identer: FolkeregisterIdenter) {
        if (sykepengesoknad.status !in listOf(NY, UTKAST_TIL_KORRIGERING)) {
            throw RuntimeException("Søknad ${sykepengesoknad.id} kan ikke gå i fra status ${sykepengesoknad.status} til SENDT")
        }

        if (sykepengesoknad.sporsmal.isEmpty()) {
            throw RuntimeException("Kan ikke sende soknad ${sykepengesoknad.id} med tom sporsmal liste")
        }

        svarDAO.overskrivSvar(sykepengesoknad)

        if (sykepengesoknad.korrigerer != null) {
            sykepengesoknadDAO.oppdaterKorrigertAv(sykepengesoknad)
        }

        val soknadSomSendes = sykepengesoknadDAO
            .finnSykepengesoknad(sykepengesoknad.id)
            .copy(status = Soknadstatus.SENDT)

        val mottaker = mottakerAvSoknadService.finnMottakerAvSoknad(soknadSomSendes, identer)
        val sendtSoknad = sykepengesoknadDAO.sendSoknad(sykepengesoknad, mottaker, avsendertype)
        soknadProducer.soknadEvent(sendtSoknad, mottaker, false, dodsdato)
    }
}
