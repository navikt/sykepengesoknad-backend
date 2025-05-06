package no.nav.helse.flex.reisetilskudd

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.avbrytSoknad
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSvar
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Kvittering
import no.nav.helse.flex.domain.Utgiftstype
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.gjenapneSoknad
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.lagreSvar
import no.nav.helse.flex.mockFlexSyketilfelleSykeforloep
import no.nav.helse.flex.oppdaterSporsmalMedResult
import no.nav.helse.flex.sendSoknadMedResult
import no.nav.helse.flex.slettSvar
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.tilSlutt
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.testutil.byttSvar
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.STATUS_SENDT
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.Instant
import java.time.LocalDate
import java.util.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ReisetilskuddIntegrationTest : FellesTestOppsett() {
    final val fnr = "123456789"

    companion object {
        val sykmeldingId = UUID.randomUUID().toString()
        val tom = LocalDate.now().minusDays(1)
        val fom = LocalDate.now().minusDays(5)
    }

    @Test
    @Order(0)
    fun `Det er ingen søknader til å begynne med`() {
        val soknader = hentSoknaderMetadata(fnr)
        soknader.shouldBeEmpty()
    }

    @Test
    @Order(1)
    fun `01 - vi oppretter en reisetilskuddsøknad`() {
        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                statusEvent = STATUS_SENDT,
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "123454543", orgNavn = "Gatekjøkkenet"),
            )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding =
            skapArbeidsgiverSykmelding(
                sykmeldingId = sykmeldingId,
                fom = fom,
                tom = tom,
                type = PeriodetypeDTO.REISETILSKUDD,
                reisetilskudd = true,
            )

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.REISETILSKUDD)
    }

    @Test
    @Order(2)
    fun `02 - søknaden har alle spørsmål`() {
        val soknader = hentSoknaderMetadata(fnr)
        assertThat(soknader).hasSize(1)

        val soknaden =
            hentSoknad(
                soknadId = soknader.first().id,
                fnr = fnr,
            )

        assertThat(soknaden.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                TRANSPORT_TIL_DAGLIG,
                REISE_MED_BIL,
                KVITTERINGER,
                UTBETALING,
                TIL_SLUTT,
            ),
        )

        assertThat(
            soknaden.sporsmal!!
                .first {
                    it.tag == ANSVARSERKLARING
                }.sporsmalstekst,
        ).isEqualTo("Jeg bekrefter at jeg vil svare så riktig som jeg kan.")
        assertThat(soknaden.sporsmal!!.first { it.tag == TIL_SLUTT }.sporsmalstekst).isEqualTo(
            tilSlutt().sporsmalstekst,
        )
    }

    @Test
    @Order(3)
    fun `Vi kan avbryte søknaden`() {
        val reisetilskudd = hentSoknaderMetadata(fnr)
        avbrytSoknad(fnr = fnr, soknadId = reisetilskudd.first().id)

        val avbruttSøknad = hentSoknaderMetadata(fnr).first()
        avbruttSøknad.status shouldBeEqualTo RSSoknadstatus.AVBRUTT
    }

    @Test
    @Order(4)
    fun `Vi kan gjenåpne søknaden`() {
        val reisetilskudd = hentSoknaderMetadata(fnr)
        gjenapneSoknad(fnr = fnr, soknadId = reisetilskudd.first().id)

        val gjenåpnet = hentSoknaderMetadata(fnr).first()
        gjenåpnet.status shouldBeEqualTo RSSoknadstatus.NY
    }

    @Test
    @Order(5)
    fun `Vi kan besvare et av spørsmålene`() {
        val reisetilskudd =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        SoknadBesvarer(reisetilskudd, this, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")

        val svaret =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            ).sporsmal!!.find { it.tag == ANSVARSERKLARING }!!.svar.first()
        svaret.verdi shouldBeEqualTo "CHECKED"
    }

    @Test
    @Order(6)
    fun `Vi kan laste opp en kvittering`() {
        val reisetilskuddSoknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        val kvitteringSpm = reisetilskuddSoknad.sporsmal!!.first { it.tag == KVITTERINGER }
        val svar =
            RSSvar(
                verdi =
                    Kvittering(
                        blobId = "9a186e3c-aeeb-4566-a865-15aa9139d364",
                        belop = 133700,
                        typeUtgift = Utgiftstype.PARKERING,
                        opprettet = Instant.now(),
                    ).serialisertTilString(),
                id = null,
            )

        val spmSomBleSvart = lagreSvar(fnr, reisetilskuddSoknad.id, kvitteringSpm.id!!, svar).oppdatertSporsmal

        val returnertSvar = spmSomBleSvart.svar.first()

        val returnertKvittering: Kvittering = objectMapper.readValue(returnertSvar.verdi)
        returnertKvittering.typeUtgift.`should be equal to`(Utgiftstype.PARKERING)
    }

    @Test
    @Order(7)
    fun `Vi kan se den opplastede kvitteringen`() {
        val reisetilskuddSoknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        val kvitteringSpm = reisetilskuddSoknad.sporsmal!!.first { it.tag == KVITTERINGER }
        kvitteringSpm.svar.size `should be equal to` 1

        val returnertSvar = kvitteringSpm.svar.first()
        val returnertKvittering: Kvittering = objectMapper.readValue(returnertSvar.verdi)

        returnertKvittering.blobId.`should be equal to`("9a186e3c-aeeb-4566-a865-15aa9139d364")
        returnertKvittering.belop.`should be equal to`(133700)
    }

    @Test
    @Order(8)
    fun `Vi kan slette en kvittering`() {
        val reisetilskuddSoknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        val kvitteringSpm = reisetilskuddSoknad.sporsmal!!.first { it.tag == KVITTERINGER }
        kvitteringSpm.svar.size `should be equal to` 1

        val svaret = kvitteringSpm.svar.first()

        slettSvar(fnr, reisetilskuddSoknad.id, kvitteringSpm.id!!, svaret.id!!)

        val reisetilskuddSoknadEtter =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        val kvitteringSpmEtter = reisetilskuddSoknadEtter.sporsmal!!.first { it.tag == KVITTERINGER }
        kvitteringSpmEtter.svar.size `should be equal to` 0
    }

    @Test
    @Order(9)
    fun `Vi laster opp en kvittering igjen`() {
        val reisetilskuddSoknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        val kvitteringSpm = reisetilskuddSoknad.sporsmal!!.first { it.tag == KVITTERINGER }
        val svar =
            RSSvar(
                verdi =
                    Kvittering(
                        blobId = "9a186e3c-aeeb-4566-a865-15aa9139d364",
                        belop = 133700,
                        typeUtgift = Utgiftstype.PARKERING,
                        opprettet = Instant.now(),
                    ).serialisertTilString(),
                id = null,
            )

        lagreSvar(fnr, reisetilskuddSoknad.id, kvitteringSpm.id!!, svar)
    }

    @Test
    @Order(10)
    fun `Vi tester å sende inn søknaden før alle svar er besvart og får bad request`() {
        val reisetilskudd = hentSoknaderMetadata(fnr).first()
        sendSoknadMedResult(fnr, reisetilskudd.id)
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    @Test
    @Order(11)
    fun `Vi besvarer et spørsmål med feil type verdi`() {
        val reisetilskudd =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        val utbetaling = reisetilskudd.sporsmal!!.first { it.tag == UTBETALING }.byttSvar(svar = "TJA")

        val json =
            oppdaterSporsmalMedResult(fnr, utbetaling, reisetilskudd.id)
                .andExpect(MockMvcResultMatchers.status().isBadRequest)
                .andReturn()
                .response.contentAsString
        json shouldBeEqualTo "{\"reason\":\"SPORSMALETS_SVAR_VALIDERER_IKKE\"}"
    }

    @Test
    @Order(12)
    fun `Vi besvarer resten av spørsmålene`() {
        val reisetilskudd =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        SoknadBesvarer(reisetilskudd, this, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(TRANSPORT_TIL_DAGLIG, "NEI")
            .besvarSporsmal(REISE_MED_BIL, "NEI")
            .besvarSporsmal(UTBETALING, "JA")
            .oppsummering()
    }

    @Test
    @Order(13)
    fun `Vi kan sende inn søknaden`() {
        val reisetilskudd =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        val sendtSøknad =
            SoknadBesvarer(reisetilskudd, this, fnr)
                .sendSoknad()
        sendtSøknad.status shouldBeEqualTo RSSoknadstatus.SENDT
    }

    @Test
    @Order(14)
    fun `Vi leser av alt som er produsert`() {
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 3)
    }
}
