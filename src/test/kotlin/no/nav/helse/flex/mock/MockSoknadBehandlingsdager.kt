package no.nav.helse.flex.mock

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.settOppSykepengesoknadBehandlingsdager
import no.nav.helse.flex.soknadsopprettelse.tilSoknadsperioder
import no.nav.helse.flex.util.tilOsloInstant
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDate.now
import java.time.LocalDateTime
import java.util.*

fun opprettBehandlingsdagsoknadTestadata(
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

    val soknadMetadata = Sykepengesoknad(
        fnr = fnr,
        startSykeforlop = startSykeforlop,
        fom = fom,
        tom = tom,
        soknadstype = Soknadstype.BEHANDLINGSDAGER,
        arbeidssituasjon = arbeidssituasjon,
        sykmeldingId = sykmeldingId,
        sykmeldingSkrevet = sykmeldingSkrevet,
        soknadPerioder = soknadsperioder.tilSoknadsperioder(),
        id = UUID.randomUUID().toString(),
        status = Soknadstatus.NY,
        opprettet = Instant.now(),
        sporsmal = emptyList(),
        utenlandskSykmelding = false,
    )
    return soknadMetadata.copy(
        sporsmal = settOppSykepengesoknadBehandlingsdager(
            soknadMetadata,
            forsteSoknadIForlop,
            now()
        )
    )
}
