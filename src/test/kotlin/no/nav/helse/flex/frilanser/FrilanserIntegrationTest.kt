package no.nav.helse.flex.frilanser

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSvar
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.hentProduserteRecords
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.sendSoknad
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.shouldBeNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FrilanserIntegrationTest : FellesTestOppsett() {
    final val fnr = "123456789"

    @BeforeAll
    fun konfigurerUnleash() {
        fakeUnleash.resetAll()
    }

    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.hentProduserteRecords()
    }

    @Test
    @Order(1)
    fun `vi oppretter en frilanser søknad`() {
        val soknader =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.FRILANSER,
                    fnr = fnr,
                ),
            )

        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.SELVSTENDIGE_OG_FRILANSERE)
    }

    @Test
    @Order(2)
    fun `søknaden har alle spørsmål`() {
        val soknader = hentSoknader(fnr)
        assertThat(soknader).hasSize(1)

        val soknaden = soknader.first()
        soknaden.inntektskilderDataFraInntektskomponenten.shouldBeNull()
        assertThat(soknaden.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER,
                OPPHOLD_UTENFOR_EOS,
                TIL_SLUTT,
            ),
        )
    }

    @Test
    @Order(3)
    fun `03 - vi svarer på ansvarserklæringa som ikke muterer søknaden`() {
        val soknaden = hentSoknader(fnr).first()
        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED", mutert = false)
            .also {
                assertThat(it.muterteSoknaden).isFalse()
            }
    }

    @Test
    @Order(4)
    fun `søknaden har fortsatt alle spørsmål`() {
        val soknaden = hentSoknader(fnr).first()
        assertThat(soknaden.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER,
                OPPHOLD_UTENFOR_EOS,
                TIL_SLUTT,
            ),
        )
    }

    @Test
    @Order(5)
    fun `Unødvendige spørsmål forsvinner når man blir friskmeldt første dag i søknadsperioden`() {
        val soknaden = hentSoknader(fnr).first()

        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(
                TILBAKE_I_ARBEID,
                "JA",
                ferdigBesvart = false,
            ).besvarSporsmal(
                TILBAKE_NAR,
                LocalDate.of(2020, 2, 1).format(DateTimeFormatter.ISO_LOCAL_DATE),
                mutert = true,
            ).also {
                assertThat(it.rSSykepengesoknad.sporsmal!!.map { it.tag }).isEqualTo(
                    listOf(
                        ANSVARSERKLARING,
                        TILBAKE_I_ARBEID,
                        ARBEID_UTENFOR_NORGE,
                        ANDRE_INNTEKTSKILDER,
                        TIL_SLUTT,
                    ),
                )
            }
    }

    @Test
    @Order(6)
    fun `Vi svarer nei på at vi ble friskmeldt - underspørsmål nullstilles`() {
        val soknaden = hentSoknader(fnr).first()

        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(
                TILBAKE_I_ARBEID,
                "NEI",
                mutert = true,
            ).also {
                assertThat(it.rSSykepengesoknad.sporsmal!!.map { it.tag }).isEqualTo(
                    listOf(
                        ANSVARSERKLARING,
                        TILBAKE_I_ARBEID,
                        "ARBEID_UNDERVEIS_100_PROSENT_0",
                        ARBEID_UTENFOR_NORGE,
                        ANDRE_INNTEKTSKILDER,
                        OPPHOLD_UTENFOR_EOS,
                        TIL_SLUTT,
                    ),
                )

                assertThat(
                    it.rSSykepengesoknad.sporsmal!!
                        .first { it.tag == TILBAKE_I_ARBEID }
                        .undersporsmal
                        .first()
                        .svar,
                ).isEqualTo(
                    emptyList<RSSvar>(),
                )
            }
    }

    @Test
    @Order(7)
    fun `Vi svarer at vi ble friskmeldt midt i søknadsperioden - Det muterer søknaden`() {
        val soknaden = hentSoknader(fnr).first()

        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(
                TILBAKE_I_ARBEID,
                "JA",
                ferdigBesvart = false,
            ).besvarSporsmal(
                TILBAKE_NAR,
                LocalDate.of(2020, 2, 4).format(DateTimeFormatter.ISO_LOCAL_DATE),
                mutert = true,
            ).also {
                assertThat(it.muterteSoknaden).isTrue()

                assertThat(
                    it.rSSykepengesoknad.sporsmal!!
                        .first { it.tag == "ARBEID_UNDERVEIS_100_PROSENT_0" }
                        .sporsmalstekst,
                ).isEqualTo(
                    "I perioden 1. - 3. februar 2020 var du 100% sykmeldt som frilanser. Jobbet du noe i denne perioden?",
                )
                assertThat(
                    it.rSSykepengesoknad.sporsmal!!
                        .first { it.tag == OPPHOLD_UTENFOR_EOS }
                        .sporsmalstekst,
                ).isEqualTo(
                    "Var du på reise utenfor EU/EØS mens du var sykmeldt 1. - 3. februar 2020?",
                )
            }
    }

    @Test
    @Order(8)
    fun `vi svarer på resten som ikke muterer søknaden`() {
        val soknaden = hentSoknader(fnr).first()
        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal("ARBEID_UNDERVEIS_100_PROSENT_0", "NEI", mutert = false)
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "NEI", mutert = false)
            .besvarSporsmal(ANDRE_INNTEKTSKILDER, "NEI", mutert = false)
            .besvarSporsmal(OPPHOLD_UTENFOR_EOS, "NEI", mutert = false)
            .oppsummering()
            .also {
                assertThat(it.muterteSoknaden).isFalse()
            }
    }

    @Test
    @Order(9)
    fun `utland muterer ikke søknaden på frilansersøknad`() {
        val soknaden = hentSoknader(fnr).first()
        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(OPPHOLD_UTENFOR_EOS, "JA", mutert = false, ferdigBesvart = false)
            .besvarSporsmal(
                OPPHOLD_UTENFOR_EOS_NAR,
                svar = """{"fom":"${soknaden.fom!!}","tom":"${soknaden.fom!!.plusDays(1)}"}""",
                mutert = false,
                ferdigBesvart = false,
            ).also {
                assertThat(it.muterteSoknaden).isFalse()
            }
    }

    @Test
    @Order(10)
    fun `vi sender inn søknaden - Den får da status sendt og blir publisert på kafka`() {
        sendSoknad(fnr, hentSoknaderMetadata(fnr).first().id)

        val soknaden = hentSoknaderMetadata(fnr).first()
        assertThat(soknaden.status).isEqualTo(RSSoknadstatus.SENDT)

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.SELVSTENDIGE_OG_FRILANSERE)
        assertThat(soknader.last().status).isEqualTo(SoknadsstatusDTO.SENDT)
    }
}
