package no.nav.syfo.mock

import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Soknadstatus.NY
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.rest.SoknadMetadata
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.soknadsopprettelse.settOppSykepengesoknadBehandlingsdager
import no.nav.syfo.soknadsopprettelse.tilSoknadsperioder
import no.nav.syfo.util.tilOsloInstant
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDate.now
import java.time.LocalDateTime

fun opprettBehandlingsdagsoknadTestadata(
    status: Soknadstatus = NY,
    startSykeforlop: LocalDate = now().minusMonths(1),
    fom: LocalDate = now().minusMonths(1),
    tom: LocalDate = now().minusMonths(1).plusDays(8),
    arbeidssituasjon: Arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
    sykmeldingId: String = "14e78e84-50a5-45bb-9919-191c54f99691",
    sykmeldingSkrevet: Instant = LocalDateTime.now().minusMonths(1).tilOsloInstant(),
    soknadsperioder: List<SykmeldingsperiodeAGDTO> = listOf(
        SykmeldingsperiodeAGDTO(
            fom = now().minusMonths(1),
            tom = now().minusMonths(1).plusDays(4),
            gradert = GradertDTO(grad = 100, reisetilskudd = false),
            type = PeriodetypeDTO.BEHANDLINGSDAGER,
            aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
            behandlingsdager = 4,
            innspillTilArbeidsgiver = null,
            reisetilskudd = false,
        )
    ),
    forsteSoknadIForlop: Boolean = true,
    fnr: String = "123445"

): Sykepengesoknad {

    return settOppSykepengesoknadBehandlingsdager(
        SoknadMetadata(
            status = status,
            fnr = fnr,
            startSykeforlop = startSykeforlop,
            fom = fom,
            tom = tom,
            soknadstype = Soknadstype.BEHANDLINGSDAGER,
            arbeidssituasjon = arbeidssituasjon,
            sykmeldingId = sykmeldingId,
            sykmeldingSkrevet = sykmeldingSkrevet,
            sykmeldingsperioder = soknadsperioder.tilSoknadsperioder()
        ),
        forsteSoknadIForlop,
        now()
    )
}
