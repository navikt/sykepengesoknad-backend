package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.testdata.behandingsdager
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class LegacyOpprettelseAvSoknadIntegrationTest : BaseTestClass() {

    val fnr = "fnr"

    @BeforeEach
    @AfterEach
    fun setUp() {
        databaseReset.resetDatabase()
    }

    @Test
    fun `Når man oppretter to søknader i samme forløp vil fravær før sykmeldingen og arbeid utenfor norge spørsmålet kun være i den første`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = (LocalDate.of(2018, 1, 1)),
                    tom = (LocalDate.of(2018, 1, 10))
                )
            )
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2018, 1, 11),
                    tom = LocalDate.of(2018, 1, 12)
                )
            ),
            oppfolgingsdato = LocalDate.of(2018, 1, 1)
        )

        val soknaderMetadata = hentSoknaderMetadata(fnr).sortedBy { it.fom }

        val forsteSoknad = hentSoknad(
            soknadId = soknaderMetadata.first().id,
            fnr = fnr
        )
        assertThat(forsteSoknad.sporsmal!!.any { it.tag == FRAVAR_FOR_SYKMELDINGEN }).isTrue()
        assertThat(forsteSoknad.sporsmal!!.any { it.tag == ARBEID_UTENFOR_NORGE }).isTrue()

        val andreSoknad = hentSoknad(
            soknadId = soknaderMetadata.last().id,
            fnr = fnr
        )
        assertThat(andreSoknad.sporsmal!!.any { it.tag == FRAVAR_FOR_SYKMELDINGEN }).isFalse()
        assertThat(andreSoknad.sporsmal!!.any { it.tag == ARBEID_UTENFOR_NORGE }).isFalse()
    }

    @Test
    fun `Når man oppretter to søknader om behandlingsdager i ulike forløp vil egenmeldingspørsmålet være i begge`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = behandingsdager(
                    fom = LocalDate.of(2018, 1, 1),
                    tom = LocalDate.of(2018, 1, 10)
                )
            ),
            oppfolgingsdato = LocalDate.of(2018, 1, 1)
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = behandingsdager(
                    fom = LocalDate.of(2018, 1, 11),
                    tom = LocalDate.of(2018, 1, 12)
                )
            ),
            oppfolgingsdato = LocalDate.of(2018, 1, 2)
        )

        val soknaderMetadata = hentSoknaderMetadata(fnr).sortedBy { it.fom }

        val forsteSoknad = hentSoknad(
            soknadId = soknaderMetadata.first().id,
            fnr = fnr
        )
        assertThat(forsteSoknad.sporsmal!!.any { it.tag == FRAVER_FOR_BEHANDLING }).isTrue()
        assertThat(forsteSoknad.sporsmal!!.any { it.tag == ARBEID_UTENFOR_NORGE }).isTrue()

        val andreSoknad = hentSoknad(
            soknadId = soknaderMetadata.last().id,
            fnr = fnr
        )
        assertThat(andreSoknad.sporsmal!!.any { it.tag == FRAVER_FOR_BEHANDLING }).isTrue()
        assertThat(andreSoknad.sporsmal!!.any { it.tag == ARBEID_UTENFOR_NORGE }).isTrue()
    }

    @Test
    fun `Når man oppretter en to søknader om behandlingsdager i samme forløp vil egenmeldingspørsmålet kun være i den første`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = behandingsdager(
                    fom = LocalDate.of(2018, 1, 1),
                    tom = LocalDate.of(2018, 1, 10)
                )
            ),
            oppfolgingsdato = LocalDate.of(2018, 1, 1)
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = behandingsdager(
                    fom = LocalDate.of(2018, 1, 11),
                    tom = LocalDate.of(2018, 1, 12)
                )
            ),
            oppfolgingsdato = LocalDate.of(2018, 1, 1)
        )

        val soknaderMetadata = hentSoknaderMetadata(fnr).sortedBy { it.fom }

        val forsteSoknad = hentSoknad(
            soknadId = soknaderMetadata.first().id,
            fnr = fnr
        )
        assertThat(forsteSoknad.sporsmal!!.any { it.tag == FRAVER_FOR_BEHANDLING }).isTrue()

        val andreSoknad = hentSoknad(
            soknadId = soknaderMetadata.last().id,
            fnr = fnr
        )
        assertThat(andreSoknad.sporsmal!!.any { it.tag == FRAVER_FOR_BEHANDLING }).isFalse()
    }

    @Test
    fun `Når man oppretter en to søknader om behandlingsdager i samme forløp med forskjellig orgnr vil egenmeldingspørsmålet være i begge`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123456789", orgNavn = "123456789"),
                sykmeldingsperioder = behandingsdager(
                    fom = LocalDate.of(2018, 1, 1),
                    tom = LocalDate.of(2018, 1, 10)
                )
            ),
            oppfolgingsdato = LocalDate.of(2018, 1, 1)
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123234243", orgNavn = "123234243"),

                sykmeldingsperioder = behandingsdager(
                    fom = LocalDate.of(2018, 1, 11),
                    tom = LocalDate.of(2018, 1, 12)
                )
            ),
            oppfolgingsdato = LocalDate.of(2018, 1, 1)
        )

        val soknaderMetadata = hentSoknaderMetadata(fnr).sortedBy { it.fom }

        val forsteSoknad = hentSoknad(
            soknadId = soknaderMetadata.first().id,
            fnr = fnr
        )
        assertThat(forsteSoknad.sporsmal!!.any { it.tag == FRAVER_FOR_BEHANDLING }).isTrue()

        val andreSoknad = hentSoknad(
            soknadId = soknaderMetadata.last().id,
            fnr = fnr
        )
        assertThat(andreSoknad.sporsmal!!.any { it.tag == FRAVER_FOR_BEHANDLING }).isTrue()
    }

    @Test
    fun `Når man oppretter en to søknader i samme forløp med forskjellig orgnummer vil fravær før sykmeldingen spørsmålet være i begge`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123456789", orgNavn = "123456789"),
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2018, 1, 1),
                    tom = LocalDate.of(2018, 1, 10)
                )
            ),
            oppfolgingsdato = LocalDate.of(2018, 1, 1)
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123234243", orgNavn = "123234243"),

                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2018, 1, 11),
                    tom = LocalDate.of(2018, 1, 12)
                )
            ),
            oppfolgingsdato = LocalDate.of(2018, 1, 1)
        )

        val soknaderMetadata = hentSoknaderMetadata(fnr).sortedBy { it.fom }

        val forsteSoknad = hentSoknad(
            soknadId = soknaderMetadata.first().id,
            fnr = fnr
        )
        assertThat(forsteSoknad.sporsmal!!.any { it.tag == FRAVAR_FOR_SYKMELDINGEN }).isTrue()

        val andreSoknad = hentSoknad(
            soknadId = soknaderMetadata.last().id,
            fnr = fnr
        )
        assertThat(andreSoknad.sporsmal!!.any { it.tag == FRAVAR_FOR_SYKMELDINGEN }).isTrue()
    }

    @Test
    fun `Når man oppretter to arbeidsledig søknader i samme forløp vil arbeid utenfor norge spørsmålet kun være i den første`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2018, 1, 1),
                    tom = LocalDate.of(2018, 1, 10)
                )
            ),
            oppfolgingsdato = LocalDate.of(2018, 1, 1)
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2018, 1, 11),
                    tom = LocalDate.of(2018, 1, 12)
                )
            ),
            oppfolgingsdato = LocalDate.of(2018, 1, 1)
        )

        val soknaderMetadata = hentSoknaderMetadata(fnr).sortedBy { it.fom }

        val forsteSoknad = hentSoknad(
            soknadId = soknaderMetadata.first().id,
            fnr = fnr
        )
        assertThat(forsteSoknad.sporsmal!!.any { it.tag == ARBEID_UTENFOR_NORGE }).isTrue()

        val andreSoknad = hentSoknad(
            soknadId = soknaderMetadata.last().id,
            fnr = fnr
        )
        assertThat(andreSoknad.sporsmal!!.any { it.tag == ARBEID_UTENFOR_NORGE }).isFalse()
    }
}
