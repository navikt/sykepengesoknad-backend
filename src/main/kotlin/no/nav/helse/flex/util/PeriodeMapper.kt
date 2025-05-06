package no.nav.helse.flex.util

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.nav.helse.flex.domain.Periode
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

object PeriodeMapper {
    val sporsmalstekstFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")!!
    private val objectMapperISOFormat =
        ObjectMapper()
            .registerModule(JavaTimeModule())
            .registerKotlinModule()

    private val javaTimeModuleSporsmalstektsFormat =
        JavaTimeModule().addDeserializer(
            LocalDate::class.java,
            LocalDateDeserializer(
                sporsmalstekstFormat,
            ),
        )!!

    private val objectMapperSporsmalstekstFormat =
        ObjectMapper()
            .registerModule(javaTimeModuleSporsmalstektsFormat)
            .registerKotlinModule()

    fun jsonISOFormatTilPeriode(json: String): Periode = getPeriode(json, objectMapperISOFormat)

    fun jsonSporsmalstektsFormatTilPeriode(json: String): Periode = getPeriode(json, objectMapperSporsmalstekstFormat)

    private fun getPeriode(
        json: String,
        objectMapper: ObjectMapper?,
    ): Periode =
        try {
            val periode = objectMapper!!.readValue(json, Periode::class.java)
            require(!periode.fom.isAfter(periode.tom))
            periode
        } catch (exception: JsonParseException) {
            throw IllegalArgumentException(exception)
        } catch (exception: JsonMappingException) {
            throw IllegalArgumentException(exception)
        } catch (iOException: IOException) {
            throw RuntimeException(iOException)
        }

    fun jsonTilOptionalPeriode(json: String): Optional<Periode> =
        try {
            Optional.of(getPeriode(json, objectMapperISOFormat))
        } catch (illegalArgumentException: IllegalArgumentException) {
            try {
                Optional.of(getPeriode(json, objectMapperSporsmalstekstFormat))
            } catch (illegalArgumentException2: IllegalArgumentException) {
                Optional.empty()
            }
        }
}
