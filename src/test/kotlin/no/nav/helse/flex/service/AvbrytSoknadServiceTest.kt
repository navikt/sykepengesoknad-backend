package no.nav.helse.flex.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import no.nav.helse.flex.domain.Soknadstatus.*
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknad
import no.nav.helse.flex.mock.opprettNySoknad
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadOppholdUtland
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class AvbrytSoknadServiceTest {
    @Mock
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Mock
    private lateinit var soknadProducer: SoknadProducer

    @InjectMocks
    private lateinit var avbrytSoknadService: AvbrytSoknadService

    @Test
    fun farAvbruttNySelvstendigSoknad() {
        val soknad = opprettNyNaeringsdrivendeSoknad()
        avbrytSoknadService.avbrytSoknad(soknad)

        verify(sykepengesoknadDAO).avbrytSoknad(any(), any())
    }

    @Test
    fun farAvbruttNyArbeidstakerSoknad() {
        val soknad = opprettNySoknad()
        avbrytSoknadService.avbrytSoknad(soknad)

        verify(sykepengesoknadDAO).avbrytSoknad(any(), any())
    }

    @Test
    fun farAvbruttNyUtlandsoppholdSoknad() {
        val soknad = settOppSoknadOppholdUtland("fnr")
        avbrytSoknadService.avbrytSoknad(soknad)

        verify(sykepengesoknadDAO).avbrytSoknad(any(), any())
    }

    @Test
    fun farAvbruttUtkastTilKorrigeringSoknad() {
        val soknad = opprettNyNaeringsdrivendeSoknad().copy(status = UTKAST_TIL_KORRIGERING)

        avbrytSoknadService.avbrytSoknad(soknad)

        verify(sykepengesoknadDAO).slettSoknad(any<Sykepengesoknad>())
    }

    @Test
    fun farIkkeAvbruttSendtSoknad() {
        assertThrows(IllegalArgumentException::class.java) {
            try {
                val soknad = opprettNyNaeringsdrivendeSoknad().copy(status = SENDT)

                avbrytSoknadService.avbrytSoknad(soknad)
            } finally {
                verify(sykepengesoknadDAO, never()).slettSoknad(any<Sykepengesoknad>())
            }
        }
    }

    @Test
    fun farIkkeAvbruttKorrigertSoknad() {
        assertThrows(IllegalArgumentException::class.java) {
            try {
                val soknad = opprettNyNaeringsdrivendeSoknad().copy(status = KORRIGERT)

                avbrytSoknadService.avbrytSoknad(soknad)
            } finally {
                verify(sykepengesoknadDAO, never()).slettSoknad(any<Sykepengesoknad>())
            }
        }
    }
}
