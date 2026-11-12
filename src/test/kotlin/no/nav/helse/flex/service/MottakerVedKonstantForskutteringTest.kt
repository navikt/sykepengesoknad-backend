package no.nav.helse.flex.service

import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.domain.Arbeidsgiverperiode
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.fakes.FlexSyketilfelleClientFake
import no.nav.helse.flex.mock.opprettNyArbeidstakerSoknad
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class MottakerVedKonstantForskutteringTest : FakesTestOppsett() {
    @Autowired
    private lateinit var mottakerAvSoknadService: MottakerAvSoknadService

    @Autowired
    private lateinit var flexSyketilfelleClient: FlexSyketilfelleClientFake

    private val fom = LocalDate.of(2026, 1, 1)
    private val soknad = opprettNyArbeidstakerSoknad(svarPaSoknad = false, basisDato = fom.plusMonths(1))
    private val identer = FolkeregisterIdenter(soknad.fnr, emptyList())

    @BeforeEach
    fun `Sett opp oppbrukt arbeidsgiverperiode`() {
        flexSyketilfelleClient.arbeidsgiverperiode = Arbeidsgiverperiode(0, true, Periode(fom, fom.plusDays(15)))
    }

    @AfterEach
    fun `Nullstill arbeidsgiverperiode`() {
        flexSyketilfelleClient.arbeidsgiverperiode = null
    }

    @Test
    fun `Arbeidstaker utenfor arbeidsgiverperioden sendes til arbeidsgiver og Nav uansett orgnummer`() {
        listOf("123456789", "", null).forEach { orgnummer ->
            val utenfor = soknad.copy(fom = fom.plusDays(16), tom = fom.plusDays(20), arbeidsgiverOrgnummer = orgnummer)

            mottakerAvSoknadService.finnMottakerAvSoknad(utenfor, identer) shouldBe Mottaker.ARBEIDSGIVER_OG_NAV
        }
    }

    @Test
    fun `Arbeidstaker innenfor arbeidsgiverperioden sendes fortsatt bare til arbeidsgiver`() {
        val innenfor = soknad.copy(fom = fom, tom = fom.plusDays(15))

        mottakerAvSoknadService.finnMottakerAvSoknad(innenfor, identer) shouldBe Mottaker.ARBEIDSGIVER
    }

    @Test
    fun `Arbeidstaker med blandet periode sendes fortsatt til arbeidsgiver og Nav`() {
        val blandet = soknad.copy(fom = fom, tom = fom.plusDays(20))

        mottakerAvSoknadService.finnMottakerAvSoknad(blandet, identer) shouldBe Mottaker.ARBEIDSGIVER_OG_NAV
    }

    @Test
    fun `Behandlingsdager og gradert reisetilskudd beholder skillet mellom arbeidstaker og andre arbeidssituasjoner`() {
        listOf(Soknadstype.BEHANDLINGSDAGER, Soknadstype.GRADERT_REISETILSKUDD).forEach { type ->
            Arbeidssituasjon.entries.forEach { situasjon ->
                val utenfor = soknad.copy(fom = fom.plusDays(16), tom = fom.plusDays(20), soknadstype = type, arbeidssituasjon = situasjon)
                val forventet = if (situasjon == Arbeidssituasjon.ARBEIDSTAKER) Mottaker.ARBEIDSGIVER_OG_NAV else Mottaker.NAV

                mottakerAvSoknadService.finnMottakerAvSoknad(utenfor, identer) shouldBe forventet
            }
        }
    }
}
