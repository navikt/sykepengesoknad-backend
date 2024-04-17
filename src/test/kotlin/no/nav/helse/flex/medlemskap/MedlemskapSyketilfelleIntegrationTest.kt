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
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
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
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should contain all`
import org.amshove.kluent.`should not contain`
import org.amshove.kluent.`should not contain any`
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

/**
 * Tester at medlemskapspørsmål kun skal stilles i én førstegangssøknad et syketilfelle, uavhengig av arbeidsgiver.
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

        val forsteSoknad =
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
            ).first()

        val andreSoknad =
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
            ).first()

        forsteSoknad.medlemskapVurdering `should be equal to` "UAVKLART"
        forsteSoknad.forstegangssoknad `should be` true

        // Skal ikke ha medlemskapspørsmål siden det ikke er en førstegangssøknad.
        andreSoknad.medlemskapVurdering `should be` null
        andreSoknad.forstegangssoknad `should be` false
    }

    @Test
    fun `Helt overlappende søknad med samme arbeidsgiver får spørsmål siden den første søknaden er slettet`() {
        repeat(2) {
            medlemskapMockWebServer.enqueue(
                lagUavklartMockResponse(),
            )
        }

        val opprinneligSoknad =
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
            ).first()

        opprinneligSoknad.medlemskapVurdering `should be equal to` "UAVKLART"
        opprinneligSoknad.forstegangssoknad `should be` true

        val klippendeSoknader =
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

        // Søknaden opprettet som følge av den første søknaden blir slettet siden den neste sykmeldingen er helt
        // overlappende. Både slettet og ny søknad returneres.
        klippendeSoknader.size `should be equal to` 2

        val forsteSoknad = klippendeSoknader.first()
        val overlappendeSoknad = klippendeSoknader.last()

        forsteSoknad.medlemskapVurdering `should be` null
        forsteSoknad.status `should be equal to` SLETTET

        overlappendeSoknad.medlemskapVurdering `should be equal to` "UAVKLART"
        overlappendeSoknad.status `should be equal to` NY
        overlappendeSoknad.forstegangssoknad `should be` true
    }

    @Test
    fun `Kun den første av søknader som klippes får medlemskapspørsmål`() {
        repeat(2) {
            medlemskapMockWebServer.enqueue(
                lagUavklartMockResponse(),
            )
        }

        val opprinneligSoknad =
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
            ).first()

        opprinneligSoknad.medlemskapVurdering `should be equal to` "UAVKLART"
        opprinneligSoknad.forstegangssoknad `should be` true

        val klippendeSoknader =
            sendSykmelding(
                forventaSoknader = 2,
                // Tvinger samme startSyketilfelle siden dette er en mock og ikke et faktisk kall til flex-syketilfelle.
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

        // Søknadene opprettes som følge av at den første søknaden blir klippet siden den delvis overlappes av neste
        // sykmelding. Begge søknader returneres etter klippingen.
        klippendeSoknader.size `should be equal to` 2

        val forsteSoknad = klippendeSoknader.first()
        val andreSoknad = klippendeSoknader.last()

        forsteSoknad.id `should be equal to` opprinneligSoknad.id
        forsteSoknad.medlemskapVurdering `should be equal to` "UAVKLART"
        forsteSoknad.forstegangssoknad `should be` true

        andreSoknad.medlemskapVurdering `should be` null
        andreSoknad.forstegangssoknad `should be` false
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
                            ) +
                                reisetilskudd(
                                    fom = LocalDate.of(2023, 1, 8),
                                    tom = LocalDate.of(2023, 1, 17),
                                ),
                    ),
            )

        // Reisetilskudd er ikke kompatibelt med medlemskapspørsmål og vil resultere i to søknader
        // i stedet for at periodene blir slått sammen til én søknad.
        soknader.size `should be equal to` 2

        val arbeidstakerSoknad = soknader.first()
        val reisetilskuddSoknad = soknader.last()

        arbeidstakerSoknad.type `should be equal to` SoknadstypeDTO.ARBEIDSTAKERE
        arbeidstakerSoknad.medlemskapVurdering `should be equal to` "UAVKLART"

        reisetilskuddSoknad.type `should be equal to` SoknadstypeDTO.REISETILSKUDD
        reisetilskuddSoknad.medlemskapVurdering `should be` null
        reisetilskuddSoknad.forstegangssoknad `should be` false
    }

    @Test
    fun `Påfølgende søknad får ikke medlemskapspørsmål selv om første søknad i samme syketilfelle mangler spørsmål`() {
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
                            fom = LocalDate.of(2023, 1, 1),
                            tom = LocalDate.of(2023, 1, 7),
                        ),
                ),
            ).first()

        // Simulerer at søknaden ble opprettet før vi begynte å stille medlemskapspørsmål ved å slette spørsmålene.
        slettMedlemskapSporsmal(forsteSoknad)
        val lagretForstegangssoknad = hentSoknad(fnr = fnr, soknadId = forsteSoknad.id)
        lagretForstegangssoknad.sporsmal!!.find { it.tag == MEDLEMSKAP_OPPHOLDSTILLATELSE } `should be` null

        val andreSoknad =
            sendSykmelding(
                // Setter samme startSyketilfelle siden dette er en mock og ikke et faktisk kall til flex-syketilfelle.
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
            ).first()

        // Påfølgende søknad ikke ha medlemskapspørsmål siden det ikke er en førstegangssøknad.
        andreSoknad.medlemskapVurdering `should be` null
        andreSoknad.forstegangssoknad `should be` false
    }

    @Test
    fun `Tilbakedatert søknad med tidligere startSyketilfelle får medlemskapspørsmål`() {
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
                            fom = LocalDate.of(2023, 1, 8),
                            tom = LocalDate.of(2023, 1, 17),
                        ),
                ),
            ).first()

        val andreSoknad =
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
            ).first()

        forsteSoknad.medlemskapVurdering `should be equal to` "UAVKLART"
        forsteSoknad.forstegangssoknad `should be` true

        // En tilbakedatert søknad vil få medlemskapspørsmål siden den vil ha en annen dato for startSykeforløp.
        forsteSoknad.medlemskapVurdering `should be equal to` "UAVKLART"
        andreSoknad.forstegangssoknad `should be` true
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
            ).first()

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
            ).first()

        // Begge søknadene er 'forstegangssoknad' siden de har forskjellig arbeidsgiver.
        forsteSoknad.forstegangssoknad `should be` true
        andreSoknad.forstegangssoknad `should be` true

        // Første søknad skal inneholde spørsmål om medlemskap, men ikke ARBEID_UTENFOR_NORGE.
        forsteSoknad.medlemskapVurdering `should be equal to` "UAVKLART"
        forsteSoknad.sporsmal.flatten().map { it.tag }
            .apply {
                this `should contain all`
                    listOf(
                        MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE,
                        MEDLEMSKAP_OPPHOLD_UTENFOR_EOS,
                        MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE,
                        MEDLEMSKAP_OPPHOLDSTILLATELSE,
                    )
            }.also {
                it `should not contain` ARBEID_UTENFOR_NORGE
            }

        // Andre søknad skal hverken inneholde ARBEID_UTENFOR_NORGE eller spørsmål om medlemskap siden vi har stilt
        // spørsmål om medlemskapspørsmål til den samme brukeren i en søknad for en annen arbeidsgiver.
        andreSoknad.medlemskapVurdering `should be` null
        andreSoknad.sporsmal.flatten().map { it.tag } `should not contain any`
            listOf(
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
            ).first()

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
            ).first()

        forsteSoknad.status `should be equal to` FREMTIDIG
        andreSoknad.status `should be equal to` FREMTIDIG

        // Aktiverer begge søknadene samtidig
        aktiveringJob.bestillAktivering(now = basisDato.plusDays(7))
        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2).tilSoknader()

        kafkaSoknader.size `should be equal to` 2
        val forsteKafkaSoknad = kafkaSoknader.find { it.id == forsteSoknad.id }
        val andreKafkaSoknad = kafkaSoknader.find { it.id == andreSoknad.id }

        forsteKafkaSoknad!!.status `should be equal to` NY
        andreKafkaSoknad!!.status `should be equal to` NY

        // Begge søknadene er 'forstegangssoknad' siden de har forskjellig arbeidsgiver.
        forsteKafkaSoknad.forstegangssoknad `should be` true
        andreKafkaSoknad.forstegangssoknad `should be` true

        // Første søknad skal inneholde spørsmål om medlemskap, men ikke ARBEID_UTENFOR_NORGE.
        forsteKafkaSoknad.medlemskapVurdering `should be equal to` "UAVKLART"
        forsteKafkaSoknad.sporsmal.flatten().map { it.tag }
            .apply {
                this `should contain all`
                    listOf(
                        MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE,
                        MEDLEMSKAP_OPPHOLD_UTENFOR_EOS,
                        MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE,
                        MEDLEMSKAP_OPPHOLDSTILLATELSE,
                    )
            }.also {
                it `should not contain` ARBEID_UTENFOR_NORGE
            }

        // Andre søknad skal hverken inneholde ARBEID_UTENFOR_NORGE eller spørsmål om medlemskap siden vi har stilt
        // spørsmål om medlemskapspørsmål til den samme brukeren i en søknad for en annen arbeidsgiver.
        andreKafkaSoknad.medlemskapVurdering `should be` null
        andreKafkaSoknad.sporsmal.flatten().map { it.tag } `should not contain any`
            listOf(
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
        val medlemskapTags =
            listOf(
                MEDLEMSKAP_OPPHOLDSTILLATELSE,
                MEDLEMSKAP_OPPHOLD_UTENFOR_EOS,
                MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE,
                MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE,
            )

        val medlemskapSporsmal =
            soknad.sporsmal!!
                .filter { it.tag in medlemskapTags }
                .flatten()
                .map { it.id!! }

        sporsmalDAO.slettEnkeltSporsmal(medlemskapSporsmal)
    }
}
