package no.nav.helse.flex.arbeidstaker

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.ANDRE_INNTEKTSKILDER_V2
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING
import no.nav.helse.flex.soknadsopprettelse.ARBEID_UTENFOR_NORGE
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER
import no.nav.helse.flex.soknadsopprettelse.FERIE_V2
import no.nav.helse.flex.soknadsopprettelse.FRAVAR_FOR_SYKMELDINGEN
import no.nav.helse.flex.soknadsopprettelse.PERMISJON_V2
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_I_ARBEID
import no.nav.helse.flex.soknadsopprettelse.UTDANNING
import no.nav.helse.flex.soknadsopprettelse.UTLAND_V2
import no.nav.helse.flex.testdata.gradertSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.MethodName::class)
class BrukerIkkeTimerForFaktiskGradHvisIkkeChecked : BaseTestClass() {

    private final val fnr = "123456789"

    private final val start = LocalDate.of(2020, 9, 22)
    private final val slutt = LocalDate.of(2020, 10, 10)

    @Test
    fun `1 - vi oppretter en arbeidstakersoknad`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = gradertSykmeldt(
                    fom = start,
                    tom = slutt
                )
            )
        )
    }

    @Test
    fun `3 - vi svarer på sporsmalene og sender den inn`() {
        flexSyketilfelleMockRestServiceServer?.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()

        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(FRAVAR_FOR_SYKMELDINGEN, "NEI")
            .besvarSporsmal(TILBAKE_I_ARBEID, "NEI")
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "NEI")
            .besvarSporsmal(FERIE_V2, "NEI")
            .besvarSporsmal(PERMISJON_V2, "NEI")
            .besvarSporsmal(UTLAND_V2, "NEI")
            .besvarSporsmal("JOBBET_DU_GRADERT_0", "JA", false)
            .besvarSporsmal("HVOR_MANGE_TIMER_PER_UKE_0", "23", false)
            .besvarSporsmal("HVOR_MYE_PROSENT_0", "CHECKED", false)
            .besvarSporsmal("HVOR_MYE_PROSENT_VERDI_0", "51", false)
            .besvarSporsmal("HVOR_MYE_TIMER_VERDI_0", "12", true)
            .besvarSporsmal(ANDRE_INNTEKTSKILDER_V2, "NEI")
            .besvarSporsmal(UTDANNING, "NEI")
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
            .sendSoknad()
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    fun `4 - vi sjekker at faktisk grad er hentet ut korrekt`() {
        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        assertThat(soknaden.status).isEqualTo(RSSoknadstatus.SENDT)

        val soknadPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()
        assertThat(soknadPaKafka.soknadsperioder!![0].faktiskGrad).isEqualTo(51)
    }
}
