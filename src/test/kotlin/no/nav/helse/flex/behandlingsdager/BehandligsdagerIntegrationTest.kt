package no.nav.helse.flex.behandlingsdager

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.avbrytSoknad
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.gjenapneSoknad
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.korrigerSoknad
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.sendSoknadMedResult
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO.AVBRUTT
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO.NY
import no.nav.helse.flex.testdata.behandingsdager
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import org.amshove.kluent.`should be equal to`
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.LocalDate
import java.util.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class BehandligsdagerIntegrationTest : FellesTestOppsett() {
    final val fnr = "12345678910"

    @Test
    @Order(1)
    fun `behandingsdagsøknad opprettes for en lang sykmelding`() {
        val kafkaSoknader =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder = behandingsdager(),
                ),
            )

        val soknader = hentSoknaderMetadata(fnr)
        assertThat(soknader).hasSize(1)
        assertThat(soknader[0].soknadstype).isEqualTo(RSSoknadstype.BEHANDLINGSDAGER)
        assertThat(soknader[0].status).isEqualTo(RSSoknadstatus.NY)

        assertThat(kafkaSoknader).hasSize(1)
        assertThat(kafkaSoknader[0].status).isEqualTo(NY)
    }

    @Test
    @Order(2)
    fun `behandlingsdager har riktig formattert spørsmålstekst`() {
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        val uke0 = soknaden.sporsmal!!.first { it.tag == "ENKELTSTAENDE_BEHANDLINGSDAGER_0" }.undersporsmal[0]
        val uke1 = soknaden.sporsmal!!.first { it.tag == "ENKELTSTAENDE_BEHANDLINGSDAGER_0" }.undersporsmal[1]

        uke0.sporsmalstekst `should be equal to` "01.01.2018 - 05.01.2018"
        uke1.sporsmalstekst `should be equal to` "08.01.2018 - 10.01.2018"
    }

    @Test
    @Order(3)
    fun `vi svarer på søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()

        // Svar på søknad
        val rsSykepengesoknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )

        SoknadBesvarer(rSSykepengesoknad = rsSykepengesoknad, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "ARBEID_UTENFOR_NORGE", svar = "NEI")
            .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER", svar = "NEI")
            .besvarSporsmal(
                tag = "ENKELTSTAENDE_BEHANDLINGSDAGER_UKE_0",
                svar = "${rsSykepengesoknad.fom}",
                ferdigBesvart = false,
            ).besvarSporsmal(
                tag = "ENKELTSTAENDE_BEHANDLINGSDAGER_UKE_1",
                svarListe = emptyList(),
                ferdigBesvart = false,
            ).also {
                sendSoknadMedResult(fnr, rsSykepengesoknad.id)
                    .andExpect(((MockMvcResultMatchers.status().isBadRequest)))
            }.besvarSporsmal(tag = "ENKELTSTAENDE_BEHANDLINGSDAGER_UKE_1", svar = "${rsSykepengesoknad.tom}")
            .oppsummering()
            .sendSoknad()

        val soknadPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()

        assertThat(soknadPaKafka.status).isEqualTo(SoknadsstatusDTO.SENDT)
        assertThat(soknadPaKafka.fnr).isEqualTo("12345678910")
        assertThat(soknadPaKafka.behandlingsdager).isEqualTo(listOf(rsSykepengesoknad.fom, rsSykepengesoknad.tom))

        val refreshedSoknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        assertThat(refreshedSoknad.status).isEqualTo(RSSoknadstatus.SENDT)
        assertThat(
            refreshedSoknad.sporsmal!!
                .find { it.tag == ANSVARSERKLARING }!!
                .svar[0]
                .verdi,
        ).isEqualTo("CHECKED")

        hentJuridiskeVurderinger(2)
    }

    @Test
    @Order(4)
    fun `vi korrigerer søknaden`() {
        val soknaden = hentSoknaderMetadata(fnr).first()

        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode(andreKorrigerteRessurser = soknaden.id)

        // Korriger søknad
        val korrigerSoknad = korrigerSoknad(soknadId = soknaden.id, fnr = fnr)
        assertThat(korrigerSoknad.status).isEqualTo(RSSoknadstatus.UTKAST_TIL_KORRIGERING)
        assertThat(
            korrigerSoknad.sporsmal!!
                .find { it.tag == ANSVARSERKLARING }!!
                .svar.size,
        ).isEqualTo(0)
        assertThat(korrigerSoknad.korrigerer).isEqualTo(soknaden.id)

        SoknadBesvarer(rSSykepengesoknad = korrigerSoknad, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .oppsummering()
            .sendSoknad()

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        val soknadPaKafka = kafkaSoknader.last()
        assertThat(soknadPaKafka.status).isEqualTo(SoknadsstatusDTO.SENDT)

        hentJuridiskeVurderinger(2)
    }

    @Test
    @Order(5)
    fun `vi kan opprette, avbryte og gjenåpne søknad`() {
        val fnr = "12343"
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = behandingsdager(),
            ),
        )

        val soknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = fnr,
            )
        SoknadBesvarer(rSSykepengesoknad = soknad, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")

        // Avbryt søknad
        avbrytSoknad(soknadId = soknad.id, fnr = fnr)
        val avbruttSoknad =
            hentSoknad(
                soknadId = soknad.id,
                fnr = fnr,
            )
        assertThat(avbruttSoknad.status).isEqualTo(RSSoknadstatus.AVBRUTT)

        val avbruttPåKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().last()

        assertThat(avbruttPåKafka.status).isEqualTo(AVBRUTT)

        // Gjenåpne søknad
        gjenapneSoknad(soknadId = soknad.id, fnr = fnr)
        val gjenapnetSoknad =
            hentSoknad(
                soknadId = avbruttSoknad.id,
                fnr = fnr,
            )
        assertThat(gjenapnetSoknad.status).isEqualTo(RSSoknadstatus.NY)
        assertThat(gjenapnetSoknad.sporsmal?.sumOf { it.svar.size }).isEqualTo(0)

        val soknadPaKafka2 = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().last()
        assertThat(soknadPaKafka2.status).isEqualTo(NY)

        // Svar på avbrutt søknad er ikke med på kafka
        val ansvarserklaring = avbruttPåKafka.sporsmal!!.first { it.tag == ANSVARSERKLARING }
        assertThat(ansvarserklaring.svar).isEmpty()
    }

    @Test
    @Order(6)
    fun `kombinert behandlingsdag og vanlig splittes riktig`() {
        val fnr = "1234323423"

        val sykmeldingId = UUID.randomUUID().toString()
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingId = sykmeldingId,
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2020, 3, 1),
                            tom = LocalDate.of(2020, 3, 15),
                            type = PeriodetypeDTO.BEHANDLINGSDAGER,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = 1,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2020, 3, 16),
                            tom = LocalDate.of(2020, 3, 31),
                            type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                    ),
            ),
            forventaSoknader = 2,
        )

        val soknader = hentSoknaderMetadata(fnr).filter { it.sykmeldingId == sykmeldingId }
        assertThat(soknader).hasSize(2)
        assertThat(soknader[0].soknadstype).isEqualTo(RSSoknadstype.BEHANDLINGSDAGER)
        assertThat(soknader[0].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(soknader[0].fom).isEqualTo(LocalDate.of(2020, 3, 1))
        assertThat(soknader[0].tom).isEqualTo(LocalDate.of(2020, 3, 15))
        assertThat(soknader[1].soknadstype).isEqualTo(RSSoknadstype.ARBEIDSTAKERE)
        assertThat(soknader[1].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(soknader[1].fom).isEqualTo(LocalDate.of(2020, 3, 16))
        assertThat(soknader[1].tom).isEqualTo(LocalDate.of(2020, 3, 31))
    }

    @Test
    @Order(7)
    fun `to behandlingsdager-perioder splittes riktig`() {
        val fnr = "1234323222"
        val sykmeldingId = UUID.randomUUID().toString()
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingId = sykmeldingId,
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2020, 3, 1),
                            tom = LocalDate.of(2020, 3, 15),
                            type = PeriodetypeDTO.BEHANDLINGSDAGER,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = 1,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2020, 3, 16),
                            tom = LocalDate.of(2020, 3, 31),
                            type = PeriodetypeDTO.BEHANDLINGSDAGER,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = 1,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                    ),
            ),
            forventaSoknader = 2,
        )

        val soknader = hentSoknaderMetadata(fnr).filter { it.sykmeldingId == sykmeldingId }
        assertThat(soknader).hasSize(2)
        assertThat(soknader[0].soknadstype).isEqualTo(RSSoknadstype.BEHANDLINGSDAGER)
        assertThat(soknader[0].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(soknader[0].fom).isEqualTo(LocalDate.of(2020, 3, 1))
        assertThat(soknader[0].tom).isEqualTo(LocalDate.of(2020, 3, 15))
        assertThat(soknader[1].soknadstype).isEqualTo(RSSoknadstype.BEHANDLINGSDAGER)
        assertThat(soknader[1].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(soknader[1].fom).isEqualTo(LocalDate.of(2020, 3, 16))
        assertThat(soknader[1].tom).isEqualTo(LocalDate.of(2020, 3, 31))
    }
}
