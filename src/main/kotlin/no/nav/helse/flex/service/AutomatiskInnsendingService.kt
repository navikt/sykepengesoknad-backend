package no.nav.helse.flex.service

import no.nav.helse.flex.domain.Avsendertype
import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.sending.SoknadSender
import no.nav.helse.flex.util.isAfterOrEqual
import no.nav.helse.flex.util.osloZone
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional
class AutomatiskInnsendingService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val mottakerAvSoknadService: MottakerAvSoknadService,
    private val avbrytSoknadService: AvbrytSoknadService,
    private val soknadSender: SoknadSender,
    private val aktiverEnkeltSoknadService: AktiverEnkeltSoknadService,
    private val identService: IdentService,
) {
    val log = logger()

    fun automatiskInnsending(fnr: String, dodsdato: LocalDate): FolkeregisterIdenter {
        val treMndSiden = LocalDate.now(osloZone).minusMonths(3)
        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(fnr)

        sykepengesoknadDAO.finnSykepengesoknader(identer)
            .filter { it.sykmeldingId != null }
            .filter { Soknadstatus.NY == it.status || Soknadstatus.FREMTIDIG == it.status }
            .forEach {
                var sykepengesoknad = it

                if (sykepengesoknad.fom!!.isAfter(dodsdato)) {
                    avbrytSoknadService.avbrytSoknad(sykepengesoknad)
                    log.info("Avbryt søknad med id: ${sykepengesoknad.id} pga fom etter dødsdato")
                    return@forEach
                }

                if (sykepengesoknad.status == Soknadstatus.FREMTIDIG) {
                    aktiverEnkeltSoknadService.aktiverSoknad(sykepengesoknad.id)
                    sykepengesoknad = sykepengesoknadDAO.finnSykepengesoknad(sykepengesoknad.id)
                }

                if (sykepengesoknad.tom!!.isAfterOrEqual(treMndSiden)) {
                    val mottaker = mottakerAvSoknadService.finnMottakerAvSoknad(sykepengesoknad, identer)

                    if (Mottaker.ARBEIDSGIVER == mottaker) {
                        avbrytSoknadService.avbrytSoknad(sykepengesoknad)
                        log.info("Avbryt søknad med id: ${sykepengesoknad.id} pga arbeidsgiver mottaker")
                    } else {
                        log.info("Automatisk innsending av søknad med id: ${sykepengesoknad.id}")
                        soknadSender.sendSoknad(sykepengesoknad, Avsendertype.SYSTEM, dodsdato, identer)
                    }

                    return@forEach
                }

                log.info("Automatisk innsending av søknad ignorerer søknader eldre enn 3 måneder ${sykepengesoknad.id}")
            }
        return identer
    }
}
