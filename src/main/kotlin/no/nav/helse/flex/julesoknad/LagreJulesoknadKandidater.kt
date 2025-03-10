package no.nav.helse.flex.julesoknad

import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.Sykmeldingstype
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.JulesoknadkandidatDAO
import no.nav.helse.flex.util.isAfterOrEqual
import no.nav.helse.flex.util.isBeforeOrEqual
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS

@Service
class LagreJulesoknadKandidater(
    private val julesoknadkandidatDAO: JulesoknadkandidatDAO,
) {
    private val log = logger()

    @Transactional
    fun lagreJulesoknadKandidater(list: List<Sykepengesoknad>) {
        if (list.any { p -> p.soknadPerioder!!.any { it.sykmeldingstype == Sykmeldingstype.REISETILSKUDD } }) {
            return
        }

        list.forEach {
            if (it.harSoknadstypeSomKanGiJulesoknad() &&
                it.erLengreEnn14Dager() &&
                it.fomDatoMellom15novemberOg7desember() &&
                it.tomDatoEtter13Desember()
            ) {
                log.info("Sykmelding ${it.sykmeldingId} har søknad som kan kanskje omfattes av julesøknadregler")
                julesoknadkandidatDAO.lagreJulesoknadkandidat(sykepengesoknadUuid = it.id)
            }
        }
    }

    private fun Sykepengesoknad.harSoknadstypeSomKanGiJulesoknad(): Boolean =
        !this.soknadPerioder!!.any { it.sykmeldingstype === no.nav.helse.flex.domain.Sykmeldingstype.BEHANDLINGSDAGER } &&
            !this.soknadPerioder.any { it.sykmeldingstype === no.nav.helse.flex.domain.Sykmeldingstype.REISETILSKUDD } &&
            this.soknadstype != Soknadstype.GRADERT_REISETILSKUDD

    // DAYS.between er inclusive for fom, men exclusive på tom og vil derfor svare med 14 dager for en søknad
    // med fom 01. til tom 15. (som er en 15 dager lang søknad).
    private fun Sykepengesoknad.erLengreEnn14Dager(): Boolean = DAYS.between(this.fom, this.tom) >= 14

    private fun Sykepengesoknad.fomDatoMellom15novemberOg7desember(): Boolean =
        this.fom!!.isBeforeOrEqual(LocalDate.of(this.fom.year, 12, 7)) &&
            this.fom.isAfterOrEqual(LocalDate.of(this.fom.year, 11, 15))

    private fun Sykepengesoknad.tomDatoEtter13Desember(): Boolean = this.tom!!.isAfter(LocalDate.of(this.fom!!.year, 12, 13))
}
