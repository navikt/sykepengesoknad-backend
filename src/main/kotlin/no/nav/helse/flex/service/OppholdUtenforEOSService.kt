package no.nav.helse.flex.service

import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.FERIE_NAR_V2
import no.nav.helse.flex.soknadsopprettelse.FERIE_V2
import no.nav.helse.flex.soknadsopprettelse.OPPHOLD_UTENFOR_EOS
import no.nav.helse.flex.soknadsopprettelse.OPPHOLD_UTENFOR_EOS_NAR
import no.nav.helse.flex.soknadsopprettelse.OpprettSoknadService
import no.nav.helse.flex.soknadsopprettelse.PERIODEUTLAND
import no.nav.helse.flex.soknadsopprettelse.PERMISJON_NAR_V2
import no.nav.helse.flex.soknadsopprettelse.PERMISJON_V2
import no.nav.helse.flex.util.DatoUtil
import no.nav.helse.flex.util.PeriodeMapper
import org.springframework.stereotype.Service

@Service
class OppholdUtenforEOSService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val identService: IdentService,
    private val opprettSoknadService: OpprettSoknadService,
) {
    fun skalOppretteSoknadForOppholdUtenforEOS(
        sykepengesoknad: Sykepengesoknad,
        identer: FolkeregisterIdenter,
    ) {
        val gyldigeFerieperioder = sykepengesoknad.hentGyldigePerioder(FERIE_V2, FERIE_NAR_V2)
        val gyldigePermisjonperioder = sykepengesoknad.hentGyldigePerioder(PERMISJON_V2, PERMISJON_NAR_V2)
        val gyldigeUtlandsperioder = sykepengesoknad.hentGyldigePerioder(OPPHOLD_UTENFOR_EOS, OPPHOLD_UTENFOR_EOS_NAR)


        val t = gyldigeUtlandsperioder.first().hentUkedager()

        val dagerUtenforFeriePermisjonOgHelg =
            gyldigeUtlandsperioder
                .flatMap { it.hentUkedager() }
                .filter { dag ->
                    !gyldigeFerieperioder.any {
                        it.erIPeriode(dag)
                    } && !gyldigePermisjonperioder.any { it.erIPeriode(dag) }
                }

        val harOppholdtSegUtenforEOS =
            sykepengesoknad.getSporsmalMedTagOrNull(OPPHOLD_UTENFOR_EOS)
                ?.svar?.firstOrNull()?.verdi == "JA"

        // skal opprette eossokan
        val skalOppretteSoknad =
            dagerUtenforFeriePermisjonOgHelg.isNotEmpty() &&
                harOppholdtSegUtenforEOS &&
                // eossoknad
                !finnesTidligereSoknad(sykepengesoknad, gyldigeUtlandsperioder, identer)

        if (skalOppretteSoknad) {
            opprettSoknadService.opprettSoknadUtland(identer)
        }
    }

    private fun Sykepengesoknad.hentGyldigePerioder(
        hva: String,
        nar: String,
    ): List<Periode> {
        val sporsmalMedTag = this.getSporsmalMedTagOrNull(hva) ?: return emptyList()
        return if (sporsmalMedTag.forsteSvar == "JA") {
            getGyldigePeriodesvar(this.getSporsmalMedTag(nar))
        } else {
            emptyList()
        }
    }

    private fun getGyldigePeriodesvar(sporsmal: Sporsmal): List<Periode> {
        return sporsmal.svar.asSequence()
            .mapNotNull { PeriodeMapper.jsonTilOptionalPeriode(it.verdi).orElse(null) }
            .filter { DatoUtil.periodeErInnenforMinMax(it, sporsmal.min, sporsmal.max) }
            .toList()
    }

    fun finnesTidligereSoknad(
        sykepengesoknad: Sykepengesoknad,
        perioderTilOppholdUtland: List<Periode>,
        identer: FolkeregisterIdenter,
    ): Boolean {
        val alleOppholdUtlandSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer.alle(), Soknadstype.OPPHOLD_UTLAND)

        if (alleOppholdUtlandSoknader.isEmpty()) return false

        return alleOppholdUtlandSoknader
            .filter { it.status == Soknadstatus.SENDT }
            .any {
                it.sporsmal.filter { it.tag == PERIODEUTLAND }
                    .any {
                        it.svar.mapNotNull { PeriodeMapper.jsonTilOptionalPeriode(it.verdi).orElse(null) }
                            .flatMap { it.hentUkedager() }
                            .any { dag -> perioderTilOppholdUtland.any { it.erIPeriode(dag) } }
                    }
            }
    }
}
