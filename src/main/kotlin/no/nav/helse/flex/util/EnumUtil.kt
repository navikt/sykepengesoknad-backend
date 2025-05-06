package no.nav.helse.flex.util

import java.util.*

object EnumUtil {
    fun <T : Enum<T>?, U : Enum<U>?> konverter(
        tClass: Class<T>?,
        from: U?,
    ): T = Optional.ofNullable(from).map { n: U -> java.lang.Enum.valueOf(tClass, n!!.name) }.orElse(null)

    fun <T : Enum<T>?> konverter(
        tClass: Class<T>?,
        from: String?,
    ): T =
        Optional
            .ofNullable(from)
            .map { n: String -> java.lang.Enum.valueOf(tClass, n) }
            .orElse(null)
}
