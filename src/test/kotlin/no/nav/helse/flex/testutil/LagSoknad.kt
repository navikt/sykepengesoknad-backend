package no.nav.helse.flex.testutil

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

fun lagSoknad(
    arbeidsgiver: Int,
    fom: LocalDate,
    tom: LocalDate,
    startSykeforlop: LocalDate,
    arbeidsSituasjon: Arbeidssituasjon,
    soknadsType: Soknadstype,
    status: Soknadstatus? = Soknadstatus.SENDT,
    fnr: String = "11111111111",
    id: String = UUID.randomUUID().toString(),
): Sykepengesoknad =
    Sykepengesoknad(
        id = id,
        fnr = fnr,
        sykmeldingId = "uuid-$arbeidsgiver",
        arbeidssituasjon = arbeidsSituasjon,
        arbeidsgiverOrgnummer = "org-$arbeidsgiver",
        startSykeforlop = startSykeforlop,
        fom = fom,
        tom = tom,
        soknadstype = soknadsType,
        status = status!!,
        egenmeldingsdagerFraSykmelding = null,
        utenlandskSykmelding = false,
        opprettet = fom.atStartOfDay().toInstant(ZoneOffset.UTC),
        soknadPerioder = emptyList(),
        sporsmal = emptyList(),
        sykmeldingSkrevet = Instant.now(),
        forstegangssoknad = false,
    )
