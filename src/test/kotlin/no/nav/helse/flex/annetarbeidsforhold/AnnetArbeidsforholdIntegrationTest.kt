package no.nav.helse.flex.annetarbeidsforhold

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AnnetArbeidsforholdIntegrationTest : BaseTestClass() {

    final val fnr = "123456789"

    @Test
    @Order(1)
    fun `Vi oppretter en annet arbeidsforhold søknad`() {
        val soknader = sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ANNET,
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2018, 1, 1),
                    tom = LocalDate.of(2018, 1, 10)
                )
            )
        )

        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.ANNET_ARBEIDSFORHOLD)
    }

    @Test
    @Order(2)
    fun `Søknaden har alle spørsmål`() {
        val soknader = hentSoknaderMetadata(fnr)
        assertThat(soknader).hasSize(1)

        val soknaden = hentSoknad(soknader.first().id, fnr)
        assertThat(soknaden.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                FRISKMELDT,
                PERMISJON_V2,
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER,
                ARBEIDSLEDIG_UTLAND,
                VAER_KLAR_OVER_AT,
                BEKREFT_OPPLYSNINGER
            )
        )
    }

    @Test
    @Order(3)
    fun `Spørsmålstekstene endrer seg når vi blir friskmeldt midt i søknadsperioden`() {
        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        assertThat(soknaden.sporsmal!!.first { it.tag == ANDRE_INNTEKTSKILDER }.sporsmalstekst)
            .isEqualTo(
                "Har du hatt inntekt mens du har vært sykmeldt i perioden 1. - 10. januar 2018?"
            )

        assertThat(soknaden.sporsmal!!.first { it.tag == ARBEIDSLEDIG_UTLAND }.sporsmalstekst)
            .isEqualTo(
                "Var du på reise utenfor EØS mens du var sykmeldt 1. - 10. januar 2018?"
            )
        assertThat(soknaden.sporsmal!!.first { it.tag == PERMISJON_V2 }.sporsmalstekst)
            .isEqualTo(
                "Tok du permisjon mens du var sykmeldt 1. - 10. januar 2018?"
            )
        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(FRISKMELDT, "NEI", false)
            .besvarSporsmal(
                FRISKMELDT_START,
                LocalDate.of(2018, 1, 5).format(DateTimeFormatter.ISO_LOCAL_DATE),
                mutert = true
            )
            .also {
                val oppdatertSoknad = it.rSSykepengesoknad

                assertThat(oppdatertSoknad.sporsmal!!.first { it.tag == ANDRE_INNTEKTSKILDER }.sporsmalstekst)
                    .isEqualTo(
                        "Har du hatt inntekt mens du har vært sykmeldt i perioden 1. - 4. januar 2018?"
                    )

                assertThat(oppdatertSoknad.sporsmal!!.first { it.tag == ARBEIDSLEDIG_UTLAND }.sporsmalstekst)
                    .isEqualTo(
                        "Var du på reise utenfor EØS mens du var sykmeldt 1. - 4. januar 2018?"
                    )
                assertThat(oppdatertSoknad.sporsmal!!.first { it.tag == PERMISJON_V2 }.sporsmalstekst)
                    .isEqualTo(
                        "Tok du permisjon mens du var sykmeldt 1. - 4. januar 2018?"
                    )
            }
    }

    @Test
    @Order(4)
    fun `Unødvendige spørsmål forsvinner når man blir friskmeldt første dag i søknadsperioden`() {
        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(
                FRISKMELDT_START,
                LocalDate.of(2018, 1, 1).format(DateTimeFormatter.ISO_LOCAL_DATE),
                mutert = true
            )
            .also {
                assertThat(it.rSSykepengesoknad.sporsmal!!.map { it.tag }).isEqualTo(
                    listOf(
                        ANSVARSERKLARING,
                        FRISKMELDT,
                        ARBEID_UTENFOR_NORGE,
                        VAER_KLAR_OVER_AT,
                        BEKREFT_OPPLYSNINGER
                    )
                )
            }
    }

    @Test
    @Order(5)
    fun `Unødvendige spørsmål kommer tilbake når man svarer at man ikke ble friskmeldt likevel`() {
        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        assertThat(soknaden.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                FRISKMELDT,
                ARBEID_UTENFOR_NORGE,
                VAER_KLAR_OVER_AT,
                BEKREFT_OPPLYSNINGER
            )
        )

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(FRISKMELDT, "JA", mutert = true)
            .also {
                assertThat(it.rSSykepengesoknad.sporsmal!!.map { it.tag }).isEqualTo(
                    listOf(
                        ANSVARSERKLARING,
                        FRISKMELDT,
                        PERMISJON_V2,
                        ARBEID_UTENFOR_NORGE,
                        ANDRE_INNTEKTSKILDER,
                        ARBEIDSLEDIG_UTLAND,
                        VAER_KLAR_OVER_AT,
                        BEKREFT_OPPLYSNINGER
                    )
                )
            }
    }

    @Test
    @Order(6)
    fun `Vi svarer på alle spørsmål og sender inn søknaden`() {
        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        val sendtSoknad = SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(FRISKMELDT, "JA")
            .besvarSporsmal(PERMISJON_V2, "NEI")
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(ANDRE_INNTEKTSKILDER, "NEI")
            .besvarSporsmal(ARBEIDSLEDIG_UTLAND, "NEI")
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "NEI")
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
            .sendSoknad()

        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.ANNET_ARBEIDSFORHOLD)
        assertThat(soknader.last().status).isEqualTo(SoknadsstatusDTO.SENDT)
    }
}
