package no.nav.syfo.service

import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.client.narmesteleder.NarmesteLederRelasjon
import no.nav.syfo.client.narmesteleder.Tilgang
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.soknadarbeidsgiver.SoknadArbeidsgiverRespons
import no.nav.syfo.domain.soknadarbeidsgiver.Soknadrelasjon
import no.nav.syfo.logger
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.soknadsopprettelse.ANDRE_INNTEKTSKILDER
import no.nav.syfo.soknadsopprettelse.ARBEID_UTENFOR_NORGE
import org.springframework.stereotype.Service
import java.util.Collections.emptyList

@Service
class SoknadArbeidsgiverService(
    private val identService: IdentService,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val narmesteLederClient: NarmesteLederClient,
) {

    private val log = logger()

    fun hentSoknader(fnr: String, orgnummer: String?): SoknadArbeidsgiverRespons {
        val narmesteLederRelasjoner = narmesteLederClient.hentRelasjonerForNarmesteleder(fnr)
            .filter { orgnummer == null || it.orgnummer == orgnummer }

        val nlSoknader = narmesteLederRelasjoner
            .map { hentSoknaderForArbeidsgiverrelasjon(it) }

        return SoknadArbeidsgiverRespons(narmesteLedere = nlSoknader.filterNotNull(), humanResources = emptyList())
    }

    private fun hentSoknaderForArbeidsgiverrelasjon(arbeidsgiverrelasjon: NarmesteLederRelasjon): Soknadrelasjon? {

        if (!(arbeidsgiverrelasjon.tilganger).contains(Tilgang.SYKEPENGESOKNAD)) {
            return null
        }

        val soknader = sykepengesoknadDAO.finnSykepengesoknaderForNl(
            arbeidsgiverrelasjon.fnr,
            arbeidsgiverrelasjon.orgnummer,
            arbeidsgiverrelasjon.aktivFom
        )
            .filter { it.soknadstype.visesPåDineSykmeldte }
            .filter {
                if (it.status == Soknadstatus.SENDT) {
                    return@filter it.sendtArbeidsgiver != null
                }
                return@filter true
            }

        if (soknader.isEmpty()) {
            return null
        }

        return Soknadrelasjon(
            fnr = arbeidsgiverrelasjon.fnr,
            orgnummer = arbeidsgiverrelasjon.orgnummer,
            navn = null,
            soknader = soknader
                .fjernSporsmalOmAndreInnntektsKilder()
                .fjernSporsmalOmArbeidUtenforNorge()
        )
    }

    fun List<Sykepengesoknad>.fjernSporsmalOmAndreInnntektsKilder() = map {
        it.fjernSporsmal(ANDRE_INNTEKTSKILDER)
    }

    fun List<Sykepengesoknad>.fjernSporsmalOmArbeidUtenforNorge() = map {
        it.fjernSporsmal(ARBEID_UTENFOR_NORGE)
    }
}
