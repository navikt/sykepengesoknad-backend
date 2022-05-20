package no.nav.syfo.soknadsopprettelse.sporsmal

import no.nav.syfo.domain.Soknadsperiode
import no.nav.syfo.domain.Sporsmal
import no.nav.syfo.domain.Svartype
import no.nav.syfo.domain.Visningskriterie
import no.nav.syfo.soknadsopprettelse.JOBBET_DU_GRADERT
import no.nav.syfo.soknadsopprettelse.undersporsmal.jobbetDuUndersporsmal

fun jobbetDuGradert(
    periode: Soknadsperiode,
    index: Int,
): Sporsmal {

    return Sporsmal(
        tag = JOBBET_DU_GRADERT + index,
        sporsmalstekst = "Sykmeldingen sier du kunne jobbe ${100 - periode.grad} %. Jobbet du mer enn det?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal = jobbetDuUndersporsmal(periode, 100 + 1 - periode.grad, index)
    )
}
