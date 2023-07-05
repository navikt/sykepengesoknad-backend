package no.nav.helse.flex.yrkesskade

import no.nav.helse.flex.*
import no.nav.helse.flex.client.yrkesskade.SakDto
import no.nav.helse.flex.client.yrkesskade.SakerResponse
import no.nav.helse.flex.mockdispatcher.YrkesskadeMockDispatcher
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.util.flatten
import org.amshove.kluent.*
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.UUID

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class YrkesskadeIntegrationTest : BaseTestClass() {

    @Autowired
    private lateinit var yrkesskadeIndikatorer: YrkesskadeIndikatorer

    private final val basisdato = LocalDate.of(2021, 9, 1)

    private val sykmeldingIdMedYrkesskade = UUID.randomUUID().toString()
    val fnr1 = "54334523"

    @Test
    @Order(1)
    fun `Køer opp yrkesskaderesponse`() {
        YrkesskadeMockDispatcher.queuedSakerRespons.add(
            SakerResponse(
                listOf(
                    SakDto(
                        kommunenr = "0101",
                        saksblokk = "ABCD",
                        saksnr = 123,
                        sakstype = "Type1",
                        mottattdato = LocalDate.now(),
                        resultat = "GODKJENT",
                        resultattekst = "Dette er resultatet",
                        vedtaksdato = LocalDate.of(2023, 1, 2),
                        skadeart = "SkadeType1",
                        diagnose = "Diagnose1",
                        skadedato = LocalDate.of(2023, 1, 2),
                        kildetabell = "Tabell1",
                        saksreferanse = "Ref123"
                    ),
                    SakDto(
                        kommunenr = "0101",
                        saksblokk = "ABCD",
                        saksnr = 123,
                        sakstype = "Type1",
                        mottattdato = LocalDate.now(),
                        resultat = "DELVIS_GODKJENT",
                        resultattekst = "Dette er resultatet",
                        vedtaksdato = LocalDate.of(1987, 5, 9),
                        skadeart = "SkadeType1",
                        diagnose = "Diagnose1",
                        skadedato = null,
                        kildetabell = "Tabell1",
                        saksreferanse = "Ref123"
                    ),
                    SakDto(
                        kommunenr = "0101",
                        saksblokk = "ABCD",
                        saksnr = 123,
                        sakstype = "Type1",
                        mottattdato = LocalDate.now(),
                        resultat = "AVSLÅTT",
                        resultattekst = "Dette er resultatet",
                        vedtaksdato = LocalDate.of(2023, 1, 2),
                        skadeart = "SkadeType1",
                        diagnose = "Diagnose1",
                        skadedato = LocalDate.of(2023, 1, 2),
                        kildetabell = "Tabell1",
                        saksreferanse = "Ref123"
                    ),
                    SakDto(
                        kommunenr = "0101",
                        saksblokk = "ABCD",
                        saksnr = 123,
                        sakstype = "Type1",
                        mottattdato = LocalDate.now(),
                        resultat = "INNVILGET",
                        resultattekst = "Dette er resultatet",
                        vedtaksdato = LocalDate.of(1989, 1, 2),
                        skadeart = "SkadeType1",
                        diagnose = "Diagnose1",
                        skadedato = LocalDate.of(1982, 1, 2),
                        kildetabell = "Tabell1",
                        saksreferanse = "Ref123"
                    )
                )
            )
        )
    }

    @Test
    @Order(2)
    fun `Arbeidstakersøknad for sykmelding med yrkesskade opprettes med yrkesskadespørsmål i seg i førstegangssoknaden`() {
        val kafkaSoknader = sendSykmelding(
            sykmeldingKafkaMessage(
                sykmeldingId = sykmeldingIdMedYrkesskade,
                fnr = fnr1,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato,
                    tom = basisdato.plusDays(35)
                )
            ),
            forventaSoknader = 2

        )

        kafkaSoknader.first().sporsmal!!.any { it.tag == "YRKESSKADE_V2" }.`should be true`()
        kafkaSoknader.first().sporsmal!!.any { it.tag == "YRKESSKADE" }.`should be false`()
        kafkaSoknader.last().sporsmal!!.any { it.tag == "YRKESSKADE_V2" }.`should be false`()

        val spmTekster = kafkaSoknader.first().sporsmal.flatten().filter { it.tag == "YRKESSKADE_V2_DATO" }.map { it.sporsmalstekst }.toList()

        spmTekster[0] `should be equal to` "Skadedato 2. januar 1982 (Vedtaksdato 2. januar 1989)"
        spmTekster[1] `should be equal to` "Vedtaksdato 9. mai 1987"
        spmTekster[2] `should be equal to` "Skadedato 2. januar 2023 (Vedtaksdato 2. januar 2023)"
    }

    @Test
    @Order(3)
    fun `Svarer ja på spørsmålet om yrkesskade`() {
        mockFlexSyketilfelleArbeidsgiverperiode()

        val soknaden = hentSoknader(fnr1).first()

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr1)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
            .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
            .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
            .besvarSporsmal(tag = "UTLAND_V2", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UTENFOR_NORGE", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_0", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
            .besvarSporsmal(tag = "YRKESSKADE_V2", svar = "JA", ferdigBesvart = false)
            .besvarSporsmal(tag = "YRKESSKADE_V2_DATO", svar = "CHECKED")
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .sendSoknad()

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.first().yrkesskade!!.`should be true`()
    }

    @Test
    @Order(6)
    fun `4 juridiske vurderinger`() {
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }
}
