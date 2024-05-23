package no.nav.helse.flex.korrigering

import no.nav.helse.flex.*
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.medlemskap.MedlemskapVurderingRepository
import no.nav.helse.flex.mock.opprettNySoknad
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.service.KorrigerSoknadService
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER
import no.nav.helse.flex.soknadsopprettelse.VAER_KLAR_OVER_AT
import no.nav.helse.flex.util.Metrikk
import org.amshove.kluent.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.beans.factory.annotation.Autowired

@ExtendWith(MockitoExtension::class)
class KorrigeringAvGammelSpmTest : FellesTestOppsett() {
    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var soknadProducer: SoknadProducer

    @Autowired
    private lateinit var metrikk: Metrikk

    @Autowired
    private lateinit var medlemskapVurderingRepository: MedlemskapVurderingRepository

    @Autowired
    private lateinit var identService: IdentService

    @Autowired
    private lateinit var korrigerSoknadService: KorrigerSoknadService

    @Test
    fun `vi erstatter gammel vær klar over at spørsmål med ny ved korrigering`() {
        val oppdatertSpormalsListe =
            listOf(
                Sporsmal(
                    id = "187808",
                    tag = VAER_KLAR_OVER_AT,
                    sporsmalstekst = "Vær klar over at:",
                    undertekst = "<ul><li>Bla bla 1</li><li>Bla bla 2</li></ul>",
                    svartype = Svartype.IKKE_RELEVANT,
                    min = null,
                    max = null,
                    kriterieForVisningAvUndersporsmal = null,
                    svar = mutableListOf(),
                    undersporsmal = mutableListOf(),
                ),
                Sporsmal(
                    id = "187809",
                    tag = BEKREFT_OPPLYSNINGER,
                    sporsmalstekst = "Jeg har lest all informasjonen og opplysningene jeg har gitt er korrekte.",
                    undertekst = null,
                    svartype = Svartype.CHECKBOX_PANEL,
                    min = null,
                    max = null,
                    kriterieForVisningAvUndersporsmal = null,
                    svar = mutableListOf(),
                    undersporsmal = mutableListOf(),
                ),
            )
        val soknad = opprettNySoknad().copy(sporsmal = oppdatertSpormalsListe, status = Soknadstatus.SENDT)

        soknad.status.shouldBeEqualTo(Soknadstatus.SENDT)

        assertThat(soknad.sporsmal.map { it.tag }).isEqualTo(
            listOf("VAER_KLAR_OVER_AT", "BEKREFT_OPPLYSNINGER"),
        )

        val korrigertSoknad = korrigerSoknadService.finnEllerOpprettUtkast(soknad, FolkeregisterIdenter(soknad.fnr, emptyList()))

        assertThat(korrigertSoknad.sporsmal.map { it.tag }).isEqualTo(
            listOf("TIL_SLUTT"),
        )
    }
}
