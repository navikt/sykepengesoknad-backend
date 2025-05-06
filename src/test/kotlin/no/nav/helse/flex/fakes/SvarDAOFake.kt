package no.nav.helse.flex.fakes

import no.nav.helse.flex.domain.*
import no.nav.helse.flex.repository.SvarDAO
import no.nav.helse.flex.repository.SvarDbRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository

@Repository
@Profile("fakes")
@Primary
class SvarDAOFake : SvarDAO {
    @Autowired
    lateinit var svarRepositoryFake: SvarRepositoryFake

    override fun finnSvar(sporsmalIder: Set<String>): HashMap<String, MutableList<Svar>> {
        TODO("Not yet implemented")
    }

    override fun lagreSvar(
        sporsmalId: String,
        svar: Svar?,
    ) {
        fun isEmpty(str: String?): Boolean = str == null || "" == str
        if (svar == null || isEmpty(svar.verdi)) {
            return
        }
        svarRepositoryFake.save(
            SvarDbRecord(
                id = null,
                sporsmalId = sporsmalId,
                verdi = svar.verdi,
            ),
        )
    }

    override fun slettSvar(sykepengesoknadUUID: String) {
        TODO("Not yet implemented")
    }

    override fun slettSvar(sporsmalIder: List<String>) {
        svarRepositoryFake.slettSvar(sporsmalIder)
    }

    override fun slettSvar(
        sporsmalId: String,
        svarId: String,
    ) {
        TODO("Not yet implemented")
    }

    override fun overskrivSvar(sykepengesoknad: Sykepengesoknad) {
        val alleSporsmalOgUndersporsmal = sykepengesoknad.alleSporsmalOgUndersporsmal()
        slettSvar(alleSporsmalOgUndersporsmal.mapNotNull { it.id })
        alleSporsmalOgUndersporsmal
            .forEach { sporsmal ->
                sporsmal.svar
                    .forEach { svar -> lagreSvar(sporsmal.id!!, svar) }
            }
    }

    override fun overskrivSvar(sporsmal: List<Sporsmal>) {
        slettSvar(sporsmal.mapNotNull { it.id })

        sporsmal.forEach {
            it.svar.forEach { svar -> lagreSvar(it.id!!, svar) }
        }
    }
}
