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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

// TODO: Flytt disse testene til MedlemskapSyketilfelleIntegrationTest.kt og rydd i eventuelle duplikater der.

/**
 * Verifiserer at en av to førstegangssøknader med forskjellig arbeidsgiver før spørsmål om medlemskap
 * eller ARBEID_UTENFOR_NORGE både når de aktiveres én og en eller og når de aktiveres frem i tid.
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
    fun `To søknader i samme syketilfelle til hver sin arbeidsgiver aktiveres når sykmelding sendes`() {
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
            ).last()

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
            ).last()

        // Det skal være gjort en medlemskapsvurdering for den første søknaden, men ikke den andre.
        assertThat(forsteSoknad.medlemskapVurdering).isEqualTo("UAVKLART")
        assertThat(andreSoknad.medlemskapVurdering).isNull()

        // Begge søknader er 'forstegangssoknad' siden de har forskjellig arbeidsgiver.
        assertThat(forsteSoknad.forstegangssoknad).isTrue()
        assertThat(andreSoknad.forstegangssoknad).isTrue()

        // Første søknad skal inneholde spørsmål om medlemskap, men ikke ARBEID_UTENFOR_NORGE.
        forsteSoknad.sporsmal.flatten().map { it.tag }
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

        // Andre søknad skal hverken inneholde ARBEID_UTENFOR_NORGE eller spørsmål om medlemskap siden vi har stilt
        // spørsmål om medlemskapspørsmål til den samme brukeren i en søknad for en annen arbeidsgiver.
        assertThat(andreSoknad.sporsmal.flatten().map { it.tag }).doesNotContain(
            MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE,
            MEDLEMSKAP_OPPHOLD_UTENFOR_EOS,
            MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE,
            MEDLEMSKAP_OPPHOLDSTILLATELSE,
            ARBEID_UTENFOR_NORGE,
        )
    }

    @Test
    fun `To søknader i samme syketilfelle til hver sin arbeidsgiver aktiveres samtidig`() {
        val fnr = "41111111111"

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
                            fom = basisDato.plusDays(2),
                            tom = basisDato.plusDays(6),
                        ),
                ),
            ).last()

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
            ).last()

        // Ingen av søknadene skal være aktivert enda.
        assertThat(forsteSoknad.status).isEqualTo(SoknadsstatusDTO.FREMTIDIG)
        assertThat(andreSoknad.status).isEqualTo(SoknadsstatusDTO.FREMTIDIG)

        // Aktiverer søknadene.
        aktiveringJob.bestillAktivering(now = basisDato.plusDays(7))
        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2).tilSoknader()

        // Sikrer at vi referer til riktig soknad mottatt på Kafka.
        assertThat(kafkaSoknader).hasSize(2)
        val forsteKafkaSoknad = kafkaSoknader.find { it.id == forsteSoknad.id }
        val andreKafkaSoknad = kafkaSoknader.find { it.id == andreSoknad.id }

        // Søkander skal være aktivert.
        assertThat(forsteKafkaSoknad!!.status).isEqualTo(SoknadsstatusDTO.NY)
        assertThat(andreKafkaSoknad!!.status).isEqualTo(SoknadsstatusDTO.NY)

        // Det skal være gjort en medlemskapsvurdering for den første søknaden, men ikke den andre.
        assertThat(forsteKafkaSoknad.medlemskapVurdering).isEqualTo("UAVKLART")
        assertThat(andreKafkaSoknad.medlemskapVurdering).isNull()

        // Begge søknader er 'forstegangssoknad' siden de har forskjellig arbeidsgiver.
        assertThat(forsteKafkaSoknad.forstegangssoknad).isTrue()
        assertThat(andreKafkaSoknad.forstegangssoknad).isTrue()

        // Første søknad skal inneholde spørsmål om medlemskap, men ikke ARBEID_UTENFOR_NORGE.
        forsteKafkaSoknad.sporsmal.flatten().map { it.tag }
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

        // Andre søknad skal hverken inneholde ARBEID_UTENFOR_NORGE eller spørsmål om medlemskap siden vi har stilt
        // spørsmål om medlemskapspørsmål til den samme brukeren i en søknad for en annen arbeidsgiver.
        assertThat(andreKafkaSoknad.sporsmal.flatten().map { it.tag }).doesNotContain(
            MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE,
            MEDLEMSKAP_OPPHOLD_UTENFOR_EOS,
            MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE,
            MEDLEMSKAP_OPPHOLDSTILLATELSE,
            ARBEID_UTENFOR_NORGE,
        )
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
