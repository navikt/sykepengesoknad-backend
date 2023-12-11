package no.nav.helse.flex.svarvalidering

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.domain.Kvittering
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Svartype.*
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.flatten
import no.nav.helse.flex.exception.AbstractApiError
import no.nav.helse.flex.exception.LogLevel
import no.nav.helse.flex.soknadsopprettelse.FERIE
import no.nav.helse.flex.util.DatoUtil
import no.nav.helse.flex.util.OBJECT_MAPPER
import no.nav.helse.flex.util.PeriodeMapper
import org.springframework.http.HttpStatus.BAD_REQUEST
import java.lang.Double.isNaN
import java.time.LocalDate
import java.util.*

fun Sykepengesoknad.validerSvarPaSoknad() {
    validerSpesialsvarPaSoknad()
    sporsmal.forEach { it.validerSvarPaSporsmal() }
}

private const val INGEN_BEHANDLING = "Ikke til behandling"

fun Sporsmal.validerSvarPaSporsmal() {
    validerAntallSvar()
    validerSvarverdier()
    validerKunUnikeSvar()
    validerGrenserPaSvar()
    validerUndersporsmal()
}

fun Sykepengesoknad.validerSpesialsvarPaSoknad() {
    if (soknadstype == Soknadstype.OPPHOLD_UTLAND) {
        sporsmal.flatten().find { it.tag == FERIE }?.let {
            if (it.forsteSvar == "JA") {
                throw ValideringException("Kan ikke sende OPPHOLD_UTLAND søknad $id med svar JA på spørsmål FERIE")
            }
        }
    }
}

fun Sporsmal.validerUndersporsmal() {
    val besvarteUndersporsmal = undersporsmal.filter { it.svar.isNotEmpty() }

    when (svartype) {
        CHECKBOX_GRUPPE -> {
            if (besvarteUndersporsmal.isEmpty()) {
                throw ValideringException("Spørsmål ${this.id} av typen $svartype må ha minst ett besvart underspørsmål")
            } else {
                besvarteUndersporsmal.forEach { it.validerSvarPaSporsmal() }
            }
        }

        RADIO_GRUPPE,
        RADIO_GRUPPE_TIMER_PROSENT -> {
            if (besvarteUndersporsmal.size == 1) {
                besvarteUndersporsmal.forEach { it.validerSvarPaSporsmal() }
            } else {
                throw ValideringException("Spørsmål ${this.id} av typen $svartype må ha eksakt ett besvart underspørsmål")
            }
        }

        BEKREFTELSESPUNKTER,
        JA_NEI,
        CHECKBOX,
        CHECKBOX_PANEL,
        DATOER,
        BELOP,
        KILOMETER,
        DATO,
        PERIODE,
        PERIODER,
        TIMER,
        FRITEKST,
        LAND,
        COMBOBOX_SINGLE,
        COMBOBOX_MULTI,
        IKKE_RELEVANT,
        GRUPPE_AV_UNDERSPORSMAL,
        PROSENT,
        RADIO,
        TALL,
        INFO_BEHANDLINGSDAGER,
        RADIO_GRUPPE_UKEKALENDER,
        KVITTERING -> {
            if (kriterieForVisningAvUndersporsmal != null) {
                if (svar.size == 1) {
                    if (svar.first().verdi == kriterieForVisningAvUndersporsmal.name) {
                        undersporsmal.forEach { it.validerSvarPaSporsmal() }
                    }
                }
            } else {
                undersporsmal.forEach { it.validerSvarPaSporsmal() }
            }
        }
    }
}

private fun String.tilLocalDate(): LocalDate {
    return LocalDate.parse(this)
}

fun String.tilKvittering(): Kvittering {
    return OBJECT_MAPPER.readValue(this)
}

private fun Sporsmal.validerGrenserPaDato(svar: Svar): () -> Boolean {
    val dato = LocalDate.parse(svar.verdi)
    val minDato = this.min?.tilLocalDate()
    val maxDato = this.max?.tilLocalDate()
    var validerer = true

    if (minDato != null && minDato.isAfter(dato)) {
        validerer = false
    }

    if (maxDato != null && maxDato.isBefore(dato)) {
        validerer = false
    }
    return { validerer }
}

private fun Sporsmal.validerGrenserPaPeriode(svar: Svar): () -> Boolean {
    val periode = PeriodeMapper.jsonISOFormatTilPeriode(svar.verdi)
    return { DatoUtil.periodeErInnenforMinMax(periode, min, max) }
}

private fun Sporsmal.validerGrenserPaaTall(svar: Svar): () -> Boolean {
    val verdi = svar.verdi.tilDoubleOgGodtaKomma()
    val min = this.min?.tilDoubleOgGodtaKomma()
    val max = this.max?.tilDoubleOgGodtaKomma()
    var validerer = true
    if (min != null && verdi < min) {
        validerer = false
    }
    if (max != null && verdi > max) {
        validerer = false
    }
    return { validerer }
}

private fun Sporsmal.validerGrenserPaaTekst(svar: Svar): () -> Boolean {
    val min = this.min?.tilDoubleOgGodtaKomma()
    val max = this.max?.tilDoubleOgGodtaKomma()
    var validerer = true
    if (min != null && svar.verdi.length < min) {
        validerer = false
    }
    if (max != null && svar.verdi.length > max) {
        validerer = false
    }
    return { validerer }
}

private fun validerGrenserPaKvittering(svar: Svar): () -> Boolean {
    return { svar.verdi.length <= 200 }
}

private fun Sporsmal.validerGrenserPaSvar(svar: Svar) {
    val predikat: () -> Boolean = when (svartype) {
        JA_NEI,
        CHECKBOX,
        CHECKBOX_PANEL,
        CHECKBOX_GRUPPE,
        IKKE_RELEVANT,
        GRUPPE_AV_UNDERSPORSMAL,
        BEKREFTELSESPUNKTER,
        RADIO,
        RADIO_GRUPPE,
        RADIO_GRUPPE_TIMER_PROSENT,
        INFO_BEHANDLINGSDAGER -> {
            { true }
        }

        DATO,
        DATOER -> validerGrenserPaDato(svar)

        KVITTERING -> validerGrenserPaKvittering(svar)
        RADIO_GRUPPE_UKEKALENDER -> {
            { svar.verdi == INGEN_BEHANDLING || validerGrenserPaDato(svar)() }
        }

        PROSENT,
        TIMER,
        TALL,
        BELOP,
        KILOMETER -> validerGrenserPaaTall(svar)

        FRITEKST,
        COMBOBOX_SINGLE,
        COMBOBOX_MULTI,
        LAND -> validerGrenserPaaTekst(svar)

        PERIODE,
        PERIODER -> validerGrenserPaPeriode(svar)
    }
    if (!predikat()) {
        throw ValideringException("Spørsmål $id med tag $tag har svarverdi utenfor grenseverdi ${svar.verdi}")
    }
}

fun Sporsmal.validerGrenserPaSvar() {
    svar.forEach { this.validerGrenserPaSvar(it) }
}

private fun validerKvittering(verdi: String): () -> Boolean {
    val kvittering = try {
        verdi.tilKvittering()
    } catch (e: Exception) {
        return { false }
    }
    return { kvittering.blobId.erUUID() && kvittering.belop >= 0 }
}

fun String.erUUID(): Boolean {
    return try {
        UUID.fromString(this)
        this.length == 36
    } catch (e: Exception) {
        false
    }
}

fun String.erHeltall(): Boolean {
    return try {
        this.toInt()
        true
    } catch (e: Exception) {
        false
    }
}

fun String.erFlyttall(): Boolean {
    return try {
        !isNaN(this.tilDoubleOgGodtaKomma())
    } catch (e: Exception) {
        false
    }
}

fun String.tilDoubleOgGodtaKomma(): Double {
    return this.replace(',', '.').toDouble()
}

fun String.erPeriode(): Boolean {
    return try {
        PeriodeMapper.jsonISOFormatTilPeriode(this)
        true
    } catch (e: Exception) {
        false
    }
}

fun String.erDato(): Boolean {
    return try {
        LocalDate.parse(this)
        true
    } catch (e: Exception) {
        false
    }
}

fun String.erDoubleMedMaxEnDesimal(): Boolean {
    return try {
        this.toBigDecimal()
        if (this.contains(".")) {
            return this.split(".")[1].length == 1
        } else {
            true
        }
    } catch (e: Exception) {
        false
    }
}

private fun Sporsmal.validerSvarverdi(svar: Svar) {
    val verdi = svar.verdi
    val predikat: () -> Boolean = when (svartype) {
        FRITEKST -> {
            if (min == null) {
                { true }
            } else {
                { verdi.trim().length >= min.toInt() }
            }
        }

        COMBOBOX_SINGLE,
        COMBOBOX_MULTI,
        BEKREFTELSESPUNKTER,
        LAND -> {
            { verdi.isNotBlank() && verdi.isNotEmpty() }
        }

        JA_NEI -> {
            { "JA" == verdi || "NEI" == verdi }
        }

        CHECKBOX_PANEL,
        RADIO,
        CHECKBOX -> {
            { "CHECKED" == verdi }
        }

        DATO,
        DATOER -> {
            { verdi.erDato() }
        }

        PROSENT,
        BELOP -> {
            { verdi.erHeltall() }
        }

        TIMER,
        TALL -> {
            { verdi.erFlyttall() }
        }

        KILOMETER -> {
            { verdi.erDoubleMedMaxEnDesimal() }
        }

        KVITTERING -> validerKvittering(svar.verdi)
        PERIODE,
        PERIODER -> {
            {
                verdi.erPeriode()
            }
        }

        RADIO_GRUPPE_UKEKALENDER -> {
            {
                verdi == INGEN_BEHANDLING || verdi.erDato()
            }
        }

        RADIO_GRUPPE,
        RADIO_GRUPPE_TIMER_PROSENT,
        IKKE_RELEVANT,
        GRUPPE_AV_UNDERSPORSMAL,
        INFO_BEHANDLINGSDAGER,
        CHECKBOX_GRUPPE -> throw IllegalStateException("Skal ha validert 0 svar allerede")
    }
    if (!predikat()) {
        throw ValideringException("Spørsmål $id med tag $tag har feil svarverdi $verdi")
    }
}

fun Sporsmal.validerSvarverdier() {
    svar.forEach { this.validerSvarverdi(it) }
}

private fun Sporsmal.validerKunUnikeSvar() {
    if (svar.size != svar.toSet().size) {
        throw ValideringException("Spørsmål $id med tag $tag har duplikate svar")
    }
}

fun Sporsmal.validerAntallSvar() {
    val predikat: (Int) -> Boolean = when (this.svartype) {
        JA_NEI,
        BELOP,
        KILOMETER,
        CHECKBOX_PANEL,
        DATO,
        RADIO_GRUPPE_UKEKALENDER,
        RADIO,
        PROSENT,
        PERIODE,
        TIMER,
        TALL,
        CHECKBOX -> {
            { it == 1 }
        }

        FRITEKST -> {
            {
                if (min == null) {
                    true
                } else {
                    it == 1
                }
            }
        }

        RADIO_GRUPPE,
        RADIO_GRUPPE_TIMER_PROSENT,
        IKKE_RELEVANT,
        GRUPPE_AV_UNDERSPORSMAL,
        INFO_BEHANDLINGSDAGER,
        CHECKBOX_GRUPPE -> {
            { it == 0 }
        }

        LAND,
        BEKREFTELSESPUNKTER,
        COMBOBOX_SINGLE,
        COMBOBOX_MULTI,
        PERIODER,
        DATOER -> {
            { it > 0 }
        }

        KVITTERING -> {
            { it >= 0 }
        }
    }
    if (!predikat(svar.size)) {
        throw ValideringException("Spørsmål $id med tag $tag har feil antall svar ${svar.size}")
    }
}

class ValideringException(message: String) : AbstractApiError(
    message = message,
    httpStatus = BAD_REQUEST,
    reason = "SPORSMALETS_SVAR_VALIDERER_IKKE",
    loglevel = LogLevel.WARN
)
