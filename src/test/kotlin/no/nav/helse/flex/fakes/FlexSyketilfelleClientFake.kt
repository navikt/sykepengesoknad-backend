package no.nav.helse.flex.fakes

import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.client.flexsyketilfelle.VentetidRequest
import no.nav.helse.flex.domain.Arbeidsgiverperiode
import no.nav.helse.flex.domain.Sykeforloep
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.service.FolkeregisterIdenter
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("fakes")
@Primary
class FlexSyketilfelleClientFake : FlexSyketilfelleClient {
    private val sykmeldingerMedSammeVentetid = mutableSetOf<String>()

    fun leggTilSykmeldingMedSammeVentetid(sykmeldingId: String) {
        sykmeldingerMedSammeVentetid.add(sykmeldingId)
    }

    fun resetSykmeldingerMedSammeVentetid() {
        sykmeldingerMedSammeVentetid.clear()
    }

    override fun hentSykeforloep(
        identer: FolkeregisterIdenter,
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
    ): List<Sykeforloep> {
        TODO("Not yet implemented")
    }

    override fun erUtenforVentetid(
        identer: FolkeregisterIdenter,
        sykmeldingId: String,
        ventetidRequest: VentetidRequest,
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun beregnArbeidsgiverperiode(
        soknad: Sykepengesoknad,
        sykmelding: SykmeldingKafkaMessage?,
        forelopig: Boolean,
        identer: FolkeregisterIdenter,
    ): Arbeidsgiverperiode? {
        TODO("Not yet implemented")
    }

    override fun hentSykmeldingerMedSammeVentetid(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        identer: FolkeregisterIdenter,
    ): Set<String> =
        when (identer.originalIdent) {
            "kast-feil" -> throw RuntimeException("Feil ved henting av sykmelding med samme ventetid")
            else -> sykmeldingerMedSammeVentetid
        }
}
