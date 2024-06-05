package no.nav.helse.flex.sending

import no.nav.helse.flex.domain.Avsendertype
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstatus.NY
import no.nav.helse.flex.domain.Soknadstatus.UTKAST_TIL_KORRIGERING
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.repository.SvarDAO
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.repository.normaliser
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.MottakerAvSoknadService
import no.nav.helse.flex.soknadsopprettelse.OPPHOLD_UTENFOR_EOS
import no.nav.helse.flex.soknadsopprettelse.OPPHOLD_UTENFOR_EOS_NAR
import no.nav.helse.flex.soknadsopprettelse.FERIE_V2
import no.nav.helse.flex.soknadsopprettelse.FERIE_NAR_V2
import no.nav.helse.flex.soknadsopprettelse.OpprettSoknadService
import no.nav.helse.flex.util.DatoUtil
import no.nav.helse.flex.util.PeriodeMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.Collections

@Service
@Transactional
class SoknadSender(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val svarDAO: SvarDAO,
    private val mottakerAvSoknadService: MottakerAvSoknadService,
    private val soknadProducer: SoknadProducer,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val opprettSoknadService: OpprettSoknadService,
) {
    fun sendSoknad(
        sykepengesoknad: Sykepengesoknad,
        avsendertype: Avsendertype,
        dodsdato: LocalDate?,
        identer: FolkeregisterIdenter,
    ): Sykepengesoknad {
        validerSoknad(sykepengesoknad)

        if (skalOppretteSoknadForOppholdUtenforEOS(sykepengesoknad)) {
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

    private fun skalOppretteSoknadForOppholdUtenforEOS(sykepengesoknad: Sykepengesoknad): Boolean {

        val gyldigeFerieperioder = sykepengesoknad.hentGyldigePerioder(FERIE_V2, FERIE_NAR_V2,)
        val gyldigeUtlandsperioder = sykepengesoknad.hentGyldigePerioder(OPPHOLD_UTENFOR_EOS, OPPHOLD_UTENFOR_EOS_NAR)

        val gyldigPeriode = gyldigeUtlandsperioder
            .filter { DatoUtil.periodeErUtenforHelg(it) }
            .any { periode -> DatoUtil.periodeHarDagerUtenforAndrePerioder(periode, gyldigeFerieperioder) }

        val harOppholdtSegUtenforEOS = sykepengesoknad.getSporsmalMedTagOrNull(OPPHOLD_UTENFOR_EOS)
            ?.svar?.firstOrNull()?.verdi == "JA"

        return gyldigPeriode && harOppholdtSegUtenforEOS
    }

    private fun Sykepengesoknad.hentGyldigePerioder(
        hva: String,
        nar: String,
    ): List<Periode> {
        return if (this.getSporsmalMedTagOrNull(hva)?.forsteSvar == "JA") {
            getGyldigePeriodesvar(this.getSporsmalMedTag(nar))
        } else {
            Collections.emptyList()
        }
    }

    private fun getGyldigePeriodesvar(sporsmal: Sporsmal): List<Periode> {
        return sporsmal.svar.asSequence()
            .map { PeriodeMapper.jsonTilOptionalPeriode(it.verdi) }
            .filter { it.isPresent }
            .map { it.get() }
            .filter { DatoUtil.periodeErInnenforMinMax(it, sporsmal.min, sporsmal.max) }
            .toList()
    }
}
