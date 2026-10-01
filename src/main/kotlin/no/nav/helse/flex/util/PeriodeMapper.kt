package no.nav.helse.flex.util

import no.nav.helse.flex.domain.Periode
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.module.kotlin.kotlinModule
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

object PeriodeMapper {
    val sporsmalstekstFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")!!

    // ISO-8601 er standardformatet til Jackson, så den delte objectMapper brukes for det.
    private val objectMapperSporsmalstekstFormat: ObjectMapper =
        JsonMapper
            .builder()
            .addModule(kotlinModule())
            .addModule(
                SimpleModule().addDeserializer(
                    LocalDate::class.java,
                    LocalDateDeserializer(sporsmalstekstFormat),
                ),
            ).medFlexEnumOppsett()
            .build()

    fun jsonISOFormatTilPeriode(json: String): Periode = getPeriode(json, objectMapper)

    fun jsonSporsmalstektsFormatTilPeriode(json: String): Periode = getPeriode(json, objectMapperSporsmalstekstFormat)

    private fun getPeriode(
        json: String,
        mapper: ObjectMapper,
    ): Periode =
        try {
            val periode = mapper.readValue(json, Periode::class.java)
            require(!periode.fom.isAfter(periode.tom))
            periode
        } catch (exception: JacksonException) {
            throw IllegalArgumentException(exception)
        }

    fun jsonTilOptionalPeriode(json: String): Optional<Periode> =
        try {
            Optional.of(getPeriode(json, objectMapper))
        } catch (illegalArgumentException: IllegalArgumentException) {
            try {
                Optional.of(getPeriode(json, objectMapperSporsmalstekstFormat))
            } catch (illegalArgumentException2: IllegalArgumentException) {
                Optional.empty()
            }
        }
}
