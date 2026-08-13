package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.*
import no.nav.helse.flex.testutil.lagSoknad
import org.amshove.kluent.`should be`
import org.junit.jupiter.api.Test
import java.time.LocalDate

class HarGapTilForrigeSoknadTest {
    private val startSykeforlop = LocalDate.of(2024, 1, 1)

    @Test
    fun `gap på to dager mellom søknader gir true`() {
        val forrige =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 14),
                startSykeforlop = startSykeforlop,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )
        val aktuell =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2024, 1, 17),
                tom = LocalDate.of(2024, 1, 31),
                startSykeforlop = startSykeforlop,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        harGapTilForrigeSoknad(listOf(forrige), aktuell.fom!!) `should be` true
    }

    @Test
    fun `back-to-back søknader gir false`() {
        val forrige =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 15),
                startSykeforlop = startSykeforlop,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )
        val aktuell =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2024, 1, 16),
                tom = LocalDate.of(2024, 1, 31),
                startSykeforlop = startSykeforlop,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        harGapTilForrigeSoknad(listOf(forrige), aktuell.fom!!) `should be` false
    }

    @Test
    fun `ingen forrige søknad gir false`() {
        val aktuell =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2024, 1, 17),
                tom = LocalDate.of(2024, 1, 31),
                startSykeforlop = startSykeforlop,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        harGapTilForrigeSoknad(emptyList(), aktuell.fom!!) `should be` false
    }

    @Test
    fun `tom fredag og fom mandag gir ikke gap over helg`() {
        val forrige =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2024, 3, 1),
                tom = LocalDate.of(2024, 3, 29),
                startSykeforlop = startSykeforlop,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )
        val aktuell =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2024, 4, 1),
                tom = LocalDate.of(2024, 4, 30),
                startSykeforlop = startSykeforlop,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        harGapTilForrigeSoknad(listOf(forrige), aktuell.fom!!) `should be` false
    }

    @Test
    fun `tom fredag og fom tirsdag gir gap`() {
        val forrige =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2024, 3, 1),
                tom = LocalDate.of(2024, 3, 29),
                startSykeforlop = startSykeforlop,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )
        val aktuell =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2024, 4, 2),
                tom = LocalDate.of(2024, 4, 30),
                startSykeforlop = startSykeforlop,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        harGapTilForrigeSoknad(listOf(forrige), aktuell.fom!!) `should be` true
    }

    @Test
    fun `avbrutt søknad ignoreres - ingen gyldig forrige gir false`() {
        val avbrutt =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 15),
                startSykeforlop = startSykeforlop,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
                status = Soknadstatus.AVBRUTT,
            )
        val aktuellBackToBack =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2024, 1, 16),
                tom = LocalDate.of(2024, 1, 31),
                startSykeforlop = startSykeforlop,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        harGapTilForrigeSoknad(listOf(avbrutt), aktuellBackToBack.fom!!) `should be` false
    }

    @Test
    fun `avbrutt søknad med nyere tom enn gyldig søknad gir ikke falsk gap`() {
        val gyldig =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 31),
                startSykeforlop = startSykeforlop,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )
        val avbrutt =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2024, 2, 1),
                tom = LocalDate.of(2024, 2, 29),
                startSykeforlop = startSykeforlop,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
                status = Soknadstatus.AVBRUTT,
            )
        val aktuell =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2024, 2, 1),
                tom = LocalDate.of(2024, 2, 29),
                startSykeforlop = startSykeforlop,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        harGapTilForrigeSoknad(listOf(gyldig, avbrutt), aktuell.fom!!) `should be` false
    }

    @Test
    fun `slettet og utgått søknad ignoreres`() {
        val slettet =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 14),
                startSykeforlop = startSykeforlop,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
                status = Soknadstatus.SLETTET,
            )
        val aktuell =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2024, 1, 17),
                tom = LocalDate.of(2024, 1, 31),
                startSykeforlop = startSykeforlop,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        harGapTilForrigeSoknad(listOf(slettet), aktuell.fom!!) `should be` false
    }

    @Test
    fun `gap sjekkes mot siste forrige søknad uansett type`() {
        val arbeidstaker =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2024, 1, 10),
                tom = LocalDate.of(2024, 1, 20),
                startSykeforlop = startSykeforlop,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )
        val aktuell =
            lagSoknad(
                arbeidsgiver = 1,
                fom = LocalDate.of(2024, 1, 23),
                tom = LocalDate.of(2024, 1, 31),
                startSykeforlop = startSykeforlop,
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            )

        harGapTilForrigeSoknad(listOf(arbeidstaker), aktuell.fom!!) `should be` true
    }
}
