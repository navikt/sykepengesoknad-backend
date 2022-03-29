package no.nav.syfo.service

import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.kafka.producer.SoknadProducer
import no.nav.syfo.logger
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.util.Metrikk
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional
class AvbrytSoknadService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val metrikk: Metrikk,
    private val soknadProducer: SoknadProducer
) {
    val log = logger()

    private fun Sykepengesoknad.avbrytOgPubliser() {
        sykepengesoknadDAO.avbrytSoknad(this, LocalDate.now())
        soknadProducer.soknadEvent(sykepengesoknadDAO.finnSykepengesoknad(this.id))
    }

    fun avbrytSoknad(sykepengesoknad: Sykepengesoknad) {
        when (sykepengesoknad.status) {
            Soknadstatus.UTKAST_TIL_KORRIGERING -> {
                sykepengesoknadDAO.slettSoknad(sykepengesoknad)
                metrikk.utkastTilKorrigeringAvbrutt()
            }
            Soknadstatus.NY, Soknadstatus.FREMTIDIG -> {
                metrikk.soknadAvbrutt(sykepengesoknad.soknadstype)
                return when (sykepengesoknad.soknadstype) {
                    Soknadstype.OPPHOLD_UTLAND -> {
                        sykepengesoknadDAO.slettSoknad(sykepengesoknad)
                    }
                    Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
                    Soknadstype.ARBEIDSLEDIG,
                    Soknadstype.ANNET_ARBEIDSFORHOLD,
                    Soknadstype.BEHANDLINGSDAGER,
                    Soknadstype.REISETILSKUDD,
                    Soknadstype.GRADERT_REISETILSKUDD,
                    Soknadstype.ARBEIDSTAKERE -> {
                        sykepengesoknad.avbrytOgPubliser()
                    }
                }
            }
            else -> {
                log.error("Kan ikke avbryte søknad med status: {}", sykepengesoknad.status)
                throw IllegalArgumentException("Kan ikke avbryte søknad med status: " + sykepengesoknad.status)
            }
        }
    }
}
