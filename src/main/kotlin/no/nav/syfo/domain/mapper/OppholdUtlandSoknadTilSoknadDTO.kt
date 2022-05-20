package no.nav.syfo.domain.mapper

import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.util.tilOsloLocalDateTime

fun konverterOppholdUtlandTilSoknadDTO(sykepengesoknad: Sykepengesoknad): SykepengesoknadDTO {
    return SykepengesoknadDTO(
        id = sykepengesoknad.id,
        fnr = sykepengesoknad.fnr,
        sykmeldingId = null,
        type = SoknadstypeDTO.OPPHOLD_UTLAND,
        status = sykepengesoknad.status.tilSoknadstatusDTO(),
        fom = null,
        tom = null,
        opprettet = sykepengesoknad.opprettet?.tilOsloLocalDateTime(),
        sendtNav = sykepengesoknad.sendtNav?.tilOsloLocalDateTime(),
        arbeidsgiver = null,
        arbeidssituasjon = null,
        korrigerer = sykepengesoknad.korrigerer,
        korrigertAv = sykepengesoknad.korrigertAv,
        sporsmal = sykepengesoknad.sporsmal.map { it.tilSporsmalDTO() },
        avsendertype = sykepengesoknad.avsendertype?.tilAvsendertypeDTO(),
        merknaderFraSykmelding = sykepengesoknad.merknaderFraSykmelding.tilMerknadDTO(),
    )
}
