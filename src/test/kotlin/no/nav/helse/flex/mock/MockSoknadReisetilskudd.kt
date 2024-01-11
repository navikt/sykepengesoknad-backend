package no.nav.helse.flex.mock

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.SettOppSoknadOptions
import no.nav.helse.flex.soknadsopprettelse.skapReisetilskuddsoknad
import no.nav.helse.flex.soknadsopprettelse.tilSoknadsperioder
import no.nav.helse.flex.util.tilOsloInstant
import no.nav.helse.flex.yrkesskade.YrkesskadeSporsmalGrunnlag
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDate.now
import java.time.LocalDateTime
import java.util.*

fun opprettNySoknadReisetilskudd(aktivertDato: LocalDate): Sykepengesoknad {
    val soknadMetadata =
        Sykepengesoknad(
            fnr = "11111111111",
            startSykeforlop = now().minusDays(24),
            fom = now().minusDays(19),
            tom = now().minusDays(10),
            soknadstype = Soknadstype.REISETILSKUDD,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            arbeidsgiverNavn = null,
            arbeidsgiverOrgnummer = null,
            sykmeldingId = "289148ba-4c3c-4b3f-b7a3-209jf32f",
            sykmeldingSkrevet = LocalDateTime.now().minusDays(19).tilOsloInstant(),
            soknadPerioder =
                listOf(
                    no.nav.syfo.sykmelding.kafka.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO(
                        fom = now().minusDays(19),
                        tom = now().minusDays(10),
                        gradert = null,
                        type = no.nav.syfo.sykmelding.kafka.model.sykmelding.model.PeriodetypeDTO.REISETILSKUDD,
                        aktivitetIkkeMulig =
                            no.nav.syfo.sykmelding.kafka.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO(
                                arbeidsrelatertArsak = null,
                            ),
                        behandlingsdager = null,
                        innspillTilArbeidsgiver = null,
                        reisetilskudd = true,
                    ),
                ).tilSoknadsperioder(),
            id = UUID.randomUUID().toString(),
            status = Soknadstatus.NY,
            opprettet = Instant.now(),
            sporsmal = emptyList(),
            utenlandskSykmelding = false,
            egenmeldingsdagerFraSykmelding = null,
            forstegangssoknad = false,
            aktivertDato = aktivertDato,
        )

    return soknadMetadata.copy(
        sporsmal =
            skapReisetilskuddsoknad(
                SettOppSoknadOptions(
                    sykepengesoknad = soknadMetadata,
                    erForsteSoknadISykeforlop = false,
                    harTidligereUtenlandskSpm = false,
                    yrkesskade = YrkesskadeSporsmalGrunnlag(),
                ),
            ),
    )
}
