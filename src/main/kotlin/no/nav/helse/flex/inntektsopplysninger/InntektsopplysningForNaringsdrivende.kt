package no.nav.helse.flex.inntektsopplysninger

import no.nav.helse.flex.client.innsendingapi.EttersendingRequest
import no.nav.helse.flex.client.innsendingapi.InnsendingApiClient
import no.nav.helse.flex.client.innsendingapi.Vedlegg
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSOPPLYSNINGER_DRIFT_VIRKSOMHETEN
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class InntektsopplysningForNaringsdrivende(
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val innsendingApiClient: InnsendingApiClient,
) {
    fun lagreOpplysningerOmDokumentasjonAvInntektsopplysninger(soknad: Sykepengesoknad) {
        if (!listOf(Arbeidssituasjon.NAERINGSDRIVENDE, Arbeidssituasjon.FISKER, Arbeidssituasjon.JORDBRUKER).contains(
                soknad.arbeidssituasjon,
            )
        ) {
            return
        }

        if (!listOf(
                Soknadstype.GRADERT_REISETILSKUDD,
                Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
            ).contains(soknad.soknadstype)
        ) {
            return
        }

        if (soknad.forstegangssoknad != true) {
            return
        }

        val visNyKvittering = soknad.getSporsmalMedTagOrNull(INNTEKTSOPPLYSNINGER_DRIFT_VIRKSOMHETEN) != null

        sykepengesoknadRepository.findBySykepengesoknadUuid(soknad.id)?.let { sykepengeSoknad ->
            val oppdatertSoknad =
                if (soknad.inntektsopplysningerMaaDokumenteres()) {
                    val dokumenter = dokumenterSomSkalSendesInn(LocalDate.now())

                    val innsendingResponse =
                        innsendingApiClient.opprettEttersending(
                            EttersendingRequest(
                                skjemanr = "NAV 08-35.01",
                                sprak = "nb_NO",
                                tema = "SYK",
                                vedleggsListe =
                                    dokumenter.map {
                                        Vedlegg(it.vedleggsnr, it.tittel)
                                    },
                                brukernotifikasjonstype = "oppgave",
                                koblesTilEksisterendeSoknad = false,
                            ),
                        )

                    sykepengeSoknad.copy(
                        inntektsopplysningerNyKvittering = visNyKvittering,
                        inntektsopplysningerInnsendingId = innsendingResponse.innsendingsId,
                        inntektsopplysningerInnsendingDokumenter = dokumenter.joinToString(","),
                    )
                } else {
                    sykepengeSoknad.copy(
                        inntektsopplysningerNyKvittering = visNyKvittering,
                    )
                }

            sykepengesoknadRepository.save(oppdatertSoknad)
        }
    }
}
