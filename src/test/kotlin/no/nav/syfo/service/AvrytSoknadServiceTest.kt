package no.nav.syfo.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import no.nav.syfo.domain.Soknadstatus.*
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.kafka.producer.SoknadProducer
import no.nav.syfo.mock.MockSoknadSelvstendigeOgFrilansere
import no.nav.syfo.mock.opprettNySoknad
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.soknadsopprettelse.settOppSoknadOppholdUtland
import no.nav.syfo.util.Metrikk
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class AvrytSoknadServiceTest {

    @Mock
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Mock
    private lateinit var soknadProducer: SoknadProducer

    @Mock
    private lateinit var metrikk: Metrikk

    @InjectMocks
    private lateinit var avbrytSoknadService: AvbrytSoknadService

    private val mockSoknadSelvstendigeOgFrilansere = MockSoknadSelvstendigeOgFrilansere(null)

    @Test
    fun farAvbruttNySelvstendigSoknad() {
        val soknad = mockSoknadSelvstendigeOgFrilansere.opprettNySoknad()
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

        verify(sykepengesoknadDAO).slettSoknad(any<Sykepengesoknad>())
    }

    @Test
    fun farAvbruttUtkastTilKorrigeringSoknad() {
        val soknad = mockSoknadSelvstendigeOgFrilansere.opprettNySoknad().copy(status = UTKAST_TIL_KORRIGERING)

        avbrytSoknadService.avbrytSoknad(soknad)

        verify(sykepengesoknadDAO).slettSoknad(any<Sykepengesoknad>())
    }

    @Test
    fun farIkkeAvbruttSendtSoknad() {
        assertThrows(IllegalArgumentException::class.java) {
            try {
                val soknad = mockSoknadSelvstendigeOgFrilansere.opprettNySoknad().copy(status = SENDT)

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
                val soknad = mockSoknadSelvstendigeOgFrilansere.opprettNySoknad().copy(status = KORRIGERT)

                avbrytSoknadService.avbrytSoknad(soknad)
            } finally {
                verify(sykepengesoknadDAO, never()).slettSoknad(any<Sykepengesoknad>())
            }
        }
    }
}
