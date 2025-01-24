package no.nav.helse.flex.fakes

import no.nav.helse.flex.domain.*
import no.nav.helse.flex.repository.SvarDAO
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository

@Repository
@Profile("fakes")
class SvarDAOFake : SvarDAO {
    override fun finnSvar(sporsmalIder: Set<String>): HashMap<String, MutableList<Svar>> {
        TODO("Not yet implemented")
    }

    override fun lagreSvar(
        sporsmalId: String,
        svar: Svar?,
    ) {
        TODO("Not yet implemented")
    }

    override fun slettSvar(sykepengesoknadUUID: String) {
        TODO("Not yet implemented")
    }

    override fun slettSvar(sporsmalIder: List<String>) {
        TODO("Not yet implemented")
    }

    override fun slettSvar(
        sporsmalId: String,
        svarId: String,
    ) {
        TODO("Not yet implemented")
    }

    override fun overskrivSvar(sykepengesoknad: Sykepengesoknad) {
        TODO("Not yet implemented")
    }

    override fun overskrivSvar(sporsmal: List<Sporsmal>) {
        TODO("Not yet implemented")
    }
}
