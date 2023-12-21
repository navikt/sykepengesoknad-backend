package no.nav.helse.flex.inntektsopplysninger

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class InntektsopplysningForNaringsdrivende(private val sykepengesoknadRepository: SykepengesoknadRepository) {

    fun lagreOpplysningerOmDokumentasjonAvInntektsopplysninger(soknad: Sykepengesoknad) {
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

        val visNyKvittering = soknad.getSporsmalMedTagOrNull(INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET) != null

        sykepengesoknadRepository.findBySykepengesoknadUuid(soknad.id)?.let { sykepengeSoknad ->
            val oppdatertSoknad = if (soknad.inntektsopplysningerMaaDokumenteres()) {
                val dokumenter = dokumenterSomSkalSendesInn(LocalDate.now()).joinToString(",")
                val innsendingsId = "TODO"

                sykepengeSoknad.copy(
                    inntektsopplysningerNyKvittering = visNyKvittering,
                    inntektsopplysningerInnsendingId = innsendingsId,
                    inntektsopplysningerInnsendingDokumenter = dokumenter
                )
            } else {
                sykepengeSoknad.copy(
                    inntektsopplysningerNyKvittering = visNyKvittering
                )
            }

            sykepengesoknadRepository.save(oppdatertSoknad)
        }
    }
}
