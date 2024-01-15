package no.nav.helse.flex

import no.nav.helse.flex.cronjob.SykeforlopFixJob
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class SykeforlopTest : BaseTestClass() {
    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var fixSykeforlopFixJob: SykeforlopFixJob

    private val fnr = "10101010101"
    private val fom = LocalDate.of(2023, 11, 27)
    private val tom = LocalDate.of(2023, 12, 1)
    private val startSykeforlop = LocalDate.of(2000, 6, 17)
    private val faktiskStartSykeforlop = LocalDate.of(2023, 11, 27)

    @BeforeEach
    @AfterEach
    fun setUp() {
        databaseReset.resetDatabase()
    }

    @Test
    fun test() {
        val kafkaSoknader =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = fom,
                            tom = tom,
                        ),
                ),
                oppfolgingsdato = startSykeforlop,
                forventaSoknader = 1,
            )

        val soknad: Sykepengesoknad
        sykepengesoknadDAO.finnSykepengesoknad(kafkaSoknader.first().id).let {
            it.fom shouldBeEqualTo fom
            it.tom shouldBeEqualTo tom
            it.startSykeforlop shouldBeEqualTo startSykeforlop
            soknad = it
        }

        fixSykeforlopFixJob.fix(
            listOf(
                SykeforlopFixJob.Soknad(
                    sykepengesoknad_uuid = soknad.id,
                    fom = soknad.fom!!,
                    tom = soknad.tom!!,
                    startSykeforlop = startSykeforlop,
                    faktiskStartSykeforlop = faktiskStartSykeforlop,
                ),
            ),
        )

        sykepengesoknadDAO.finnSykepengesoknad(kafkaSoknader.first().id) shouldBeEqualTo
            soknad.copy(
                startSykeforlop = faktiskStartSykeforlop,
            )
    }
}
