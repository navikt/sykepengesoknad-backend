package no.nav.helse.flex.fiskere

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidssituasjonDTO
import no.nav.helse.flex.sykepengesoknad.kafka.FiskerBladDTO
import no.nav.helse.flex.sykepengesoknad.kafka.FiskerLottOgHyreDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.LottOgHyre
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test

class FiskerHyreSoknadTest : FellesTestOppsett() {
    @Test
    fun `Opprett og aktiver fisker hyre søknad`() {
        val fnr = "12345678910"
        val soknad =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    arbeidssituasjon = Arbeidssituasjon.FISKER,
                    lottOgHyre = LottOgHyre.HYRE,
                ),
            ).single()

        soknad.type `should be equal to` SoknadstypeDTO.FISKERE
        soknad.arbeidssituasjon `should be equal to` ArbeidssituasjonDTO.FISKER
        soknad.sporsmal!!.map { it.tag } `should be equal to`
            listOf(
                "ANSVARSERKLARING",
                "TILBAKE_I_ARBEID",
                "FERIE_V2",
                "PERMISJON_V2",
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                "ARBEID_UTENFOR_NORGE",
                "ANDRE_INNTEKTSKILDER_V2",
                "OPPHOLD_UTENFOR_EOS",
                "TIL_SLUTT",
            )
        soknad.fiskerLottOgHyre `should be equal to` FiskerLottOgHyreDTO.HYRE
        soknad.fiskerBlad `should be equal to` FiskerBladDTO.A
    }
}
