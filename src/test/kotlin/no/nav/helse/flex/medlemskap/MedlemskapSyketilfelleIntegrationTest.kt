package no.nav.helse.flex.medlemskap

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.aktivering.AktiveringJob
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.hentProduserteRecords
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.repository.SporsmalDAO
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.ARBEID_UTENFOR_NORGE
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_OPPHOLDSTILLATELSE
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_OPPHOLD_UTENFOR_EOS
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.reisetilskudd
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
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

// TODO: Bruk kluent stedet for assertThat() for likhet på tvers av tester.
/**
 * Tester at medlemskapspørsmål kun skal stilles i én førstegangssøknad et syketilfelle, uavhenging av arbeidsgiver.
 *
 * @see MedlemskapSporsmalIntegrationTest for testing av hele flyten fra sykmelding blir sendt til spørsmål
 *      om medlemskap blir besvart og søknaden sendt inn.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MedlemskapSyketilfelleIntegrationTest : FellesTestOppsett() {

    @Autowired
    private lateinit var sporsmalDAO: SporsmalDAO

    @Autowired
    private lateinit var aktiveringJob: AktiveringJob

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

    private val fnr = "31111111111"

    @Test
    fun `Påfølgende søknad får ikke medlemskapspørsmål`() {
        medlemskapMockWebServer.enqueue(
            lagUavklartMockResponse(),
        )

        val soknader1 =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    fnr = fnr,
                    arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = LocalDate.of(2023, 1, 1),
                            tom = LocalDate.of(2023, 1, 7),
                        ),
                ),
            )

        val soknader2 =
            sendSykmelding(
                oppfolgingsdato = LocalDate.of(2023, 1, 1),
                sykmeldingKafkaMessage =
                    sykmeldingKafkaMessage(
                        arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                        fnr = fnr,
                        arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                        sykmeldingsperioder =
                            heltSykmeldt(
                                fom = LocalDate.of(2023, 1, 8),
                                tom = LocalDate.of(2023, 1, 17),
                            ),
                    ),
            )

        assertThat(soknader1).hasSize(1)
        assertThat(soknader1.last().medlemskapVurdering).isEqualTo("UAVKLART")
        assertThat(soknader1.last().forstegangssoknad).isTrue()

        assertThat(soknader2).hasSize(1)
        // Skal ikke ha medlemskapspørsmål siden det ikke er en førstegangssøknad.
        assertThat(soknader2.last().medlemskapVurdering).isNull()
        assertThat(soknader2.last().forstegangssoknad).isFalse()
    }

    @Test
    fun `Helt overlappende søknad med samme arbeidsgiver får spørsmål siden den første søknaden er slettet`() {
        repeat(2) {
            medlemskapMockWebServer.enqueue(
                lagUavklartMockResponse(),
            )
        }

        val soknader1 =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    fnr = fnr,
                    arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = LocalDate.of(2023, 1, 1),
                            tom = LocalDate.of(2023, 1, 7),
                        ),
                ),
            )

        val soknader2 =
            sendSykmelding(
                forventaSoknader = 2,
                sykmeldingKafkaMessage =
                    sykmeldingKafkaMessage(
                        arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                        fnr = fnr,
                        arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                        sykmeldingsperioder =
                            heltSykmeldt(
                                fom = LocalDate.of(2023, 1, 1),
                                tom = LocalDate.of(2023, 1, 7),
                            ),
                    ),
            )

        assertThat(soknader1).hasSize(1)
        assertThat(soknader1.last().medlemskapVurdering).isEqualTo("UAVKLART")
        assertThat(soknader1.last().forstegangssoknad).isTrue()

        assertThat(soknader2).hasSize(2)
        // Søknaden opprettet som følge av den første søknaden blir slettet siden den neste sykmeldingen er helt
        // overlappende. Både slettet og ny søknad returneres.
        assertThat(soknader2.first().medlemskapVurdering).isNull()
        assertThat(soknader2.last().medlemskapVurdering).isEqualTo("UAVKLART")
        assertThat(soknader2.last().forstegangssoknad).isTrue()
    }

    @Test
    fun `Kun den første av søknader som klippes får medlemskapspørsmål`() {
        repeat(2) {
            medlemskapMockWebServer.enqueue(
                lagUavklartMockResponse(),
            )
        }

        val soknader1 =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    fnr = fnr,
                    arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = LocalDate.of(2023, 1, 1),
                            tom = LocalDate.of(2023, 1, 7),
                        ),
                ),
            )

        val soknader2 =
            sendSykmelding(
                forventaSoknader = 2,
                // Tvinger samme startStyketilfelle siden dette er en mock og ikke et faktisk kall til flex-syketilfelle.
                oppfolgingsdato = LocalDate.of(2023, 1, 1),
                sykmeldingKafkaMessage =
                    sykmeldingKafkaMessage(
                        arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                        fnr = fnr,
                        arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                        sykmeldingsperioder =
                            heltSykmeldt(
                                fom = LocalDate.of(2023, 1, 5),
                                tom = LocalDate.of(2023, 1, 17),
                            ),
                    ),
            )

        assertThat(soknader1).hasSize(1)
        assertThat(soknader1.last().medlemskapVurdering).isEqualTo("UAVKLART")
        assertThat(soknader1.last().forstegangssoknad).isTrue()

        // Søknaden opprettet som følge av den første søknaden blir klippet siden den delvis overlappes av neste
        // sykmelding. Begge søknader returneres etter klippingen.
        assertThat(soknader2).hasSize(2)
        assertThat(soknader2.first().medlemskapVurdering).isEqualTo("UAVKLART")
        assertThat(soknader2.first().forstegangssoknad).isTrue()
        assertThat(soknader2.last().medlemskapVurdering).isNull()
        assertThat(soknader2.last().forstegangssoknad).isFalse()
    }

    @Test
    fun `Andre periode i ikke-kompatibel søknad får ikke medlemskapspørsmål`() {
        medlemskapMockWebServer.enqueue(
            lagUavklartMockResponse(),
        )

        val soknader =
            sendSykmelding(
                forventaSoknader = 2,
                sykmeldingKafkaMessage =
                    sykmeldingKafkaMessage(
                        arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                        fnr = fnr,
                        arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                        sykmeldingsperioder =
                            heltSykmeldt(
                                fom = LocalDate.of(2023, 1, 1),
                                tom = LocalDate.of(2023, 1, 7),
                                // Reisetilskudd er ikke kompatibelt med medlemskapspørsmål og vil resultere i to søknader
                                // i stedet for at periodene blir slått sammen til én søknad.
                            ) +
                                reisetilskudd(
                                    fom = LocalDate.of(2023, 1, 8),
                                    tom = LocalDate.of(2023, 1, 17),
                                ),
                    ),
            )

        assertThat(soknader).hasSize(2)
        assertThat(soknader.first().medlemskapVurdering).isEqualTo("UAVKLART")
        assertThat(soknader.last().medlemskapVurdering).isNull()
        assertThat(soknader.last().forstegangssoknad).isFalse()
    }

    @Test
    fun `Påfølgende søknad får ikke medlemskapspørsmål selv om første søknad i samme syketilfelle mangler spørsmål`() {
        medlemskapMockWebServer.enqueue(
            lagUavklartMockResponse(),
        )

        val soknader1 =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    fnr = fnr,
                    arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = LocalDate.of(2023, 1, 1),
                            tom = LocalDate.of(2023, 1, 7),
                        ),
                ),
            )

        // Simulerer at søknaden ble opprettet før vi begynte å stille medlemskapspørsmål ved å slette spørsmålene.
        slettMedlemskapSporsmal(soknader1.first())

        val soknader2 =
            sendSykmelding(
                oppfolgingsdato = LocalDate.of(2023, 1, 1),
                sykmeldingKafkaMessage =
                    sykmeldingKafkaMessage(
                        arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                        fnr = fnr,
                        arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                        sykmeldingsperioder =
                            heltSykmeldt(
                                fom = LocalDate.of(2023, 1, 8),
                                tom = LocalDate.of(2023, 1, 17),
                            ),
                    ),
            )

        val lagretForstegangssoknad = hentSoknad(fnr = fnr, soknadId = soknader1.first().id)
        assertThat(lagretForstegangssoknad.sporsmal!!.find { it.tag == MEDLEMSKAP_OPPHOLDSTILLATELSE }).isNull()

        assertThat(soknader2).hasSize(1)
        // Skal ikke ha medlemskapspørsmål siden det ikke er en førstegangssøknad.
        assertThat(soknader2.last().medlemskapVurdering).isNull()
        assertThat(soknader2.last().forstegangssoknad).isFalse()
    }

    @Test
    fun `Tilbakedatert søknad med tidligere startSyketilfelle får ikke medlemskapspørsmål`() {
        medlemskapMockWebServer.enqueue(
            lagUavklartMockResponse(),
        )

        val soknader1 =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    fnr = fnr,
                    arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = LocalDate.of(2023, 1, 8),
                            tom = LocalDate.of(2023, 1, 17),
                        ),
                ),
            )

        val soknader2 =
            sendSykmelding(
                sykmeldingKafkaMessage =
                    sykmeldingKafkaMessage(
                        arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                        fnr = fnr,
                        arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                        sykmeldingsperioder =
                            heltSykmeldt(
                                fom = LocalDate.of(2023, 1, 1),
                                tom = LocalDate.of(2023, 1, 7),
                            ),
                    ),
            )

        assertThat(soknader1).hasSize(1)
        assertThat(soknader1.last().medlemskapVurdering).isEqualTo("UAVKLART")
        assertThat(soknader1.last().forstegangssoknad).isTrue()

        // En tilbakedatert søknad vil ikke få medlemskapspørsmål siden en søknad opprettet før den tilbakedaterte
        // allerede har medlemskapspørsmål.
        assertThat(soknader2).hasSize(1)
        assertThat(soknader2.last().medlemskapVurdering).isNull()
        assertThat(soknader2.last().forstegangssoknad).isTrue()
    }

    @Test
    fun `En av to søknader til hver sin arbeidsgiver får medlemskapspørsmål når søknadene aktiveres hver for seg`() {
        val fnr = "31111111111"
        val basisDato = LocalDate.now()

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

        // Begge søknader er 'forstegangssoknad' siden de har forskjellig arbeidsgiver.
        assertThat(forsteSoknad.forstegangssoknad).isTrue()
        assertThat(andreSoknad.forstegangssoknad).isTrue()

        // Første søknad skal inneholde spørsmål om medlemskap, men ikke ARBEID_UTENFOR_NORGE.
        assertThat(forsteSoknad.medlemskapVurdering).isEqualTo("UAVKLART")

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
        assertThat(andreSoknad.medlemskapVurdering).isNull()

        assertThat(andreSoknad.sporsmal.flatten().map { it.tag }).doesNotContain(
            MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE,
            MEDLEMSKAP_OPPHOLD_UTENFOR_EOS,
            MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE,
            MEDLEMSKAP_OPPHOLDSTILLATELSE,
            ARBEID_UTENFOR_NORGE,
        )
    }

    @Test
    fun `En av to søknader til hver sin arbeidsgiver får medlemskapspørsmål når søknadene aktiveres samtidig`() {
        val fnr = "41111111111"
        val basisDato = LocalDate.now()

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

        assertThat(forsteSoknad.status).isEqualTo(SoknadsstatusDTO.FREMTIDIG)
        assertThat(andreSoknad.status).isEqualTo(SoknadsstatusDTO.FREMTIDIG)

        aktiveringJob.bestillAktivering(now = basisDato.plusDays(7))
        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2).tilSoknader()

        assertThat(kafkaSoknader).hasSize(2)
        val forsteKafkaSoknad = kafkaSoknader.find { it.id == forsteSoknad.id }
        val andreKafkaSoknad = kafkaSoknader.find { it.id == andreSoknad.id }

        assertThat(forsteKafkaSoknad!!.status).isEqualTo(SoknadsstatusDTO.NY)
        assertThat(andreKafkaSoknad!!.status).isEqualTo(SoknadsstatusDTO.NY)

        // Begge søknader er 'forstegangssoknad' siden de har forskjellig arbeidsgiver.
        assertThat(forsteKafkaSoknad.forstegangssoknad).isTrue()
        assertThat(andreKafkaSoknad.forstegangssoknad).isTrue()

        // Første søknad skal inneholde spørsmål om medlemskap, men ikke ARBEID_UTENFOR_NORGE.
        assertThat(forsteKafkaSoknad.medlemskapVurdering).isEqualTo("UAVKLART")

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
        assertThat(andreKafkaSoknad.medlemskapVurdering).isNull()

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

    private fun slettMedlemskapSporsmal(soknad: SykepengesoknadDTO) {
        val medlemskapSporsmal =
            soknad.sporsmal!!
                .filter {
                    it.tag in
                        listOf(
                            MEDLEMSKAP_OPPHOLDSTILLATELSE,
                            MEDLEMSKAP_OPPHOLD_UTENFOR_EOS,
                            MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE,
                            MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE,
                        )
                }.flatten()
        sporsmalDAO.slettEnkeltSporsmal(medlemskapSporsmal.map { it.id!! }.distinct())
    }
}
