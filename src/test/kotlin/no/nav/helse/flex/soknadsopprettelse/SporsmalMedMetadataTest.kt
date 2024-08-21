package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.repository.SoknadLagrer
import no.nav.helse.flex.util.toJsonNode
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class SporsmalMedMetadataTest : FellesTestOppsett() {
    @Autowired
    lateinit var soknadLagrer: SoknadLagrer

    @Test
    fun `Vi kan lagre og hente søknad med json metadata i spørsmålet`() {
        val soknad =
            settOppSoknadOppholdUtland("12345678900").copy(
                sporsmal =
                    listOf(
                        Sporsmal(
                            tag = "TULLETAG",
                            svartype = Svartype.JA_NEI,
                            metadata = mapOf("land" to "Sverige").toJsonNode(),
                        ),
                    ),
            )

        soknadLagrer.lagreSoknad(soknad)

        val soknadHentetFraFrontend = hentSoknader("12345678900").first()
        soknadHentetFraFrontend.sporsmal!!.shouldHaveSize(1)
        soknadHentetFraFrontend.getSporsmalMedTag("TULLETAG").metadata!!.get("land").asText() `should be equal to` "Sverige"
    }
}
