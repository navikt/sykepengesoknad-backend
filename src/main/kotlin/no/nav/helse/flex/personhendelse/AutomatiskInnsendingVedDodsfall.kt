package no.nav.helse.flex.personhendelse

import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.flex.aktivering.AktiverEnkeltSoknad
import no.nav.helse.flex.domain.Avsendertype
import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.DodsmeldingDAO
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.sending.SoknadSender
import no.nav.helse.flex.service.AvbrytSoknadService
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.service.MottakerAvSoknadService
import no.nav.helse.flex.util.isAfterOrEqual
import no.nav.helse.flex.util.osloZone
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional
class AutomatiskInnsendingVedDodsfall(
    private val dodsmeldingDAO: DodsmeldingDAO,
    private val registry: MeterRegistry,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val mottakerAvSoknadService: MottakerAvSoknadService,
    private val avbrytSoknadService: AvbrytSoknadService,
    private val soknadSender: SoknadSender,
    private val aktiverEnkeltSoknad: AktiverEnkeltSoknad,
    private val identService: IdentService,
) {
    private val log = logger()

    fun sendSoknaderForDode(): Int {

        val aktorerMed2UkerGammelDodsmelding = dodsmeldingDAO.fnrMedToUkerGammelDodsmelding()

        var antall = 0

        aktorerMed2UkerGammelDodsmelding.forEach { (fnr, dodsDato) ->
            try {
                val identer = automatiskInnsending(fnr, dodsDato)

                dodsmeldingDAO.slettDodsmelding(identer)

                registry.counter("dodsmeldinger_prossesert").increment()

                antall++
            } catch (e: Exception) {
                log.error("Feil ved prossering av dødsmelding for aktor $fnr", e)
            }
        }

        return antall
    }

    fun automatiskInnsending(fnr: String, dodsdato: LocalDate): FolkeregisterIdenter {
        val aatteMndSiden = LocalDate.now(osloZone).minusMonths(8)
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
                    aktiverEnkeltSoknad.aktiverSoknad(sykepengesoknad.id)
                    sykepengesoknad = sykepengesoknadDAO.finnSykepengesoknad(sykepengesoknad.id)
                }

                if (sykepengesoknad.tom!!.isAfterOrEqual(aatteMndSiden)) {
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
