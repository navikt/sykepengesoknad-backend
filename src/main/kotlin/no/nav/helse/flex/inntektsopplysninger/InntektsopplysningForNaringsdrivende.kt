package no.nav.helse.flex.inntektsopplysninger

import no.nav.helse.flex.client.innsendingapi.EttersendingRequest
import no.nav.helse.flex.client.innsendingapi.InnsendingApiClient
import no.nav.helse.flex.client.innsendingapi.Vedlegg
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.repository.SykepengesoknadDbRecord
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSOPPLYSNINGER_DRIFT_VIRKSOMHETEN
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET
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

        val visNyKvittering =
            soknad.getSporsmalMedTagOrNull(INNTEKTSOPPLYSNINGER_DRIFT_VIRKSOMHETEN) != null ||
                soknad.getSporsmalMedTagOrNull(
                    INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET,
                ) != null
        val sykepengesoknadDbRecord =
            sykepengesoknadRepository.findBySykepengesoknadUuid(soknad.id)
                ?: throw IllegalStateException("Fant ikke søknad")

        fun skapOppdatertSoknad(): SykepengesoknadDbRecord {
            if (soknad.inntektsopplysningerMaaDokumenteres()) {
                if (soknad.korrigerer != null) {
                    val korrigertSoknad =
                        sykepengesoknadRepository.findBySykepengesoknadUuid(soknad.korrigerer)
                    if (korrigertSoknad?.inntektsopplysningerInnsendingId != null) {
                        return sykepengesoknadDbRecord.copy(
                            inntektsopplysningerNyKvittering = korrigertSoknad.inntektsopplysningerNyKvittering,
                            inntektsopplysningerInnsendingId = korrigertSoknad.inntektsopplysningerInnsendingId,
                            inntektsopplysningerInnsendingDokumenter = korrigertSoknad.inntektsopplysningerInnsendingDokumenter,
                        )
                    }
                }

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

                return sykepengesoknadDbRecord.copy(
                    inntektsopplysningerNyKvittering = visNyKvittering,
                    inntektsopplysningerInnsendingId = innsendingResponse.innsendingsId,
                    inntektsopplysningerInnsendingDokumenter = dokumenter.joinToString(","),
                )
            } else {
                if (soknad.korrigerer != null) {
                    val korrigertSoknad =
                        sykepengesoknadRepository.findBySykepengesoknadUuid(soknad.korrigerer)
                    if (korrigertSoknad?.inntektsopplysningerInnsendingId != null) {
                        innsendingApiClient.slett(korrigertSoknad.inntektsopplysningerInnsendingId)
                    }
                }
                return sykepengesoknadDbRecord.copy(
                    inntektsopplysningerNyKvittering = visNyKvittering,
                )
            }
        }

        sykepengesoknadRepository.save(skapOppdatertSoknad())
    }
}
