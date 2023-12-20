package no.nav.helse.flex.sending

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Avsendertype
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstatus.NY
import no.nav.helse.flex.domain.Soknadstatus.UTKAST_TIL_KORRIGERING
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.inntektsopplysninger.dokumenterSomSkalSendes
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SvarDAO
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.repository.normaliser
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.MottakerAvSoknadService
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_JA
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_NEI
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSOPPLYSNINGER_VARIG_ENDRING_25_PROSENT
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional
class SoknadSender(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val svarDAO: SvarDAO,
    private val mottakerAvSoknadService: MottakerAvSoknadService,
    private val soknadProducer: SoknadProducer,
    private val sykepengesoknadRepository: SykepengesoknadRepository
) {

    fun sendSoknad(
        sykepengesoknad: Sykepengesoknad,
        avsendertype: Avsendertype,
        dodsdato: LocalDate?,
        identer: FolkeregisterIdenter
    ) {
        if (sykepengesoknad.status !in listOf(NY, UTKAST_TIL_KORRIGERING)) {
            throw RuntimeException("Søknad ${sykepengesoknad.id} kan ikke gå i fra status ${sykepengesoknad.status} til SENDT.")
        }

        if (sykepengesoknad.sporsmal.isEmpty()) {
            throw RuntimeException("Kan ikke sende soknad ${sykepengesoknad.id} med som ikke har spørsmål.")
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
        val opprinneligSendt = sykepengesoknadRepository
            .findByFnrIn(identer.alle())
            .finnOpprinneligSendt(sendtSoknad.normaliser().soknad)

        try {

            hentOgLagreOmNaringsdrivendeSendeInntektsopplysninger(sendtSoknad)
        } catch (e: Exception) {
            log.error("Feil ved henting og lagring av inntektsopplysninger for næringsdrivende. ${sendtSoknad.id}", e)
        }

        soknadProducer.soknadEvent(
            sykepengesoknad = sendtSoknad,
            mottaker = mottaker,
            erEttersending = false,
            dodsdato = dodsdato,
            opprinneligSendt = opprinneligSendt
        )
    }

    private val log = logger()

    fun Sykepengesoknad.skalHaDokumenter(): Boolean {
        getSporsmalMedTagOrNull(INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_JA)?.let {
            if (it.forsteSvar == "CHECKED") {
                return true
            }
        }

        getSporsmalMedTagOrNull(INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_NEI)?.let { sporsmal1 ->
            if (sporsmal1.forsteSvar == "CHECKED") {
                getSporsmalMedTagOrNull(INNTEKTSOPPLYSNINGER_VARIG_ENDRING_25_PROSENT)?.let {
                    if (it.forsteSvar == "JA") {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun hentOgLagreOmNaringsdrivendeSendeInntektsopplysninger(soknad: Sykepengesoknad) {
        if (soknad.arbeidssituasjon != Arbeidssituasjon.NAERINGSDRIVENDE) {
            return
        }

        if (!listOf(
                Soknadstype.GRADERT_REISETILSKUDD,
                Soknadstype.SELVSTENDIGE_OG_FRILANSERE
            ).contains(soknad.soknadstype)
        ) {
            return
        }

        if (soknad.forstegangssoknad != true) {
            return
        }

        val erNyKvittering = soknad.getSporsmalMedTagOrNull(INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET) != null

        sykepengesoknadRepository.findBySykepengesoknadUuid(soknad.id)?.let { sykepengeSoknad ->
            val oppdatertSykepengeSoknad = if (soknad.skalHaDokumenter()) {
                val dokumenter = dokumenterSomSkalSendes(LocalDate.now()).joinToString(",")
                val innsendingsId = "TODO"

                sykepengeSoknad.copy(
                    inntektsopplysningerNyKvittering = erNyKvittering,
                    inntektsopplysningerInnsendingId = innsendingsId,
                    inntektsopplysningerInnsendingDokumenter = dokumenter
                )
            } else {
                sykepengeSoknad.copy(
                    inntektsopplysningerNyKvittering = erNyKvittering
                )
            }

            sykepengesoknadRepository.save(oppdatertSykepengeSoknad)
        }
    }
}
