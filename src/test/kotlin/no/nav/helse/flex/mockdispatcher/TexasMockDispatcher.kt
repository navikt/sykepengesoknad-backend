package no.nav.helse.flex.mockdispatcher

import no.nav.helse.flex.client.texas.TexasHentTokenResponse

object TexasMockDispatcher : FellesQueueDispatcher<TexasHentTokenResponse>(
    defaultFactory = {
        TexasHentTokenResponse(
            "token",
        )
    },
)
