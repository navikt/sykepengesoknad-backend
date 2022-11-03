package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.aktivering.AktiverEnkeltSoknad
import no.nav.helse.flex.aktivering.kafka.AktiveringProducer
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.rest.SoknadMetadata
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testutil.opprettSoknadFraSoknadMetadata
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.util.tilOsloInstant
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class LegacyOpprettelseAvSoknadIntegrationTest : BaseTestClass() {

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var opprettSoknadService: OpprettSoknadService

    @Autowired
    private lateinit var aktiveringProducer: AktiveringProducer

    @Autowired
    private lateinit var aktiverEnkeltSoknad: AktiverEnkeltSoknad

    val fnr = "fnr"

    @BeforeEach
    @AfterEach
    fun setUp() {
        databaseReset.resetDatabase()
    }

    @Test
    fun `Når man oppretter to søknader i samme forløp vil fravær før sykmeldingen og arbeid utenfor norge spørsmålet kun være i den første`() {
        // Opprett søknad
        val soknad = SoknadMetadata(
            arbeidsgiverOrgnummer = "123456789",
            arbeidsgiverNavn = "Bedrift AS",
            startSykeforlop = LocalDate.of(2018, 1, 1),
            sykmeldingSkrevet = LocalDateTime.of(2018, 1, 1, 12, 0).tilOsloInstant(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = (LocalDate.of(2018, 1, 1)),
                    tom = (LocalDate.of(2018, 1, 10)),
                    gradert = GradertDTO(grad = 100, reisetilskudd = false),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
            ).tilSoknadsperioder(),
            fnr = fnr,
            fom = LocalDate.of(2018, 1, 1),
            tom = LocalDate.of(2018, 1, 10),
            sykmeldingId = "sykmeldingId"
        )

        opprettSoknadService.opprettSoknadFraSoknadMetadata(
            soknad.copy(id = UUID.randomUUID().toString()),
            sykepengesoknadDAO,
            aktiveringProducer, aktiverEnkeltSoknad
        )
        opprettSoknadService.opprettSoknadFraSoknadMetadata(
            soknad.copy(
                id = UUID.randomUUID().toString(),
                fom = LocalDate.of(2018, 1, 11),
                tom = LocalDate.of(2018, 1, 12)
            ),
            sykepengesoknadDAO,
            aktiveringProducer, aktiverEnkeltSoknad
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

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2).tilSoknader()
        assertThat(soknader.first().status).isEqualTo(SoknadsstatusDTO.NY)
        assertThat(soknader.last().status).isEqualTo(SoknadsstatusDTO.NY)
    }

    @Test
    fun `Når man oppretter to søknader om behandlingsdager i ulike forløp vil egenmeldingspørsmålet være i begge`() {
        // Opprett søknad
        val soknad = SoknadMetadata(
            arbeidsgiverOrgnummer = "123456789",
            arbeidsgiverNavn = "Bedrift AS",
            startSykeforlop = LocalDate.of(2018, 1, 1),
            sykmeldingSkrevet = LocalDateTime.of(2018, 1, 1, 12, 0).tilOsloInstant(),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = (LocalDate.of(2018, 1, 1)),
                    tom = (LocalDate.of(2018, 1, 10)),
                    gradert = GradertDTO(grad = 100, reisetilskudd = false),
                    type = PeriodetypeDTO.BEHANDLINGSDAGER,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
            ).tilSoknadsperioder(),
            soknadstype = Soknadstype.BEHANDLINGSDAGER,
            fnr = fnr,
            fom = LocalDate.of(2018, 1, 1),
            tom = LocalDate.of(2018, 1, 10),
            sykmeldingId = "sykmeldingId"
        )

        opprettSoknadService.opprettSoknadFraSoknadMetadata(
            soknad.copy(id = UUID.randomUUID().toString()),
            sykepengesoknadDAO,
            aktiveringProducer, aktiverEnkeltSoknad
        )
        opprettSoknadService.opprettSoknadFraSoknadMetadata(
            soknad.copy(
                id = UUID.randomUUID().toString(),
                fom = LocalDate.of(2018, 1, 11),
                tom = LocalDate.of(2018, 1, 12),
                startSykeforlop = LocalDate.of(2018, 1, 2)
            ),
            sykepengesoknadDAO,
            aktiveringProducer, aktiverEnkeltSoknad
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

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    fun `Når man oppretter en to søknader om behandlingsdager i samme forløp vil egenmeldingspørsmålet kun være i den første`() {
        // Opprett søknad
        val soknad = SoknadMetadata(
            arbeidsgiverOrgnummer = "123456789",
            arbeidsgiverNavn = "Bedrift AS",
            startSykeforlop = LocalDate.of(2018, 1, 1),
            sykmeldingSkrevet = LocalDateTime.of(2018, 1, 1, 12, 0).tilOsloInstant(),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = (LocalDate.of(2018, 1, 1)),
                    tom = (LocalDate.of(2018, 1, 10)),
                    gradert = GradertDTO(grad = 100, reisetilskudd = false),
                    type = PeriodetypeDTO.BEHANDLINGSDAGER,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
            ).tilSoknadsperioder(),
            soknadstype = Soknadstype.BEHANDLINGSDAGER,
            fnr = fnr,
            fom = LocalDate.of(2018, 1, 1),
            tom = LocalDate.of(2018, 1, 10),
            sykmeldingId = "sykmeldingId"
        )

        opprettSoknadService.opprettSoknadFraSoknadMetadata(
            soknad.copy(id = UUID.randomUUID().toString()),
            sykepengesoknadDAO,
            aktiveringProducer, aktiverEnkeltSoknad
        )
        opprettSoknadService.opprettSoknadFraSoknadMetadata(
            soknad.copy(
                id = UUID.randomUUID().toString(),
                fom = LocalDate.of(2018, 1, 11),
                tom = LocalDate.of(2018, 1, 12)
            ),
            sykepengesoknadDAO,
            aktiveringProducer, aktiverEnkeltSoknad
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

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    fun `Når man oppretter en to søknader om behandlingsdager i samme forløp med forskjellig orgnr vil egenmeldingspørsmålet være i begge`() {
        // Opprett søknad
        val soknad = SoknadMetadata(
            arbeidsgiverOrgnummer = "123456789",
            arbeidsgiverNavn = "Bedrift AS",
            startSykeforlop = LocalDate.of(2018, 1, 1),
            sykmeldingSkrevet = LocalDateTime.of(2018, 1, 1, 12, 0).tilOsloInstant(),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = (LocalDate.of(2018, 1, 1)),
                    tom = (LocalDate.of(2018, 1, 10)),
                    gradert = GradertDTO(grad = 100, reisetilskudd = false),
                    type = PeriodetypeDTO.BEHANDLINGSDAGER,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = 1,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
            ).tilSoknadsperioder(),
            soknadstype = Soknadstype.BEHANDLINGSDAGER,
            fnr = fnr,
            fom = LocalDate.of(2018, 1, 1),
            tom = LocalDate.of(2018, 1, 10),
            sykmeldingId = "sykmeldingId"
        )

        opprettSoknadService.opprettSoknadFraSoknadMetadata(
            soknad.copy(id = UUID.randomUUID().toString()),
            sykepengesoknadDAO,
            aktiveringProducer, aktiverEnkeltSoknad
        )
        opprettSoknadService.opprettSoknadFraSoknadMetadata(
            soknad.copy(
                id = UUID.randomUUID().toString(),
                fom = LocalDate.of(2018, 1, 11),
                tom = LocalDate.of(2018, 1, 12),
                arbeidsgiverOrgnummer = "123234243"
            ),
            sykepengesoknadDAO,
            aktiveringProducer, aktiverEnkeltSoknad
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
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    fun `Når man oppretter en to søknader i samme forløp med forskjellig orgnummer vil fravær før sykmeldingen spørsmålet være i begge`() {
        // Opprett søknad
        val soknad = SoknadMetadata(
            arbeidsgiverOrgnummer = "123456789",
            arbeidsgiverNavn = "Bedrift AS",
            startSykeforlop = LocalDate.of(2018, 1, 1),
            sykmeldingSkrevet = LocalDateTime.of(2018, 1, 1, 12, 0).tilOsloInstant(),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = (LocalDate.of(2018, 1, 1)),
                    tom = (LocalDate.of(2018, 1, 10)),
                    gradert = GradertDTO(grad = 100, reisetilskudd = false),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
            ).tilSoknadsperioder(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            fnr = fnr,
            fom = LocalDate.of(2018, 1, 1),
            tom = LocalDate.of(2018, 1, 10),
            sykmeldingId = "sykmeldingId"
        )

        opprettSoknadService.opprettSoknadFraSoknadMetadata(
            soknad.copy(id = UUID.randomUUID().toString()),
            sykepengesoknadDAO,
            aktiveringProducer, aktiverEnkeltSoknad
        )
        opprettSoknadService.opprettSoknadFraSoknadMetadata(
            soknad.copy(
                id = UUID.randomUUID().toString(),
                sykmeldingId = "annen-melding",
                arbeidsgiverOrgnummer = "123456788"
            ),
            sykepengesoknadDAO,
            aktiveringProducer, aktiverEnkeltSoknad
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

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    fun `Når man oppretter to arbeidsledig søknader i samme forløp vil arbeid utenfor norge spørsmålet kun være i den første`() {
        // Opprett søknad
        val soknad = SoknadMetadata(
            arbeidsgiverOrgnummer = null,
            arbeidsgiverNavn = null,
            startSykeforlop = LocalDate.of(2018, 1, 1),
            sykmeldingSkrevet = LocalDateTime.of(2018, 1, 1, 12, 0).tilOsloInstant(),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = (LocalDate.of(2018, 1, 1)),
                    tom = (LocalDate.of(2018, 1, 10)),
                    gradert = GradertDTO(grad = 100, reisetilskudd = false),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
            ).tilSoknadsperioder(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            fnr = fnr,
            fom = LocalDate.of(2018, 1, 1),
            tom = LocalDate.of(2018, 1, 10),
            sykmeldingId = "sykmeldingId"
        )

        opprettSoknadService.opprettSoknadFraSoknadMetadata(
            soknad.copy(id = UUID.randomUUID().toString()),
            sykepengesoknadDAO,
            aktiveringProducer, aktiverEnkeltSoknad
        )
        opprettSoknadService.opprettSoknadFraSoknadMetadata(
            soknad.copy(
                id = UUID.randomUUID().toString(),
                fom = LocalDate.of(2018, 1, 11),
                tom = LocalDate.of(2018, 1, 12)
            ),
            sykepengesoknadDAO,
            aktiveringProducer, aktiverEnkeltSoknad
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

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2).tilSoknader()
        assertThat(soknader.first().status).isEqualTo(SoknadsstatusDTO.NY)
        assertThat(soknader.last().status).isEqualTo(SoknadsstatusDTO.NY)
    }

    @Test
    fun `Når man oppretter en søknad tilhørende en egenmeldt sykmelding så kan vi se det på søknaden`() {
        val soknad = SoknadMetadata(
            arbeidsgiverOrgnummer = "123456789",
            arbeidsgiverNavn = "Bedrift AS",
            startSykeforlop = LocalDate.of(2018, 1, 1),
            sykmeldingSkrevet = LocalDateTime.of(2018, 1, 1, 12, 0).tilOsloInstant(),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = (LocalDate.of(2018, 1, 1)),
                    tom = (LocalDate.of(2018, 1, 10)),
                    gradert = GradertDTO(grad = 100, reisetilskudd = false),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
            ).tilSoknadsperioder(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            fnr = fnr,
            fom = LocalDate.of(2018, 1, 1),
            tom = LocalDate.of(2018, 1, 10),
            sykmeldingId = "sykmeldingId",
            egenmeldtSykmelding = true
        )
        opprettSoknadService.opprettSoknadFraSoknadMetadata(soknad, sykepengesoknadDAO, aktiveringProducer, aktiverEnkeltSoknad)

        val rsSoknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )
        assertThat(rsSoknad.egenmeldtSykmelding).isTrue()
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    fun `Når man oppretter en søknad ikke tilhørende en egenmeldt sykmelding så kan vi se det på søknaden`() {
        val soknad = SoknadMetadata(
            arbeidsgiverOrgnummer = "123456789",
            arbeidsgiverNavn = "Bedrift AS",
            startSykeforlop = LocalDate.of(2018, 1, 1),
            sykmeldingSkrevet = LocalDateTime.of(2018, 1, 1, 12, 0).tilOsloInstant(),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = (LocalDate.of(2018, 1, 1)),
                    tom = (LocalDate.of(2018, 1, 10)),
                    gradert = GradertDTO(grad = 100, reisetilskudd = false),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
            ).tilSoknadsperioder(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            fnr = fnr,
            fom = LocalDate.of(2018, 1, 1),
            tom = LocalDate.of(2018, 1, 10),
            sykmeldingId = "sykmeldingId",
            egenmeldtSykmelding = false
        )
        opprettSoknadService.opprettSoknadFraSoknadMetadata(soknad, sykepengesoknadDAO, aktiveringProducer, aktiverEnkeltSoknad)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest[0].egenmeldtSykmelding).isFalse()
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }
}
