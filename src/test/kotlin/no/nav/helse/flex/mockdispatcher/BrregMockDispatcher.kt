package no.nav.helse.flex.mockdispatcher

import no.nav.helse.flex.client.brreg.RolleDto
import no.nav.helse.flex.client.brreg.RollerDto
import no.nav.helse.flex.client.brreg.Rolletype

object BrregMockDispatcher : FellesQueueDispatcher<RollerDto>(
    defaultFactory = {
        RollerDto(
            roller =
                listOf(
                    RolleDto(
                        rolletype = Rolletype.INNH,
                        organisasjonsnummer = "orgnummer",
                        organisasjonsnavn = "orgnavn",
                    ),
                ),
        )
    },
)
