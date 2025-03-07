package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.medlemskap.tilPostgresJson
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.util.*

class FriskTilArbeidSoknadIntegrationTest : FellesTestOppsett() {
    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var friskTilArbeidRepository: FriskTilArbeidRepository

    @Autowired
    lateinit var soknadProducer: SoknadProducer

    @Test
    fun `Lagre og les`() {
        val fnr = "11111111111"

        val lagretVedtak =
            friskTilArbeidRepository.save(
                FriskTilArbeidVedtakDbRecord(
                    vedtakUuid = UUID.randomUUID().toString(),
                    key = fnr.asProducerRecordKey(),
                    opprettet = Instant.now(),
                    fnr = fnr,
                    fom = LocalDate.now(),
                    tom = LocalDate.now().plusWeeks(2),
                    behandletStatus = BehandletStatus.NY,
                    vedtak = "{}".tilPostgresJson(),
                ),
            )

        val soknad =
            Sykepengesoknad(
                id = UUID.randomUUID().toString(),
                fnr = fnr,
                soknadstype = Soknadstype.FRISKMELDT_TIL_ARBEIDSFORMIDLING,
                status = Soknadstatus.FREMTIDIG,
                opprettet = Instant.now(),
                sporsmal = emptyList(),
                sykmeldingId = null,
                fom = LocalDate.now(),
                tom = LocalDate.now().plusWeeks(2),
                startSykeforlop = null,
                sykmeldingSkrevet = null,
                utenlandskSykmelding = false,
                egenmeldingsdagerFraSykmelding = null,
                forstegangssoknad = null,
                friskTilArbeidVedtakId = lagretVedtak.id,
            )

        sykepengesoknadDAO.lagreSykepengesoknad(soknad).also {
            it.friskTilArbeidVedtakId `should be equal to` lagretVedtak.id
        }

        hentSoknaderMetadata(fnr).first().also {
            it.friskTilArbeidVedtakId `should be equal to` lagretVedtak.id
        }

        hentSoknader(fnr).first().also {
            it.friskTilArbeidVedtakId `should be equal to` lagretVedtak.id
        }

        soknadProducer.soknadEvent(soknad)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first().also {
            it.friskTilArbeidVedtakId `should be equal to` lagretVedtak.id
        }
    }
}
