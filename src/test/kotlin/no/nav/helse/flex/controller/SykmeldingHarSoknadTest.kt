package no.nav.helse.flex.controller

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.controller.domain.RSHarSoknadForSykmeldingResponse
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.fakes.KlippetSykepengesoknadRepositoryFake
import no.nav.helse.flex.fakes.SoknadLagrerFake
import no.nav.helse.flex.fakes.SykepengesoknadRepositoryFake
import no.nav.helse.flex.repository.KlippVariant
import no.nav.helse.flex.repository.KlippetSykepengesoknadDbRecord
import no.nav.helse.flex.testutil.lagSoknad
import no.nav.helse.flex.tokenxToken
import no.nav.helse.flex.util.objectMapper
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class SykmeldingHarSoknadTest : FakesTestOppsett() {
    @Autowired
    private lateinit var soknadLagrer: SoknadLagrerFake

    @Autowired
    private lateinit var sykepengesoknadRepository: SykepengesoknadRepositoryFake

    @Autowired
    private lateinit var klippetSykepengesoknadRepository: KlippetSykepengesoknadRepositoryFake

    private val fnr = "12345678901"

    @Test
    fun `returnerer harSoknad=true når søknad finnes for sykmelding`() {
        val sykmeldingUuid = UUID.randomUUID().toString()

        soknadLagrer.lagreSoknad(
            lagSoknad(
                fnr = fnr,
                arbeidsgiver = 1,
                fom = LocalDate.now().minusDays(14),
                tom = LocalDate.now(),
                startSykeforlop = LocalDate.now().minusDays(14),
                arbeidsSituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                soknadsType = Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
                status = Soknadstatus.NY,
                sykmeldingId = sykmeldingUuid,
            ),
        )

        val response = hentHarSoknadForSykmeldingOgArbeidssituasjon(sykmeldingUuid, fnr)
        response.harSoknad `should be equal to` true
    }

    @Test
    fun `returnerer harSoknad=true når søknad er klippet bort`() {
        val sykmeldingUuid = UUID.randomUUID().toString()

        soknadLagrer.lagreSoknad(
            lagSoknad(
                fnr = fnr,
                arbeidsgiver = 1,
                fom = LocalDate.now().minusDays(14),
                tom = LocalDate.now(),
                startSykeforlop = LocalDate.now().minusDays(14),
                arbeidsSituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                soknadsType = Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
                status = Soknadstatus.NY,
                sykmeldingId = sykmeldingUuid,
            ),
        )

        val responseFoerKlipp = hentHarSoknadForSykmeldingOgArbeidssituasjon(sykmeldingUuid, fnr)
        responseFoerKlipp.harSoknad `should be equal to` true

        val lagretSoknad = sykepengesoknadRepository.findBySykmeldingUuid(sykmeldingUuid).single()
        sykepengesoknadRepository.deleteById(lagretSoknad.id!!)
        klippetSykepengesoknadRepository.save(
            KlippetSykepengesoknadDbRecord(
                sykepengesoknadUuid = lagretSoknad.sykepengesoknadUuid,
                sykmeldingUuid = sykmeldingUuid,
                klippVariant = KlippVariant.SOKNAD_STARTER_INNI_SLUTTER_INNI,
                periodeFor = "[]",
                periodeEtter = null,
                timestamp = Instant.now(),
            ),
        )

        val responseEtterKlipp = hentHarSoknadForSykmeldingOgArbeidssituasjon(sykmeldingUuid, fnr)
        responseEtterKlipp.harSoknad `should be equal to` true

        val lagretKlipp = klippetSykepengesoknadRepository.findBySykmeldingUuid(sykmeldingUuid)!!
        klippetSykepengesoknadRepository.deleteById(lagretKlipp.id!!)

        val responseEtterSlettingAvKlipp = hentHarSoknadForSykmeldingOgArbeidssituasjon(sykmeldingUuid, fnr)
        responseEtterSlettingAvKlipp.harSoknad `should be equal to` false
    }

    @Test
    fun `returnerer harSoknad=false når ingen søknad finnes for sykmelding`() {
        val response = hentHarSoknadForSykmeldingOgArbeidssituasjon(UUID.randomUUID().toString(), fnr)
        response.harSoknad `should be equal to` false
    }

    @Test
    fun `frilanser ignorerer søknad for næringsdrivende og klipp`() {
        val sykmeldingUuid = UUID.randomUUID().toString()

        soknadLagrer.lagreSoknad(
            lagSoknad(
                fnr = fnr,
                arbeidsgiver = 1,
                fom = LocalDate.now().minusDays(14),
                tom = LocalDate.now(),
                startSykeforlop = LocalDate.now().minusDays(14),
                arbeidsSituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                soknadsType = Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
                status = Soknadstatus.NY,
                sykmeldingId = sykmeldingUuid,
            ),
        )
        klippetSykepengesoknadRepository.save(
            KlippetSykepengesoknadDbRecord(
                sykepengesoknadUuid = UUID.randomUUID().toString(),
                sykmeldingUuid = sykmeldingUuid,
                klippVariant = KlippVariant.SOKNAD_STARTER_INNI_SLUTTER_INNI,
                periodeFor = "[]",
                periodeEtter = null,
                timestamp = Instant.now(),
            ),
        )

        hentHarSoknadForSykmeldingOgArbeidssituasjon(sykmeldingUuid, fnr, Arbeidssituasjon.NAERINGSDRIVENDE)
            .harSoknad `should be equal to` true
        hentHarSoknadForSykmeldingOgArbeidssituasjon(sykmeldingUuid, fnr, Arbeidssituasjon.FRILANSER)
            .harSoknad `should be equal to` false
    }

    @Test
    fun `returnerer 403 når søknad tilhører annen bruker`() {
        val sykmeldingUuid = UUID.randomUUID().toString()
        val annetFnr = "99999999999"

        soknadLagrer.lagreSoknad(
            lagSoknad(
                fnr = annetFnr,
                arbeidsgiver = 1,
                fom = LocalDate.now().minusDays(14),
                tom = LocalDate.now(),
                startSykeforlop = LocalDate.now().minusDays(14),
                arbeidsSituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                soknadsType = Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
                status = Soknadstatus.NY,
                sykmeldingId = sykmeldingUuid,
            ),
        )

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v2/soknader/sykmelding/$sykmeldingUuid/harSoknad/${Arbeidssituasjon.NAERINGSDRIVENDE.name}")
                    .header(
                        "Authorization",
                        "Bearer ${server.tokenxToken(fnr = fnr, clientId = "flex-sykmeldinger-backend-client-id")}",
                    ).contentType(MediaType.APPLICATION_JSON),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `returnerer 400 ved ugyldig arbeidssituasjon for opt-in`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v2/soknader/sykmelding/${UUID.randomUUID()}/harSoknad/${Arbeidssituasjon.ARBEIDSTAKER.name}")
                    .header(
                        "Authorization",
                        "Bearer ${server.tokenxToken(fnr = fnr, clientId = "flex-sykmeldinger-backend-client-id")}",
                    ).contentType(MediaType.APPLICATION_JSON),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `returnerer 401 uten token`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v2/soknader/sykmelding/${UUID.randomUUID()}/harSoknad/${Arbeidssituasjon.NAERINGSDRIVENDE.name}")
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(status().isUnauthorized)
    }

    private fun hentHarSoknadForSykmeldingOgArbeidssituasjon(
        sykmeldingUuid: String,
        fnr: String,
        arbeidssituasjon: Arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
        clientId: String = "flex-sykmeldinger-backend-client-id",
    ): RSHarSoknadForSykmeldingResponse {
        val json =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/api/v2/soknader/sykmelding/$sykmeldingUuid/harSoknad/${arbeidssituasjon.name}")
                        .header(
                            "Authorization",
                            "Bearer ${server.tokenxToken(fnr = fnr, clientId = clientId)}",
                        ).contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        return objectMapper.readValue(json)
    }
}
