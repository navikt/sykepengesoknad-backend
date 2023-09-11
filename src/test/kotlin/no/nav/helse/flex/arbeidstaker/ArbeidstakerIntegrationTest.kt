package no.nav.helse.flex.arbeidstaker

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSporsmal
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING
import no.nav.helse.flex.sykepengesoknad.kafka.MerknadDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.util.tilOsloLocalDateTime
import no.nav.syfo.model.Merknad
import org.amshove.kluent.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.OffsetDateTime

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ArbeidstakerIntegrationTest : BaseTestClass() {

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    private final val fnr = "12454578474"
    private final val basisdato = LocalDate.of(2021, 9, 1)
    private final val oppfolgingsdato = basisdato.minusDays(20)

    @Test
    @Order(1)
    fun `Arbeidstakersøknader opprettes for en lang sykmelding`() {
        val kafkaSoknader = sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.minusDays(20),
                    tom = basisdato.plusDays(15)

                ),
                merknader = listOf(Merknad(type = "UGYLDIG_TILBAKEDATERING", beskrivelse = "Hey"))
            ),
            oppfolgingsdato = oppfolgingsdato,

            forventaSoknader = 2
        )

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(2)
        assertThat(hentetViaRest[0].soknadstype).isEqualTo(RSSoknadstype.ARBEIDSTAKERE)
        assertThat(hentetViaRest[1].soknadstype).isEqualTo(RSSoknadstype.ARBEIDSTAKERE)
        assertThat(hentetViaRest[0].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[1].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[0].merknaderFraSykmelding!!.first().type).isEqualTo("UGYLDIG_TILBAKEDATERING")
        assertThat(hentetViaRest[0].merknaderFraSykmelding!!.first().beskrivelse).isEqualTo("Hey")

        assertThat(kafkaSoknader).hasSize(2)
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.NY)
        assertThat(kafkaSoknader[0].merknaderFraSykmelding!!.first()).isEqualTo(
            MerknadDTO(
                type = "UGYLDIG_TILBAKEDATERING",
                beskrivelse = "Hey"
            )
        )
        assertThat(kafkaSoknader[1].status).isEqualTo(SoknadsstatusDTO.NY)
        assertThat(kafkaSoknader[1].sendTilGosys).isNull()
        assertThat(kafkaSoknader[1].merknader).isNull()

        val dbSoknader = sykepengesoknadRepository.findBySykepengesoknadUuidIn(kafkaSoknader.map { it.id })
        dbSoknader shouldHaveSize 2

        dbSoknader[0].fom `should be equal to` basisdato.minusDays(20)
        dbSoknader[0].forstegangssoknad!!.`should be true`()
        dbSoknader[1].fom `should be equal to` basisdato.minusDays(2)
        dbSoknader[1].forstegangssoknad!!.`should be false`()
    }

    @Test
    @Order(2)
    fun `Vi kan ikke korrigere en soknad som ikke er sendt`() {
        val soknaden = hentSoknaderMetadata(fnr)[0]
        korrigerSoknadMedResult(soknaden.id, fnr).andExpect(status().isBadRequest)
            .andReturn()
    }

    @Test
    @Order(3)
    fun `Søknadene har spørsmål som forventet`() {
        val soknader = hentSoknaderMetadata(fnr)

        val soknad1 = hentSoknad(soknader[0].id, fnr)
        assertThat(soknad1.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                "ANSVARSERKLARING",
                "TILBAKE_I_ARBEID",
                "FERIE_V2",
                "PERMISJON_V2",
                "UTLAND_V2",
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                "ARBEID_UTENFOR_NORGE",
                "ANDRE_INNTEKTSKILDER_V2",
                "VAER_KLAR_OVER_AT",
                "BEKREFT_OPPLYSNINGER"
            )
        )
        assertThat(soknad1.sporsmal!!.first { it.tag == ANSVARSERKLARING }.sporsmalstekst).isEqualTo("Jeg vet at jeg kan miste retten til sykepenger hvis opplysningene jeg gir ikke er riktige eller fullstendige. Jeg vet også at NAV kan holde igjen eller kreve tilbake penger, og at å gi feil opplysninger kan være straffbart.")

        val soknad2 = hentSoknad(soknader[1].id, fnr)
        assertThat(soknad2.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                "ANSVARSERKLARING",
                "TILBAKE_I_ARBEID",
                "FERIE_V2",
                "PERMISJON_V2",
                "UTLAND_V2",
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                "ANDRE_INNTEKTSKILDER_V2",
                "VAER_KLAR_OVER_AT",
                "BEKREFT_OPPLYSNINGER"
            )
        )
    }

    @Test
    @Order(4)
    fun `Id på et allerede besvart spørsmål endres ikke når vi svarer på et annet spørsmål`() {
        fun hentAnsvarserklering(id: String): RSSporsmal {
            return hentSoknad(id, fnr).sporsmal!!.first { it.tag == "ANSVARSERKLARING" }
        }

        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
            fnr = fnr
        )

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")

        val besvartSporsmal = hentAnsvarserklering(soknaden.id)

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")

        val besvartSporsmalEtterAnnetSvar = hentAnsvarserklering(soknaden.id)

        besvartSporsmalEtterAnnetSvar.svar.first().id `should be equal to` besvartSporsmal.svar.first().id
    }

    @Test
    @Order(5)
    fun `Den nyeste søknaden kan ikke sendes først`() {
        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).filter { it.status == RSSoknadstatus.NY }.sortedBy { it.fom }
                .last().id,
            fnr = fnr
        )

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
            .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
            .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
            .besvarSporsmal(tag = "UTLAND_V2", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_0", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")

        val res =
            sendSoknadMedResult(fnr, soknaden.id).andExpect(status().isBadRequest).andReturn().response.contentAsString
        res `should be equal to` "{\"reason\":\"FORSOK_PA_SENDING_AV_NYERE_SOKNAD\"}"
    }

    @Test
    @Order(6)
    fun `Vi besvarer og sender inn den første søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
            fnr = fnr
        )

        val sendtSoknad = SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
            .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
            .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
            .besvarSporsmal(tag = "UTLAND_V2", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UTENFOR_NORGE", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_0", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(kafkaSoknader).hasSize(1)
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.SENDT)
        kafkaSoknader[0].arbeidUtenforNorge!!.`should be false`()

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSoknad.id)
        soknadFraDatabase.sendtArbeidsgiver `should not be` null
        soknadFraDatabase.sendtNav `should be` null
        soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver
    }

    @Test
    @Order(7)
    fun `Vi besvarer og sender inn den andre søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()

        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
            fnr = fnr
        )
        val sendtSoknad = SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
            .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
            .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
            .besvarSporsmal(tag = "UTLAND_V2", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_0", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)
        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(kafkaSoknader).hasSize(1)
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.SENDT)
        assertThat(kafkaSoknader[0].sendTilGosys).isNull()
        assertThat(kafkaSoknader[0].merknader).isNull()
        kafkaSoknader[0].arbeidUtenforNorge.`should be null`()

        assertThat(kafkaSoknader[0].merknaderFraSykmelding!!.first()).isEqualTo(
            MerknadDTO(
                type = "UGYLDIG_TILBAKEDATERING",
                beskrivelse = "Hey"
            )
        )
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSoknad.id)
        soknadFraDatabase.sendtArbeidsgiver `should not be` null
        soknadFraDatabase.sendtNav `should be` null
        soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver
    }

    @Test
    @Order(8)
    fun `4 - vi korrigerer og sender inn søknaden, opprinnelig sendt blir satt riktig`() {
        flexSyketilfelleMockRestServiceServer.reset()

        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).sortedBy { it.fom }.first { it.status == RSSoknadstatus.SENDT }.id,
            fnr = fnr
        )
        mockFlexSyketilfelleArbeidsgiverperiode(andreKorrigerteRessurser = soknaden.id)

        val soknadDb = sykepengesoknadRepository.findBySykepengesoknadUuid(soknaden.id)!!
        val sendtTidspunkt = OffsetDateTime.now().minusDays(3)
        sykepengesoknadRepository.save(soknadDb.copy(sendt = sendtTidspunkt.toInstant()))
        val korrigerendeSoknad = korrigerSoknad(soknaden.id, fnr)

        val sendtSoknad = SoknadBesvarer(rSSykepengesoknad = korrigerendeSoknad, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(kafkaSoknader[0].opprinneligSendt).isNotNull()
        assertThat(kafkaSoknader[0].opprinneligSendt).isEqualToIgnoringNanos(
            sendtTidspunkt.toInstant().tilOsloLocalDateTime()
        )

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    @Order(9)
    fun `En ny oppfølgende sykmelding fører ikke til førstegangssoknad`() {
        val kafkaSoknader = sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.plusDays(20),
                    tom = basisdato.plusDays(60)

                )
            ),
            oppfolgingsdato = oppfolgingsdato,
            forventaSoknader = 2
        )

        val dbSoknader = sykepengesoknadRepository.findBySykepengesoknadUuidIn(kafkaSoknader.map { it.id })
        dbSoknader shouldHaveSize 2

        dbSoknader[0].forstegangssoknad!!.`should be false`()
        dbSoknader[1].forstegangssoknad!!.`should be false`()
    }

    @Autowired
    private lateinit var namedParameterJdbcTemplate: NamedParameterJdbcTemplate

    @Autowired
    private lateinit var oppdaterForstegangssoknadJob: OppdaterForstegangssoknadJob

    @Test
    @Order(10)
    fun `Tester oppdater forstegangssoknad job`() {
        namedParameterJdbcTemplate.update(
            """
            UPDATE sykepengesoknad
            SET forstegangssoknad = null
            """,
            MapSqlParameterSource()
        )

        val dbSoknaderFor = sykepengesoknadRepository.findByFnrIn(listOf(fnr)).map { Pair(it.fom, it.forstegangssoknad) }.sortedBy { it.first }
        dbSoknaderFor.`should be equal to`(
            listOf(
                Pair(basisdato.minusDays(20), null),
                Pair(basisdato.minusDays(20), null),
                Pair(basisdato.minusDays(2), null),
                Pair(basisdato.plusDays(20), null),
                Pair(basisdato.plusDays(41), null)
            )
        )

        oppdaterForstegangssoknadJob.oppdaterForstegangssoknadJob()

        val dbSoknaderEtter = sykepengesoknadRepository.findByFnrIn(listOf(fnr)).map { Pair(it.fom, it.forstegangssoknad) }.sortedBy { it.first }
        dbSoknaderEtter.`should be equal to`(
            listOf(
                Pair(basisdato.minusDays(20), true),
                Pair(basisdato.minusDays(20), true),
                Pair(basisdato.minusDays(2), false),
                Pair(basisdato.plusDays(20), false),
                Pair(basisdato.plusDays(41), false)
            )
        )
    }
}
