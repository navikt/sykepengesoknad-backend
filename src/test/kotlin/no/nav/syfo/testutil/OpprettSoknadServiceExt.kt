package no.nav.syfo.testutil

import no.nav.syfo.domain.rest.SoknadMetadata
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.soknadsopprettelse.OpprettSoknadService
import no.nav.syfo.soknadsopprettelse.genererSykepengesoknadFraMetadata

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
