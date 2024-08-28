package no.nav.helse.flex.service

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSvartype
import no.nav.helse.flex.controller.domain.sykepengesoknad.flatten
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.mock.opprettNyArbeidstakerSoknad
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER
import no.nav.helse.flex.soknadsopprettelse.TIL_SLUTT
import no.nav.helse.flex.soknadsopprettelse.sporsmal.tilSluttGammel
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should contain`
import org.amshove.kluent.`should not be`
import org.amshove.kluent.`should not contain`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class HentSoknadServiceTest : FellesTestOppsett() {
    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @BeforeEach
    fun beforeEach() {
        databaseReset.resetDatabase()
    }

    @Test
    fun `søknad med BEKREFT_OPPLYSNINGNER fjerner tagen`() {
        val soknadArbeidstaker = opprettNyArbeidstakerSoknad()
        val soknadGammel =
            soknadArbeidstaker.replaceSporsmal(
                tilSluttGammel(),
            )
        soknadGammel.getSporsmalMedTag(BEKREFT_OPPLYSNINGER) `should not be` null

        sykepengesoknadDAO.lagreSykepengesoknad(soknadGammel)
        val hentetSoknad = hentSoknad(soknadGammel.id, soknadGammel.fnr)
        hentetSoknad `should not be` null
        hentetSoknad.alleSporsmalOgUndersporsmal().flatten().map { it.tag }.let {
            it `should contain` TIL_SLUTT
            it `should not contain` BEKREFT_OPPLYSNINGER
        }
    }

    @Test
    fun `søknad uten BEKREFT_OPPLYSNINGNER returnerer søknad`() {
        val soknadArbeidstaker = opprettNyArbeidstakerSoknad()

        sykepengesoknadDAO.lagreSykepengesoknad(soknadArbeidstaker)
        val hentetSoknad = hentSoknad(soknadArbeidstaker.id, soknadArbeidstaker.fnr)
        hentetSoknad `should not be` null
        hentetSoknad.alleSporsmalOgUndersporsmal().flatten().map { it.tag }.let {
            it `should contain` TIL_SLUTT
            it `should not contain` BEKREFT_OPPLYSNINGER
        }
    }

    @Test
    fun `søknad med BEKREFTELSESPUNKTER får svartype OPPSUMMERING`() {
        val soknadArbeidstaker = opprettNyArbeidstakerSoknad()
        val soknadGammel =
            soknadArbeidstaker.replaceSporsmal(
                tilSluttGammel(),
            )
        soknadGammel.getSporsmalMedTag(TIL_SLUTT).svartype `should be equal to` Svartype.BEKREFTELSESPUNKTER

        sykepengesoknadDAO.lagreSykepengesoknad(soknadGammel)
        val hentetSoknad = hentSoknad(soknadGammel.id, soknadGammel.fnr)
        hentetSoknad `should not be` null
        hentetSoknad.alleSporsmalOgUndersporsmal().flatten().map { it.tag }.let {
            it `should contain` TIL_SLUTT
        }
        hentetSoknad.alleSporsmalOgUndersporsmal().flatten().map { it.svartype }.let {
            it `should contain` RSSvartype.OPPSUMMERING
            it `should not contain` RSSvartype.BEKREFTELSESPUNKTER
        }
    }
}
