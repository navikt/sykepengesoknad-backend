package no.nav.helse.flex.mockdispatcher

import no.nav.helse.flex.client.sykmeldinger.SykmeldingerResponse

object FlexSykmeldingMockDispatcher :
    FellesQueueDispatcher<SykmeldingerResponse>(
        defaultFactory = { SykmeldingerResponse(sykmeldinger = emptyList()) },
    )
