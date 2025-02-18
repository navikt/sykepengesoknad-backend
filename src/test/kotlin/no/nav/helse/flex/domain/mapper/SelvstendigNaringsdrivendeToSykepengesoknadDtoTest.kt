package no.nav.helse.flex.domain.mapper

import no.nav.helse.flex.domain.BrregRolle
import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.SelvstendigNaringsdrivendeInfo
import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentSoknadsPerioderMedFaktiskGrad
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknad
import no.nav.helse.flex.sykepengesoknad.kafka.Rolle
import no.nav.helse.flex.sykepengesoknad.kafka.SelvstendigNaringsdrivendeDTO
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import java.time.LocalDate.now

class SelvstendigNaringsdrivendeToSykepengesoknadDtoTest {
    @Test
    fun `burde inneholde selvstedig næringsdrivende`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad().copy(
                soknadPerioder = (
                    listOf(
                        Soknadsperiode(
                            now().minusDays(19),
                            now()
                                .minusDays(15),
                            100,
                            null,
                        ),
                    )
                ),
                selvstendigNaringsdrivende =
                    SelvstendigNaringsdrivendeInfo(
                        listOf(
                            BrregRolle(
                                "123456789",
                                "Test",
                                "ROLLE",
                            ),
                        ),
                    ),
            )
        val soknadDto =
            konverterTilSykepengesoknadDTO(
                soknad,
                Mottaker.ARBEIDSGIVER_OG_NAV,
                false,
                hentSoknadsPerioderMedFaktiskGrad(soknad).first,
            )

        soknadDto.selvstendigNaringsdrivende `should be equal to`
            SelvstendigNaringsdrivendeDTO(
                roller =
                    listOf(
                        Rolle(
                            "123456789",
                            "ROLLE",
                        ),
                    ),
            )
    }
}
