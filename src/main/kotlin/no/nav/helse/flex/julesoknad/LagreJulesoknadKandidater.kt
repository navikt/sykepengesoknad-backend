package no.nav.helse.flex.julesoknad

import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykmeldingstype
import no.nav.helse.flex.domain.rest.SoknadMetadata
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
    fun lagreJulesoknadKandidater(list: List<SoknadMetadata>) {

        if (list.any { p -> p.sykmeldingsperioder.any { it.sykmeldingstype == Sykmeldingstype.REISETILSKUDD } }) {
            return
        }

        list.forEach {

            if (it.harSoknadstypeSomKanGiJulesoknad() &&
                it.erLengreEnn14Dager() &&
                it.fomDatoMellom11novemberOg7desember() &&
                it.tomDatoEtter13Desember()
            ) {
                log.info("Sykmelding ${it.sykmeldingId} har søknad som kan kanskje omfattes av julesøknadregler")
                julesoknadkandidatDAO.lagreJulesoknadkandidat(sykepengesoknadUuid = it.id)
            }
        }
    }

    private fun SoknadMetadata.harSoknadstypeSomKanGiJulesoknad(): Boolean =
        !this.sykmeldingsperioder.any { it.sykmeldingstype === no.nav.helse.flex.domain.Sykmeldingstype.BEHANDLINGSDAGER } &&
            !this.sykmeldingsperioder.any { it.sykmeldingstype === no.nav.helse.flex.domain.Sykmeldingstype.REISETILSKUDD } &&
            this.soknadstype != Soknadstype.GRADERT_REISETILSKUDD

    private fun SoknadMetadata.erLengreEnn14Dager(): Boolean = DAYS.between(this.fom, this.tom) >= 14

    private fun SoknadMetadata.fomDatoMellom11novemberOg7desember(): Boolean =
        this.fom.isBeforeOrEqual(LocalDate.of(this.fom.year, 12, 7)) &&
            this.fom.isAfterOrEqual(LocalDate.of(this.fom.year, 11, 15))

    private fun SoknadMetadata.tomDatoEtter13Desember(): Boolean = this.tom.isAfter(LocalDate.of(this.fom.year, 12, 13))
}
