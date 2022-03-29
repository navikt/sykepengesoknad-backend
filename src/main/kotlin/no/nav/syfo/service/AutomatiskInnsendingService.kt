package no.nav.syfo.service

import no.nav.syfo.domain.Avsendertype
import no.nav.syfo.domain.Mottaker
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.logger
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.util.isAfterOrEqual
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional
class AutomatiskInnsendingService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val mottakerAvSoknadService: MottakerAvSoknadService,
    private val avbrytSoknadService: AvbrytSoknadService,
    private val sendSoknadService: SendSoknadService,
    private val aktiverEnkeltSoknadService: AktiverEnkeltSoknadService,
    private val identService: IdentService,
) {
    val log = logger()

    fun automatiskInnsending(aktorId: String, dodsdato: LocalDate) {
        val treMndSiden = LocalDate.now().minusMonths(3)
        val identer = identService.hentFolkeregisterIdenterMedHistorikkForAktorid(aktorId)
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
                    val mottaker = mottakerAvSoknadService.finnMottakerAvSoknad(sykepengesoknad, identer.tilFolkergisteridenter())

                    if (Mottaker.ARBEIDSGIVER == mottaker) {
                        avbrytSoknadService.avbrytSoknad(sykepengesoknad)
                        log.info("Avbryt søknad med id: ${sykepengesoknad.id} pga arbeidsgiver mottaker")
                    } else {
                        log.info("Automatisk innsending av søknad med id: ${sykepengesoknad.id}")
                        sendSoknadService.sendSoknad(sykepengesoknad, Avsendertype.SYSTEM, dodsdato, identer.tilFolkergisteridenter())
                    }

                    return@forEach
                }

                log.info("Automatisk innsending av søknad ignorerer søknader eldre enn 3 måneder ${sykepengesoknad.id}")
            }
    }
}

private fun List<String>.tilFolkergisteridenter(): FolkeregisterIdenter {
    val originalIdent = this.first()
    return FolkeregisterIdenter(originalIdent = originalIdent, andreIdenter = this.filterNot { it == originalIdent })
}
