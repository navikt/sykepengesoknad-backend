package no.nav.helse.flex.oppholdUtenforEOS

import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.util.DatoUtil
import no.nav.helse.flex.util.PeriodeMapper
import org.springframework.stereotype.Service
import java.util.*

@Service
class OppholdUtenforEOSService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val identService: IdentService,
) {
    fun skalOppretteSoknadForOppholdUtenforEOS(sykepengesoknad: Sykepengesoknad): Boolean {
        val gyldigeFerieperioder = sykepengesoknad.hentGyldigePerioder(FERIE_V2, FERIE_NAR_V2)
        val gyldigePermisjonperioder = sykepengesoknad.hentGyldigePerioder(PERMISJON_V2, PERMISJON_NAR_V2)
        val gyldigeUtlandsperioder = sykepengesoknad.hentGyldigePerioder(OPPHOLD_UTENFOR_EOS, OPPHOLD_UTENFOR_EOS_NAR)

        val dagerUtenforFeriePermisjonOgHelg =
            gyldigeUtlandsperioder
                .flatMap { it.hentUkedager() }
                .filter { dag ->
                    gyldigeFerieperioder.none { it.erIPeriode(dag) } &&
                        gyldigePermisjonperioder.none { it.erIPeriode(dag) }
                }

        val harOppholdtSegUtenforEOS =
            sykepengesoknad.getSporsmalMedTagOrNull(OPPHOLD_UTENFOR_EOS)
                ?.svar?.firstOrNull()?.verdi == "JA"

        return dagerUtenforFeriePermisjonOgHelg.isNotEmpty() && harOppholdtSegUtenforEOS && !finnesTidligereSoknad(sykepengesoknad)
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

    fun finnesTidligereSoknad(sykepengesoknad: Sykepengesoknad): Boolean {
        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(sykepengesoknad.fnr)
        val alleOppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer.alle(), Soknadstype.OPPHOLD_UTLAND)

        if (alleOppholdUtlandSoknader.isEmpty()) {
            return false
        }

        val allePerioderTilOppholdUtenforEOS = sykepengesoknad.hentGyldigePerioder(OPPHOLD_UTENFOR_EOS, OPPHOLD_UTENFOR_EOS_NAR)

        val harOverlappIDager =
            alleOppholdUtlandSoknader
                .map { Periode(it.fom!!, it.tom!!) }
                .flatMap { it.hentUkedager() }
                .any { dagFraAlleSoknader ->
                    allePerioderTilOppholdUtenforEOS.any { it.erIPeriode(dagFraAlleSoknader) }
                }

        return harOverlappIDager
    }
}
