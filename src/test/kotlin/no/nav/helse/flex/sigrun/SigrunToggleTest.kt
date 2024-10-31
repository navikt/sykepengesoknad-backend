package no.nav.helse.flex.sigrun

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.medIndex
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.util.flatten
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SigrunToggleTest : FellesTestOppsett() {
    @BeforeEach
    fun konfigurerUnleash() {
        databaseReset.resetDatabase()
        fakeUnleash.resetAll()
    }

    @Test
    fun `Metadata på spørsmål om varig endring har inntektsopplysniner fra Sigrun når toggle er på`() {
        fakeUnleash.enable("sykepengesoknad-backend-sigrun")
        val soknader =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                    fnr = "87654321234",
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = LocalDate.now().minusDays(30),
                            tom = LocalDate.now().minusDays(1),
                        ),
                ),
            )

        soknader shouldHaveSize 1

        soknader.first().sporsmal!!.flatten().first {
            it.tag == "INNTEKTSOPPLYSNINGER_VARIG_ENDRING_25_PROSENT"
        }.metadata `should not be` null
    }

    @Test
    fun `Metadata på spørsmål om varig endring er null når toggle er av`() {
        fakeUnleash.disable("sykepengesoknad-backend-sigrun")

        val soknader =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                    fnr = "87654321234",
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = LocalDate.now().minusDays(30),
                            tom = LocalDate.now().minusDays(1),
                        ),
                ),
            )

        soknader shouldHaveSize 1

        soknader.first().sporsmal!!.flatten().first {
            it.tag == "INNTEKTSOPPLYSNINGER_VARIG_ENDRING_25_PROSENT"
        }.metadata.toString() `should be equal to` "null"
    }

    @Test
    fun `Metadata med inntektsopplysniner fra Sigrun blir med på Kafka når søknaden sendes `() {
        fakeUnleash.enable("sykepengesoknad-backend-sigrun")
        val soknader =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                    fnr = "87654321234",
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = LocalDate.now().minusDays(30),
                            tom = LocalDate.now().minusDays(1),
                        ),
                ),
            )

        soknader shouldHaveSize 1

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = hentSoknader("87654321234").first(), mockMvc = this, fnr = "87654321234")
                .besvarSporsmal()
                .sendSoknad()
        sendtSoknad.status `should be equal to` RSSoknadstatus.SENDT

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 1)
        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.first().sporsmal!!.flatten().first { it.tag == INNTEKTSOPPLYSNINGER_VARIG_ENDRING_25_PROSENT }
            .metadata `should not be` null
    }

    private fun SoknadBesvarer.besvarSporsmal(): SoknadBesvarer {
        this.besvarSporsmal(tag = ANSVARSERKLARING, svar = "CHECKED")
            .besvarSporsmal(tag = medIndex(ARBEID_UNDERVEIS_100_PROSENT, 0), svar = "NEI")
            .besvarSporsmal(tag = TILBAKE_I_ARBEID, svar = "NEI")
            .besvarSporsmal(tag = ANDRE_INNTEKTSKILDER, svar = "NEI")
            .besvarSporsmal(tag = OPPHOLD_UTENFOR_EOS, svar = "NEI")
            .besvarSporsmal(tag = ARBEID_UTENFOR_NORGE, svar = "NEI")
            .besvarSporsmal(tag = INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET, svar = null, ferdigBesvart = false)
            .besvarSporsmal(
                tag = INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET_NEI,
                svar = "CHECKED",
                ferdigBesvart = false,
            )
            .besvarSporsmal(tag = INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET, svar = null, ferdigBesvart = false)
            .besvarSporsmal(
                tag = INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_NEI,
                svar = "CHECKED",
                ferdigBesvart = false,
            )
            .besvarSporsmal(tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING, svar = "JA", ferdigBesvart = false)
            .besvarSporsmal(
                tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE,
                svar = null,
                ferdigBesvart = false,
            )
            .besvarSporsmal(
                tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE_ENDRET_INNSATS,
                svar = "CHECKED",
                ferdigBesvart = false,
            )
            .besvarSporsmal(tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_25_PROSENT, svar = "NEI")
            .oppsummering()
        return this
    }
}
