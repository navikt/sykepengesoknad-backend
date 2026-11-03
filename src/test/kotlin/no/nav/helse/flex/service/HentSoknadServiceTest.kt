package no.nav.helse.flex.service

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
        soknadGammel.getSporsmalMedTag(BEKREFT_OPPLYSNINGER) shouldNotBe null

        sykepengesoknadDAO.lagreSykepengesoknad(soknadGammel)
        val hentetSoknad = hentSoknad(soknadGammel.id, soknadGammel.fnr)
        hentetSoknad shouldNotBe null
        hentetSoknad.alleSporsmalOgUndersporsmal().flatten().map { it.tag }.let {
            it shouldContain TIL_SLUTT
            it shouldNotContain BEKREFT_OPPLYSNINGER
        }
    }

    @Test
    fun `søknad uten BEKREFT_OPPLYSNINGNER returnerer søknad`() {
        val soknadArbeidstaker = opprettNyArbeidstakerSoknad()

        sykepengesoknadDAO.lagreSykepengesoknad(soknadArbeidstaker)
        val hentetSoknad = hentSoknad(soknadArbeidstaker.id, soknadArbeidstaker.fnr)
        hentetSoknad shouldNotBe null
        hentetSoknad.alleSporsmalOgUndersporsmal().flatten().map { it.tag }.let {
            it shouldContain TIL_SLUTT
            it shouldNotContain BEKREFT_OPPLYSNINGER
        }
    }

    @Test
    fun `søknad med BEKREFTELSESPUNKTER får svartype OPPSUMMERING`() {
        val soknadArbeidstaker = opprettNyArbeidstakerSoknad()
        val soknadGammel =
            soknadArbeidstaker.replaceSporsmal(
                tilSluttGammel(),
            )
        soknadGammel.getSporsmalMedTag(TIL_SLUTT).svartype shouldBe Svartype.BEKREFTELSESPUNKTER

        sykepengesoknadDAO.lagreSykepengesoknad(soknadGammel)
        val hentetSoknad = hentSoknad(soknadGammel.id, soknadGammel.fnr)
        hentetSoknad shouldNotBe null
        hentetSoknad.alleSporsmalOgUndersporsmal().flatten().map { it.tag }.let {
            it shouldContain TIL_SLUTT
        }
        hentetSoknad.alleSporsmalOgUndersporsmal().flatten().map { it.svartype }.let {
            it shouldContain RSSvartype.OPPSUMMERING
            it shouldNotContain RSSvartype.BEKREFTELSESPUNKTER
        }
    }
}
