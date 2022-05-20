package no.nav.helse.flex.testutil

import no.nav.helse.flex.domain.rest.SoknadMetadata
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.OpprettSoknadService
import no.nav.helse.flex.soknadsopprettelse.genererSykepengesoknadFraMetadata

fun OpprettSoknadService.opprettSoknadFraSoknadMetadata(
    soknadMetadata: SoknadMetadata,
    sykepengesoknadDAO: SykepengesoknadDAO
) {

    val eksisterendeSoknader = sykepengesoknadDAO.finnSykepengesoknader(listOf(soknadMetadata.fnr))

    val sortertSoknadMetadata =
        soknadMetadata.copy(sykmeldingsperioder = soknadMetadata.sykmeldingsperioder.sortedBy { it.fom })
    val soknad = genererSykepengesoknadFraMetadata(sortertSoknadMetadata, eksisterendeSoknader)
    lagreOgPubliserSøknad(soknad, eksisterendeSoknader)
}
