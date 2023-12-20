package no.nav.helse.flex.inntektsopplysninger

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_JA
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_NEI
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSOPPLYSNINGER_VARIG_ENDRING_25_PROSENT
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class InntektsopplysningForNaringsdrivende(private val sykepengesoknadRepository: SykepengesoknadRepository) {

    fun Sykepengesoknad.skalHaInntektsmeldingDokumenter(): Boolean {
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

    fun lagreInnsendingsopplysninger(soknad: Sykepengesoknad) {
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
            val oppdatertSykepengeSoknad = if (soknad.skalHaInntektsmeldingDokumenter()) {
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
