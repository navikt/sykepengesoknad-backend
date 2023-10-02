package no.nav.helse.flex.medlemskap

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.hentProduserteRecords
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.repository.SporsmalDAO
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_OPPHOLDSTILLATELSE
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_OPPHOLD_UTENFOR_EOS
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.reisetilskudd
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.util.flatten
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MedlemskapSyketilfelleIntegrationTest : BaseTestClass() {

    @Autowired
    private lateinit var sporsmalDAO: SporsmalDAO

    // Trigger response fra LovMe med alle spørsmål.
    private final val fnr = "31111111111"

    @AfterAll
    fun slettAlleMedlemskapVurderinger() {
        juridiskVurderingKafkaConsumer.hentProduserteRecords()
    }

    @AfterEach
    fun hentAlleKafkaMeldinger() {
        databaseReset.resetDatabase()
    }

    @Test
    fun `Påfølgende søknad får ikke medlemskapspørsmål`() {
        val soknader1 = sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 7)
                )
            )
        )

        val soknader2 = sendSykmelding(
            oppfolgingsdato = LocalDate.of(2023, 1, 1),
            sykmeldingKafkaMessage = sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2023, 1, 8),
                    tom = LocalDate.of(2023, 1, 17)
                )
            )
        )

        assertThat(soknader1).hasSize(1)
        assertThat(soknader1.last().medlemskapVurdering).isEqualTo("UAVKLART")

        assertThat(soknader2).hasSize(1)
        // Skal ikke ha medlemskapspørsmål siden det ikke er en førstegangssøknad.
        assertThat(soknader2.last().medlemskapVurdering).isNull()
    }

    @Test
    fun `Helt overlappende får spørsmål siden den første er slettet`() {
        val soknader1 = sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 7)
                )
            )
        )

        val soknader2 = sendSykmelding(
            forventaSoknader = 2,
            sykmeldingKafkaMessage = sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 7)
                )
            )
        )

        assertThat(soknader1).hasSize(1)
        assertThat(soknader1.last().medlemskapVurdering).isEqualTo("UAVKLART")

        assertThat(soknader2).hasSize(2)
        // Søknaden opprettet som følge av den første søkanden blir slettet siden den neste sykmeldingen er helt
        // overlappende. Både slettet og ny søkand returneres.
        assertThat(soknader2.first().medlemskapVurdering).isNull()
        assertThat(soknader2.last().medlemskapVurdering).isEqualTo("UAVKLART")
    }

    @Test
    fun `Kun den første av søknader som klippes får medlemskapspørsmål`() {
        val soknader1 = sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 7)
                )
            )
        )

        val soknader2 = sendSykmelding(
            forventaSoknader = 2,
            // Tvinger samme startStyketilfelle siden dette er en mock og ikke et faktisk kall til flex-syketilfelle.
            oppfolgingsdato = LocalDate.of(2023, 1, 1),
            sykmeldingKafkaMessage = sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2023, 1, 5),
                    tom = LocalDate.of(2023, 1, 17)
                )
            )
        )

        assertThat(soknader1).hasSize(1)
        assertThat(soknader1.last().medlemskapVurdering).isEqualTo("UAVKLART")

        // Søknaden opprettet som følge av den første søkanden blir klippet siden den delvis overlappes av neste
        // sykmelding. Begge søknader returneres etter klippingen.
        assertThat(soknader2).hasSize(2)
        assertThat(soknader2.first().medlemskapVurdering).isEqualTo("UAVKLART")
        assertThat(soknader2.last().medlemskapVurdering).isNull()
    }

    @Test
    fun `Andre periode i ikke-kompatibel søknad får ikke medlemskapspørsmål`() {
        val soknader = sendSykmelding(
            forventaSoknader = 2,
            sykmeldingKafkaMessage = sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 7)
                ) + reisetilskudd(
                    fom = LocalDate.of(2023, 1, 8),
                    tom = LocalDate.of(2023, 1, 17)

                )
            )
        )

        assertThat(soknader).hasSize(2)
        assertThat(soknader.first().medlemskapVurdering).isEqualTo("UAVKLART")
        assertThat(soknader.last().medlemskapVurdering).isNull()
    }

    @Test
    fun `Påfølgende søknad får ikke medlemskapspørsmål selv om det ikke er stilt spørsmål tidligere i syketilfelle`() {
        val soknader1 = sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 7)
                )
            )
        )

        // Simulerer at søknaden ble opprettet før vi begynte å stille medlemskapspørsmål ved å slette spørsmålene.
        slettMedlemskapSporsmal(soknader1.first())

        val soknader2 = sendSykmelding(
            oppfolgingsdato = LocalDate.of(2023, 1, 1),
            sykmeldingKafkaMessage = sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2023, 1, 8),
                    tom = LocalDate.of(2023, 1, 17)
                )
            )
        )

        val lagretForstegangssoknad = hentSoknad(fnr = fnr, soknadId = soknader1.first().id)
        assertThat(lagretForstegangssoknad.sporsmal!!.find { it.tag == MEDLEMSKAP_OPPHOLDSTILLATELSE }).isNull()

        assertThat(soknader2).hasSize(1)
        // Skal ikke ha medlemskapspørsmål siden det ikke er en førstegangssøknad.
        assertThat(soknader2.last().medlemskapVurdering).isNull()
    }

    @Test
    fun `Tilbakedatert søknad får medlemskapspørsmål`() {
        val soknader1 = sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2023, 1, 8),
                    tom = LocalDate.of(2023, 1, 17)
                )
            )
        )

        val soknader2 = sendSykmelding(
            sykmeldingKafkaMessage = sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "000000001", orgNavn = "Arbeidsgiver 1"),
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 7)
                )
            )
        )

        assertThat(soknader1).hasSize(1)
        assertThat(soknader1.last().medlemskapVurdering).isEqualTo("UAVKLART")

        // Den opprinnelige førstegangssøknaden får ikke oppdatert startSyketilfelle, så ny søknad tilfredstiller
        // sjekken på om det er en førstegangsøknad og at det ikke er stilt medlemskapspørsmål tidligere i syketilfelle.
        assertThat(soknader2).hasSize(1)
        assertThat(soknader2.last().medlemskapVurdering).isEqualTo("UAVKLART")
    }

    private fun slettMedlemskapSporsmal(soknad: SykepengesoknadDTO) {
        val medlemskapSporsmal = soknad.sporsmal!!
            .filter {
                it.tag in listOf(
                    MEDLEMSKAP_OPPHOLDSTILLATELSE,
                    MEDLEMSKAP_OPPHOLD_UTENFOR_EOS,
                    MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE,
                    MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE
                )
            }.flatten()
        sporsmalDAO.slettEnkeltSporsmal(medlemskapSporsmal.map { it.id!! }.distinct())
    }
}