package no.nav.syfo.migrering

import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Soknadstatus.KORRIGERT
import no.nav.syfo.domain.Soknadstatus.SENDT
import no.nav.syfo.domain.Soknadstatus.UTGATT
import no.nav.syfo.domain.Svar
import no.nav.syfo.soknadsopprettelse.settOppSoknadOppholdUtland
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be equal to`
import org.junit.jupiter.api.Test

class FjernSvarTest {
    val sykepengesoknadUtenSvar = settOppSoknadOppholdUtland(fnr = "1235").copy(status = Soknadstatus.NY)
    val sporsmalMedSvar = sykepengesoknadUtenSvar.sporsmal.first().copy(svar = listOf(Svar(verdi = "CHECKED", id = null)))
    val sykepengesoknadMedSvar = sykepengesoknadUtenSvar.replaceSporsmal(sporsmalMedSvar)

    @Test
    fun `test fjerner ikke svar på NY`() {
        sykepengesoknadMedSvar.fjernSvarFraUtgatt() `should be equal to` sykepengesoknadMedSvar
    }

    @Test
    fun `test fjerner ikke svar på SENDT`() {
        sykepengesoknadMedSvar.copy(status = SENDT).fjernSvarFraUtgatt() `should be equal to` sykepengesoknadMedSvar.copy(status = SENDT)
    }

    @Test
    fun `test fjerner ikke svar på Korrigert`() {
        sykepengesoknadMedSvar.copy(status = KORRIGERT).fjernSvarFraUtgatt() `should be equal to` sykepengesoknadMedSvar.copy(status = KORRIGERT)
    }

    @Test
    fun `test fjerner svar på utlopt`() {
        sykepengesoknadMedSvar.copy(status = UTGATT).fjernSvarFraUtgatt() `should not be equal to` sykepengesoknadMedSvar.copy(status = UTGATT)
        sykepengesoknadMedSvar.copy(status = UTGATT).fjernSvarFraUtgatt() `should be equal to` sykepengesoknadUtenSvar.copy(status = UTGATT)
    }

    @Test
    fun `test fjerner svar på undersporsmal`() {
        val sporsmalMedSvar = sykepengesoknadUtenSvar.getSporsmalMedTag("FERIE").copy(svar = listOf(Svar(verdi = "JA", id = null)))
        val sykepengesoknadMedSvar = sykepengesoknadUtenSvar.replaceSporsmal(sporsmalMedSvar)

        sykepengesoknadMedSvar.copy(status = UTGATT).fjernSvarFraUtgatt() `should not be equal to` sykepengesoknadMedSvar.copy(status = UTGATT)
        sykepengesoknadMedSvar.copy(status = UTGATT).fjernSvarFraUtgatt() `should be equal to` sykepengesoknadUtenSvar.copy(status = UTGATT)
    }
}
