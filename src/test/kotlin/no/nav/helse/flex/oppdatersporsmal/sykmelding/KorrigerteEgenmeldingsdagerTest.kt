package no.nav.helse.flex.oppdatersporsmal.sykmelding

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidsgiverperiode
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS
import no.nav.helse.flex.util.serialisertTilString
import no.nav.syfo.sykmelding.kafka.model.ShortNameKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.SporsmalOgSvarKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.SvartypeKafkaDTO
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class KorrigerteEgenmeldingsdagerTest : FellesTestOppsett() {
    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    private val fnr = "12345678900"
    private val basisdato = LocalDate.of(2021, 9, 1)
    private val sykmeldingKafkaMessage =
        sykmeldingKafkaMessage(
            fnr = fnr,
            sykmeldingsperioder =
                heltSykmeldt(
                    fom = basisdato,
                    tom = basisdato.plusDays(10),
                ),
        )

    @BeforeAll
    fun konfigurerUnleash() {
        fakeUnleash.resetAll()
    }

    @Test
    @Order(1)
    fun `Arbeidstakersøknad opprettes`() {
        fakeUnleash.enable(UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS)
        mockFlexSyketilfelleSykeforloep(
            sykmeldingKafkaMessage.sykmelding.id,
            sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.minOf { it.fom },
        )
        kafkaProducer.send(
            ProducerRecord(
                SYKMELDINGSENDT_TOPIC,
                sykmeldingKafkaMessage.sykmelding.id,
                sykmeldingKafkaMessage.serialisertTilString(),
            ),
        )
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    @Order(2)
    fun `Vi besvarer og sender inn søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = fnr,
            )

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
                .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
                .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
                .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
                .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
                .besvarSporsmal(tag = "OPPHOLD_UTENFOR_EOS", svar = "NEI")
                .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_0", svar = "NEI")
                .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
                .besvarSporsmal(tag = "TIL_SLUTT", svar = "Jeg lover å ikke lyve!", ferdigBesvart = false)
                .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
                .sendSoknad()

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSoknad.id)
        soknadFraDatabase.status shouldBeEqualTo Soknadstatus.SENDT
        soknadFraDatabase.sendtArbeidsgiver shouldNotBeEqualTo null
        soknadFraDatabase.sendtNav shouldBeEqualTo null
    }

    @Test
    @Order(3)
    fun `Korrigerer egenmeldingsdager i sykmeldingen så den fremdeles er innenfor arbeidsgiverperioden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleSykeforloep(
            sykmeldingKafkaMessage.sykmelding.id,
            sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.minOf { it.fom },
        )
        mockFlexSyketilfelleArbeidsgiverperiode(
            arbeidsgiverperiode =
                Arbeidsgiverperiode(
                    antallBrukteDager = 14,
                    oppbruktArbeidsgiverperiode = false,
                    arbeidsgiverPeriode = Periode(basisdato.minusDays(3), basisdato.minusDays(3).plusDays(16)),
                ),
        )
        kafkaProducer.send(
            ProducerRecord(
                SYKMELDINGSENDT_TOPIC,
                sykmeldingKafkaMessage.sykmelding.id,
                sykmeldingKafkaMessage.copy(
                    event =
                        sykmeldingKafkaMessage.event.copy(
                            erSvarOppdatering = true,
                            sporsmals =
                                sykmeldingKafkaMessage.event.sporsmals!!.plus(
                                    SporsmalOgSvarKafkaDTO(
                                        tekst = "Brukte du egenmeldingsdager før sykmeldinga?",
                                        shortName = ShortNameKafkaDTO.EGENMELDINGSDAGER,
                                        svartype = SvartypeKafkaDTO.DAGER,
                                        svar = basisdato.minusDays(3).datesUntil(basisdato).toList().serialisertTilString(),
                                    ),
                                ),
                        ),
                ).serialisertTilString(),
            ),
        )

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 1)
        hentSoknaderMetadata(fnr).first().sendtTilNAVDato shouldBeEqualTo null
    }

    @Test
    @Order(4)
    fun `Korrigerer så til å være utenfor arbeidsgiverperioden og ettersendes til NAV`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleSykeforloep(
            sykmeldingKafkaMessage.sykmelding.id,
            sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.minOf { it.fom },
        )
        mockFlexSyketilfelleArbeidsgiverperiode(
            arbeidsgiverperiode =
                Arbeidsgiverperiode(
                    antallBrukteDager = 20,
                    oppbruktArbeidsgiverperiode = true,
                    arbeidsgiverPeriode = Periode(basisdato.minusDays(9), basisdato.minusDays(9).plusDays(16)),
                ),
        )
        kafkaProducer.send(
            ProducerRecord(
                SYKMELDINGSENDT_TOPIC,
                sykmeldingKafkaMessage.sykmelding.id,
                sykmeldingKafkaMessage.copy(
                    event =
                        sykmeldingKafkaMessage.event.copy(
                            erSvarOppdatering = true,
                            sporsmals =
                                sykmeldingKafkaMessage.event.sporsmals!!.plus(
                                    SporsmalOgSvarKafkaDTO(
                                        tekst = "Brukte du egenmeldingsdager før sykmeldinga?",
                                        shortName = ShortNameKafkaDTO.EGENMELDINGSDAGER,
                                        svartype = SvartypeKafkaDTO.DAGER,
                                        svar = basisdato.minusDays(9).datesUntil(basisdato).toList().serialisertTilString(),
                                    ),
                                ),
                        ),
                ).serialisertTilString(),
            ),
        )

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 3)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)

        await().atMost(10, TimeUnit.SECONDS).until {
            hentSoknaderMetadata(fnr).first().sendtTilNAVDato != null
        }
    }
}
