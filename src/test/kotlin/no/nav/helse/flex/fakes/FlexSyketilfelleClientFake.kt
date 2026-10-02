package no.nav.helse.flex.fakes

import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.client.flexsyketilfelle.VentetidForSykmeldingResponse
import no.nav.helse.flex.domain.Arbeidsgiverperiode
import no.nav.helse.flex.domain.Sykeforloep
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("fakes")
@Primary
class FlexSyketilfelleClientFake : FlexSyketilfelleClient {
    override fun hentSykeforloep(
        identer: FolkeregisterIdenter,
        sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
    ): List<Sykeforloep> {
        TODO("Not yet implemented")
    }

    override fun hentVentetidForSykmelding(
        identer: FolkeregisterIdenter,
        sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
    ): VentetidForSykmeldingResponse {
        TODO("Not yet implemented")
    }

    override fun beregnArbeidsgiverperiode(
        soknad: Sykepengesoknad,
        sykmelding: SykmeldingKafkaMessageDTO?,
        forelopig: Boolean,
        identer: FolkeregisterIdenter,
    ): Arbeidsgiverperiode? {
        TODO("Not yet implemented")
    }
}
