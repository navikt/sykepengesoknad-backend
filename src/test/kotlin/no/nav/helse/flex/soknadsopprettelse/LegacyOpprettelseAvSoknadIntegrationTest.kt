package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.testdata.behandingsdager
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class LegacyOpprettelseAvSoknadIntegrationTest : BaseTestClass() {

    private val fnr = "12345678900"

    @BeforeEach
    @AfterEach
    fun setUp() {
        databaseReset.resetDatabase()
    }

    @Test
    fun `Når man oppretter to BEHANDLINGSDAGER-søknader i ulike forløp vil ARBEID_UTENFOR_NORGE være i begge`() {
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
        assertThat(forsteSoknad.sporsmal!!.any { it.tag == ARBEID_UTENFOR_NORGE }).isTrue()

        val andreSoknad = hentSoknad(
            soknadId = soknaderMetadata.last().id,
            fnr = fnr
        )
        assertThat(andreSoknad.sporsmal!!.any { it.tag == ARBEID_UTENFOR_NORGE }).isTrue()
    }

    @Test
    fun `Når man oppretter to ARBEIDSLEDIG søknader i samme forløp vil ARBEID_UTENFOR_NORGE finnes kun i den første`() {
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
