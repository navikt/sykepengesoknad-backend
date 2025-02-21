package no.nav.helse.flex.frisktilarbeid.sporsmal

import no.nav.helse.flex.*
import no.nav.helse.flex.aktivering.SoknadAktivering
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.frisktilarbeid.*
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.testutil.SoknadBesvarer
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FTASporsmalMuteringTest() : FakesTestOppsett() {
    @Autowired
    lateinit var friskTilArbeidCronJob: FriskTilArbeidCronJob

    @Autowired
    lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    @Autowired
    lateinit var aktivering: SoknadAktivering

    private val fnr = "11111111111"

    @Test
    @Order(1)
    fun `Oppretter en ny friskmeldt søknad`() {
        val key = sendFtaVedtak(fnr, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 28))
        friskTilArbeidCronJob.startBehandlingAvFriskTilArbeidVedtakStatus()
        val friskTilArbeidDbRecord =
            friskTilArbeidRepository.findAll().first { it.key == key }
        sykepengesoknadRepository.findByFriskTilArbeidVedtakId(friskTilArbeidDbRecord.id!!).shouldHaveSize(2)
    }

    @Test
    @Order(2)
    fun `Muterer bort ved jobb første dag`() {
        val soknad = hentSoknader(fnr).first { it.status == RSSoknadstatus.NY }
        SoknadBesvarer(rSSykepengesoknad = soknad, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_JA, "CHECKED", false)
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_NY_JOBB, "NEI", false)
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_NAR, "2020-01-01", true, mutert = true)
            .also {
                assertThat(it.muterteSoknaden).isTrue()
            }
    }

    @Test
    @Order(3)
    fun `To spørsmål forsvant`() {
        hentSoknader(fnr)
            .first { it.status == RSSoknadstatus.NY }
            .sporsmal!!
            .map { it.tag } `should be equal to`
            listOf(
                ANSVARSERKLARING,
                FTA_JOBBSITUASJONEN_DIN,
                TIL_SLUTT,
            )
    }

    @Test
    @Order(4)
    fun `Muterer tilbake`() {
        val soknad = hentSoknader(fnr).first { it.status == RSSoknadstatus.NY }
        SoknadBesvarer(rSSykepengesoknad = soknad, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_JA, null, false)
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_NEI, "CHECKED", false)
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT, "NEI", false)
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_AVREGISTRERT_NAR, "2020-01-02", true, mutert = true)
            .also {
                assertThat(it.muterteSoknaden).isTrue()
            }
    }

    @Test
    @Order(5)
    fun `To spørsmål tilbake`() {
        val soknaden =
            hentSoknader(fnr)
                .first { it.status == RSSoknadstatus.NY }
        soknaden
            .sporsmal!!
            .map { it.tag } `should be equal to`
            listOf(
                ANSVARSERKLARING,
                FTA_JOBBSITUASJONEN_DIN,
                FTA_INNTEKT_UNDERVEIS,
                FTA_REISE_TIL_UTLANDET,
                TIL_SLUTT,
            )

        soknaden.sporsmal!!.first {
            it.tag == FTA_INNTEKT_UNDERVEIS
        }.sporsmalstekst `should be equal to` "Hadde du  inntekt i perioden 1. - 1. januar 2020?"
        soknaden.sporsmal!!.first {
            it.tag == FTA_REISE_TIL_UTLANDET
        }.sporsmalstekst `should be equal to` "Var du på reise utenfor EU/EØS i perioden 1. - 1. januar 2020?"
    }

    @Test
    @Order(6)
    fun `Svaret på neste spørsmål muterer ikke`() {
        val soknad = hentSoknader(fnr).first { it.status == RSSoknadstatus.NY }
        SoknadBesvarer(rSSykepengesoknad = soknad, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(FTA_INNTEKT_UNDERVEIS, "NEI", false)
            .also {
                assertThat(it.muterteSoknaden).isFalse()
            }
    }
}
