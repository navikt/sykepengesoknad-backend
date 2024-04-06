package no.nav.helse.flex.medlemskap

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.aktivering.AktiveringJob
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.hentProduserteRecords
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.ARBEID_UTENFOR_NORGE
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_OPPHOLDSTILLATELSE
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_OPPHOLD_UTENFOR_EOS
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_MEDLEMSKAP_SPORSMAL
import no.nav.helse.flex.util.flatten
import no.nav.helse.flex.util.serialisertTilString
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldNotBe
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Verifiserer at en av to førstegangssøknader med forskjellig arbeidsgiver før spørsmål om medlemskap
 * eller ARBEID_UTENFOR_NORGE når de aktiveres frem i tid.
 */
class MedlemskapToArbeidsgivereIntegrationTest : FellesTestOppsett() {
    @Autowired
    private lateinit var aktiveringJob: AktiveringJob
    private val basisDato = LocalDate.now()

    @BeforeAll
    fun konfigurerUnleash() {
        fakeUnleash.resetAll()
        fakeUnleash.enable(UNLEASH_CONTEXT_MEDLEMSKAP_SPORSMAL)
    }

    @AfterEach
    fun slettFraDatabase() {
        databaseReset.resetDatabase()
    }

    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.hentProduserteRecords()
    }

    @Test
    fun `Søknader til hver sin arbeidsgiver aktiveres når sykmelding mottas skal ha spørsmål om medlemskap`() {
        val fnr = "31111111111"

        medlemskapMockWebServer.enqueue(
            lagUavklartMockResponse(),
        )

        val forsteSoknad =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    fnr = fnr,
                    arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = basisDato.minusDays(6),
                            tom = basisDato.minusDays(2),
                        ),
                ),
            )

        val andreSoknad =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    fnr = fnr,
                    arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "000000002", orgNavn = "Arbeidsgiver 2"),
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = basisDato.minusDays(6),
                            tom = basisDato.minusDays(2),
                        ),
                ),
            )

        // Det skal kun gjøres request til LovMe for den første søknaden.
        medlemskapMockWebServer.takeRequest(10, TimeUnit.MILLISECONDS) shouldNotBe null
        medlemskapMockWebServer.takeRequest(10, TimeUnit.MILLISECONDS) shouldBe null

        assertThat(forsteSoknad).hasSize(1)

        // Det skal være gjort en medlemskapsvurdering for den første søknaden, men ikke den andre.
        assertThat(forsteSoknad.last().medlemskapVurdering).isEqualTo("UAVKLART")
        assertThat(andreSoknad.last().medlemskapVurdering).isNull()

        // Begge søknader er 'forstegangssoknad' siden de har forskjellig arbeidsgiver.
        assertThat(forsteSoknad.last().forstegangssoknad).isTrue()
        assertThat(andreSoknad.last().forstegangssoknad).isTrue()

        // Første søknad skal inneholde spørsmål om medlemskap, men ikke ARBEID_UTENFOR_NORGE.
        forsteSoknad.last().sporsmal.flatten().map { it.tag }
            .apply {
                assertThat(this).contains(
                    MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE,
                    MEDLEMSKAP_OPPHOLD_UTENFOR_EOS,
                    MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE,
                    MEDLEMSKAP_OPPHOLDSTILLATELSE,
                )
            }.also {
                assertThat(it).doesNotContain(
                    ARBEID_UTENFOR_NORGE,
                )
            }

        // TODO: Blir dette riktig siden den andre søknaden også strengt tatt er en førstegangssøknad?
        // Andre søknad skal hverken inneholde ARBEID_UTENFOR_NORGE eller spørsmål om medlemskap siden vi har stilt
        // spørsmål om medlemskapspørsmål til den samme brukeren i en søknad for en annen arbeidsgiver.
        assertThat(andreSoknad.last().sporsmal.flatten().map { it.tag }).doesNotContain(
            MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE,
            MEDLEMSKAP_OPPHOLD_UTENFOR_EOS,
            MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE,
            MEDLEMSKAP_OPPHOLDSTILLATELSE,
            ARBEID_UTENFOR_NORGE,
        )
    }

    @Test
    fun `Søknader til hver sin arbeidsgiver som aktiveres frem i tid skal ha ha spørsmål om medlemskap`() {
        val fnr = "41111111111"

        val forsteSoknad =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    fnr = fnr,
                    arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = basisDato.plusDays(2),
                            tom = basisDato.plusDays(6),
                        ),
                ),
            )

        val andreSoknad =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    fnr = fnr,
                    arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "000000002", orgNavn = "Arbeidsgiver 2"),
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = basisDato.plusDays(2),
                            tom = basisDato.plusDays(6),
                        ),
                ),
            )

        // Ingen av søknadene skal være aktivert enda.
        assertThat(forsteSoknad.last().status).isEqualTo(SoknadsstatusDTO.FREMTIDIG)
        assertThat(andreSoknad.last().status).isEqualTo(SoknadsstatusDTO.FREMTIDIG)

        // Aktiverer søknadene.
        aktiveringJob.bestillAktivering(now = basisDato.plusDays(7))
        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2).tilSoknader()

        // Sikrer at vi referer til riktig soknad.
        assertThat(kafkaSoknader).hasSize(2)
        val forsteKafkaSoknad = kafkaSoknader.find { it.id == forsteSoknad.last().id }
        val andreKafkaSoknad = kafkaSoknader.find { it.id == andreSoknad.last().id }

        // Aktiverte søknader skal ha status FFREMTIDIG.
        assertThat(forsteKafkaSoknad!!.status).isEqualTo(SoknadsstatusDTO.NY)
        assertThat(andreKafkaSoknad!!.status).isEqualTo(SoknadsstatusDTO.NY)

        // Det skal kun gjøres request til LovMe for den første søknaden.
        medlemskapMockWebServer.takeRequest(10, TimeUnit.MILLISECONDS) shouldBe null

        // Begge søknadene mangler både spørsmål om medlemskap og ARBEID_UTENFOR_NORGE.
        kafkaSoknader.forEach { soknad ->
            assertThat(soknad.sporsmal.flatten().map { it.tag }).doesNotContain(
                MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE,
                MEDLEMSKAP_OPPHOLD_UTENFOR_EOS,
                MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE,
                MEDLEMSKAP_OPPHOLDSTILLATELSE,
                ARBEID_UTENFOR_NORGE,
            )
        }
    }

    private fun lagUavklartMockResponse() =
        MockResponse().setResponseCode(200).setBody(
            MedlemskapVurderingResponse(
                svar = MedlemskapVurderingSvarType.UAVKLART,
                sporsmal =
                    listOf(
                        MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
                        MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE,
                        MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_EØS_OMRÅDE,
                        MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_NORGE,
                    ),
            ).serialisertTilString(),
        )
}
