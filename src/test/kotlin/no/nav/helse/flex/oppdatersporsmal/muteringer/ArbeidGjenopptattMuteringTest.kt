package no.nav.helse.flex.oppdatersporsmal.muteringer

import no.nav.helse.flex.soknadsopprettelse.JOBBET_DU_100_PROSENT
import no.nav.helse.flex.soknadsopprettelse.PERMISJON_V2
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_I_ARBEID
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadArbeidstaker
import no.nav.helse.flex.testutil.besvarsporsmal
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be null`
import org.amshove.kluent.`should not be null`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test
import skapSoknadMetadata
import java.time.LocalDate

class ArbeidGjenopptattMuteringTest {

    @Test
    fun `spørsmål i søknaden gjenoppstår hvis de av en eller annen grunn mangla`() {
        val fom = LocalDate.now().minusDays(19)
        val soknadMetadata = skapSoknadMetadata(fnr = "12345612")
        val standardSoknad = (soknadMetadata).copy(
            sporsmal = settOppSoknadArbeidstaker(
                sykepengesoknad = soknadMetadata,
                erForsteSoknadISykeforlop = true,
                tidligsteFomForSykmelding = fom,
                andreKjenteArbeidsforhold = emptyList()
            )
        )

        val soknadUtenJobbetDU = standardSoknad
            .besvarsporsmal(TILBAKE_I_ARBEID, svar = "NEI")
            .fjernSporsmal("JOBBET_DU_100_PROSENT_0")

        soknadUtenJobbetDU.sporsmal.find { it.tag.startsWith(JOBBET_DU_100_PROSENT) }.`should be null`()
        soknadUtenJobbetDU.sporsmal.shouldHaveSize(11)

        val mutertSoknad = soknadUtenJobbetDU.arbeidGjenopptattMutering()

        mutertSoknad.sporsmal.find { it.tag.startsWith(JOBBET_DU_100_PROSENT) }.`should not be null`()
        mutertSoknad.sporsmal.shouldHaveSize(12)
    }

    @Test
    fun `en liten tekstlig endring i et spørsmål gjør ikke at det byttes ut`() {
        val fom = LocalDate.now().minusDays(19)
        val soknadMetadata = skapSoknadMetadata(fnr = "12345612")
        val standardSoknad = (soknadMetadata).copy(
            sporsmal = settOppSoknadArbeidstaker(
                sykepengesoknad = soknadMetadata,
                erForsteSoknadISykeforlop = true,
                tidligsteFomForSykmelding = fom,
                andreKjenteArbeidsforhold = emptyList()
            )
        )

        val spm = standardSoknad.sporsmal.find { it.tag == PERMISJON_V2 }!!.copy(sporsmalstekst = "Var De i permisjon?")

        val soknadMedEgenPermisjonSpmTekst = standardSoknad
            .replaceSporsmal(spm)

        soknadMedEgenPermisjonSpmTekst `should be equal to` soknadMedEgenPermisjonSpmTekst.arbeidGjenopptattMutering()
    }
}
