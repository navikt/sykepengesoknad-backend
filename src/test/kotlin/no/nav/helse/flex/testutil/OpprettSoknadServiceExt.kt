package no.nav.helse.flex.testutil

import no.nav.helse.flex.aktivering.AktiverEnkeltSoknad
import no.nav.helse.flex.aktivering.kafka.AktiveringProducer
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.rest.SoknadMetadata
import no.nav.helse.flex.domain.rest.tilSykepengesoknad
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.OpprettSoknadService

@Deprecated("ikke bruk")
fun OpprettSoknadService.opprettSoknadFraSoknadMetadata(
    soknadMetadata: SoknadMetadata,
    sykepengesoknadDAO: SykepengesoknadDAO,
    aktiveringProducer: AktiveringProducer,
    aktiverEnkeltSoknad: AktiverEnkeltSoknad
): Sykepengesoknad {

    val eksisterendeSoknader = sykepengesoknadDAO.finnSykepengesoknader(listOf(soknadMetadata.fnr))

    val sortertSoknadMetadata =
        soknadMetadata.copy(sykmeldingsperioder = soknadMetadata.sykmeldingsperioder.sortedBy { it.fom })
    val soknad = (sortertSoknadMetadata.tilSykepengesoknad()).copy(
        sporsmal = aktiverEnkeltSoknad.genererSykepengesoknadSporsmal(
            sortertSoknadMetadata.tilSykepengesoknad(),
            eksisterendeSoknader
        )
    )
    soknad.lagreSøknad(eksisterendeSoknader).publiserEllerReturnerAktiveringBestilling()
        ?.let { aktiveringProducer.leggPaAktiveringTopic(it) }
    return soknad
}
