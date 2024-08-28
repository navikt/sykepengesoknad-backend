@file:Suppress("ktlint:standard:max-line-length")

package no.nav.helse.flex.svarvalidering

import no.nav.helse.flex.domain.*
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.oppholdUtenforEOSSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.tilSlutt
import no.nav.helse.flex.soknadsopprettelse.sporsmal.tilSluttGammel
import no.nav.helse.flex.testutil.byttSvar
import no.nav.helse.flex.util.DatoUtil.periodeTilJson
import no.nav.helse.flex.util.serialisertTilString
import org.amshove.kluent.`should be false`
import org.amshove.kluent.`should be true`
import org.amshove.kluent.shouldStartWith
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.*

internal class SvarValideringTest {
    @Test
    fun `test er double med max en desimal`() {
        "34.2".`er double med max en desimal`().`should be true`()
        "34".`er double med max en desimal`().`should be true`()
        "34.0".`er double med max en desimal`().`should be true`()
        "-34.0".`er double med max en desimal`().`should be true`()

        "34.".`er double med max en desimal`().`should be false`()
        "34,2".`er double med max en desimal`().`should be false`()
        "34.20".`er double med max en desimal`().`should be false`()
        "aergaerg".`er double med max en desimal`().`should be false`()
        "".`er double med max en desimal`().`should be false`()
    }

    @Test
    fun `test er heltall`() {
        "3".`er heltall`().`should be true`()
        "-3".`er heltall`().`should be true`()
        "0".`er heltall`().`should be true`()
        "000".`er heltall`().`should be true`()
        "34".`er heltall`().`should be true`()

        "34.0".`er heltall`().`should be false`()
        "3.0".`er heltall`().`should be false`()
        "34,2".`er heltall`().`should be false`()
        "aergaerg".`er heltall`().`should be false`()
        "".`er heltall`().`should be false`()
    }

    @Test
    fun `test er dato`() {
        "2020-03-12".`er dato`().`should be true`()

        "2020-02-30".`er dato`().`should be false`()
        "2020-3-12".`er dato`().`should be false`()
        "34.0".`er dato`().`should be false`()
        "aergaerg".`er dato`().`should be false`()
        "".`er dato`().`should be false`()
    }

    @Test
    fun `test er uuid`() {
        "d4ee3021-4021-45c4-aa82-4af38bd99505".`er uuid`().`should be true`()
        UUID.randomUUID().toString().`er uuid`().`should be true`()

        "d4ee3021-4021-45c4-aa82-4af38bd99505a".`er uuid`().`should be false`()
        "d4ee3021-4021-45c4-aa82-4af38bd9950".`er uuid`().`should be false`()
        "d4ee3021402145c4aa824af38bd99505".`er uuid`().`should be false`()
    }

    @Test
    fun `test validering av beløp grenseverdi`() {
        "d4ee3021-4021-45c4-aa82-4af38bd99505".`er uuid`().`should be true`()
        UUID.randomUUID().toString().`er uuid`().`should be true`()

        "d4ee3021-4021-45c4-aa82-4af38bd99505a".`er uuid`().`should be false`()
        "d4ee3021-4021-45c4-aa82-4af38bd9950".`er uuid`().`should be false`()
        "d4ee3021402145c4aa824af38bd99505".`er uuid`().`should be false`()
    }

    @Test
    fun `test validering av beløp spørsmål`() {
        val spm = offentligTransportBeløpSpørsmål()

        spm `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag OFFENTLIG_TRANSPORT_BELOP har feil antall svar 0"

        spm.byttSvar(svar = "1.0") `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag OFFENTLIG_TRANSPORT_BELOP har feil svarverdi 1.0"
        spm.byttSvar(svar = "-1") `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag OFFENTLIG_TRANSPORT_BELOP har svarverdi utenfor grenseverdi -1"
        spm.byttSvar(svar = "0").validerSvarPaSporsmal()
        spm.byttSvar(svar = "1").validerSvarPaSporsmal()
    }

    @Test
    fun `test validering av kvittering`() {
        val spm = kvitteringSporsmal(formattertPeriode = " okok")

        // Ingen svar er ok
        spm.validerSvarPaSporsmal()

        spm.byttSvar(svar = "ikke jsånn") `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag KVITTERINGER har feil svarverdi ikke jsånn"
        val kvittering =
            Kvittering(
                belop = 234,
                blobId = UUID.randomUUID().toString(),
                opprettet = Instant.now(),
                typeUtgift = Utgiftstype.OFFENTLIG_TRANSPORT,
            )
        spm.byttSvar(svar = kvittering.serialisertTilString()).validerSvarPaSporsmal()

        // krever uuid
        spm.byttSvar(svar = kvittering.copy(blobId = "ikkeuuid").serialisertTilString()) `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag KVITTERINGER har feil svarverdi"

        // Krever positivt beløp
        spm.byttSvar(svar = kvittering.copy(belop = -20).serialisertTilString()) `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag KVITTERINGER har feil svarverdi"

        // Feiler når råteksten er for lang for databasen
        spm.byttSvar(svar = kvittering.serialisertTilString().padEnd(1000)) `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag KVITTERINGER har svarverdi utenfor grenseverdi"
    }

    @Test
    fun `test reiseMedBilSpørsmål`() {
        val spm =
            reiseMedBilSpørsmål(fom = LocalDate.now(), tom = LocalDate.now().plusDays(2), formattertPeriode = " okok")

        spm `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag REISE_MED_BIL har feil antall svar 0"

        spm.byttSvar(svar = "NEI").validerSvarPaSporsmal()
        spm.byttSvar(svar = "KANSKJE") `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag $REISE_MED_BIL har feil svarverdi KANSKJE"

        spm.byttSvar(svar = "JA") `valider svar og forvent feilmelding` "Spørsmål ${spm.idForTag(BIL_DATOER)} med tag $BIL_DATOER har feil antall svar 0"

        val igår = LocalDate.now().minusDays(1).toString()

        spm.byttSvar(svar = "JA")
            .byttSvar(tag = BIL_DATOER, svar = igår)
            .`valider svar og forvent feilmelding`(
                "Spørsmål ${spm.idForTag(BIL_DATOER)} med tag $BIL_DATOER har svarverdi utenfor grenseverdi $igår",
            )

        spm.byttSvar(svar = "JA")
            .byttSvar(tag = BIL_DATOER, svar = LocalDate.now().toString())
            .byttSvar(tag = BIL_BOMPENGER, svar = "NEI")
            .`valider svar og forvent feilmelding`("Spørsmål ${spm.idForTag(KM_HJEM_JOBB)} med tag $KM_HJEM_JOBB har feil antall svar 0")

        spm.byttSvar(svar = "JA")
            .byttSvar(tag = BIL_DATOER, svar = LocalDate.now().toString())
            .byttSvar(tag = BIL_BOMPENGER, svar = "NEI")
            .byttSvar(tag = KM_HJEM_JOBB, svar = "4.45")
            .`valider svar og forvent feilmelding`("Spørsmål ${spm.idForTag(KM_HJEM_JOBB)} med tag $KM_HJEM_JOBB har feil svarverdi 4.45")

        spm.byttSvar(svar = "JA")
            .byttSvar(tag = BIL_DATOER, svar = LocalDate.now().toString())
            .byttSvar(tag = BIL_BOMPENGER, svar = "NEI")
            .byttSvar(tag = KM_HJEM_JOBB, svar = "4.4")
            .validerSvarPaSporsmal()

        spm.byttSvar(svar = "JA")
            .byttSvar(tag = BIL_DATOER, svar = LocalDate.now().toString())
            .byttSvar(tag = BIL_BOMPENGER, svar = "JA")
            .byttSvar(tag = KM_HJEM_JOBB, svar = "4.4")
            .`valider svar og forvent feilmelding`(
                "Spørsmål ${spm.idForTag(BIL_BOMPENGER_BELOP)} med tag $BIL_BOMPENGER_BELOP har feil antall svar 0",
            )

        spm.byttSvar(svar = "JA")
            .byttSvar(tag = BIL_DATOER, svar = LocalDate.now().toString())
            .byttSvar(tag = BIL_BOMPENGER, svar = "JA")
            .byttSvar(tag = BIL_BOMPENGER_BELOP, svar = "1000")
            .byttSvar(tag = KM_HJEM_JOBB, svar = "4.4")
            .validerSvarPaSporsmal()

        spm.byttSvar(svar = "JA")
            .byttSvar(tag = BIL_DATOER, svar = listOf(LocalDate.now().toString(), LocalDate.now().toString()))
            .`valider svar og forvent feilmelding`("Spørsmål ${spm.idForTag(BIL_DATOER)} med tag $BIL_DATOER har duplikate svar")
    }

    @Test
    fun `test transport til daglig spørsmål`() {
        val spm = transportTilDagligSpørsmål()

        spm `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag $TRANSPORT_TIL_DAGLIG har feil antall svar 0"

        spm.byttSvar(svar = "NEI")
            .validerSvarPaSporsmal()

        spm.byttSvar(svar = "JA")
            .`valider svar og forvent feilmelding`(
                "Spørsmål ${spm.idForTag(TYPE_TRANSPORT)} av typen ${Svartype.CHECKBOX_GRUPPE} må ha minst ett besvart underspørsmål",
            )

        spm.byttSvar(svar = "JA")
            .byttSvar(tag = BIL_TIL_DAGLIG, svar = "CHECKED")
            .validerSvarPaSporsmal()
    }

    @Test
    fun `test land spørsmål`() {
        val spm = landSporsmal()

        spm `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag $LAND har feil antall svar 0"

        spm.byttSvar(svar = "Sverige").validerSvarPaSporsmal()
    }

    @Test
    fun `test periode utland spørsmål`() {
        val spm = periodeSporsmal()

        spm `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag $PERIODEUTLAND har feil antall svar 0"

        spm.byttSvar(svar = periodeTilJson(LocalDate.now(), LocalDate.now())).validerSvarPaSporsmal()
    }

    @Test
    fun `test opphold utenfor EØS spørsmål`() {
        val spm = oppholdUtenforEOSSporsmal(LocalDate.now(), LocalDate.now())

        spm `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag $OPPHOLD_UTENFOR_EOS har feil antall svar 0"

        spm.byttSvar(svar = "NEI").validerSvarPaSporsmal()

        spm.byttSvar(svar = "JA") `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag $OPPHOLD_UTENFOR_EOS_NAR har feil antall svar 0"

        spm.byttSvar(tag = OPPHOLD_UTENFOR_EOS, svar = "JA")
            .byttSvar(tag = OPPHOLD_UTENFOR_EOS_NAR, svar = periodeTilJson(LocalDate.now(), LocalDate.now()))
            .validerSvarPaSporsmal()
    }

    @Test
    fun `test TIL_SLUTT`() {
        val spm = tilSlutt()
        spm `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag $TIL_SLUTT har feil antall svar 0"

        spm.byttSvar(svar = "true").validerSvarPaSporsmal()
    }

    @Test
    fun `test TIL_SLUTT med BEKREFT_OPPLYSNINGER`() {
        val spm = tilSluttGammel()
        spm `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag $TIL_SLUTT har feil antall svar 0"

        spm.byttSvar(svar = "true").validerSvarPaSporsmal()
    }

    private fun Sporsmal.idForTag(tag: String): String? {
        return listOf(this).flatten().first { it.tag == tag }.id
    }

    private fun String.`er double med max en desimal`() = this.erDoubleMedMaxEnDesimal()

    private fun String.`er heltall`() = this.erHeltall()

    private fun String.`er dato`() = this.erDato()

    private fun String.`er uuid`() = this.erUUID()

    private infix fun Sporsmal.`valider svar og forvent feilmelding`(s: String) {
        try {
            validerSvarPaSporsmal()
            fail("Forventer exeption")
        } catch (e: ValideringException) {
            e.message?.shouldStartWith(s)
        }
    }
}
