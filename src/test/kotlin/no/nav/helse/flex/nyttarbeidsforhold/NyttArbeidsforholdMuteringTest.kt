package no.nav.helse.flex.nyttarbeidsforhold

import no.nav.helse.flex.*
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.testutil.SoknadBesvarer
import org.amshove.kluent.*
import org.junit.jupiter.api.*
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class NyttArbeidsforholdMuteringTest : NyttArbeidsforholdFellesOppsett() {
    @Test
    @Order(2)
    fun `Har forventa nytt arbeidsforhold førstegangsspørsmål`() {
        val soknaden = hentSoknader(fnr = fnr).first()

        val nyttArbeidsforholdSpm =
            soknaden.sporsmal!!.find {
                it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS
            }!!
        @Suppress("ktlint:standard:max-line-length")
        nyttArbeidsforholdSpm.sporsmalstekst `should be equal to` "Har du jobbet noe hos Kiosken, avd Oslo AS i perioden 26. august - 15. september 2024?"
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedOrgnummer").textValue() `should be equal to` "999888777"
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedNavn").textValue() `should be equal to` "Kiosken, avd Oslo AS"
        nyttArbeidsforholdSpm.metadata!!.get("startdatoAareg").textValue() `should be equal to` "2024-09-05"
        nyttArbeidsforholdSpm.metadata!!.get("fom").textValue() `should be equal to` "2024-08-26"
        nyttArbeidsforholdSpm.metadata!!.get("tom").textValue() `should be equal to` "2024-09-15"
    }

    @Test
    @Order(3)
    fun `Svarer tilbake i arbeid dag 1`() {
        val soknaden = hentSoknader(fnr = fnr).first()

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .tilbakeIArbeid(soknaden.fom!!)
    }

    @Test
    @Order(4)
    fun `Spørsmålet er mutert bort`() {
        val soknaden = hentSoknader(fnr = fnr).first()

        soknaden.sporsmal!!.find {
            it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS
        }.shouldBeNull()
    }

    @Test
    @Order(5)
    fun `Svarer tilbake i arbeid dag 2`() {
        val soknaden = hentSoknader(fnr = fnr).first()

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .tilbakeIArbeid(soknaden.fom!!.plusDays(1))
    }

    @Test
    @Order(6)
    fun `Spørsmålet er fortsatt mutert bort fordi det var før startdatoen`() {
        val soknaden = hentSoknader(fnr = fnr).first()

        soknaden.sporsmal!!.find {
            it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS
        }.shouldBeNull()
    }

    @Test
    @Order(7)
    fun `Svarer tilbake i arbeid dagen etter startdatoen`() {
        val soknaden = hentSoknader(fnr = fnr).first()
        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .tilbakeIArbeid(LocalDate.of(2024, 9, 6))
    }

    @Test
    @Order(8)
    fun `Spørsmålet er tilbake`() {
        val soknaden = hentSoknader(fnr = fnr).first()

        val nyttArbeidsforholdSpm =
            soknaden.sporsmal!!.find {
                it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS
            }!!
        @Suppress("ktlint:standard:max-line-length")
        nyttArbeidsforholdSpm.sporsmalstekst `should be equal to` "Har du jobbet noe hos Kiosken, avd Oslo AS i perioden 26. august - 5. september 2024?"
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedOrgnummer").textValue() `should be equal to` "999888777"
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedNavn").textValue() `should be equal to` "Kiosken, avd Oslo AS"
        nyttArbeidsforholdSpm.metadata!!.get("startdatoAareg").textValue() `should be equal to` "2024-09-05"
        nyttArbeidsforholdSpm.metadata!!.get("fom").textValue() `should be equal to` "2024-08-26"
        nyttArbeidsforholdSpm.metadata!!.get("tom").textValue() `should be equal to` "2024-09-05"
    }

    @Test
    @Order(9)
    fun `Svarer tilbake i arbeid på startdatoen`() {
        val soknaden = hentSoknader(fnr = fnr).first()
        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .tilbakeIArbeid(LocalDate.of(2024, 9, 5))
    }

    @Test
    @Order(10)
    fun `Spørsmålet er ikke der på startdatoen`() {
        val soknaden = hentSoknader(fnr = fnr).first()
        soknaden.sporsmal!!.find {
            it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS
        }.shouldBeNull()
    }

    @Test
    @Order(11)
    fun `Svarer ikke tilbake i arbeid`() {
        val soknaden = hentSoknader(fnr = fnr).first()
        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .tilbakeIArbeid(dato = null)
    }

    @Test
    @Order(12)
    fun `Er tilbake på originalt spørsmål`() {
        val soknaden = hentSoknader(fnr = fnr).first()

        val nyttArbeidsforholdSpm =
            soknaden.sporsmal!!.find {
                it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS
            }!!
        @Suppress("ktlint:standard:max-line-length")
        nyttArbeidsforholdSpm.sporsmalstekst `should be equal to` "Har du jobbet noe hos Kiosken, avd Oslo AS i perioden 26. august - 15. september 2024?"
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedOrgnummer").textValue() `should be equal to` "999888777"
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedNavn").textValue() `should be equal to` "Kiosken, avd Oslo AS"
        nyttArbeidsforholdSpm.metadata!!.get("startdatoAareg").textValue() `should be equal to` "2024-09-05"
        nyttArbeidsforholdSpm.metadata!!.get("fom").textValue() `should be equal to` "2024-08-26"
        nyttArbeidsforholdSpm.metadata!!.get("tom").textValue() `should be equal to` "2024-09-15"
    }
}
