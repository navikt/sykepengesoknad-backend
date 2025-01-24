package no.nav.helse.flex.fakes

import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.repository.SoknadsperiodeDAO
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import java.util.*
import kotlin.collections.HashMap

@Repository
@Profile("fakes")
class SoknadsperiodeDAOFake : SoknadsperiodeDAO {
    override fun lagreSoknadperioder(
        sykepengesoknadId: String,
        soknadPerioder: List<Soknadsperiode>,
    ) {
        TODO("Not yet implemented")
    }

    override fun finnSoknadPerioder(sykepengesoknadIds: Set<String>): HashMap<String, MutableList<Soknadsperiode>> {
        TODO("Not yet implemented")
    }

    override fun slettSoknadPerioder(sykepengesoknadId: String) {
        TODO("Not yet implemented")
    }
}
